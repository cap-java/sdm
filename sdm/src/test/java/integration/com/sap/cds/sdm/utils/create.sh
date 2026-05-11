#!/bin/bash
set -euo pipefail


# ---------------------------------------------------------------------------
# create.sh — Upload a file to SAP Document Management Service via CMIS API
## Usage: ./create.sh <cmisName> <file> [parentFolderID]
#
#   cmisName      The name the document will have inside the CMIS repository
#   file          Path to the local file to upload
#   parentFolderID  (Optional) CMIS object ID of the parent folder to upload into.
#                   If not provided, the file is uploaded to the repository root.
#
# Required config in credentials.properties:
#   CMIS_URL, defaultRepositoryID, authUrl, cmisClientID, cmisClientSecret
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/../../../../../../../resources/credentials.properties"

# Load key=value pairs from .properties file without shell expansion of values
load_props() {
  local key val
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    val="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ -z "$key" ]] && continue
    printf -v "$key" '%s' "$val"
  done < "$1"
}

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found at $CONFIG_FILE"
  exit 1
fi
load_props "$CONFIG_FILE"
CMIS_URL="${CMIS_URL%/}/"

# --- Validate positional parameters ---
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 <cmisName> <file> [parentFolderID]"
  exit 1
fi

CMIS_NAME="$1"
FILE_PATH="$2"
ARG_FOLDER_ID="${3:-}"
EFFECTIVE_FOLDER_ID="${ARG_FOLDER_ID:-}"

if [[ ! -f "$FILE_PATH" ]]; then
  echo "ERROR: File not found: $FILE_PATH"
  exit 1
fi

# --- Validate required config variables ---
for var in CMIS_URL defaultRepositoryID authUrl cmisClientID cmisClientSecret username password; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

# --- Obtain OAuth2 access token (password grant) ---
TOKEN_RESPONSE=$(curl -s -X POST "${authUrl}/oauth/token" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${cmisClientID}" \
  --data-urlencode "client_secret=${cmisClientSecret}" \
  --data-urlencode "username=${username}" \
  --data-urlencode "password=${password}")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" \
  | grep -o '"access_token":"[^"]*"' \
  | sed 's/"access_token":"//;s/"$//' || true)

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: Failed to obtain access token."
  echo "Token endpoint response: $TOKEN_RESPONSE"
  exit 1
fi

# --- Detect MIME type of the local file ---
MIME_TYPE=$(file --mime-type -b "$FILE_PATH")

# --- Build the CMIS browser endpoint URL ---
if [[ -n "${EFFECTIVE_FOLDER_ID}" ]]; then
  CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root?objectId=${EFFECTIVE_FOLDER_ID}"
else
  CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root"
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
