#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# get-object-id.sh — Find the CMIS object ID for an object by name in the SDM repository.
#
# Usage: ./get-object-id.sh <cmisName> [folderID] [cmisType]
#
#   cmisName    The cmis:name of the object to look up
#   folderID    (Optional) CMIS object ID of the parent folder to search in.
#               If omitted, searches the entire repository.
#   cmisType    (Optional) CMIS type to query. Defaults to 'cmis:folder'.
#               Use 'cmis:document' to find uploaded files.
#
# On success, the resolved CMIS object ID is printed to stdout and the script
# exits with code 0.  On failure the script exits with a non-zero code.
#
# Required config in cf-config.env:
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

echo "[DEBUG] SCRIPT_DIR=$SCRIPT_DIR"
echo "[DEBUG] CONFIG_FILE=$CONFIG_FILE"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found at $CONFIG_FILE"
  exit 1
fi
echo "[DEBUG] Config file found, loading properties..."
load_props "$CONFIG_FILE" || {
  echo "ERROR: load_props failed with exit code $?"
  exit 1
}
defaultRepositoryID="${SDM_REPOSITORY_ID:-$defaultRepositoryID}"
authUrl="${SDM_AUTH_URL:-$authUrl}"
CMIS_URL="${CMIS_URL%/}/"
echo "[DEBUG] Properties loaded. defaultRepositoryID=$defaultRepositoryID"
echo "[DEBUG] CMIS_URL=$CMIS_URL"

# --- Validate positional parameters ---
if [[ $# -lt 1 || $# -gt 3 ]]; then
  echo "Usage: $0 <cmisName> [folderID] [cmisType]"
  exit 1
fi

CMIS_NAME="$1"
PARENT_FOLDER_ID="${2:-}"
CMIS_TYPE="${3:-cmis:folder}"
echo "[DEBUG] CMIS_NAME=$CMIS_NAME, PARENT_FOLDER_ID=$PARENT_FOLDER_ID, CMIS_TYPE=$CMIS_TYPE"

# --- Validate required config variables ---
for var in CMIS_URL defaultRepositoryID authUrl cmisClientID cmisClientSecret username password; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done
echo "[DEBUG] All required config variables validated"

# --- Obtain OAuth2 access token (password grant) ---
echo "[DEBUG] Requesting token from: ${authUrl}/oauth/token"
TOKEN_RESPONSE=$(curl -s -X POST "${authUrl}/oauth/token" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${cmisClientID}" \
  --data-urlencode "client_secret=${cmisClientSecret}" \
  --data-urlencode "username=${username}" \
  --data-urlencode "password=${password}" 2>&1) || {
  echo "ERROR: curl for token failed with exit code $?"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
}

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" \
  | grep -o '"access_token":"[^"]*"' \
  | sed 's/"access_token":"//;s/"$//' || true)

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: Failed to obtain access token."
  echo "Token endpoint response: $TOKEN_RESPONSE"
  exit 1
fi
echo "[DEBUG] Access token obtained (length=${#ACCESS_TOKEN})"

# --- Execute CMIS query to find the folder by name ---
QUERY_URL="${CMIS_URL}browser/${defaultRepositoryID}"
echo "[DEBUG] QUERY_URL=$QUERY_URL"

if [[ -n "${PARENT_FOLDER_ID}" ]]; then
  CMIS_QUERY="SELECT cmis:objectId FROM ${CMIS_TYPE} WHERE cmis:name = '${CMIS_NAME}' AND IN_FOLDER('${PARENT_FOLDER_ID}')"
else
  CMIS_QUERY="SELECT cmis:objectId FROM ${CMIS_TYPE} WHERE cmis:name = '${CMIS_NAME}'"
fi
echo "[DEBUG] CMIS_QUERY=$CMIS_QUERY"
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "${QUERY_URL}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -G \
  --data-urlencode "cmisselector=query" \
  --data-urlencode "q=${CMIS_QUERY}" 2>&1) || {
  echo "ERROR: curl for CMIS query failed with exit code $?"
  echo "Response: $RESPONSE"
  exit 1
}

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')
echo "[DEBUG] CMIS query HTTP_CODE=$HTTP_CODE"

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: CMIS query failed (HTTP ${HTTP_CODE})."
  echo "$BODY"
  exit 1
fi

# --- Parse the objectId from the JSON response ---
OBJECT_ID=$(echo "$BODY" \
  | grep -o '"cmis:objectId"[^}]*"value":"[^"]*"' \
  | head -1 \
  | grep -o '"value":"[^"]*"' \
  | sed 's/"value":"//;s/"$//' || true)

if [[ -z "$OBJECT_ID" ]]; then
  echo "ERROR: No ${CMIS_TYPE} found with name '${CMIS_NAME}'."
  echo "Query response: $BODY"
  exit 1
fi

echo "Found object ID for '${CMIS_NAME}': ${OBJECT_ID}"
