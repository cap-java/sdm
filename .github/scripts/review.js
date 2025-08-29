const { context, getOctokit } = require("@actions/github");
const { GoogleGenerativeAI } = require("@google/generative-ai");

// Function to handle exponential backoff for API calls.
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchWithBackoff(func, maxRetries = 5, initialDelay = 1000) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await func();
    } catch (error) {
      if (error.status === 429) {
        console.warn(`Rate limit exceeded. Retrying in ${initialDelay}ms...`);
        await sleep(initialDelay);
        initialDelay *= 2; // Exponential backoff
      } else {
        throw error;
      }
    }
  }
  throw new Error("Max retries exceeded.");
}

async function run() {
  try {
    const octokit = getOctokit(process.env.GITHUB_TOKEN);
    const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
    
    const { owner, repo } = context.repo;

    if (context.eventName === "pull_request") {
      const pr = context.payload.pull_request;
      const prNumber = pr.number;
      
      console.log(`Starting review for PR #${prNumber}...`);
      
      const { data: files } = await octokit.rest.pulls.listFiles({
        owner,
        repo,
        pull_number: prNumber,
      });
      
      let diffContent = "";
      for (const file of files) {
        if (file.status === "added" || file.status === "modified") {
          console.log(`Getting diff for file: ${file.filename}`);
          const { data: diff } = await octokit.rest.repos.getCommit({
            owner,
            repo,
            ref: file.sha,
            mediaType: { format: "diff" },
          });
          diffContent += `\n\n--- FILE: ${file.filename} ---\n\n${diff}`;
        }
      }

      if (!diffContent) {
        await octokit.rest.issues.createComment({
          owner,
          repo,
          issue_number: prNumber,
          body: "## Gemini Automated Review\n\nNo code changes to review in this pull request."
        });
        console.log("No diff content. Comment posted.");
        return;
      }

      const prompt = `You are a helpful and professional code reviewer.
      Analyze the following Git diff and provide a structured review.
      Your response must be formatted as a single Markdown block with the following sections:

      ### Summary of Changes
      A concise, high-level overview of what the changes in the diff accomplish.

      ### Best Practices Review
      Identify any code that does not follow best practices. Suggest improvements for readability, maintainability, or efficiency.

      ### Potential Bugs
      Point out any potential bugs, edge cases, or logical errors that might arise from the changes.

      ### Recommendations
      Provide specific, actionable recommendations for improvement, with code examples where helpful.

      ### Overall
      Provide a final one-sentence overall assessment of the changes.

      ---
      Git Diff to analyze:
      \`\`\`diff
      ${diffContent}
      \`\`\`
      `;

      console.log("Sending diff to Gemini for analysis...");
      const result = await fetchWithBackoff(() => model.generateContent(prompt));
      const reviewComment = result.response.text();

      if (reviewComment) {
        await octokit.rest.issues.createComment({
          owner,
          repo,
          issue_number: prNumber,
          body: `## Gemini Automated Review\n\n${reviewComment}`
        });
        console.log("Gemini review comment posted successfully.");
      } else {
        console.log("No content to review or API response was empty.");
      }

    } else if (context.eventName === "issues") {
      const issue = context.payload.issue;
      const issueNumber = issue.number;
      const { owner, repo } = context.repo;
      
      console.log(`Starting analysis for Issue #${issueNumber}...`);
      
      const prompt = `Analyze the following issue description and provide a structured, helpful reply.
      
      Issue Title: ${issue.title}
      Issue Body:
      ${issue.body}
      
      Provide a response in Markdown format. The response should include:
      1. An acknowledgment of the issue.
      2. A clear summary of the problem.
      3. Potential root causes or areas to investigate.
      4. A proposed solution or next steps.
      5. Use code blocks for any code examples.
      `;
      
      const result = await fetchWithBackoff(() => model.generateContent(prompt));
      const commentBody = result.response.text();
      
      if (commentBody) {
        await octokit.rest.issues.createComment({
          owner,
          repo,
          issue_number: issueNumber,
          body: `## Gemini Automated Review\n\n${commentBody}`
        });
        console.log("Gemini review comment posted successfully.");
      } else {
        console.log("No content to review or API response was empty.");
      }
    }
  
  } catch (error) {
    console.error(`An error occurred: ${error.message}`);
    throw error;
  }
}

run();
