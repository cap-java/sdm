#!/usr/bin/env node

/**
 * Self-Healing CI - Failure Analysis Script
 * 
 * This script analyzes workflow failure logs and classifies the failure type,
 * generates a diagnosis, and recommends actions.
 */

const fs = require('fs');
const path = require('path');
const yaml = require('yaml');

// Parse command line arguments
const args = process.argv.slice(2);
const getArg = (name) => {
  const index = args.indexOf(`--${name}`);
  return index !== -1 ? args[index + 1] : null;
};

const logsPath = getArg('logs');
const runDetailsPath = getArg('run-details');
const jobsPath = getArg('jobs');
const configPath = getArg('config');

// Load configuration
let config = {
  mode: 'assist',
  classification: {
    code: {
      patterns: [
        'BUILD FAILURE', 'COMPILATION ERROR', 'Test.*failed', 'Tests run:.*Failures:',
        'error: cannot find symbol', 'error: incompatible types', 'SyntaxError',
        'TypeError', 'eslint.*error', 'checkstyle.*ERROR', 'spotless'
      ],
      auto_fix_enabled: false
    },
    workflow: {
      patterns: [
        'Invalid workflow file', 'unexpected value', 'action.*not found',
        'uses:.*@.*not found', 'permission.*denied', 'Required secret.*not found',
        'matrix.*invalid', 'Unexpected input'
      ],
      auto_fix_enabled: true
    },
    infrastructure: {
      patterns: [
        'timeout', 'ETIMEDOUT', 'ECONNREFUSED', 'rate limit', '503 Service',
        '502 Bad Gateway', 'Could not resolve host', 'TLS handshake timeout',
        'connection reset', 'No space left on device'
      ],
      auto_fix_enabled: true,
      retry_on_infra_failure: true
    },
    quality_gate: {
      patterns: [
        'Quality Gate.*FAILED', 'coverage.*below', 'Quality gate status',
        'does not meet.*threshold'
      ],
      auto_fix_enabled: false,
      create_issue: true
    }
  },
  retry: {
    enabled: true,
    max_attempts: 2,
    auto_retry_types: ['infrastructure']
  }
};

if (configPath && fs.existsSync(configPath)) {
  try {
    const configContent = fs.readFileSync(configPath, 'utf8');
    config = { ...config, ...yaml.parse(configContent) };
  } catch (e) {
    console.error(`Warning: Could not load config from ${configPath}: ${e.message}`);
  }
}

// Read logs
let logs = '';
if (logsPath && fs.existsSync(logsPath)) {
  logs = fs.readFileSync(logsPath, 'utf8');
}

// Read run details
let runDetails = {};
if (runDetailsPath && fs.existsSync(runDetailsPath)) {
  try {
    runDetails = JSON.parse(fs.readFileSync(runDetailsPath, 'utf8'));
  } catch (e) {
    console.error(`Warning: Could not parse run details: ${e.message}`);
  }
}

// Read jobs
let jobs = { jobs: [] };
if (jobsPath && fs.existsSync(jobsPath)) {
  try {
    jobs = JSON.parse(fs.readFileSync(jobsPath, 'utf8'));
  } catch (e) {
    console.error(`Warning: Could not parse jobs: ${e.message}`);
  }
}

/**
 * Classify the failure based on log patterns
 * Uses weighted scoring to determine the most likely root cause
 */
function classifyFailure(logs) {
  const classifications = [];
  
  // Extract the last 100 lines where the actual error usually is
  const logLines = logs.split('\n');
  const lastSection = logLines.slice(-100).join('\n');
  
  for (const [type, settings] of Object.entries(config.classification)) {
    let score = 0;
    let matchedPattern = null;
    
    for (const pattern of settings.patterns || []) {
      const regex = new RegExp(pattern, 'gi');
      const matches = logs.match(regex) || [];
      const lastSectionMatches = lastSection.match(regex) || [];
      
      if (matches.length > 0) {
        // Patterns in the last section (near the error) are more important
        score += lastSectionMatches.length * 3;
        score += matches.length;
        if (!matchedPattern) matchedPattern = pattern;
      }
    }
    
    if (score > 0) {
      classifications.push({
        type,
        pattern: matchedPattern,
        settings,
        score
      });
    }
  }
  
  // Sort by score (highest first) then by specificity
  // Quality gate and workflow are more specific than generic code/infra
  const specificity = { quality_gate: 10, workflow: 8, code: 5, infrastructure: 3 };
  classifications.sort((a, b) => {
    const scoreA = a.score + (specificity[a.type] || 0);
    const scoreB = b.score + (specificity[b.type] || 0);
    return scoreB - scoreA;
  });
  
  if (classifications.length > 0) {
    return classifications[0];
  }
  
  return { type: 'unknown', pattern: null, settings: {} };
}

/**
 * Extract key error lines from logs
 */
function extractKeyErrors(logs, maxLines = 20) {
  const errorPatterns = [
    /##\[error\].*/gi,
    /Error:.*/gi,
    /Exception:.*/gi,
    /FAILURE:.*/gi,
    /FAILED.*/gi,
    /fatal:.*/gi,
    /error:.*/gi
  ];
  
  const errorLines = [];
  const lines = logs.split('\n');
  
  for (const line of lines) {
    for (const pattern of errorPatterns) {
      if (pattern.test(line)) {
        const cleanLine = line.replace(/^\s*\S+\s+UNKNOWN STEP\s+\S+\s*/, '').trim();
        if (cleanLine && !errorLines.includes(cleanLine)) {
          errorLines.push(cleanLine);
        }
        break;
      }
    }
    if (errorLines.length >= maxLines) break;
  }
  
  return errorLines;
}

/**
 * Find failed jobs and steps
 */
function findFailedComponents(jobs) {
  const failed = [];
  
  for (const job of jobs.jobs || []) {
    if (job.conclusion === 'failure') {
      const failedSteps = (job.steps || [])
        .filter(step => step.conclusion === 'failure')
        .map(step => ({
          name: step.name,
          number: step.number
        }));
      
      failed.push({
        jobName: job.name,
        jobId: job.id,
        failedSteps
      });
    }
  }
  
  return failed;
}

/**
 * Generate diagnosis and recommendations
 */
function generateDiagnosis(classification, errorLines, failedComponents, logs) {
  let diagnosis = [];
  let recommendations = [];
  
  // Summary based on classification
  switch (classification.type) {
    case 'code':
      diagnosis.push('**Type:** Code Failure (tests, compilation, or linting)');
      recommendations.push('Review the failing tests or compilation errors');
      recommendations.push('Check recent code changes for regressions');
      break;
      
    case 'quality_gate':
      diagnosis.push('**Type:** Quality Gate Failure');
      
      // Check if it's SonarQube
      if (/sonar/i.test(logs)) {
        diagnosis.push('The SonarQube Quality Gate check failed.');
        recommendations.push('Review the SonarQube dashboard for detailed issues');
        recommendations.push('Check for new code smells, bugs, or security vulnerabilities');
        recommendations.push('Ensure test coverage meets the threshold');
        
        // Extract SonarQube dashboard URL if present
        const dashboardMatch = logs.match(/dashboard\?id=[^\s&]+(&[^\s]+)?/);
        if (dashboardMatch) {
          diagnosis.push(`\n**SonarQube Dashboard:** Check the dashboard for details`);
        }
      }
      break;
      
    case 'workflow':
      diagnosis.push('**Type:** Workflow Configuration Issue');
      recommendations.push('Check the workflow YAML file for syntax errors');
      recommendations.push('Verify action versions are correct and available');
      recommendations.push('Ensure all required secrets are configured');
      break;
      
    case 'infrastructure':
      diagnosis.push('**Type:** Infrastructure/Transient Failure');
      recommendations.push('This may be a transient issue - retry may resolve it');
      recommendations.push('Check external service status if issue persists');
      recommendations.push('Consider adding retry logic for flaky steps');
      break;
      
    default:
      diagnosis.push('**Type:** Unknown Failure Type');
      recommendations.push('Manual investigation required');
  }
  
  // Add failed components
  if (failedComponents.length > 0) {
    diagnosis.push('\n**Failed Jobs/Steps:**');
    for (const comp of failedComponents) {
      diagnosis.push(`- Job: \`${comp.jobName}\``);
      for (const step of comp.failedSteps) {
        diagnosis.push(`  - Step ${step.number}: \`${step.name}\``);
      }
    }
  }
  
  // Add key error lines
  if (errorLines.length > 0) {
    diagnosis.push('\n**Key Error Lines:**');
    diagnosis.push('```');
    diagnosis.push(errorLines.slice(0, 10).join('\n'));
    diagnosis.push('```');
  }
  
  // Add matched pattern
  if (classification.pattern) {
    diagnosis.push(`\n**Matched Pattern:** \`${classification.pattern}\``);
  }
  
  // Add recommendations
  diagnosis.push('\n**Recommendations:**');
  for (const rec of recommendations) {
    diagnosis.push(`- ${rec}`);
  }
  
  return diagnosis.join('\n');
}

/**
 * Determine actions to take
 */
function determineActions(classification, config) {
  const actions = {
    shouldRetry: false,
    shouldCreateIssue: false,
    shouldCreatePR: false
  };
  
  const mode = config.mode || 'assist';
  const settings = classification.settings || {};
  
  // Retry for infrastructure failures
  if (classification.type === 'infrastructure' && config.retry?.enabled) {
    if (config.retry.auto_retry_types?.includes('infrastructure')) {
      actions.shouldRetry = true;
    }
  }
  
  // Create issue for quality gate and code failures
  if (['quality_gate', 'code', 'unknown'].includes(classification.type)) {
    actions.shouldCreateIssue = true;
  }
  
  // Create PR for workflow issues in auto-fix mode
  if (mode === 'auto-fix' && classification.type === 'workflow' && settings.auto_fix_enabled) {
    actions.shouldCreatePR = true;
  }
  
  // Always create issue in assist mode (except for retried infra failures)
  if (mode === 'assist' && !actions.shouldRetry) {
    actions.shouldCreateIssue = true;
  }
  
  return actions;
}

// Main execution
const classification = classifyFailure(logs);
const errorLines = extractKeyErrors(logs);
const failedComponents = findFailedComponents(jobs);
const diagnosis = generateDiagnosis(classification, errorLines, failedComponents, logs);
const actions = determineActions(classification, config);

// Output results for GitHub Actions (using GITHUB_OUTPUT file, not deprecated set-output)
const setOutput = (name, value) => {
  const outputFile = process.env.GITHUB_OUTPUT;
  if (outputFile) {
    // Handle multiline values
    if (value.includes('\n')) {
      const delimiter = `EOF_${Date.now()}`;
      fs.appendFileSync(outputFile, `${name}<<${delimiter}\n${value}\n${delimiter}\n`);
    } else {
      fs.appendFileSync(outputFile, `${name}=${value}\n`);
    }
  }
  // Note: Not using deprecated ::set-output command
};

setOutput('classification', classification.type);
setOutput('should_retry', actions.shouldRetry.toString());
setOutput('should_create_issue', actions.shouldCreateIssue.toString());
setOutput('should_create_pr', actions.shouldCreatePR.toString());
setOutput('diagnosis', diagnosis);

// Log summary
console.log('\n=== Self-Healing CI Analysis ===\n');
console.log(`Classification: ${classification.type}`);
console.log(`Should Retry: ${actions.shouldRetry}`);
console.log(`Should Create Issue: ${actions.shouldCreateIssue}`);
console.log(`Should Create PR: ${actions.shouldCreatePR}`);
console.log('\n--- Diagnosis ---\n');
console.log(diagnosis);
