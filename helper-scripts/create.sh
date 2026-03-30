#!/bin/bash
set -euo pipefail

echo "test"

# ---------------------------------------------------------------------------
# create.sh — Upload a file to SAP Document Management Service via CMIS API
## Usage: ./create.sh <cmisName> <file> [parentFolderID]
#
#   cmisName      The name the document will have inside the CMIS repository
#   file          Path to the local file to upload
#   parentFolderID  (Optional) CMIS object ID of the parent folder to upload into.
#                   Takes precedence over CMIS_FOLDER_ID from cf-config.env.
#                   If neither is provided, the file is uploaded to the repository root.
#
# Required config in cf-config.env:
#   CMIS_URL, CMIS_REPOSITORY_ID, CMIS_TOKEN_URL, CMIS_CLIENT_ID, CMIS_CLIENT_SECRET
# Optional config:
#   CMIS_FOLDER_ID  — fallback target folder object ID; overridden by the parentFolderID argument
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/cf-config.env"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found at $CONFIG_FILE"
  exit 1
fi
source "$CONFIG_FILE"

# --- Validate positional parameters ---
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 <cmisName> <file> [parentFolderID]"
  exit 1
fi

CMIS_NAME="$1"
FILE_PATH="$2"
ARG_FOLDER_ID="${3:-}"

if [[ ! -f "$FILE_PATH" ]]; then
  echo "ERROR: File not found: $FILE_PATH"
  exit 1
fi

# --- Validate required config variables ---
for var in CMIS_URL CMIS_REPOSITORY_ID CMIS_TOKEN_URL CMIS_CLIENT_ID CMIS_CLIENT_SECRET CMIS_USERNAME CMIS_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

# --- Obtain OAuth2 access token (password grant) ---
echo "Fetching OAuth2 token..."
TOKEN_RESPONSE=$(curl -s -X POST "${CMIS_TOKEN_URL}/oauth/token" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${CMIS_CLIENT_ID}" \
  --data-urlencode "client_secret=${CMIS_CLIENT_SECRET}" \
  --data-urlencode "username=${CMIS_USERNAME}" \
  --data-urlencode "password=${CMIS_PASSWORD}")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" \
  | grep -o '"access_token":"[^"]*"' \
  | sed 's/"access_token":"//;s/"$//')

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: Failed to obtain access token."
  echo "Token endpoint response: $TOKEN_RESPONSE"
  exit 1
fi

# --- Detect MIME type of the local file ---
MIME_TYPE=$(file --mime-type -b "$FILE_PATH")

# --- Resolve the target folder: argument takes precedence over config ---
# In CMIS Browser Binding, the folder is addressed via objectId as a URL query param
EFFECTIVE_FOLDER_ID="${ARG_FOLDER_ID:-${CMIS_FOLDER_ID:-}}"

# --- Build the CMIS browser endpoint URL ---
if [[ -n "${EFFECTIVE_FOLDER_ID}" ]]; then
  CMIS_ENDPOINT="${CMIS_URL}browser/${CMIS_REPOSITORY_ID}/root?objectId=${EFFECTIVE_FOLDER_ID}"
else
  CMIS_ENDPOINT="${CMIS_URL}browser/${CMIS_REPOSITORY_ID}/root"
fi

# --- Assemble curl arguments ---
CURL_ARGS=(
  -s -w "\n%{http_code}"
  -X POST "$CMIS_ENDPOINT"
  -H "Authorization: Bearer $ACCESS_TOKEN"
  -F "cmisaction=createDocument"
  -F "propertyId[0]=cmis:name"
  -F "propertyValue[0]=${CMIS_NAME}"
  -F "propertyId[1]=cmis:objectTypeId"
  -F "propertyValue[1]=cmis:document"
  -F "succinct=true"
  -F "filename=@${FILE_PATH};type=${MIME_TYPE}"
)

echo "Creating document '${CMIS_NAME}' in repository '${CMIS_REPOSITORY_ID}'..."
RESPONSE=$(curl "${CURL_ARGS[@]}")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "201" || "$HTTP_CODE" == "200" ]]; then
  OBJECT_ID=$(echo "$BODY" \
    | grep -o '"cmis:objectId":"[^"]*"' \
    | head -1 \
    | sed 's/"cmis:objectId":"//;s/"$//')
  echo "SUCCESS: Document '${CMIS_NAME}' created."
  echo "Object ID: ${OBJECT_ID}"
else
  echo "ERROR: Failed to create document (HTTP ${HTTP_CODE})."
  echo "$BODY"
  exit 1
fi
