#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# create-folder.sh — Create a folder in SAP Document Management Service via CMIS API
#
# Usage: ./create-folder.sh <folderName> [parentFolderID]
#
#   folderName      The name the folder will have inside the CMIS repository
#   parentFolderID  (Optional) CMIS object ID of the parent folder.
#                   If not provided, the folder is created at the repository root.
#
# Required config in credentials.properties:
#   CMIS_URL, defaultRepositoryID, authUrl, cmisClientID, cmisClientSecret
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/../../../../../../../resources/credentials.properties"

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
defaultRepositoryID="${SDM_REPOSITORY_ID:-$defaultRepositoryID}"
authUrl="${SDM_AUTH_URL:-$authUrl}"
CMIS_URL="${CMIS_URL%/}/"

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <folderName> [parentFolderID]"
  exit 1
fi

FOLDER_NAME="$1"
PARENT_FOLDER_ID="${2:-}"

for var in CMIS_URL defaultRepositoryID authUrl cmisClientID cmisClientSecret username password; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

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

if [[ -n "${PARENT_FOLDER_ID}" ]]; then
  CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root?objectId=${PARENT_FOLDER_ID}"
else
  CMIS_ENDPOINT="${CMIS_URL}browser/${defaultRepositoryID}/root"
fi

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$CMIS_ENDPOINT" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "cmisaction=createFolder" \
  -F "propertyId[0]=cmis:name" \
  -F "propertyValue[0]=${FOLDER_NAME}" \
  -F "propertyId[1]=cmis:objectTypeId" \
  -F "propertyValue[1]=cmis:folder" \
  -F "succinct=true")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "201" || "$HTTP_CODE" == "200" ]]; then
  OBJECT_ID=$(echo "$BODY" \
    | grep -o '"cmis:objectId":"[^"]*"' \
    | head -1 \
    | sed 's/"cmis:objectId":"//;s/"$//')
  echo "SUCCESS: Folder '${FOLDER_NAME}' created."
  echo "Object ID: ${OBJECT_ID}"
else
  echo "ERROR: Failed to create folder (HTTP ${HTTP_CODE})."
  echo "$BODY"
  exit 1
fi
