#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# cf-logs.sh — Fetch recent CF app logs and print them to stdout.
#
# Usage: ./cf-logs.sh [--app <appName>]
#
# If --app is not provided, uses APP_NAME from credentials.properties.
# Prints the recent logs to stdout for parsing by the calling test.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/../../../../../../../resources/credentials.properties"

# Parse optional --app argument
CLI_APP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --app) CLI_APP="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# Load key=value pairs from .properties file
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
  echo "ERROR: Config file not found"
  exit 1
fi
load_props "$CONFIG_FILE"

# Apply CLI override
[[ -n "$CLI_APP" ]] && APP_NAME="$CLI_APP"

# Validate
for var in CF_API_ENDPOINT CF_ORG CF_SPACE CF_USERNAME APP_NAME; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set"
    exit 1
  fi
done

# CF Login
if [[ -n "${CF_PASSWORD:-}" ]]; then
  cf login -a "$CF_API_ENDPOINT" -u "$CF_USERNAME" -p "$CF_PASSWORD" -o "$CF_ORG" -s "$CF_SPACE" > /dev/null 2>&1
else
  cf login -a "$CF_API_ENDPOINT" -u "$CF_USERNAME" -o "$CF_ORG" -s "$CF_SPACE" > /dev/null 2>&1
fi

# Fetch recent logs
cf logs "$APP_NAME" --recent
