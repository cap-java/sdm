#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# read.sh — Read (download) a document from SAP Document Management Service via CMIS API
#
# Usage: ./read.sh <objectID> [outputPath]
#
#   objectID      The CMIS object ID of the document to read/download
#   outputPath    (Optional) Local path to save the downloaded content.
#                 If omitted, content is written to stdout.
#
# Required config in credentials.properties:
#   CMIS_URL, defaultRepositoryID, authUrl,
#   cmisClientID, cmisClientSecret, username, password
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/../../../../../../../resources/credentials.properties"

# Load key=value pairs from .properties file without shell expansion of values
load_props() {
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
defaultRepositoryID="${SDM_REPOSITORY_ID:-$defaultRepositoryID}"
authUrl="${SDM_AUTH_URL:-$authUrl}"
CMIS_URL="${CMIS_URL%/}/"

# --- Validate positional parameters ---
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <objectID> [outputPath]"
  exit 1
fi

OBJECT_ID="$1"
OUTPUT_PATH="${2:-}"

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

# --- Build the CMIS browser endpoint URL for content stream ---
CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root?objectId=${OBJECT_ID}&cmisselector=content"


if [[ -n "${OUTPUT_PATH}" ]]; then
  # Download to file
  HTTP_CODE=$(curl -s -w "%{http_code}" \
    -X GET "$CMIS_ENDPOINT" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -o "$OUTPUT_PATH")

  if [[ "$HTTP_CODE" == "200" ]]; then
    echo "SUCCESS: Document '${OBJECT_ID}' saved to '${OUTPUT_PATH}'."
    exit 0
  else
    echo "ERROR: Failed to read document. HTTP status: $HTTP_CODE"
    # Print the output file content for debugging (it may contain the error body)
    if [[ -f "$OUTPUT_PATH" ]]; then
      cat "$OUTPUT_PATH"
    fi
    exit 1
  fi
else
  # Stream to stdout
  RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X GET "$CMIS_ENDPOINT" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [[ "$HTTP_CODE" == "200" ]]; then
    echo "$BODY"
    exit 0
  else
    echo "ERROR: Failed to read document. HTTP status: $HTTP_CODE"
    echo "$BODY"
    exit 1
  fi
fi
