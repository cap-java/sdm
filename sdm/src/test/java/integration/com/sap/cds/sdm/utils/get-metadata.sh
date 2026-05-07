#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# get-metadata.sh — Retrieve CMIS metadata (properties) for a document
#
# Usage: ./get-metadata.sh <objectID>
#
#   objectID      The CMIS object ID of the document to retrieve metadata for
#
# On success, prints the JSON properties of the object to stdout and exits
# with code 0. On failure, exits with a non-zero code.
#
# Required config in credentials.properties:
#   CMIS_URL, CMIS_REPOSITORY_ID, CMIS_TOKEN_URL,
#   CMIS_CLIENT_ID, CMIS_CLIENT_SECRET, CMIS_USERNAME, CMIS_PASSWORD
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
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <objectID>"
  exit 1
fi

OBJECT_ID="$1"

# --- Validate required config variables ---
for var in CMIS_URL CMIS_REPOSITORY_ID CMIS_TOKEN_URL CMIS_CLIENT_ID CMIS_CLIENT_SECRET CMIS_USERNAME CMIS_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

# --- Obtain OAuth2 access token (password grant) ---
echo "Fetching OAuth2 token..." >&2
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
  echo "ERROR: Failed to obtain access token." >&2
  echo "Token endpoint response: $TOKEN_RESPONSE" >&2
  exit 1
fi

# --- Fetch object properties via CMIS browser binding ---
CMIS_ENDPOINT="${CMIS_URL}browser/${CMIS_REPOSITORY_ID}/root?objectId=${OBJECT_ID}&cmisselector=object"

echo "Fetching metadata for object '${OBJECT_ID}'..." >&2

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "$CMIS_ENDPOINT" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "200" ]]; then
  echo "SUCCESS: Metadata retrieved for object '${OBJECT_ID}'." >&2
  echo "$BODY"
  exit 0
else
  echo "ERROR: Failed to retrieve metadata. HTTP status: $HTTP_CODE" >&2
  echo "$BODY" >&2
  exit 1
fi
