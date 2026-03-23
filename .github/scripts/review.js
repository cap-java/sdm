const { context, getOctokit } = require("@actions/github");
const { GoogleGenerativeAI } = require("@google/generative-ai");
const fs = require("fs");
const path = require("path");

// Utility functions
//----------------------------------------------------------------------------------------------------------------
function safeParseInt(envVar, defaultValue) {
    const value = parseInt(envVar);
    return !isNaN(value) && value > 0 ? value : defaultValue;
}

const MAX_RETRIES = safeParseInt(process.env.MAX_RETRIES, 5);
const INITIAL_DELAY_MS = safeParseInt(process.env.INITIAL_DELAY_MS, 1000);
const MAX_CHUNK_TOKENS = safeParseInt(process.env.MAX_CHUNK_TOKENS, 10000);

async function fetchWithBackoff(func, maxRetries = MAX_RETRIES, initialDelay = INITIAL_DELAY_MS) {
    let retries = 0;
    let delay = initialDelay;

    while (retries < maxRetries) {
        try {
            return await func();
        } catch (error) {
            // Check for known fatal errors (e.g., 400 Bad Request, 404 Not Found)
            // These should not be retried as the input/model is fundamentally wrong.
            if (error.status === 400 || error.status === 404) {
                console.error(`Fatal error (${error.status}) encountered. Aborting immediately.`);
                throw error; 
            }

            // Check for transient errors (e.g., 429 Rate Limit, 5xx Server Error)
            if (error.status === 429 || error.status >= 500) {
                console.warn(`Transient error (${error.status || error.message}) encountered. Retrying in ${delay}ms...`);
                await new Promise(resolve => setTimeout(resolve, delay));
                delay *= 2;
                retries++;
            } else {
                console.error("Non-retryable/unknown error encountered. Aborting fetchWithBackoff. Details:", error);
                throw error;
            }
        }
    }

    const error = new Error(`Max retries (${maxRetries}) exceeded.`);
    error.status = 504;
    throw error;
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

async function splitDiffIntoTokens(genAI, diff, maxTokens = MAX_CHUNK_TOKENS) {
    if (!diff || diff.length === 0) {
        return [];
    }
    const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
    const lines = diff.split('\n');
    const chunks = [];
    let currentChunk = '';

    for (const line of lines) {
        const tempChunk = currentChunk + line + '\n';
        try {
            const tokenCount = (await model.countTokens(tempChunk)).totalTokens;
            if (tokenCount < maxTokens) {
                currentChunk = tempChunk;
            } else {
                chunks.push(currentChunk);
                currentChunk = line + '\n';
            }
        } catch (error) {
            console.error("Error counting tokens. Skipping chunking for this line. Details:", error);
            chunks.push(currentChunk);
            currentChunk = line + '\n';
        }
    }
    if (currentChunk.length > 0) {
        chunks.push(currentChunk);
    }
    return chunks;
}

// Project domain context — injected into issue analysis prompts
//----------------------------------------------------------------------------------------------------------------

const PROJECT_CONTEXT = `
**Project:** SAP CAP Java SDK plugin for Document Management Service (SDM / CMIS) integration.
**Purpose:** Intercepts CAP Attachments CRUD events and routes document storage to SAP Document Management Service via CMIS REST API. Distributed as a Maven JAR (Java 17) consumed by CAP Java apps via Java SPI.

**Key Architectural Components:**
- Event Handlers (CAP @Before/@On/@After lifecycle): SDMCreateAttachmentsHandler, SDMReadAttachmentsHandler, SDMUpdateAttachmentsHandler, SDMAttachmentsServiceHandler (core SDM bridge), SDMCustomServiceHandler (COPY/MOVE operations).
- Token Management: TokenHandler singleton — OAuth2 named-user and technical-user flows; cached via EhCache 3 (userTokenCache, clientCredentialsTokenCache, 660-min TTL).
- Caching: CacheConfig (static EhCache 3) — 8 named caches: userTokenCache, clientCredentialsTokenCache, userAuthoritiesTokenCache, repoCache, secondaryTypesCache, secondaryPropertiesCache, maxAllowedAttachmentsCache, errorMessageCache.
- Upload Scan States: uploading → Success | Failed | VirusDetected | VirusScanInprogress.
- HTTP Layer: Apache HttpClient 5 for CMIS REST calls; multipart upload for document creation; RetryUtils for transient failure retry.
- Multi-tenancy: Single-tenant and multi-tenant Cloud Foundry deployments with separate integration test workflows.
- Key Dependencies: cds-services-api 3.10.3, SAP Cloud SDK 5.21.0 (OAuth2DestinationBuilder), token-client 3.5.7, EhCache 3.10.8, Apache HttpClient 5.4.4.
- Build: Maven multi-module, Spotless (Google Java Format), JaCoCo, SonarQube, Black Duck, CodeQL on GitHub Actions.
`.trim();

/**
 * Calls Gemini to classify an issue by type and affected component.
 * Returns { type, component } — never throws; falls back to defaults on error.
 */
async function classifyIssue(issueTitle, issueBody, genAI) {
    const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
    const prompt = `Classify this GitHub issue for the SAP CAP Java SDM plugin project.
Issue Title: ${issueTitle}
Issue Body: ${truncate(issueBody || '', 800)}

Respond ONLY with a valid JSON object — no markdown, no code fences, no extra text.
Required format:
{"type":"<bug|enhancement|question|security|documentation|performance>","component":"<caching|auth-token|handler|service|persistence|ci-cd|sdm-api|integration-test|build|docs|unknown>"}`;
    try {
        const result = await fetchWithBackoff(() => model.generateContent(prompt));
        const text = result.response.text().trim().replace(/```(?:json)?\n?|\n?```/g, '').trim();
        const parsed = JSON.parse(text);
        // Validate values to avoid injecting arbitrary label names
        const validTypes = new Set(['bug', 'enhancement', 'question', 'security', 'documentation', 'performance']);
        const validComponents = new Set(['caching', 'auth-token', 'handler', 'service', 'persistence', 'ci-cd', 'sdm-api', 'integration-test', 'build', 'docs', 'unknown']);
        return {
            type: validTypes.has(parsed.type) ? parsed.type : 'bug',
            component: validComponents.has(parsed.component) ? parsed.component : 'unknown',
        };
    } catch (e) {
        console.warn("Issue classification failed:", e.message);
        return { type: 'bug', component: 'unknown' };
    }
}

// Core logic functions
//----------------------------------------------------------------------------------------------------------------

async function updateReadme(octokit, owner, repo, aiGeneratedContent, pull_number) {
    const readmePath = "README.md";
    let readmeSha;
    
    // context.payload.pull_request is guaranteed to exist here
    const headRef = context.payload.pull_request.head.ref;
    
    console.log("Attempting to read existing README.md...");
    try {
        const { data } = await octokit.rest.repos.getContents({
            owner,
            repo,
            path: readmePath,
            ref: headRef, // Use the head ref of the PR
        });
        readmeSha = data.sha;
        console.log("README.md file found. Its SHA is:", readmeSha);
    } catch (error) {
        if (error.status === 404) {
            console.warn("README.md not found. Will create a new one.");
            readmeSha = null;
        } else {
            console.error("Error fetching README.md:", error);
            throw error;
        }
    }

    try {
        await octokit.rest.repos.createOrUpdateFileContents({
            owner,
            repo,
            path: readmePath,
            message: `chore(readme): Update README with changes from PR #${pull_number}`,
            content: Buffer.from(aiGeneratedContent).toString('base64'),
            sha: readmeSha,
            branch: headRef,
        });
        console.log("README.md updated successfully.");
    } catch (error) {
        console.error("Failed to update README.md:", error);
        throw error;
    }
}

async function createFeatureDocument(octokit, owner, repo, title, aiGeneratedContent) {
    const featureDocPath = `docs/features/${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.md`;
    const headRef = context.payload.pull_request.head.ref;

    try {
        await octokit.rest.repos.createOrUpdateFileContents({
            owner,
            repo,
            path: featureDocPath,
            message: `docs(feature): Add feature documentation for "${title}"`,
            content: Buffer.from(aiGeneratedContent).toString('base64'),
            branch: headRef,
        });
        console.log("Feature document created successfully at:", featureDocPath);
    } catch (error) {
        console.error("Failed to create feature document:", error);
        throw error;
    }
}

async function performPRReview(octokit, diffContent, pull_number, genAI) {
    const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
    const chunks = await splitDiffIntoTokens(genAI, diffContent);
    const chunkReviews = [];

    if (chunks.length === 0) {
        console.log("No diff content to review.");
        return;
    }

    console.log(`Splitting diff into ${chunks.length} chunks for processing...`);

    for (let i = 0; i < chunks.length; i++) {
        const chunk = chunks[i];
        
        // --- PROMPT: Instructing for point-by-point, structured feedback with code ---
        const chunkPrompt = `You are a helpful and expert AI code reviewer named Gemini. Analyze the following Git diff chunk. Your response must be highly structured and strictly focused on identifying issues and providing solutions.

        For each issue found, format your finding with a clear bullet point and, if the fix is simple, provide the recommended code change directly beneath it in a code block.

        Issue Types must include: BEST_PRACTICE, POTENTIAL_BUG, REFACTOR, DEPENDENCY_ISSUE.

        Format your findings strictly as:
        - [ISSUE TYPE]: [Concise description of the issue.]
        [Optional Code Snippet with FIX]

        Git Diff Chunk:
        \`\`\`diff
        ${chunk}
        \`\`\`
        
        Provide only the structured list of findings and nothing else.
        `;
        // --- END PROMPT ---

        try {
            const result = await fetchWithBackoff(() => model.generateContent(chunkPrompt));
            chunkReviews.push(result.response.text());
            console.log(`Review for chunk ${i + 1} of ${chunks.length} generated.`);
        } catch (error) {
            // Fatal error occurred, stop processing chunks
            console.error(`Fatal error encountered during review chunk processing. Aborting review.`);
            
            // Post a single error message and return immediately
            const errorMessage = `❌ **Gemini Review Failed** ❌\n\nA critical error occurred during the review process (likely due to an incorrect model configuration, API key issue, or a malformed request). The first error encountered was:\n\n\`\`\`\n${error.message}\n\`\`\`\n\n**Action Required:** Please check the model name, API key, and retry the review.`;
            await octokit.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: pull_number,
                body: errorMessage,
            });
            return;
        }
    }

    // --- PROMPT: Instructing for highly actionable synthesis with code snippets ---
    const synthesisPrompt = `You are a helpful and expert AI code reviewer named Gemini. Synthesize the following partial code reviews into a single, cohesive, and highly actionable final review. The partial reviews already contain point-by-point findings and recommended code changes.

    Your review must strictly follow this exact markdown format and content. Prioritize clear, point-by-point feedback. Ensure the Recommendations section includes the actual code snippets gathered from the partial reviews, not just descriptions.

    ######
    **Gemini Automated Review**
    **Summary of Changes**
    [A brief, high-level summary of all the changes across the PR.]
    **Best Practices Review** 💡
    [A clear, bulleted list of all best practices violations identified across the partial reviews. Each point must be concise and actionable.]
    **Potential Bugs** 🐛
    [A clear, bulleted list of all potential bugs or errors. Reference specific files or lines if possible.]
    **Recommendations & Required Changes** 🛠️
    [A prioritized, point-by-point list of all required code changes and improvements. **For every critical recommendation, you MUST provide the recommended code snippet.** Do not just describe the fix—show it in a code block.]
    **Quality Rating** ⭐
    [A rating out of 10 that reflects the overall quality of the code.]
    **Overall Assessment**
    [A brief, overall assessment of the code quality and readiness for merge, based on the severity of the issues found.]
    ######
    
    Partial Reviews to Synthesize:
    ${chunkReviews.join('\n\n---\n\n')}
    `;
    // --- END PROMPT ---
    
    let reviewBody = "Review generation failed.";
    try {
        const finalReviewResult = await fetchWithBackoff(() => model.generateContent(synthesisPrompt));
        reviewBody = finalReviewResult.response.text();
        console.log("Gemini's final review generated successfully.");
    } catch (error) {
        console.error(`Error synthesizing final review. Details:`, error);
        reviewBody = `An error occurred while synthesizing the final review. Please check the partial reviews below for details:\n\n${chunkReviews.join('\n\n---\n\n')}`;
    }

    const readmePrompt = `You are a helpful and expert AI assistant. Based on the following PR summary and changes, decide if the README file needs to be updated. If it does, provide the complete, updated content for the README. If not, respond with just "NO_UPDATE".

    PR Summary: ${reviewBody}
    Git Diff:
    \`\`\`diff
    ${diffContent}
    \`\`\`
    
    If the README needs updating, provide the full content in a single block. Do not add any extra commentary outside of the content block.`;

    let readmeContent = 'NO_UPDATE';
    try {
        const readmeResult = await fetchWithBackoff(() => model.generateContent(readmePrompt));
        readmeContent = readmeResult.response.text().trim();
        if (readmeContent !== 'NO_UPDATE') {
            await updateReadme(octokit, context.repo.owner, context.repo.repo, readmeContent, pull_number);
        }
    } catch (error) {
        console.error("Failed to check or update README. Details:", error);
    }

    const featureLabel = context.payload.pull_request.labels.find(label => label.name === 'feature');
    if (featureLabel) {
        const featureDocPrompt = `You are an expert technical writer. Based on the following PR title and Git diff, create a concise feature document. The document should explain what the new feature is, how to use it, and any new configurations. Format the response as a single markdown file content.

        PR Title: ${context.payload.pull_request.title}
        Git Diff:
        \`\`\`diff
        ${diffContent}
        \`\`\`
        `;
        try {
            const featureDocResult = await fetchWithBackoff(() => model.generateContent(featureDocPrompt));
            const featureDocContent = featureDocResult.response.text();
            await createFeatureDocument(octokit, context.repo.owner, context.repo.repo, context.payload.pull_request.title, featureDocContent);
        } catch (error) {
            console.error("Failed to create feature document. Details:", error);
        }
    }

    await octokit.rest.issues.createComment({
        owner: context.repo.owner,
        repo: context.repo.repo,
        issue_number: pull_number,
        body: reviewBody,
    });
    console.log("Gemini's final review posted successfully.");
}

async function handleCommentResponse(octokit, commentBody, number, genAI) {
    const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
    const userQuestion = commentBody.replace("Hey Gemini,", "").trim();
    let prompt;

    // Check if the comment is on a pull request (context.payload.issue.pull_request will be set)
    if (context.payload.issue.pull_request) {
        // This is a comment on a PR, so we can get the diff
        const diffContent = await getDiff(octokit, context.repo.owner, context.repo.repo, number);
        prompt = `A user has a question about a pull request. The pull request diff is below, followed by the user's question. Please provide a clear and concise answer.

        ---
        Git Diff:
        \`\`\`diff
        ${diffContent}
        \`\`\`

        ---
        User's question:
        ${userQuestion}
        `;
    } else {
        // This is a comment on a regular issue. Include repo scan + project context for better answers.
        const issueTitle = context.payload.issue.title;
        const issueBody = context.payload.issue.body;
        const repoScan = scanRepositoryForIssue(issueTitle, userQuestion, process.cwd());
        const codeContext = repoScan.matches.length > 0
            ? `\n**Relevant Code Context (from repository scan):**\n${truncate(repoScan.matches.map(m => `File: ${m.file}\n${m.snippet}`).join('\n---\n'), 4000)}`
            : '';
        prompt = `You are an expert engineer on the SAP CAP Java SDM plugin. A user has a question about a GitHub issue. Use the project context, issue details, and any relevant code snippets to give a precise, actionable answer.

**Project Context:**
${PROJECT_CONTEXT}

---
**Issue Title:** ${issueTitle}
**Issue Body:** ${issueBody}
${codeContext}

---
**User's question:**
${userQuestion}
`;
    }

    let response = "Error: Could not generate a response to your comment.";
    try {
        const result = await fetchWithBackoff(() => model.generateContent(prompt));
        response = result.response.text();
        console.log("Gemini's response generated successfully.");
    } catch (error) {
        console.error(`Error generating response to comment. Details:`, error);
        // Post a single error message for the comment response
        response = `❌ **Gemini Response Failed** ❌\n\nA critical error occurred while generating a response (likely due to an incorrect model configuration, API key issue, or a malformed request). The error was:\n\n\`\`\`\n${error.message}\n\`\`\`\n\n**Action Required:** Please check the model configuration and API key.`;
    }

    if (response) {
        await octokit.rest.issues.createComment({
            owner: context.repo.owner,
            repo: context.repo.repo,
            issue_number: number,
            body: `## Gemini's Response\n\n${response}`
        });
        console.log("Gemini's response posted successfully.");
    }
}

async function handleNewIssue(octokit, owner, repo, issueNumber, issueTitle, issueBody, genAI, priorAnalysis = null) {
    console.log(`Processing new issue #${issueNumber}: ${issueTitle}`);

    // Guard: if the issue body is missing or too short, request more details instead of attempting analysis
    if (!issueBody || issueBody.trim().length < 30) {
        console.log("Issue body too short or empty. Requesting more details from author.");
        await octokit.rest.issues.createComment({
            owner, repo, issue_number: issueNumber,
            body: `👋 Thanks for opening this issue!\n\nTo provide accurate root-cause analysis and remediation steps, could you share a bit more context?\n\n**Helpful details to include:**\n- Steps to reproduce the problem\n- Expected behaviour vs. actual behaviour\n- Relevant logs, stack traces, or error messages\n- Your environment: CAP Java SDK version, tenant type (single/multi), Cloud Foundry plan\n\n_I'll automatically re-analyse once the description is updated._`,
        });
        return;
    }

    // Primary lightweight model for generation
    const flashModel = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });

    // Fetch historical issues (open + closed) excluding current
    const pastIssues = await fetchPastIssues(octokit, owner, repo, issueNumber);

    // Perform semantic + lexical similarity search
    const similar = await findSimilarIssueSemantic(issueTitle, issueBody, pastIssues, genAI);
    if (similar) {
        console.log(`Semantic similar issue found: #${similar.number} (score=${(similar.score || 0).toFixed(3)})`);
        await ensureLabel(octokit, owner, repo, "duplicate", { description: "Indicates this issue duplicates an existing one", color: "d73a4a" });
        await octokit.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: ["duplicate"] });
        const status = similar.state === "closed" ? "closed" : "in progress";
        const resolution = extractResolution(similar.body);
        
        // --- UPDATED COMMENT TO REFLECT CONTENT CHECK ---
        const duplicateComment = `### 🔁 Potential Duplicate Detected (Semantic Match)\nBased on **issue context and body**, a related issue appears to already exist: **#${similar.number} - ${similar.title}** (${status}).\n\n**Link:** ${similar.html_url}\n\n**Summary (Truncated):**\n${truncate(similar.body || '(no body)', 800)}\n\n${resolution ? `**Extracted Resolution / Status Notes:**\n${resolution}\n\n` : ''}If this is a duplicate, please consolidate discussion there and consider closing this one. If not, comment with \`not a duplicate\` and I will proceed with fresh root-cause analysis.`;
        // --- END UPDATED COMMENT ---

        await octokit.rest.issues.createComment({ owner, repo, issue_number: issueNumber, body: duplicateComment });
        return;
    }

    // No match: classify issue and scan repository for potential causes — run in parallel
    const [classification, repoScan] = await Promise.all([
        classifyIssue(issueTitle, issueBody, genAI),
        Promise.resolve(scanRepositoryForIssue(issueTitle, issueBody, process.cwd())),
    ]);

    console.log(`Issue classified — type: "${classification.type}", component: "${classification.component}"`);
    console.log(`Repository scan complete. Matched contexts: ${repoScan.matches.length}`);

    // Apply type and component labels
    const typeColorMap = {
        bug: 'd73a4a', enhancement: 'a2eeef', question: 'd876e3',
        security: 'e4e669', documentation: '0075ca', performance: 'f9d0c4',
    };
    const componentLabel = `component:${classification.component}`;
    await Promise.all([
        ensureLabel(octokit, owner, repo, classification.type, { color: typeColorMap[classification.type] || 'cccccc', description: `Issue type: ${classification.type}` }),
        ensureLabel(octokit, owner, repo, componentLabel, { color: '1d76db', description: `Affected component: ${classification.component}` }),
    ]);
    await octokit.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: [classification.type, componentLabel] });

    const joinedContexts = repoScan.matches.map(m => `File: ${m.file}\n${m.snippet}`).join("\n---\n");
    
    // --- DETAILED PROMPT FIX (from previous request) ---
    const recPrompt = `You are an expert senior engineer and specialist in the SAP CAP Java SDK ecosystem. Use the project context below to ground your analysis in the actual architecture.

**Project Context:**
${PROJECT_CONTEXT}

**Issue Classification:** Type = ${classification.type}, Component = ${classification.component}
${priorAnalysis ? `\n⚠️ **This is a Refined Analysis.** The previous automated analysis is shown below — deepen or correct it based on updated context:\n\`\`\`\n${truncate(priorAnalysis, 2000)}\n\`\`\`\n` : ''}
A new issue was filed. Use the code contexts to hypothesize root causes and generate a detailed, prioritized remediation checklist. Your output must strictly follow the required markdown structure below.

Crucially, for the most likely and actionable remediation steps, you **must include the exact code snippet** showing the required change in a markdown code block. Do not just describe the fix—show the code.

Format your output strictly as:

######
## 🧪 Initial Analysis & Proposed Remediation

**Summary & Root Cause Hypothesis**
[A detailed summary of the issue, including a hypothesis on the root cause. Reference specific classes (e.g., TokenHandler, SDMServiceImpl, CacheConfig) where relevant.]

---

### 🥇 Prioritized Remediation Steps (with Code Fixes)

1. **Verify Annotation Placement (High Priority):**
    * **Rationale:** [Explain why this is the most likely fix, e.g., technical fields require a specific placement.]
    * **Action & Required Change:** [State the action clearly, followed by the specific code snippet showing the fix in CDS or a relevant configuration file (e.g., manifest.json). If no change is required, state the expected state.]

2. **Inspect OData $metadata Output (High Priority):**
    * **Rationale:** [Explain what inspecting the metadata will confirm (backend generation vs. UI rendering issue).]
    * **Action & Command:** [Provide the exact command/URL to check, e.g., \`https://<service>/$metadata\`]

3. **Test UI-Level Override (Medium Priority):**
    * **Rationale:** [Explain why a UI override might be necessary if the backend annotation is ignored.]
    * **Action & Required Change:** [Provide the action and the specific code snippet for the change, likely in \`manifest.json\` or a similar UI config.]

---

**Risk Assessment**
[A brief assessment of the risk/impact of applying the proposed fixes.]
######

Issue Title: ${issueTitle}
Issue Body: ${issueBody}
Relevant Code Contexts (truncated):
${truncate(joinedContexts, 12000)}
`;
    // --- END DETAILED PROMPT FIX ---
    
    let recommendations = "Failed to generate recommendations.";
    try {
        const recResult = await fetchWithBackoff(() => flashModel.generateContent(recPrompt));
        recommendations = recResult.response.text();
    } catch (error) {
        console.error("Error generating remediation recommendations", error);
        // Post a single error message for the issue handler
        recommendations = `❌ **Gemini Analysis Failed** ❌\n\nA critical error occurred while generating the initial analysis (likely due to an incorrect model configuration, API key issue, or a malformed request). The error was:\n\n\`\`\`\n${error.message}\n\`\`\`\n\n**Action Required:** Please check the model configuration and API key.`;
    }
    
    await ensureLabel(octokit, owner, repo, "awaiting-confirmation", { description: "Pending maintainer confirmation for remediation", color: "5319e7" });
    await octokit.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: ["awaiting-confirmation"] });
    const confirmComment = `${recommendations}\n\n**Next Step:** Reply with \`confirm remediation\` to approve moving forward (e.g., drafting a PR or creating task list). Reply with \`refine analysis\` for a deeper pass, or \`discard recommendations\` to remove them.`;
    await octokit.rest.issues.createComment({ owner, repo, issue_number: issueNumber, body: confirmComment });
    console.log("Posted remediation proposal awaiting confirmation.");
}

// ----------------------------------------------------------------------------------------------
// Duplicate Issue Detection & Repository Scan Helpers
// ----------------------------------------------------------------------------------------------

function tokenize(text) {
    return (text || "").toLowerCase().replace(/[^a-z0-9\s]/g, " ").split(/\s+/).filter(Boolean);
}

function jaccardSimilarity(aTokens, bTokens) {
    const a = new Set(aTokens);
    const b = new Set(bTokens);
    const intersection = [...a].filter(t => b.has(t));
    const unionSize = new Set([...a, ...b]).size || 1;
    return intersection.length / unionSize;
}

async function fetchPastIssues(octokit, owner, repo, currentIssueNumber) {
    const issues = await octokit.paginate(octokit.rest.issues.listForRepo, { owner, repo, state: "all", per_page: 100 });
    return issues.filter(i => i.number !== currentIssueNumber); // exclude current
}

function extractResolution(body) {
    if (!body) return null;
    const resolutionMatch = body.match(/(?:Resolution|Fix|Root Cause)[:\-]\s*([\s\S]{0,400})/i);
    return resolutionMatch ? resolutionMatch[1].trim() : null;
}

function truncate(str, max) {
    if (!str) return "";
    return str.length <= max ? str : str.slice(0, max) + "...";
}

function lexicalSimilarityCandidate(newTitleTokens, newBodyTokens, issue) {
    const titleScore = jaccardSimilarity(newTitleTokens, tokenize(issue.title));
    const bodyScore = jaccardSimilarity(newBodyTokens, tokenize(issue.body));
    return (titleScore * 0.7) + (bodyScore * 0.3);
}

async function findSimilarIssueSemantic(title, body, pastIssues, genAI) {
    const MAX_EMBED_ISSUES = safeParseInt(process.env.MAX_SEMANTIC_ISSUES, 150);
    let embeddingModel;
    try { embeddingModel = genAI.getGenerativeModel({ model: "text-embedding-004" }); } catch { embeddingModel = null; }
    const newTitleTokens = tokenize(title); const newBodyTokens = tokenize(body);
    const newText = `${title}\n${body}`;
    let newEmbedding = null;
    if (embeddingModel) {
        try { newEmbedding = await getEmbeddingSafe(embeddingModel, newText); } catch (e) { console.warn("New issue embedding failed", e.message); }
    }
    const scored = pastIssues.map(i => ({ issue: i, score: lexicalSimilarityCandidate(newTitleTokens, newBodyTokens, i) }))
        .sort((a,b)=>b.score-a.score).slice(0, MAX_EMBED_ISSUES);
    let best = null;
    if (newEmbedding) {
        for (const candidate of scored) {
            let emb; try { emb = await getEmbeddingSafe(embeddingModel, `${candidate.issue.title}\n${candidate.issue.body}`); } catch { continue; }
            const cosine = cosineSimilarity(newEmbedding, emb);
            // Semantic match is weighted much higher here for better accuracy based on content
            const combined = (cosine * 0.85) + (candidate.score * 0.15); 
            if (!best || combined > best.score) best = { ...candidate.issue, score: combined };
        }
        // Higher threshold for semantic duplicate since it's based on content vectors
        if (best && best.score >= 0.78) return best; 
    }
    
    // Fallback: If no embedding model or no strong semantic match, use LLM for refinement on best lexical match
    const lexicalBest = scored[0];
    if (lexicalBest && lexicalBest.score >= 0.5) {
        try {
            const flashModel = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
            const similarityPrompt = `Determine if Issue A duplicates Issue B based on the **full context (title and body)**. Respond only with YES or NO.\nIssue A Title: ${title}\nIssue A Body: ${truncate(body, 1000)}\nIssue B Title: ${lexicalBest.issue.title}\nIssue B Body: ${truncate(lexicalBest.issue.body, 1000)}\n`;
            const res = await fetchWithBackoff(() => flashModel.generateContent(similarityPrompt));
            if (/YES/.test(res.response.text().trim().toUpperCase())) return { ...lexicalBest.issue, score: lexicalBest.score };
        } catch (e) { console.warn("LLM similarity refinement failed", e.message); }
    }
    return null;
}

async function getEmbeddingSafe(embeddingModel, text) {
    const result = await fetchWithBackoff(() => embeddingModel.embedContent(text));
    const vector = result.embedding?.values || result.embedding || result?.data || [];
    if (!Array.isArray(vector) || vector.length === 0) throw new Error("Empty embedding vector");
    return vector;
}

function cosineSimilarity(a,b){
    if(!a||!b||a.length!==b.length) return 0;
    let dot=0; let na=0; let nb=0;
    for(let i=0;i<a.length;i++){
        dot += a[i]*b[i];
        na += a[i]*a[i];
        nb += b[i]*b[i];
    }
    return dot/((Math.sqrt(na)*Math.sqrt(nb))||1);
}
/**
 * Scan the repository for lines possibly related to an issue by keyword intersection.
 * Heuristic: collect tokens >3 chars from title/body; walk allowed extensions; count hits; return up to 40 lines containing any keyword per file.
 */
function scanRepositoryForIssue(issueTitle, issueBody, rootDir) {
    const keywords = [...new Set([...tokenize(issueTitle), ...tokenize(issueBody)]).values()].filter(k => k.length > 3);
    const matches = [];
    const exts = new Set([".js", ".ts", ".java", ".md", ".yml", ".yaml", ".xml", ".json", ".cds", ".mta"]);
    function walk(dir) {
        let entries;
        try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
        for (const entry of entries) {
            const full = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'target') continue;
                walk(full);
            } else {
                const ext = path.extname(entry.name);
                if (!exts.has(ext)) continue;
                let content;
                try { content = fs.readFileSync(full, 'utf8'); } catch { continue; }
                const lower = content.toLowerCase();
                let hitCount = 0;
                for (const kw of keywords) {
                    if (lower.includes(kw)) hitCount++;
                }
                if (hitCount > 0) {
                    const lines = content.split(/\r?\n/);
                    const relevant = lines.filter(l => keywords.some(k => l.toLowerCase().includes(k))).slice(0, 40);
                    matches.push({ file: path.relative(rootDir, full), snippet: relevant.join("\n") });
                }
            }
        }
    }
    walk(rootDir);
    return { keywords, matches };
}

async function ensureLabel(octokit, owner, repo, name, meta) {
    try {
        await octokit.rest.issues.getLabel({ owner, repo, name });
    } catch (e) {
        if (e.status === 404) {
            await octokit.rest.issues.createLabel({ owner, repo, name, color: meta.color || 'cccccc', description: meta.description || '' });
        } else {
            console.warn(`Could not verify/create label ${name}:`, e.message);
        }
    }
}

// Main function
// This is the entry point for the script execution.
//----------------------------------------------------------------------------------------------------------------

async function run() {
    try {
        const octokit = getOctokit(process.env.GITHUB_TOKEN);
        const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
        
        const { owner, repo } = context.repo;

        // Determine the number based on the event payload (only issue number is available from comment event)
        let number;
        if (context.payload.issue) {
            number = context.payload.issue.number;
        } else {
            console.log("Could not determine issue/PR number from payload. Exiting.");
            return;
        }

        // Conditional logic based on event type

        if (context.eventName === 'issue_comment') {
            const commentBody = context.payload.comment.body.toLowerCase().trim();

            if (context.payload.issue.pull_request) {
                // This is a comment on a Pull Request (PR)
                
                // CRITICAL FIX: Fetch the full PR object for use in subsequent functions (labels, head.ref)
                const { data: pullRequest } = await octokit.rest.pulls.get({
                    owner,
                    repo,
                    pull_number: number,
                });
                context.payload.pull_request = pullRequest; // Attach full PR object to context
                
                // 1. Check for explicit review command
                if (commentBody.includes('review this pr') || commentBody.includes('gemini review')) {
                    console.log(`Explicit review command detected on PR #${number}. Initiating full review.`);
                    
                    const diffContent = await getDiff(octokit, owner, repo, number);
                    await performPRReview(octokit, diffContent, number, genAI);
                    
                // 2. Check for general "Hey Gemini" question
                } else if (commentBody.startsWith("hey gemini,")) { 
                    console.log(`"Hey Gemini," question detected on PR #${number}. Initiating response.`);
                    await handleCommentResponse(octokit, context.payload.comment.body, number, genAI);
                } else {
                    console.log(`Comment on PR #${number} did not contain a review or question command. No action taken.`);
                }
                
            } else {
                // This is a comment on a regular Issue
                 if (commentBody === 'confirm remediation') {
                    // Maintainer confirmation flow
                    const issueLabels = context.payload.issue.labels.map(l => l.name);
                    if (issueLabels.includes('awaiting-confirmation')) {
                        console.log('Remediation confirmed. Generating task checklist...');

                        // Fetch the last bot comment that contains the analysis
                        const { data: allComments } = await octokit.rest.issues.listComments({ owner, repo, issue_number: number, per_page: 100 });
                        const analysisComment = [...allComments].reverse().find(
                            c => c.user.login === 'github-actions[bot]' && c.body.includes('Prioritized Remediation Steps')
                        );
                        const priorAnalysis = analysisComment ? analysisComment.body : '(no prior analysis found)';

                        const taskModel = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
                        const taskPrompt = `Based on this issue remediation analysis, generate a concise GitHub Markdown task checklist using - [ ] checkboxes. Group tasks under clear headings (e.g., ## Investigation, ## Code Changes, ## Verification). Keep each task to one concrete action. Output only the checklist — no preamble, no summary.

Analysis:
${truncate(priorAnalysis, 3000)}`;
                        let taskList = '';
                        try {
                            const taskResult = await fetchWithBackoff(() => taskModel.generateContent(taskPrompt));
                            taskList = taskResult.response.text();
                        } catch (e) {
                            console.error("Failed to generate task checklist:", e.message);
                            taskList = '_(Checklist generation failed — please review the analysis comment above and create tasks manually.)_';
                        }

                        await ensureLabel(octokit, owner, repo, 'remediation-approved', { description: 'Remediation steps approved by maintainer', color: '0e8a16' });
                        await octokit.rest.issues.addLabels({ owner, repo, issue_number: number, labels: ['remediation-approved'] });
                        // Remove awaiting-confirmation label
                        const remaining = issueLabels.filter(l => l !== 'awaiting-confirmation');
                        try { await octokit.rest.issues.removeLabel({ owner, repo, issue_number: number, name: 'awaiting-confirmation' }); } catch {}
                        await octokit.rest.issues.createComment({
                            owner, repo, issue_number: number,
                            body: `✅ **Remediation Approved — Generated Task Checklist**\n\n${taskList}`,
                        });
                    } else {
                        console.log('Confirmation comment received but issue not in awaiting-confirmation state.');
                    }
                } else if (commentBody === 'refine analysis') {
                    console.log('Refine analysis requested.');
                    const issueTitle = context.payload.issue.title;
                    const issueBody = context.payload.issue.body;

                    // Fetch the last bot analysis comment to use as prior context for refinement
                    const { data: allComments } = await octokit.rest.issues.listComments({ owner, repo, issue_number: number, per_page: 100 });
                    const priorBotComment = [...allComments].reverse().find(c => c.user.login === 'github-actions[bot]');
                    const priorAnalysis = priorBotComment ? priorBotComment.body : null;

                    await handleNewIssue(octokit, owner, repo, number, issueTitle, issueBody, genAI, priorAnalysis);
                } else if (commentBody === 'discard recommendations') {
                    console.log('Discard recommendations requested.');
                    try { await octokit.rest.issues.removeLabel({ owner, repo, issue_number: number, name: 'awaiting-confirmation' }); } catch {}
                    await octokit.rest.issues.createComment({ owner, repo, issue_number: number, body: '🗑️ Recommendations discarded. Provide new details or ask for re-analysis if needed.' });
                } else if (commentBody.startsWith("hey gemini,")) {
                    console.log(`"Hey Gemini," comment detected on Issue #${number}. Initiating response.`);
                    await handleCommentResponse(octokit, context.payload.comment.body, number, genAI);
                } else {
                    console.log(`Comment on Issue #${number} did not contain a question command. No action taken.`);
                }
            }
            
        } else if (context.eventName === 'issues' && context.payload.action === 'opened') {
            // New Issue Handling 
            console.log(`New Issue event detected for #${number}. Generating summary.`);
            const issueTitle = context.payload.issue.title;
            const issueBody = context.payload.issue.body;
            await handleNewIssue(octokit, owner, repo, number, issueTitle, issueBody, genAI);

        } else if (context.eventName === 'issues' && context.payload.action === 'edited') {
            // Issue body/title was edited — if no prior analysis exists, run fresh; otherwise nudge for refinement
            console.log(`Issue #${number} was edited. Checking for existing analysis...`);
            const { data: allComments } = await octokit.rest.issues.listComments({ owner, repo, issue_number: number, per_page: 100 });
            const hasBotAnalysis = allComments.some(
                c => c.user.login === 'github-actions[bot]' && c.body.includes('Prioritized Remediation Steps')
            );
            if (hasBotAnalysis) {
                // An analysis already exists — notify the author that they can request a refresh
                await octokit.rest.issues.createComment({
                    owner, repo, issue_number: number,
                    body: `📝 **Issue Updated** — the description has been edited.\n\nIf the changes significantly affect scope or context, comment \`refine analysis\` and I'll generate a fresh analysis based on the updated content.`,
                });
            } else {
                // No prior analysis — run fresh analysis
                console.log(`No prior analysis found for #${number}. Running fresh analysis.`);
                const issueTitle = context.payload.issue.title;
                const issueBody = context.payload.issue.body;
                await handleNewIssue(octokit, owner, repo, number, issueTitle, issueBody, genAI);
            }

        } else {
            console.log(`Event '${context.eventName}' did not match any triggers. No action taken.`);
        }
    } catch (error) {
        console.error(`An error occurred: ${error.message}`);
        throw error;
    }
}

run();
