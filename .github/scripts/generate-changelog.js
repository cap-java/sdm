/**
 * AI Changelog Generator
 * ─────────────────────────────────────────────────────────────────────────────
 * Triggered by the changelog.yml workflow on every GitHub release event.
 *
 * What it does:
 *  1. Finds the previous tag to determine the commit range
 *  2. Fetches merged PRs and commit messages since the last tag
 *  3. Calls Gemini to write a structured changelog entry matching the project's
 *     existing CHANGELOG.md format (## Version / ### Added / ### Fixed …)
 *  4. Prepends the generated entry to CHANGELOG.md
 *  5. Git commit + push back to develop is handled by the workflow (not here)
 *
 * Required env vars: GITHUB_TOKEN, GEMINI_API_KEY, RELEASE_TAG
 */

'use strict';

const { getOctokit, context } = require('@actions/github');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const fs = require('fs');
const path = require('path');

// ─── Helpers ─────────────────────────────────────────────────────────────────

function truncate(str, max) {
    if (!str) return '';
    return str.length <= max ? str : str.slice(0, max) + '...';
}

async function fetchWithRetry(fn, retries = 3, delay = 1000) {
    for (let i = 0; i < retries; i++) {
        try { return await fn(); }
        catch (e) {
            if (i === retries - 1 || (e.status && e.status < 500)) throw e;
            console.warn(`Retry ${i + 1}/${retries} after error: ${e.message}`);
            await new Promise(r => setTimeout(r, delay * Math.pow(2, i)));
        }
    }
}

// ─── Main ────────────────────────────────────────────────────────────────────

async function run() {
    const token      = process.env.GITHUB_TOKEN;
    const apiKey     = process.env.GEMINI_API_KEY;
    const currentTag = process.env.RELEASE_TAG;

    if (!token)      throw new Error('GITHUB_TOKEN is required');
    if (!currentTag) throw new Error('RELEASE_TAG is required');

    const octokit        = getOctokit(token);
    const { owner, repo } = context.repo;

    console.log(`\n📋 Generating changelog for: ${owner}/${repo} @ ${currentTag}\n`);

    // ─── 1. Find previous tag ────────────────────────────────────────────────
    const tags = await fetchWithRetry(() =>
        octokit.paginate(octokit.rest.repos.listTags, { owner, repo, per_page: 100 })
    );

    const currentIdx  = tags.findIndex(t => t.name === currentTag);
    const previousTag = currentIdx >= 0 && tags[currentIdx + 1] ? tags[currentIdx + 1].name : null;

    console.log(`🔖 Range: ${previousTag ?? '(beginning of repo)'} → ${currentTag}`);

    // ─── 2. Get commits between tags ─────────────────────────────────────────
    let commitMessages = [];
    if (previousTag) {
        try {
            const { data } = await fetchWithRetry(() =>
                octokit.rest.repos.compareCommits({ owner, repo, base: previousTag, head: currentTag })
            );
            commitMessages = data.commits
                .map(c => c.commit.message.split('\n')[0].trim())
                .filter(m =>
                    m.length > 0 &&
                    !m.startsWith('Merge ') &&
                    !m.startsWith('Update version') &&
                    !m.startsWith("docs(changelog):")
                );
            console.log(`📝 Found ${commitMessages.length} relevant commits`);
        } catch (e) {
            console.warn('⚠️  Could not compare commits:', e.message);
        }
    }

    // ─── 3. Get merged PRs since previous tag ────────────────────────────────
    let mergedPRs = [];
    try {
        // Resolve the date of the previous tag's commit for filtering PRs
        let since = null;
        if (previousTag) {
            const refData = await fetchWithRetry(() =>
                octokit.rest.git.getRef({ owner, repo, ref: `tags/${previousTag}` })
            );
            let sha = refData.data.object.sha;
            // Annotated tags point to a tag object, not a commit directly
            if (refData.data.object.type === 'tag') {
                const tagObj = await fetchWithRetry(() =>
                    octokit.rest.git.getTag({ owner, repo, tag_sha: sha })
                );
                sha = tagObj.data.object.sha;
            }
            const commitData = await fetchWithRetry(() =>
                octokit.rest.repos.getCommit({ owner, repo, ref: sha })
            );
            since = commitData.data.commit.committer.date;
            console.log(`📅 Looking for PRs merged after: ${since}`);
        }

        const allPRs = await fetchWithRetry(() =>
            octokit.paginate(octokit.rest.pulls.list, {
                owner, repo, state: 'closed', per_page: 100, sort: 'updated', direction: 'desc'
            })
        );

        mergedPRs = allPRs
            .filter(pr => {
                if (!pr.merged_at) return false;
                if (since && new Date(pr.merged_at) <= new Date(since)) return false;
                return true;
            })
            .map(pr => ({
                number: pr.number,
                title:  pr.title,
                labels: pr.labels.map(l => l.name),
                body:   truncate(pr.body || '', 400),
            }));

        console.log(`🔀 Found ${mergedPRs.length} merged PRs`);
    } catch (e) {
        console.warn('⚠️  Could not fetch merged PRs:', e.message);
    }

    // ─── 4. Build Gemini prompt ───────────────────────────────────────────────
    const prBlock = mergedPRs.length > 0
        ? mergedPRs.map(pr =>
            `- PR #${pr.number}: "${pr.title}" [labels: ${pr.labels.join(', ') || 'none'}]\n  Body: ${pr.body || '(none)'}`)
          .join('\n')
        : '(no PR data available — use commit messages)';

    const commitBlock = commitMessages.length > 0
        ? commitMessages.slice(0, 50).map(m => `- ${m}`).join('\n')
        : '(no commit data available)';

    const prompt = `You are a technical changelog writer for the SAP CAP Java SDK Document Management Service plugin.

**Project context:**
This is a Maven JAR library (Java 17) used by SAP CAP Java applications to route Attachment CRUD events to SAP Document Management Service via CMIS REST API. Key subsystems: CAP event handlers (@Before/@On/@After), OAuth2 token management (TokenHandler), EhCache 3 caching (8 named caches), Apache HttpClient 5 CMIS calls, multi-tenant Cloud Foundry deployments, upload virus scan states (uploading / Success / Failed / VirusDetected / VirusScanInprogress).

**Version:** ${currentTag}

**Merged PRs since previous release (${previousTag ?? 'beginning'}):**
${prBlock}

**Relevant commit messages:**
${commitBlock}

**Instructions — follow EXACTLY:**
1. Output ONLY the changelog entry — no preamble, no explanation, no markdown fences.
2. Match this exact format:

## Version ${currentTag}

### Added
- <user-facing feature description>

### Fixed
- <user-facing bug fix description>

3. Only include sections (Added / Fixed / Changed / Security / Deprecated) that have actual content — omit sections with nothing to report.
4. Write each bullet as a clear, concise sentence a library consumer can understand. No internal class names.
5. If a PR title or commit is vague, infer from the body or omit it entirely.
6. Do NOT fabricate changes — only describe what is evidenced by the PR/commit data above.`;

    // ─── 5. Call Gemini (with structured fallback) ────────────────────────────
    let changelogEntry = '';

    if (apiKey) {
        try {
            const genAI = new GoogleGenerativeAI(apiKey);
            const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
            const result = await fetchWithRetry(() => model.generateContent(prompt));
            changelogEntry = result.response.text().trim();
            console.log('\n✅ Gemini generated changelog entry:\n');
            console.log(changelogEntry);
        } catch (e) {
            console.warn(`⚠️  Gemini failed (${e.message}), falling back to structured list.`);
        }
    } else {
        console.warn('⚠️  GEMINI_API_KEY not set — using structured fallback.');
    }

    // Structured fallback: build entry from PR labels without Gemini
    if (!changelogEntry) {
        const added   = mergedPRs.filter(pr => pr.labels.some(l => ['enhancement', 'feature'].includes(l)));
        const fixed   = mergedPRs.filter(pr => pr.labels.some(l => ['bug', 'fix'].includes(l)));
        const security = mergedPRs.filter(pr => pr.labels.includes('security'));
        const other   = mergedPRs.filter(pr =>
            !added.includes(pr) && !fixed.includes(pr) && !security.includes(pr) && pr.title
        );

        changelogEntry = `## Version ${currentTag}`;
        if (added.length)    changelogEntry += `\n\n### Added\n${added.map(pr    => `- ${pr.title} (#${pr.number})`).join('\n')}`;
        if (fixed.length)    changelogEntry += `\n\n### Fixed\n${fixed.map(pr    => `- ${pr.title} (#${pr.number})`).join('\n')}`;
        if (security.length) changelogEntry += `\n\n### Security\n${security.map(pr => `- ${pr.title} (#${pr.number})`).join('\n')}`;
        if (other.length)    changelogEntry += `\n\n### Changed\n${other.map(pr  => `- ${pr.title} (#${pr.number})`).join('\n')}`;

        if (mergedPRs.length === 0 && commitMessages.length > 0) {
            changelogEntry += `\n\n### Changed\n${commitMessages.slice(0, 15).map(m => `- ${m}`).join('\n')}`;
        }

        console.log('\n📋 Structured fallback entry:\n');
        console.log(changelogEntry);
    }

    // ─── 6. Prepend entry to CHANGELOG.md ────────────────────────────────────
    const changelogPath = path.join(process.cwd(), 'CHANGELOG.md');

    if (!fs.existsSync(changelogPath)) {
        throw new Error(`CHANGELOG.md not found at ${changelogPath}`);
    }

    const existing = fs.readFileSync(changelogPath, 'utf8');

    // Guard: don't double-write if this version already exists
    if (existing.includes(`## Version ${currentTag}`)) {
        console.log(`\nℹ️  Version ${currentTag} already present in CHANGELOG.md — skipping write.`);
        return;
    }

    // Insert after the header lines (everything before the first "## Version" block)
    const firstVersionIdx = existing.indexOf('\n## Version');
    let newContent;
    if (firstVersionIdx !== -1) {
        newContent =
            existing.slice(0, firstVersionIdx + 1) +
            '\n' + changelogEntry + '\n' +
            existing.slice(firstVersionIdx + 1);
    } else {
        // No existing version entries — append after header
        newContent = existing.trimEnd() + '\n\n' + changelogEntry + '\n';
    }

    fs.writeFileSync(changelogPath, newContent, 'utf8');
    console.log('\n✅ CHANGELOG.md updated successfully.');
}

run().catch(err => {
    console.error('❌ Changelog generation failed:', err.message);
    process.exit(1);
});
