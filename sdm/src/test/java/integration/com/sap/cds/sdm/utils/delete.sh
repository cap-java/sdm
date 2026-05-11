#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# delete.sh — Delete a document from SAP Document Management Service via CMIS API
#
# Usage: ./delete.sh <objectID> [parentFolderID]
#
#   objectID      The CMIS object ID of the document to delete
#   parentFolderID  (Optional) The CMIS object ID of the parent folder.
#                   If provided, the endpoint is scoped to that folder.
#                   If omitted, defaults to the repository root.
#
# Required config in cf-config.env:
#   CMIS_URL, defaultRepositoryID, authUrl, cmisClientID, cmisClientSecret,
#   username, password
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
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <objectID> [parentFolderID]"
  exit 1
fi

OBJECT_ID="$1"
PARENT_FOLDER_ID="${2:-}"

# --- Validate required config variables ---
for var in CMIS_URL defaultRepositoryID authUrl cmisClientID cmisClientSecret username password; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

# --- Obtain OAuth2 access token (password grant) ---
echo "Fetching OAuth2 token..."
TOKEN_RESPONSE=$(curl -s -X POST "${authUrl}/oauth/token" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${cmisClientID}" \
  --data-urlencode "client_secret=${cmisClientSecret}" \
  --data-urlencode "username=${username}" \
  --data-urlencode "password=${password}")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" \
  | grep -o '"access_token":"[^"]*"' \
  | sed 's/"access_token":"//;s/"$//')

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: Failed to obtain access token."
  echo "Token endpoint response: $TOKEN_RESPONSE"
  exit 1
fi

# --- Build the CMIS browser endpoint URL ---
# For delete, the target object is always identified by the objectId form field.
# The parentFolderID is logged for context only; it does NOT go in the URL,
# as having ?objectId= in the URL conflicts with the objectId form field.
CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root"

if [[ -n "${PARENT_FOLDER_ID}" ]]; then
  echo "Deleting object '${OBJECT_ID}' (parent folder: '${PARENT_FOLDER_ID}')..."
else
  echo "Deleting object '${OBJECT_ID}'..."
fi

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$CMIS_ENDPOINT" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "cmisaction=delete" \
  -F "objectId=${OBJECT_ID}" \
  -F "allVersions=true")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "204" ]]; then
  echo "SUCCESS: Object '${OBJECT_ID}' deleted."
else
  echo "ERROR: Failed to delete object (HTTP ${HTTP_CODE})."
  echo "$BODY"
  exit 1
fi
