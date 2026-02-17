#!/usr/bin/env node

/**
 * Self-Healing CI - Auto-Fix Script
 * 
 * This script applies automated fixes for known failure patterns.
 * It's designed to make minimal, targeted changes that are safe to apply.
 */

const fs = require('fs');
const path = require('path');
const yaml = require('yaml');

// Get environment variables
const diagnosis = process.env.DIAGNOSIS || '';
const classification = process.env.CLASSIFICATION || 'unknown';

// Known fix patterns and their implementations
const fixPatterns = {
  // Update deprecated action versions
  'deprecated-actions': {
    patterns: [
      /Node\.js 12 actions are deprecated/i,
      /set-output command is deprecated/i,
      /save-state command is deprecated/i
    ],
    apply: updateDeprecatedActions
  },
  
  // Fix permission issues
  'permissions': {
    patterns: [
      /Resource not accessible by integration/i,
      /permission.*denied/i,
      /pull_requests:.*read/i
    ],
    apply: addMissingPermissions
  },
  
  // Fix unexpected inputs warning
  'unexpected-inputs': {
    patterns: [
      /Unexpected input\(s\)/i
    ],
    apply: removeUnexpectedInputs
  },
  
  // Update action versions
  'action-versions': {
    patterns: [
      /actions\/checkout@v[123]/i,
      /actions\/setup-java@v[123]/i,
      /actions\/setup-node@v[123]/i
    ],
    apply: updateActionVersions
  }
};

/**
 * Update deprecated GitHub Actions to latest versions
 */
function updateDeprecatedActions(workflowPath) {
  let content = fs.readFileSync(workflowPath, 'utf8');
  let changes = [];
  
  // Map of actions to update
  const actionUpdates = {
    'actions/checkout@v2': 'actions/checkout@v4',
    'actions/checkout@v3': 'actions/checkout@v4',
    'actions/setup-node@v2': 'actions/setup-node@v4',
    'actions/setup-node@v3': 'actions/setup-node@v4',
    'actions/setup-java@v2': 'actions/setup-java@v4',
    'actions/setup-java@v3': 'actions/setup-java@v4',
    'actions/upload-artifact@v2': 'actions/upload-artifact@v4',
    'actions/upload-artifact@v3': 'actions/upload-artifact@v4',
    'actions/download-artifact@v2': 'actions/download-artifact@v4',
    'actions/download-artifact@v3': 'actions/download-artifact@v4',
    'actions/cache@v2': 'actions/cache@v4',
    'actions/cache@v3': 'actions/cache@v4'
  };
  
  for (const [oldAction, newAction] of Object.entries(actionUpdates)) {
    if (content.includes(oldAction)) {
      content = content.replace(new RegExp(escapeRegex(oldAction), 'g'), newAction);
      changes.push(`Updated ${oldAction} → ${newAction}`);
    }
  }
  
  if (changes.length > 0) {
    fs.writeFileSync(workflowPath, content);
  }
  
  return changes;
}

/**
 * Add missing permissions to workflow
 */
function addMissingPermissions(workflowPath) {
  let content = fs.readFileSync(workflowPath, 'utf8');
  let changes = [];
  
  try {
    const workflow = yaml.parse(content);
    
    // Check if permissions block exists
    if (!workflow.permissions) {
      // Add basic permissions after 'on:' block
      const onMatch = content.match(/^on:\s*\n([\s\S]*?)(?=\n\w)/m);
      if (onMatch) {
        const insertPoint = onMatch.index + onMatch[0].length;
        const permissionsBlock = `\npermissions:\n  contents: read\n  pull-requests: write\n  issues: write\n`;
        content = content.slice(0, insertPoint) + permissionsBlock + content.slice(insertPoint);
        changes.push('Added permissions block with contents:read, pull-requests:write, issues:write');
        fs.writeFileSync(workflowPath, content);
      }
    }
  } catch (e) {
    console.error(`Error parsing workflow: ${e.message}`);
  }
  
  return changes;
}

/**
 * Remove or fix unexpected inputs
 */
function removeUnexpectedInputs(workflowPath) {
  let content = fs.readFileSync(workflowPath, 'utf8');
  let changes = [];
  
  // Common unexpected inputs that can be safely removed
  const unexpectedInputs = {
    'sonarqube-quality-gate-action': ['sonar_host_url']
  };
  
  try {
    const workflow = yaml.parse(content);
    
    // Find steps with unexpected inputs
    for (const jobName in workflow.jobs || {}) {
      const job = workflow.jobs[jobName];
      for (const step of job.steps || []) {
        if (step.uses) {
          for (const [actionPattern, inputs] of Object.entries(unexpectedInputs)) {
            if (step.uses.includes(actionPattern)) {
              for (const input of inputs) {
                if (step.with && step.with[input]) {
                  // Remove the input
                  delete step.with[input];
                  changes.push(`Removed unexpected input '${input}' from ${step.uses}`);
                }
              }
            }
          }
        }
      }
    }
    
    if (changes.length > 0) {
      fs.writeFileSync(workflowPath, yaml.stringify(workflow, { lineWidth: 0 }));
    }
  } catch (e) {
    console.error(`Error processing workflow: ${e.message}`);
  }
  
  return changes;
}

/**
 * Update action versions to latest
 */
function updateActionVersions(workflowPath) {
  let content = fs.readFileSync(workflowPath, 'utf8');
  let changes = [];
  
  const versionUpdates = [
    { pattern: /actions\/checkout@v[12]/g, replacement: 'actions/checkout@v4', desc: 'checkout → v4' },
    { pattern: /actions\/checkout@v3/g, replacement: 'actions/checkout@v4', desc: 'checkout v3 → v4' },
    { pattern: /actions\/setup-java@v[123]/g, replacement: 'actions/setup-java@v4', desc: 'setup-java → v4' },
    { pattern: /actions\/setup-node@v[123]/g, replacement: 'actions/setup-node@v4', desc: 'setup-node → v4' }
  ];
  
  for (const update of versionUpdates) {
    if (update.pattern.test(content)) {
      content = content.replace(update.pattern, update.replacement);
      changes.push(`Updated ${update.desc}`);
    }
  }
  
  if (changes.length > 0) {
    fs.writeFileSync(workflowPath, content);
  }
  
  return changes;
}

/**
 * Escape special regex characters
 */
function escapeRegex(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Find all workflow files
 */
function findWorkflowFiles() {
  const workflowDir = '.github/workflows';
  const files = [];
  
  if (fs.existsSync(workflowDir)) {
    for (const file of fs.readdirSync(workflowDir)) {
      if (file.endsWith('.yml') || file.endsWith('.yaml')) {
        // Skip the self-healing workflow itself
        if (file !== 'self-healing.yml') {
          files.push(path.join(workflowDir, file));
        }
      }
    }
  }
  
  return files;
}

/**
 * Main execution
 */
function main() {
  console.log('=== Self-Healing CI - Auto-Fix ===\n');
  console.log(`Classification: ${classification}`);
  console.log(`Diagnosis:\n${diagnosis}\n`);
  
  const allChanges = [];
  const workflowFiles = findWorkflowFiles();
  
  // Determine which fixes to apply based on diagnosis
  for (const [fixName, fix] of Object.entries(fixPatterns)) {
    const shouldApply = fix.patterns.some(pattern => pattern.test(diagnosis));
    
    if (shouldApply) {
      console.log(`\nApplying fix: ${fixName}`);
      
      for (const workflowPath of workflowFiles) {
        console.log(`  Processing: ${workflowPath}`);
        try {
          const changes = fix.apply(workflowPath);
          if (changes.length > 0) {
            allChanges.push({
              file: workflowPath,
              fix: fixName,
              changes
            });
            console.log(`    ${changes.length} change(s) applied`);
          }
        } catch (e) {
          console.error(`    Error: ${e.message}`);
        }
      }
    }
  }
  
  // Output results for GitHub Actions
  const outputFile = process.env.GITHUB_OUTPUT;
  const changesMade = allChanges.length > 0;
  
  let changesDescription = 'No automatic fixes were applied.';
  if (changesMade) {
    changesDescription = allChanges.map(c => {
      return `**${c.file}** (${c.fix}):\n${c.changes.map(ch => `- ${ch}`).join('\n')}`;
    }).join('\n\n');
  }
  
  if (outputFile) {
    fs.appendFileSync(outputFile, `changes_made=${changesMade}\n`);
    const delimiter = `EOF_${Date.now()}`;
    fs.appendFileSync(outputFile, `changes_description<<${delimiter}\n${changesDescription}\n${delimiter}\n`);
  }
  
  console.log('\n=== Summary ===');
  console.log(`Changes made: ${changesMade}`);
  if (changesMade) {
    console.log('\nChanges:');
    console.log(changesDescription);
  }
}

main();
