const { context, getOctokit } = require("@actions/github");
const { GoogleGenerativeAI } = require("@google/generative-ai");

async function fetchWithBackoff(func, maxRetries = 5, initialDelay = 1000) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await func();
    } catch (error) {
      if (error.status === 429) {
        console.warn(`Rate limit exceeded. Retrying in ${initialDelay}ms...`);
        await new Promise(resolve => setTimeout(resolve, initialDelay));
        initialDelay *= 2;
      } else {
        throw error;
      }
    }
  }
  throw new Error("Max retries exceeded.");
}

async function getDiff(octokit, owner, repo, pull_number) {
  console.log(`Fetching diff for PR #${pull_number}`);
  const { data: pullRequest } = await octokit.rest.pulls.get({
    owner,
    repo,
    pull_number,
    mediaType: { format: "diff" },
  });
  return pullRequest;
}

async function performPRReview(octokit, diffContent, pull_number, genAI) {
  const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
  const prompt = `You are a helpful and expert AI code reviewer named Gemini. Your task is to review a pull request based on the provided Git diff.
  
  Your review should be organized into five sections using the following markdown format:

  ######
  Gemini Automated Review
  Summary of Changes
  [A brief, high-level summary of what the commit does.]
  Best Practices Review
  [A concise list of best practices, including formatting, naming conventions, and code organization. Be specific and reference code snippets if necessary.]
  Potential Bugs
  [A list of potential bugs or errors. Highlight security vulnerabilities, race conditions, or logic errors.]
  Recommendations
  [A prioritized list of actionable recommendations for improving the code. Be polite and constructive.]
  Overall
  [A brief overall assessment of the code quality and readiness for merge.]
  ######

  If you don't find any issues, simply state that in the "Overall" section.

  Here is the Git diff to review:
  \`\`\`diff
  ${diffContent}
  \`\`\`
  `;

  const result = await fetchWithBackoff(() => model.generateContent(prompt));
  const reviewBody = result.response.text();
  console.log("Gemini's review generated successfully.");

  await octokit.rest.issues.createComment({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: pull_number,
    body: reviewBody,
  });
  console.log("Gemini's review posted successfully.");
}

async function handleCommentResponse(octokit, commentBody, pull_number, genAI) {
  const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
  const userQuestion = commentBody.replace("Hey Gemini,", "").trim();

  const diffContent = await getDiff(octokit, context.repo.owner, context.repo.repo, pull_number);

  const prompt = `A user has a question about a pull request. The pull request diff is below, followed by the user's question. Please provide a clear and concise answer.

  ---
  Git Diff:
  \`\`\`diff
  ${diffContent}
  \`\`\`

  ---
  User's question:
  ${userQuestion}
  `;

  const result = await fetchWithBackoff(() => model.generateContent(prompt));
  const response = result.response.text();

  if (response) {
    await octokit.rest.issues.createComment({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: pull_number,
      body: `## Gemini's Response\n\n${response}`
    });
    console.log("Gemini's response posted successfully.");
  }
}

async function run() {
  try {
    const octokit = getOctokit(process.env.GITHUB_TOKEN);
    const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    
    const { owner, repo } = context.repo;
    const pull_number = context.payload.pull_request ? context.payload.pull_request.number : context.payload.issue.number;

    if (context.eventName === 'pull_request') {
      const diffContent = await getDiff(octokit, owner, repo, pull_number);
      await performPRReview(octokit, diffContent, pull_number, genAI);
    } else if (context.eventName === 'issue_comment') {
      const commentBody = context.payload.comment.body;
      if (commentBody.startsWith("Hey Gemini,")) {
        await handleCommentResponse(octokit, commentBody, pull_number, genAI);
      }
    }
  } catch (error) {
    console.error(`An error occurred: ${error.message}`);
    throw error;
  }
}

run();
