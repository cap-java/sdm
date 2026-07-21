#!/bin/bash
set -euo pipefail

# Defaults
APP_NAME="demoappjava-srv"
VAR_NAME="REPOSITORY_ID"
CLI_VALUE=""

# Parse CLI arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --app)   APP_NAME="$2"; shift 2 ;;
    --key)   VAR_NAME="$2"; shift 2 ;;
    --value) CLI_VALUE="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

VAR_VALUE="$CLI_VALUE"

# --- Validate required variables ---
for var in APP_NAME VAR_NAME VAR_VALUE; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set (checked CLI args)"
    exit 1
  fi
done

echo "=== Cloud Foundry Environment Variable Updater ==="
echo "=================================================="

# Assumes CF CLI is already logged in before running tests.
# To login manually: cf login -a <CF_API_ENDPOINT> -u <username> -p <password> -o <CF_ORG> -s <CF_SPACE>

# --- Update environment variable ---
echo ""
echo "Setting $VAR_NAME=$VAR_VALUE on app $APP_NAME..."
cf set-env "$APP_NAME" "$VAR_NAME" "$VAR_VALUE" > /dev/null 2>&1

# --- Restage the app to pick up the change ---
echo ""
echo "Restaging app..."
cf restage "$APP_NAME" > /dev/null 2>&1
echo "Restage complete."

echo ""
echo "Done."
