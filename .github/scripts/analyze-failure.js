const { Octokit } = require("@octokit/rest");
const { GoogleGenerativeAI } = require("@google/generative-ai");

// Configuration
const MAX_LOG_LINES = 500; // Lines to analyze from the tail of the log
const MAX_TOKENS = 15000;

async function run() {
    try {
        const token = process.env.GITHUB_TOKEN;
        const apiKey = process.env.GEMINI_API_KEY;
        const runId = process.env.WORKFLOW_RUN_ID;
        const workflowName = process.env.WORKFLOW_NAME;
        const owner = process.env.REPO_OWNER;
        const repo = process.env.REPO_NAME;

        if (!token || !apiKey || !runId) {
            throw new Error("Missing required environment variables.");
        }

        // Use Octokit directly instead of @actions/github to avoid ESM/CJS issues
        const octokit = new Octokit({ auth: token });

        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });

        console.log(`Analyzing failure for workflow run ${runId} (${workflowName})...`);

        // 1. Get Jobs for the Run
        const jobs = await octokit.rest.actions.listJobsForWorkflowRun({
            owner,
            repo,
            run_id: runId,
        });

        // 2. Find the Failed Job(s)
        const failedJob = jobs.data.jobs.find(j => j.conclusion === 'failure');
        if (!failedJob) {
            console.log("No failed job found (or it was cancelled). Exiting.");
            return;
        }

        console.log(`Found failed job: ${failedJob.name} (ID: ${failedJob.id})`);

        // 3. Download Logs for the Failed Job
        console.log("Downloading logs...");
        let logContent = "";
        try {
            const logs = await octokit.rest.actions.downloadJobLogsForWorkflowRun({
                owner,
                repo,
                job_id: failedJob.id,
            });
            logContent = logs.data;
        } catch (error) {
            // Sometimes logs redirect, handle if necessary, but octokit usually handles it.
            // If raw text is returned, use it.
            logContent = error.response?.data || "";
            if (!logContent && typeof logs === 'string') logContent = logs;
            if (!logContent) {
                console.error("Could not retrieve logs:", error.message);
                return;
            }
        }

        // 4. Pre-process Logs (Tail & Token Limit)
        const lines = logContent.split('\n');
        // Simple heuristic: Take the last N lines, as errors are usually at the end.
        // For better results, one could scan for "ERROR" or "FAILED" and take surrounding context.
        const tailLogs = lines.slice(-MAX_LOG_LINES).join('\n');

        console.log(`Extracted log tail (${tailLogs.length} characters). Generating analysis...`);

        // 5. Generate Analysis with Gemini
        const prompt = `
        You are a DevOps Expert and "Build Doctor".
        A GitHub Actions workflow '${workflowName}' failed.
        
        Analyze the following log snippet (last ${MAX_LOG_LINES} lines) to identify the root cause.
        
        Log Snippet:
        \`\`\`
        ${tailLogs}
        \`\`\`
        
        Your response must be a concise Markdown comment suitable for a developer.
        Structure:
        
        ## 🩺 Build Doctor Diagnosis
        
        **1. Root Cause:** 
        (Explain what went wrong in 1-2 senteces. Be specific: Syntax error, Dependencies, Test failure, Infra, etc.)
        
        **2. Relevant Log Lines:**
        (Quote the specific error message from the logs)
        
        **3. Suggested Fix:**
        (Actionable advice. If it's a code fix, show the snippet. If it's a config tweak, show the command or yaml change.)
        
        **Confidence:** (High/Medium/Low)
        `;

        const result = await model.generateContent(prompt);
        const analysis = result.response.text();

        console.log("Analysis generated. Posting comment...");

        // 6. Post Comment
        // We need to find where to post.
        // If triggered by PR, we post to the PR.
        // If triggered by Push, we post to the Commit.

        // Context is tricky in 'workflow_run'. We have to look at the 'workflow_run' event payload.
        // We can get the PRs associated with the run.
        const runDetails = await octokit.rest.actions.getWorkflowRun({
            owner,
            repo,
            run_id: runId
        });

        const prs = runDetails.data.pull_requests;

        if (prs && prs.length > 0) {
            // Post to the first associated PR
            const prNumber = prs[0].number;
            await octokit.rest.issues.createComment({
                owner,
                repo,
                issue_number: prNumber,
                body: analysis
            });
            console.log(`Posted analysis to PR #${prNumber}`);
        } else {
            // Post to the Commit
            const headSha = runDetails.data.head_sha;
            await octokit.rest.repos.createCommitComment({
                owner,
                repo,
                commit_sha: headSha,
                body: analysis
            });
            console.log(`Posted analysis to Commit ${headSha}`);
        }

    } catch (error) {
        console.error("Build Doctor failed:", error);
        process.exit(1);
    }
}

run();
