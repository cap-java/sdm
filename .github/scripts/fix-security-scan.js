const fs = require('fs');
const path = require('path');
const { Octokit } = require("@octokit/rest");
const { GoogleGenerativeAI } = require("@google/generative-ai");

// Configuration
const SARIF_PATH = process.env.SARIF_PATH || './sarif-results';
const MAX_FIXES_PER_RUN = 5; // Avoid huge PRs

async function run() {
    try {
        const token = process.env.GITHUB_TOKEN;
        const apiKey = process.env.GEMINI_API_KEY;
        const repoOwner = process.env.REPO_OWNER;
        const repoName = process.env.REPO_NAME;
        const baseBranch = process.env.BASE_BRANCH || 'develop';

        if (!token || !apiKey) {
            throw new Error("Missing required environment variables (GITHUB_TOKEN, GEMINI_API_KEY).");
        }

        const octokit = new Octokit({ auth: token });
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });

        // 1. Find and Parse SARIF Files
        const sarifFiles = findSarifFiles(SARIF_PATH);
        if (sarifFiles.length === 0) {
            console.log("No SARIF files found. Exiting.");
            return;
        }

        console.log(`Found ${sarifFiles.length} SARIF files. Analyzing...`);

        const vulnerabilities = [];
        for (const file of sarifFiles) {
            const content = JSON.parse(fs.readFileSync(file, 'utf8'));
            const runs = content.runs || [];
            for (const run of runs) {
                const results = run.results || [];
                for (const result of results) {
                    // Filter for specific severity if needed, e.g., error/warning
                    // SARIF levels: error, warning, note, none
                    if (result.level === 'error' || result.level === 'warning') {
                        vulnerabilities.push(result);
                    }
                }
            }
        }

        if (vulnerabilities.length === 0) {
            console.log("No vulnerabilities found to fix. Exiting.");
            return;
        }

        // 2. Group by File
        const filesToFix = {};
        for (const vuln of vulnerabilities) {
            const loc = vuln.locations?.[0]?.physicalLocation;
            if (!loc) continue;

            const filePath = loc.artifactLocation.uri;
            if (!filesToFix[filePath]) {
                filesToFix[filePath] = [];
            }
            filesToFix[filePath].push({
                ruleId: vuln.ruleId,
                message: vuln.message.text,
                startLine: loc.region?.startLine,
                endLine: loc.region?.endLine
            });
        }

        // 3. Generate Fixes
        let fixedCount = 0;
        const appliedFixes = [];

        for (const [filePath, issues] of Object.entries(filesToFix)) {
            if (fixedCount >= MAX_FIXES_PER_RUN) break;

            console.log(`Processing ${filePath} with ${issues.length} issues...`);

            if (!fs.existsSync(filePath)) {
                console.warn(`File ${filePath} not found locally. Skipping.`);
                continue;
            }

            const fileContent = fs.readFileSync(filePath, 'utf8');

            const prompt = `You are a Security Expert and Secure Coding Assistant.
            
            I have a file with the following security vulnerabilities identified by CodeQL:
            
            ${issues.map(i => `- Line ${i.startLine}: ${i.message} (${i.ruleId})`).join('\n')}
            
            File Content (${filePath}):
            \`\`\`
            ${fileContent}
            \`\`\`
            
            Please provide the FIXED file content. 
            - Fix ONLY the reported security issues.
            - Do not change other logic or formatting if possible.
            - Return ONLY the full valid file content in a code block.
            - Do not add conversational text / markdown outside the code block.
            `;

            try {
                const result = await model.generateContent(prompt);
                const responseText = result.response.text();

                // Extract code block
                const match = responseText.match(/```(?:\w+)?\n([\s\S]*?)```/);
                if (match && match[1]) {
                    const fixedContent = match[1];
                    fs.writeFileSync(filePath, fixedContent);
                    console.log(`Applied fix to ${filePath}`);
                    appliedFixes.push({ filePath, issues });
                    fixedCount++;
                } else {
                    console.warn(`Failed to extract code block for ${filePath}`);
                }

            } catch (error) {
                console.error(`Error generating fix for ${filePath}:`, error);
            }
        }

        if (appliedFixes.length === 0) {
            console.log("No fixes applied.");
            return;
        }

        // 4. Create PR
        const branchName = `fix/security/codeql-remediation-${Date.now()}`;
        console.log(`Creating branch ${branchName}...`);

        // Create branch (this requires the workflow to have checked out the repo with fetch-depth: 0 or sufficient permissions)
        // We will use git commands for simplicity as we are in a runner environment
        const exec = require('child_process').execSync;

        // Configure git
        exec(`git config --global user.name "Gemini Security Bot"`);
        exec(`git config --global user.email "gemini-bot@example.com"`);

        exec(`git checkout -b ${branchName}`);
        exec(`git add .`);
        exec(`git commit -m "fix(security): automated remediation for ${appliedFixes.length} files"`);
        exec(`git push origin ${branchName}`);

        // Open PR
        console.log("Opening Pull Request...");
        const title = `fix(security): automated remediation for detected vulnerabilities`;
        const body = `## 🛡️ Gemini Security Remediation
        
        This PR was automatically generated to fix high-severity security issues detected by CodeQL.
        
        ### 🔍 Fixed Issues:
        ${appliedFixes.map(f => `\n#### ${f.filePath}\n${f.issues.map(i => `- [${i.ruleId}] ${i.message} (Line ${i.startLine})`).join('\n')}`).join('\n')}
        
        > [!WARNING]
        > Please review these changes carefully. AI-generated code should be validated before merging.
        `;

        await octokit.rest.pulls.create({
            owner: repoOwner,
            repo: repoName,
            title: title,
            body: body,
            head: branchName,
            base: baseBranch
        });

        console.log("Pull Request created successfully!");

    } catch (error) {
        console.error("Security Fix Agent failed:", error);
        process.exit(1);
    }
}

function findSarifFiles(dir, fileList = []) {
    if (!fs.existsSync(dir)) return [];
    const files = fs.readdirSync(dir);
    files.forEach(file => {
        const filePath = path.join(dir, file);
        const stat = fs.statSync(filePath);
        if (stat.isDirectory()) {
            findSarifFiles(filePath, fileList);
        } else {
            if (path.extname(file) === '.sarif') {
                fileList.push(filePath);
            }
        }
    });
    return fileList;
}

run();
