# Helper Scripts

This folder contains shell scripts for managing SAP Document Management Service (SDM/CMIS) objects and Cloud Foundry / BTP subscription lifecycle tasks.

All scripts read their configuration from [`cf-config.env`](cf-config.env) located in the **same directory** as the scripts. Copy [`cf-config.env.example`](cf-config.env.example) to `cf-config.env` and fill in your values before running any script.

---

## Configuration file

### `cf-config.env`

Central configuration file sourced by every script. See [`cf-config.env.example`](cf-config.env.example) for a full annotated template.

| Section | Variables |
|---|---|
| Cloud Foundry (provider) | `CF_API_ENDPOINT`, `CF_ORG`, `CF_SPACE`, `CF_USERNAME`, `CF_PASSWORD`, `APP_NAME` |
| CF env-var update | `VAR_NAME`, `VAR_VALUE` |
| Consumer account | `CONSUMER_CF_API_ENDPOINT`, `CONSUMER_CF_ORG`, `CONSUMER_CF_SPACE`, `CONSUMER_CF_USERNAME`, `CONSUMER_CF_PASSWORD` |
| BTP subscription | `CONSUMER_SUBACCOUNT_ID`, `SAAS_APP_NAME`, `SAAS_APP_PLAN`, `ROLE_ASSIGNMENT_EMAILS`, `ROLE_COLLECTION_NAME`, `APP_ROLE_FILTER` |
| BTP CLI | `BTP_CLI_URL`, `BTP_GLOBAL_ACCOUNT_SUBDOMAIN` |
| CMIS / SDM | `CMIS_URL`, `CMIS_REPOSITORY_ID`, `CMIS_TOKEN_URL`, `CMIS_CLIENT_ID`, `CMIS_CLIENT_SECRET`, `CMIS_USERNAME`, `CMIS_PASSWORD`, `CMIS_FOLDER_ID` |

---

## Scripts

### `create.sh` — Upload a document to SDM

**Function**  
Uploads a local file to the SAP Document Management Service repository via the CMIS Browser Binding API. An OAuth2 access token is obtained automatically using the password grant before the upload.

**Required config (`cf-config.env`)**  
`CMIS_URL`, `CMIS_REPOSITORY_ID`, `CMIS_TOKEN_URL`, `CMIS_CLIENT_ID`, `CMIS_CLIENT_SECRET`, `CMIS_USERNAME`, `CMIS_PASSWORD`

**Optional config**  
`CMIS_FOLDER_ID` — fallback target folder object ID; overridden by the `parentFolderID` argument if supplied. If neither is provided the file is uploaded to the repository root.

**Parameters**

| # | Name | Default | Description |
|---|---|---|---|
| 1 | `cmisName` | — | Name the document will have inside the CMIS repository |
| 2 | `file` | — | Path to the local file to upload |
| 3 | `parentFolderID` | _(CMIS_FOLDER_ID or root)_ | CMIS object ID of the parent folder to upload into. Takes precedence over `CMIS_FOLDER_ID` from `cf-config.env`. |

**Shell usage**
```bash
cd helper-scripts

# Upload to the repository root (or CMIS_FOLDER_ID from cf-config.env)
./create.sh "my-document.pdf" "/path/to/my-document.pdf"

# Upload into a specific parent folder
./create.sh "my-document.pdf" "/path/to/my-document.pdf" "<parentFolderObjectId>"
```

**Usage in integration tests**  
Called inside `testCreateEntityAndCheck` (Order 1) via the `runShellScript` helper. Pass the parent folder object ID as the third argument when uploading into a specific folder:
```java
// Without a specific parent folder (uses CMIS_FOLDER_ID from config or root)
int exitCode = runShellScript("../helper-scripts/create.sh", "README.md", "../README.md");
if (exitCode != 0) {
    fail("create.sh exited with non-zero code: " + exitCode);
}

// With an explicit parent folder
int exitCode = runShellScript(
    "../helper-scripts/create.sh", "README.md", "../README.md", parentFolderObjectId);
if (exitCode != 0) {
    fail("create.sh exited with non-zero code: " + exitCode);
}
```

---

### `get-object-id.sh` — Resolve a CMIS object ID by name

**Function**  
Queries the SDM repository using a CMIS SQL statement to find the `cmis:objectId` of an object (folder or document) by its `cmis:name`. The resolved ID is printed to stdout on the last line, making it easy to capture programmatically.

**Required config (`cf-config.env`)**  
`CMIS_URL`, `CMIS_REPOSITORY_ID`, `CMIS_TOKEN_URL`, `CMIS_CLIENT_ID`, `CMIS_CLIENT_SECRET`, `CMIS_USERNAME`, `CMIS_PASSWORD`

**Parameters**

| # | Name | Default | Description |
|---|---|---|---|
| 1 | `cmisName` | — | `cmis:name` of the object to look up |
| 2 | `folderID` | _(repository root)_ | CMIS object ID of the parent folder to search within |
| 3 | `cmisType` | `cmis:folder` | CMIS type to query — use `cmis:document` to find uploaded files |

**Shell usage**
```bash
# Find a folder by name anywhere in the repository
./get-object-id.sh "entityId__attachments"

# Find a document inside a specific folder
./get-object-id.sh "sample.pdf" "<parentFolderObjectId>" "cmis:document"
```

**Usage in integration tests**  
Called inside `testUploadSingleAttachmentPDF` (Order 3) via the `runShellScriptAndCaptureOutput` helper to resolve both folder and document IDs before deletion:
```java
// Step 1: resolve the parent folder object ID
String folderLine = runShellScriptAndCaptureOutput(
    "../helper-scripts/get-object-id.sh", entityID + "__attachments");
String parentFolderObjectId = folderLine.contains(": ")
    ? folderLine.substring(folderLine.lastIndexOf(": ") + 2).trim()
    : folderLine;

// Step 2: resolve the document object ID by filename inside the parent folder
String docLine = runShellScriptAndCaptureOutput(
    "../helper-scripts/get-object-id.sh",
    file.getName(),
    parentFolderObjectId,
    "cmis:document");
String documentObjectId = docLine.contains(": ")
    ? docLine.substring(docLine.lastIndexOf(": ") + 2).trim()
    : docLine;
```

---

### `delete.sh` — Delete a document from SDM

**Function**  
Sends a CMIS `delete` action to remove a document from the repository by its object ID. An OAuth2 access token is obtained automatically before the request.

**Required config (`cf-config.env`)**  
`CMIS_URL`, `CMIS_REPOSITORY_ID`, `CMIS_TOKEN_URL`, `CMIS_CLIENT_ID`, `CMIS_CLIENT_SECRET`, `CMIS_USERNAME`, `CMIS_PASSWORD`

**Parameters**

| # | Name | Default | Description |
|---|---|---|---|
| 1 | `objectID` | — | CMIS object ID of the document to delete |
| 2 | `parentFolderID` | _(optional)_ | CMIS object ID of the parent folder (used for logging; does not change the delete target) |

**Shell usage**
```bash
./delete.sh "<documentObjectId>"
./delete.sh "<documentObjectId>" "<parentFolderObjectId>"
```

**Usage in integration tests**  
Called inside `testUploadSingleAttachmentPDF` (Order 3) after the document object ID has been resolved with `get-object-id.sh`:
```java
int deleteExitCode = runShellScript(
    "../helper-scripts/delete.sh", documentObjectId, parentFolderObjectId);
if (deleteExitCode != 0) {
    fail("delete.sh failed with exit code: " + deleteExitCode);
}
```

---

### `cf-subscribe.sh` — Subscribe a BTP consumer subaccount to a SaaS app

**Function**  
Uses the BTP CLI to subscribe a consumer subaccount to a SaaS application and then assigns all app role collections to the configured email addresses.

**Required config (`cf-config.env`)**  
`CONSUMER_CF_USERNAME` (or `CF_USERNAME`), `CONSUMER_SUBACCOUNT_ID`, `SAAS_APP_NAME`

**Optional config**  
`SAAS_APP_PLAN`, `ROLE_ASSIGNMENT_EMAILS`, `ROLE_COLLECTION_NAME`, `APP_ROLE_FILTER`, `BTP_CLI_URL`, `BTP_GLOBAL_ACCOUNT_SUBDOMAIN`

**Shell usage**
```bash
cd helper-scripts
./cf-subscribe.sh
```

**No direct usage in integration tests.** Run manually before a test suite to set up a consumer subscription.

---

### `cf-unsubscribe.sh` — Unsubscribe a BTP consumer subaccount

**Function**  
Uses the BTP CLI to remove a SaaS subscription from a consumer subaccount.

**Required config (`cf-config.env`)**  
`CONSUMER_CF_USERNAME` (or `CF_USERNAME`), `CONSUMER_SUBACCOUNT_ID`, `SAAS_APP_NAME`

**Shell usage**
```bash
cd helper-scripts
./cf-unsubscribe.sh
```

**No direct usage in integration tests.** Run manually to tear down a consumer subscription after testing.

---

### `cf-update-env.sh` — Update a Cloud Foundry app environment variable

**Function**  
Logs in to Cloud Foundry and sets a user-provided environment variable on a CF application, then restages the app so the change takes effect.

**Required config (`cf-config.env`)**  
`CF_API_ENDPOINT`, `CF_ORG`, `CF_SPACE`, `CF_USERNAME`, `APP_NAME`, `VAR_NAME`, `VAR_VALUE`

**Optional config**  
`CF_PASSWORD` — if left empty you will be prompted at runtime.

**Shell usage**
```bash
cd helper-scripts
./cf-update-env.sh
```

**No direct usage in integration tests.** Run manually to update configuration values (e.g. `REPOSITORY_ID`) on a deployed application.

---

## Typical test workflow

```
1. Fill in cf-config.env (copy from cf-config.env.example)
2. (Multi-tenant only) Run cf-subscribe.sh to set up the consumer subscription
3. Run the integration tests — scripts are invoked automatically:
      Order 1  →  create.sh          (uploads a test document to SDM)
      Order 3  →  get-object-id.sh   (resolves folder + document object IDs)
               →  delete.sh          (cleans up the uploaded document)
4. (Multi-tenant only) Run cf-unsubscribe.sh to tear down after testing
```

---

## Helper methods in the test class

Two private methods in `IntegrationTest_SingleFacet` are used to invoke the scripts:

| Method | Returns | Use for |
|---|---|---|
| `runShellScript(scriptPath, args...)` | `int` exit code | Scripts where only success/failure matters (`create.sh`, `delete.sh`) |
| `runShellScriptAndCaptureOutput(scriptPath, args...)` | `String` last stdout line | Scripts that print a result value (`get-object-id.sh`) |

Both methods stream stdout and stderr to the console with `[script]` / `[script-err]` prefixes for easy debugging.
