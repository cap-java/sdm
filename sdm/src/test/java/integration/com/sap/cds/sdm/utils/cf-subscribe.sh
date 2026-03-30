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
  echo "ERROR: Config file not found at $CONFIG_FILE"
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
    echo "ERROR: $var is not set in $CONFIG_FILE"
    exit 1
  fi
done

echo "=== BTP Subaccount SaaS Subscription ==="
echo "BTP URL:      $BTP_URL"
echo "User:         $CONSUMER_USER"
echo "Subaccount:   $CONSUMER_SUBACCOUNT_ID"
echo "Application:  $SAAS_APP_NAME"
echo "Plan:         ${SAAS_APP_PLAN:-<none>}"
echo "=========================================="

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

# --- Check current subscription status ---
GET_ARGS=(--subaccount "$CONSUMER_SUBACCOUNT_ID" --of-app "$SAAS_APP_NAME")
if [[ -n "${SAAS_APP_PLAN:-}" ]]; then
  GET_ARGS+=(--plan "$SAAS_APP_PLAN")
fi

# Use list to find the exact app row and check its state
# Use -w (whole word) so "NOT_SUBSCRIBED" does NOT match "SUBSCRIBED"
CURRENT_STATE=$(btp list accounts/subscription --subaccount "$CONSUMER_SUBACCOUNT_ID" 2>/dev/null \
  | grep -F "$SAAS_APP_NAME" | grep -ow "SUBSCRIBED" | head -1 || true)

if [[ "$CURRENT_STATE" == "SUBSCRIBED" ]]; then
  echo ""
  echo "Already subscribed to '$SAAS_APP_NAME' — skipping subscription step."
else
  # --- Subscribe to SaaS application at subaccount level ---
  echo ""
  echo "Subscribing to '$SAAS_APP_NAME' in subaccount $CONSUMER_SUBACCOUNT_ID..."
  SUBSCRIBE_ARGS=(--subaccount "$CONSUMER_SUBACCOUNT_ID" --to-app "$SAAS_APP_NAME")
  if [[ -n "${SAAS_APP_PLAN:-}" ]]; then
    SUBSCRIBE_ARGS+=(--plan "$SAAS_APP_PLAN")
  fi
  btp subscribe accounts/subaccount "${SUBSCRIBE_ARGS[@]}"

  # --- Wait for subscription to complete ---
  echo ""
  echo "Waiting for subscription to be ready..."
  while true; do
    STATE=$(btp get accounts/subscription "${GET_ARGS[@]}" 2>/dev/null | grep -i "status:" | awk '{print $2}' || true)
    if echo "$STATE" | grep -qi "SUBSCRIBED"; then
      echo "Subscription to '$SAAS_APP_NAME' is active."
      break
    elif echo "$STATE" | grep -qi "SUBSCRIBE_FAILED"; then
      echo "ERROR: Subscription failed."
      btp get accounts/subscription "${GET_ARGS[@]}"
      exit 1
    else
      echo "  State: ${STATE:-pending} — waiting 10s..."
      sleep 10
    fi
  done
fi

echo ""
echo "Done."

# --- Create role collection from app roles and assign to configured email IDs ---

# Parse comma-separated arrays and strip surrounding whitespace from each element
IFS=',' read -ra _emails_raw  <<< "${ROLE_ASSIGNMENT_EMAILS:-}"
IFS=',' read -ra _colls_raw   <<< "${ROLE_COLLECTION_NAME:-}"

EMAILS_ARRAY=()
for _e in "${_emails_raw[@]}"; do
  _e="${_e#"${_e%%[![:space:]]*}"}"; _e="${_e%"${_e##*[![:space:]]}"}" 
  [[ -n "$_e" ]] && EMAILS_ARRAY+=("$_e")
done

COLLECTIONS_ARRAY=()
for _c in "${_colls_raw[@]}"; do
  _c="${_c#"${_c%%[![:space:]]*}"}"; _c="${_c%"${_c##*[![:space:]]}"}" 
  [[ -n "$_c" ]] && COLLECTIONS_ARRAY+=("$_c")
done

if [[ ${#COLLECTIONS_ARRAY[@]} -eq 0 ]]; then
  echo ""
  echo "No ROLE_COLLECTION_NAME configured — skipping role collection setup."
  exit 0
fi

if [[ ${#EMAILS_ARRAY[@]} -eq 0 ]]; then
  echo ""
  echo "No ROLE_ASSIGNMENT_EMAILS configured — skipping role assignment."
  exit 0
fi

ROLE_FILTER="${APP_ROLE_FILTER:-$SAAS_APP_NAME}"

echo ""
echo "=== Role Collection Setup ==="
echo "Fetching roles for app filter: '$ROLE_FILTER'..."

# After a fresh subscription, role templates can take time to be provisioned.
# Retry up to 6 times (5 minutes total) before giving up.
MATCHED_ROLES=""
ROLES_RAW=""
MAX_RETRIES=6
RETRY_INTERVAL=30
for ((attempt=1; attempt<=MAX_RETRIES; attempt++)); do
  ROLES_RAW=$(btp list security/role --subaccount "$CONSUMER_SUBACCOUNT_ID" 2>&1) || true

  if echo "$ROLES_RAW" | grep -qi "^error\|FAILED"; then
    echo "ERROR: Could not fetch roles from subaccount."
    echo "$ROLES_RAW"
    exit 1
  fi

  # BTP CLI columns: name | appId | roleTemplateName | description
  MATCHED_ROLES=$(echo "$ROLES_RAW" \
    | grep -i "$ROLE_FILTER" \
    | awk '{print $1 "|" $3 "|" $2}' \
    || true)

  if [[ -n "$MATCHED_ROLES" ]]; then
    break
  fi

  if [[ $attempt -lt $MAX_RETRIES ]]; then
    echo "  Roles for '$ROLE_FILTER' not yet provisioned (attempt $attempt/$MAX_RETRIES) — waiting ${RETRY_INTERVAL}s..."
    sleep "$RETRY_INTERVAL"
  fi
done

if [[ -z "$MATCHED_ROLES" ]]; then
  echo "WARNING: No roles found matching '$ROLE_FILTER' after $MAX_RETRIES attempts."
  echo "The role templates may not be provisioned yet for this subscription."
  echo "Hint: set APP_ROLE_FILTER in credentials.properties to a substring of your app's appId."
  echo "Available appIds in this subaccount (sample):"
  echo "$ROLES_RAW" | awk 'NR>1 && $2~/!/ {print "  " $2}' | sort -u | head -20
  exit 0
fi

echo "Found roles:"
echo "$MATCHED_ROLES" | while IFS='|' read -r RNAME RTEMPLATE RAPPID; do
  echo "  - $RNAME (template: $RTEMPLATE, appId: $RAPPID)"
done

# For each role collection: create it, add roles, then assign all emails
for COLLECTION_NAME in "${COLLECTIONS_ARRAY[@]}"; do
  echo ""
  echo "--- Processing role collection: '$COLLECTION_NAME' ---"

  # Create the role collection if it doesn't already exist
  # Use awk exact first-column match to avoid "ak-test" matching "ak-test2" as a substring
  COLLECTION_EXISTS=$(btp list security/role-collection --subaccount "$CONSUMER_SUBACCOUNT_ID" 2>/dev/null \
    | awk -v name="$COLLECTION_NAME" '$1 == name {found=1} END {print found+0}' || echo 0)
  if [[ "$COLLECTION_EXISTS" == "1" ]]; then
    echo "Role collection '$COLLECTION_NAME' already exists — skipping creation."
  else
    echo "Creating role collection '$COLLECTION_NAME'..."
    btp create security/role-collection "$COLLECTION_NAME" \
      --subaccount "$CONSUMER_SUBACCOUNT_ID" \
      --description "Auto-created role collection for $SAAS_APP_NAME" \
      && echo "Role collection created." \
      || echo "WARNING: Could not create role collection — it may already exist, continuing."
  fi

  # Add each role to the collection (safe to re-run; duplicate adds are ignored)
  echo "Adding roles to collection '$COLLECTION_NAME'..."
  while IFS='|' read -r RNAME RTEMPLATE RAPPID; do
    [[ -z "$RNAME" ]] && continue
    echo "  Adding role '$RNAME'..."
    btp add security/role "$RNAME" \
      --to-role-collection "$COLLECTION_NAME" \
      --subaccount "$CONSUMER_SUBACCOUNT_ID" \
      --of-app "$RAPPID" \
      --of-role-template "$RTEMPLATE" \
      && echo "  OK: $RNAME" \
      || echo "  WARNING: Could not add role '$RNAME' (may already be in collection) — continuing."
  done <<< "$MATCHED_ROLES"

  # Assign the role collection to each email
  echo "Assigning '$COLLECTION_NAME' to users..."
  for EMAIL in "${EMAILS_ARRAY[@]}"; do
    echo "  Assigning to $EMAIL..."
    btp assign security/role-collection "$COLLECTION_NAME" \
      --subaccount "$CONSUMER_SUBACCOUNT_ID" \
      --to-user "$EMAIL" \
      --create-user-if-missing \
      && echo "  OK: $EMAIL" \
      || echo "  WARNING: Failed to assign to $EMAIL — continuing."
  done
done

echo ""
echo "Role assignment complete."
