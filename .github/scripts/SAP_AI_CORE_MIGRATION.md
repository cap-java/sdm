# SAP AI Core SDK Migration Guide

## Overview

This document describes the migration from Google Gemini API to SAP AI Core SDK in the `review.js` GitHub Actions script.

## Changes Made

### 1. Package Dependencies

**Previous:**
```javascript
const { GoogleGenerativeAI } = require("@google/generative-ai");
```

**New:**
```javascript
const { OrchestrationClient } = require("@sap-ai-sdk/orchestration");
const { AzureOpenAiChatClient, AzureOpenAiEmbeddingClient } = require("@sap-ai-sdk/foundation-models");
```

### 2. Required npm Packages

Install the following packages in your GitHub Action workflow or package.json:

```bash
npm install @sap-ai-sdk/orchestration @sap-ai-sdk/foundation-models
```

### 3. Environment Variables

**Previous:**
- `GEMINI_API_KEY` - Google Gemini API key

**New:**
- `AICORE_SERVICE_KEY` - SAP AI Core service key (JSON format)

#### Setting Up AICORE_SERVICE_KEY

The `AICORE_SERVICE_KEY` should contain a JSON object with your SAP AI Core service credentials:

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

To set it as a GitHub Secret:
1. Go to your repository's Settings > Secrets and variables > Actions
2. Click "New repository secret"
3. Name: `AICORE_SERVICE_KEY`
4. Value: Paste the entire JSON service key (as a single line or formatted)
5. Click "Add secret"

#### Obtaining SAP AI Core Service Key

1. Log into SAP BTP Cockpit
2. Navigate to your subaccount
3. Go to Services > Instances and Subscriptions
4. Find your AI Core service instance
5. Click on the instance and create/view a service key
6. Copy the JSON credentials

### 4. Model Configuration

**Previous Model:**
- `gemini-2.5-flash` (Google Gemini)
- `text-embedding-004` (Google embeddings)

**New Model:**
- `gpt-4o` (Azure OpenAI via SAP AI Core)
- `text-embedding-ada-002` (Azure OpenAI embeddings)

The model is configured in the `OrchestrationClient` initialization:

```javascript
const aiClient = new OrchestrationClient({
    llm: {
        modelName: 'gpt-4o',
        modelParams: {
            max_tokens: 4096,
            temperature: 0.7
        }
    }
});
```

### 5. API Method Changes

#### Content Generation

**Previous:**
```javascript
const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
const result = await model.generateContent(prompt);
const text = result.response.text();
```

**New:**
```javascript
const result = await aiClient.chatCompletion({
    messagesHistory: [{ role: 'user', content: prompt }]
});
const text = result.getContent();
```

#### Embeddings

**Previous:**
```javascript
const embeddingModel = genAI.getGenerativeModel({ model: "text-embedding-004" });
const result = await embeddingModel.embedContent(text);
const vector = result.embedding?.values || [];
```

**New:**
```javascript
const embeddingModel = new AzureOpenAiEmbeddingClient({ modelName: 'text-embedding-ada-002' });
const result = await embeddingModel.run({ input: text });
const vector = result.getEmbedding() || [];
```

### 6. Authentication

The SAP AI Core SDK automatically handles authentication using the service key:

- The SDK reads `AICORE_SERVICE_KEY` from `process.env`
- It uses the `clientid` and `clientsecret` to obtain an OAuth access token
- The token is automatically refreshed as needed
- No manual token generation code is required

### 7. User-Facing Changes

The script now responds to both legacy and new trigger phrases:
- **Review Commands:** "review this pr", "gemini review", "ai review"
- **Question Commands:** "hey gemini,", "hey ai,"
- **Review Header:** Changed from "Gemini Automated Review" to "AI Automated Review"

Error messages are now vendor-agnostic, referring to "AI" instead of "Gemini".

## Testing

### Local Testing

To test locally with the SAP AI Core SDK:

1. Create a `.env` file:
```bash
AICORE_SERVICE_KEY='{"clientid":"...","clientsecret":"...","url":"..."}'
GITHUB_TOKEN='your-github-token'
```

2. Load environment variables:
```bash
node --env-file=.env review.js
```

### GitHub Actions Testing

1. Add `AICORE_SERVICE_KEY` as a repository secret
2. Update your workflow YAML to use the new secret:
```yaml
env:
  AICORE_SERVICE_KEY: ${{ secrets.AICORE_SERVICE_KEY }}
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Benefits of SAP AI Core SDK

1. **Enterprise-Grade Security:** Centralized credential management via SAP BTP
2. **Flexible Model Selection:** Access multiple LLM providers (Azure OpenAI, AWS Bedrock, etc.)
3. **Cost Control:** Better visibility and control over AI spending through SAP AI Core
4. **Compliance:** Data residency and compliance features built into SAP AI Core
5. **Orchestration Features:** Built-in templating, grounding, data masking, and content filtering

## Troubleshooting

### Common Issues

1. **"Could not find service credentials for AI Core"**
   - Verify `AICORE_SERVICE_KEY` environment variable is set
   - Check JSON formatting is valid
   - Ensure all required fields are present (clientid, clientsecret, url)

2. **Authentication Errors (401)**
   - Verify client credentials are correct
   - Check if the service key has expired
   - Ensure the AI Core service instance is active

3. **Model Not Found Errors**
   - Verify `gpt-4o` deployment exists in your AI Core resource group
   - Check if you're using the correct resource group
   - Ensure the deployment is in "RUNNING" status

4. **Rate Limiting**
   - SAP AI Core may have different rate limits than Google Gemini
   - Adjust `MAX_RETRIES` and `INITIAL_DELAY_MS` environment variables if needed

## Rollback Plan

If you need to rollback to Google Gemini:

1. Restore the previous version of `review.js` from git history
2. Change the GitHub Secret from `AICORE_SERVICE_KEY` to `GEMINI_API_KEY`
3. Update the workflow to use `GEMINI_API_KEY`
4. Uninstall SAP AI SDK packages and reinstall `@google/generative-ai`

## Additional Resources

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP Cloud SDK for AI JavaScript Documentation](https://sap.github.io/ai-sdk/docs/js/overview-cloud-sdk-for-ai-js)
- [SAP AI Core Service Guide](https://help.sap.com/docs/sap-ai-core/sap-ai-core-service-guide)
- [GitHub: SAP AI SDK JS](https://github.com/SAP/ai-sdk-js)
