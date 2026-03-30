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

# --- Resolve consumer credentials (fall back to provider credentials) ---
CONSUMER_USER="${CONSUMER_CF_USERNAME:-$CF_USERNAME}"
CONSUMER_PASS="${CONSUMER_CF_PASSWORD:-$CF_PASSWORD}"
BTP_URL="${BTP_CLI_URL:-https://cli.btp.cloud.sap}"

# --- Validate required variables ---
for var in CONSUMER_USER CONSUMER_SUBACCOUNT_ID SAAS_APP_NAME; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

echo "=== BTP Subaccount SaaS Unsubscription ==="
echo "BTP URL:      $BTP_URL"
echo "User:         $CONSUMER_USER"
echo "Subaccount:   $CONSUMER_SUBACCOUNT_ID"
echo "Application:  $SAAS_APP_NAME"
echo "Plan:         ${SAAS_APP_PLAN:-<none>}"
echo "==========================================="

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
btp login "${LOGIN_ARGS[@]}"

# --- Unsubscribe from SaaS application at subaccount level ---
echo ""
echo "Unsubscribing from '$SAAS_APP_NAME' in subaccount $CONSUMER_SUBACCOUNT_ID..."
UNSUBSCRIBE_ARGS=(--subaccount "$CONSUMER_SUBACCOUNT_ID" --from-app "$SAAS_APP_NAME")
if [[ -n "${SAAS_APP_PLAN:-}" ]]; then
  UNSUBSCRIBE_ARGS+=(--plan "$SAAS_APP_PLAN")
fi
btp unsubscribe accounts/subaccount "${UNSUBSCRIBE_ARGS[@]}" --confirm

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
    echo "Successfully unsubscribed from '$SAAS_APP_NAME'."
    break
  elif echo "$STATE" | grep -qi "UNSUBSCRIBE_FAILED"; then
    echo "ERROR: Unsubscription failed."
    btp get accounts/subscription "${GET_ARGS[@]}"
    exit 1
  else
    echo "  State: ${STATE:-pending} — waiting 10s..."
    sleep 10
  fi
done

echo ""
echo "Done."
