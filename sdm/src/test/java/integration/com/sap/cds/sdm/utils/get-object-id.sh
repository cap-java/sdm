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

# --- Validate positional parameters ---
if [[ $# -lt 1 || $# -gt 3 ]]; then
  echo "Usage: $0 <cmisName> [folderID] [cmisType]"
  exit 1
fi

CMIS_NAME="$1"
PARENT_FOLDER_ID="${2:-}"
CMIS_TYPE="${3:-cmis:folder}"

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

# --- Execute CMIS query to find the folder by name ---
# The CMIS Browser Binding query endpoint is the repository URL (no /root).
QUERY_URL="${CMIS_URL}browser/${defaultRepositoryID}"

if [[ -n "${PARENT_FOLDER_ID}" ]]; then
  CMIS_QUERY="SELECT cmis:objectId FROM ${CMIS_TYPE} WHERE cmis:name = '${CMIS_NAME}' AND IN_FOLDER('${PARENT_FOLDER_ID}')"
  echo "Searching for ${CMIS_TYPE} '${CMIS_NAME}' inside folder '${PARENT_FOLDER_ID}'..."
else
  CMIS_QUERY="SELECT cmis:objectId FROM ${CMIS_TYPE} WHERE cmis:name = '${CMIS_NAME}'"
  echo "Searching for ${CMIS_TYPE} '${CMIS_NAME}' in repository..."
fi
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "${QUERY_URL}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -G \
  --data-urlencode "cmisselector=query" \
  --data-urlencode "q=${CMIS_QUERY}")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: CMIS query failed (HTTP ${HTTP_CODE})."
  echo "$BODY"
  exit 1
fi

# --- Parse the objectId from the JSON response ---
# The response is a CMIS query result; each entry has cmis:objectId.value
OBJECT_ID=$(echo "$BODY" \
  | grep -o '"cmis:objectId"[^}]*"value":"[^"]*"' \
  | head -1 \
  | grep -o '"value":"[^"]*"' \
  | sed 's/"value":"//;s/"$//')

if [[ -z "$OBJECT_ID" ]]; then
  echo "ERROR: No ${CMIS_TYPE} found with name '${CMIS_NAME}'."
  echo "Query response: $BODY"
  exit 1
fi

echo "Found object ID for '${CMIS_NAME}': ${OBJECT_ID}"
