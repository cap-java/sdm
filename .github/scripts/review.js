const { context, getOctokit } = require("@actions/github");
const { GoogleGenerativeAI } = require("@google/generative-ai");

async function fetchWithBackoff(func, maxRetries = 10, initialDelay = 2000) {
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

// New function to split the diff into chunks
function splitDiffIntoChunks(diff, maxTokens = 10000) {
  const lines = diff.split('\n');
  const chunks = [];
  let currentChunk = '';

  for (const line of lines) {
    if ((currentChunk + line).length < maxTokens) {
      currentChunk += line + '\n';
    } else {
      chunks.push(currentChunk);
      currentChunk = line + '\n';
    }
  }
  if (currentChunk.length > 0) {
    chunks.push(currentChunk);
  }
  return chunks;
}

async function performPRReview(octokit, diffContent, pull_number, genAI) {
  const model = genAI.getGenerativeModel({ model: "gemini-1.5-pro" });

  const chunks = splitDiffIntoChunks(diffContent);
  const chunkReviews = [];

  console.log(`Splitting diff into ${chunks.length} chunks for processing...`);

  for (let i = 0; i < chunks.length; i++) {
    const chunk = chunks[i];
    const chunkPrompt = `You are a helpful and expert AI code reviewer named Gemini. Analyze the following Git diff chunk and provide a concise review of its contents. Do not provide a final summary. Focus on a summary of changes, best practices, potential bugs, and recommendations for this specific chunk.

    Git Diff Chunk:
    \`\`\`diff
    ${chunk}
    \`\`\`
    `;

    const result = await fetchWithBackoff(() => model.generateContent(chunkPrompt));
    chunkReviews.push(result.response.text());
    console.log(`Review for chunk ${i + 1} of ${chunks.length} generated.`);
  }

  // Now, synthesize the reviews into a single final review
  const synthesisPrompt = `You are a helpful and expert AI code reviewer named Gemini. Synthesize the following partial code reviews into a single, cohesive, and comprehensive final review. Your review must strictly follow this exact markdown format and content:

  ######
  **Gemini Automated Review**
  **Summary of Changes**
  [A brief, high-level summary of all the commits.]
  **Best Practices Review**
  [A concise, bulleted list of all best practices violations. Be specific and include issues like Inconsistent Formatting, Redundant Dependency, Unused Property, Redundant Exclusion, Version Mismatch, Missing Version in dependency, and Unnecessary Comments.]
  **Potential Bugs**
  [A concise, bulleted list of all potential bugs or errors. Reference specific issues found.]
  **Recommendations**
  [A prioritized, bulleted list of all actionable recommendations for improving the code. For the most critical recommendations, provide a code snippet showing the improved version.]
  **Overall**
  [A brief overall assessment of the code quality and readiness for merge.]
  ######
  
  Partial Reviews to Synthesize:
  ${chunkReviews.join('\n\n---\n\n')}
  `;

  const finalReviewResult = await fetchWithBackoff(() => model.generateContent(synthesisPrompt));
  const reviewBody = finalReviewResult.response.text();

  console.log("Gemini's final review generated successfully.");

  await octokit.rest.issues.createComment({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: pull_number,
    body: reviewBody,
  });
  console.log("Gemini's final review posted successfully.");
}

async function handleCommentResponse(octokit, commentBody, pull_number, genAI) {
  const model = genAI.getGenerativeModel({ model: "gemini-1.5-pro" });
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
