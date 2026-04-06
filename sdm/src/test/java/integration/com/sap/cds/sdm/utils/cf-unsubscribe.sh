#!/bin/bash
set -euo pipefail

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

# --- Load config ---
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found"
  exit 1
fi

load_props "$CONFIG_FILE"

# --- Resolve consumer credentials (fall back to provider credentials) ---
CONSUMER_USER="${CONSUMER_CF_USERNAME:-$CF_USERNAME}"
CONSUMER_PASS="${CONSUMER_CF_PASSWORD:-$CF_PASSWORD}"
BTP_URL="${BTP_CLI_URL:-https://cli.btp.cloud.sap}"

# --- Validate required variables ---
for var in CONSUMER_USER CONSUMER_SUBACCOUNT_ID SAAS_APP_NAME; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: Required variable $var is not set in config"
    exit 1
  fi
done

echo "=== BTP Subaccount SaaS Unsubscription ==="
echo "============================================"

# --- BTP Login ---
echo ""
echo "Logging in to SAP BTP..."
LOGIN_ARGS=(--url "$BTP_URL" --user "$CONSUMER_USER")
if [[ -n "${CONSUMER_PASS:-}" ]]; then
  LOGIN_ARGS+=(--password "$CONSUMER_PASS")
fi
if [[ -n "${BTP_GLOBAL_ACCOUNT_SUBDOMAIN:-}" ]]; then
  LOGIN_ARGS+=(--subdomain "$BTP_GLOBAL_ACCOUNT_SUBDOMAIN")
fi
btp login "${LOGIN_ARGS[@]}" > /dev/null 2>&1

# --- Unsubscribe from SaaS application at subaccount level ---
echo ""
echo "Unsubscribing from SaaS application..."
UNSUBSCRIBE_ARGS=(--subaccount "$CONSUMER_SUBACCOUNT_ID" --from-app "$SAAS_APP_NAME")
if [[ -n "${SAAS_APP_PLAN:-}" ]]; then
  UNSUBSCRIBE_ARGS+=(--plan "$SAAS_APP_PLAN")
fi
btp unsubscribe accounts/subaccount "${UNSUBSCRIBE_ARGS[@]}" --confirm > /dev/null 2>&1

# --- Wait for unsubscription to complete ---
echo ""
echo "Waiting for unsubscription to complete..."
while true; do
  GET_ARGS=(--subaccount "$CONSUMER_SUBACCOUNT_ID" --of-app "$SAAS_APP_NAME")
  if [[ -n "${SAAS_APP_PLAN:-}" ]]; then
    GET_ARGS+=(--plan "$SAAS_APP_PLAN")
  fi
  STATE=$(btp get accounts/subscription "${GET_ARGS[@]}" 2>/dev/null | grep -i "status:" | awk '{print $2}' || true)
  if [[ -z "$STATE" ]] || echo "$STATE" | grep -qi "NOT_SUBSCRIBED"; then
    echo "Successfully unsubscribed."
    break
  elif echo "$STATE" | grep -qi "UNSUBSCRIBE_FAILED"; then
    echo "ERROR: Unsubscription failed."
    exit 1
  else
    echo "  State: ${STATE:-pending} — waiting 10s..."
    sleep 10
  fi
done

echo ""
echo "Done."
