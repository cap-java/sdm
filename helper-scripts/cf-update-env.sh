#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/cf-config.env"

# --- Load config ---
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found at $CONFIG_FILE"
  exit 1
fi

source "$CONFIG_FILE"

# --- Validate required variables ---
for var in CF_API_ENDPOINT CF_ORG CF_SPACE CF_USERNAME APP_NAME VAR_NAME VAR_VALUE; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

echo "=== Cloud Foundry Environment Variable Updater ==="
echo "API:   $CF_API_ENDPOINT"
echo "Org:   $CF_ORG"
echo "Space: $CF_SPACE"
echo "App:   $APP_NAME"
echo "Var:   $VAR_NAME = $VAR_VALUE"
echo "=================================================="

# --- CF Login ---
echo ""
echo "Logging in to Cloud Foundry..."
if [[ -n "${CF_PASSWORD:-}" ]]; then
  cf login -a "$CF_API_ENDPOINT" -u "$CF_USERNAME" -p "$CF_PASSWORD" -o "$CF_ORG" -s "$CF_SPACE"
else
  cf login -a "$CF_API_ENDPOINT" -u "$CF_USERNAME" -o "$CF_ORG" -s "$CF_SPACE"
fi

# --- Update environment variable ---
echo ""
echo "Setting $VAR_NAME=$VAR_VALUE on $APP_NAME..."
cf set-env "$APP_NAME" "$VAR_NAME" "$VAR_VALUE"

# --- Restage the app to pick up the change ---
echo ""
echo "Restaging $APP_NAME..."
cf restage "$APP_NAME"
echo "Restage complete."

echo ""
echo "Done."
