# AI Code Review Bot - SAP AI Core Integration

## Summary of Changes

This project has been migrated from **Google Gemini API** to **SAP AI Core SDK** for AI-powered code reviews, issue analysis, and automated documentation generation.

## Key Changes

### 1. Dependencies Updated

**Removed:**
- `@google/generative-ai`

**Added:**
- `@sap-ai-sdk/orchestration` (v2.4.0)
- `@sap-ai-sdk/foundation-models` (v2.4.0)

### 2. Environment Variables

| Old Variable | New Variable | Description |
|-------------|--------------|-------------|
| `GEMINI_API_KEY` | `AICORE_SERVICE_KEY` | JSON service key containing client credentials |

### 3. API Changes

All AI operations now use SAP AI Core's Orchestration Service:

- **Chat Completion:** `aiClient.chatCompletion({ messagesHistory: [...] })`
- **Response Extraction:** `result.getContent()`
- **Embeddings:** `embeddingModel.run({ input: text })`

### 4. Models Used

| Purpose | Old Model | New Model |
|---------|-----------|-----------|
| Code Review | gemini-2.5-flash | gpt-4o |
| Embeddings | text-embedding-004 | text-embedding-ada-002 |

## Installation

```bash
cd .github/scripts
npm install
```

## Configuration

### Setting up SAP AI Core Service Key

1. **Create SAP AI Core Instance:**
   - Log into SAP BTP Cockpit
   - Create an AI Core service instance with `extended` or `sap-internal` plan
   - Create a service key

2. **Add Service Key to GitHub:**
   - Go to Repository Settings > Secrets and variables > Actions
   - Create a new secret: `AICORE_SERVICE_KEY`
   - Paste your service key JSON

Example service key format:
```json
{
  "clientid": "your-client-id",
  "clientsecret": "your-client-secret",
  "url": "https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com",
  "serviceurls": {
    "AI_API_URL": "https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com"
  }
}
```

### Ensure GPT-4o Deployment

Make sure you have a `gpt-4o` deployment in your SAP AI Core instance:

1. Log into SAP AI Launchpad
2. Navigate to ML Operations > Deployments
3. Create a deployment for `gpt-4o` if not already available
4. Wait for status to be "RUNNING"

## Usage

The bot responds to the following triggers:

### For Pull Requests

**Review Commands:**
- "review this pr"
- "gemini review" (legacy)
- "ai review" (new)

**Ask Questions:**
- "hey gemini, [your question]" (legacy)
- "hey ai, [your question]" (new)

### For Issues

**On New Issues:**
- Automatically analyzes and suggests remediation

**Maintainer Actions:**
- "confirm remediation" - Approve suggested fixes
- "refine analysis" - Request deeper analysis
- "discard recommendations" - Dismiss suggestions
- "not a duplicate" - Override duplicate detection

**Ask Questions:**
- "hey gemini, [your question]"
- "hey ai, [your question]"

## Features

### 1. PR Code Review
- Analyzes Git diffs
- Identifies bugs, best practice violations
- Provides actionable recommendations with code snippets
- Quality rating (0-10)

### 2. Automated Documentation
- Updates README when changes affect documentation
- Generates feature docs for PRs with 'feature' label

### 3. Issue Management
- Semantic duplicate detection
- Root cause analysis
- Remediation recommendations
- Repository code scanning

### 4. Interactive Q&A
- Context-aware responses
- Supports both PR and issue discussions

## Architecture

```
┌─────────────────────┐
│  GitHub Actions     │
│  (Workflow Runner)  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    review.js        │
│  (Main Script)      │
└──────────┬──────────┘
           │
           ├──────────────────────┐
           │                      │
           ▼                      ▼
┌──────────────────────┐  ┌──────────────────┐
│ OrchestrationClient  │  │ EmbeddingClient  │
│  (Chat/Generation)   │  │  (Similarity)    │
└──────────┬───────────┘  └──────────┬───────┘
           │                         │
           └────────────┬────────────┘
                        │
                        ▼
           ┌────────────────────────┐
           │   SAP AI Core          │
           │   (OAuth + LLM Proxy)  │
           └────────────┬───────────┘
                        │
                        ▼
           ┌────────────────────────┐
           │  Azure OpenAI / Others │
           │  (Underlying Models)   │
           └────────────────────────┘
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GITHUB_TOKEN` | Yes | - | GitHub API token |
| `AICORE_SERVICE_KEY` | Yes | - | SAP AI Core service credentials (JSON) |
| `MAX_RETRIES` | No | 5 | Max retry attempts for transient errors |
| `INITIAL_DELAY_MS` | No | 1000 | Initial retry delay in milliseconds |
| `MAX_CHUNK_TOKENS` | No | 10000 | Max tokens per diff chunk |
| `MAX_SEMANTIC_ISSUES` | No | 150 | Max issues for semantic similarity |

## Error Handling

The script includes robust error handling:

1. **Transient Errors (429, 5xx):** Automatic retry with exponential backoff
2. **Fatal Errors (400, 404):** Immediate failure with error reporting
3. **Authentication Errors:** Clear messaging about service key issues
4. **Model Errors:** Graceful fallback with error messages posted to GitHub

## Local Development

1. Create `.env` file:
```bash
AICORE_SERVICE_KEY='{"clientid":"...","clientsecret":"...","url":"..."}'
GITHUB_TOKEN='your-github-token'
```

2. Run the script:
```bash
node --env-file=.env review.js
```

## Migration Notes

If you're migrating from the Google Gemini version:

1. ✅ All existing functionality preserved
2. ✅ Backward compatible trigger phrases ("hey gemini")
3. ✅ New trigger phrases added ("hey ai", "ai review")
4. ✅ Error messages now vendor-agnostic
5. ⚠️ Token counting replaced with character-based chunking (1 token ≈ 4 chars)
6. ⚠️ Embedding model changed (text-embedding-004 → text-embedding-ada-002)

## Benefits of SAP AI Core

1. **Enterprise Security:** OAuth 2.0, service key management via SAP BTP
2. **Cost Control:** Centralized billing and usage tracking
3. **Compliance:** Data residency options, audit logs
4. **Flexibility:** Access multiple LLM providers through one interface
5. **Integration:** Native integration with SAP ecosystem

## Troubleshooting

### "Could not find service credentials for AI Core"
**Solution:** Verify `AICORE_SERVICE_KEY` is set correctly in GitHub Secrets

### Authentication 401 Errors
**Solution:** 
- Check client credentials are correct
- Verify service key hasn't expired
- Ensure AI Core instance is active

### "Model not found" Errors
**Solution:**
- Verify gpt-4o deployment exists in your resource group
- Check deployment status is "RUNNING"
- Ensure you're using the correct resource group (default is 'default')

### Rate Limiting Issues
**Solution:**
- Increase `MAX_RETRIES` and `INITIAL_DELAY_MS`
- Check your AI Core service plan limits
- Monitor usage in SAP AI Launchpad

## Files Modified

- `.github/scripts/review.js` - Main script (fully rewritten)
- `.github/scripts/package.json` - New dependencies
- `.github/workflows/ai-review-example.yml` - Sample workflow

## Files Added

- `.github/scripts/SAP_AI_CORE_MIGRATION.md` - Detailed migration guide
- `.github/scripts/README.md` - This file

## Additional Resources

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP AI SDK JavaScript](https://sap.github.io/ai-sdk/docs/js/overview-cloud-sdk-for-ai-js)
- [SAP AI Launchpad](https://help.sap.com/docs/ai-launchpad)
- [GitHub: SAP AI SDK JS](https://github.com/SAP/ai-sdk-js)

## Support

For issues related to:
- **SAP AI Core:** Open a ticket in SAP Support Portal
- **This Script:** Create an issue in this repository
- **SAP AI SDK:** [GitHub Issues](https://github.com/SAP/ai-sdk-js/issues)

## License

Same as parent repository.
