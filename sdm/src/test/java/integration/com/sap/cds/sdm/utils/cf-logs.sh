#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# cf-logs.sh — Fetch recent CF app logs and print them to stdout.
#
# Usage: ./cf-logs.sh [--app <appName>]
#
# If --app is not provided, defaults to demoappjava-srv.
# Prints the recent logs to stdout for parsing by the calling test.
# ---------------------------------------------------------------------------

# Default
APP_NAME="demoappjava-srv"

# Parse optional --app argument
while [[ $# -gt 0 ]]; do
  case "$1" in
    --app) APP_NAME="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# Validate
if [[ -z "${APP_NAME:-}" ]]; then
  echo "ERROR: APP_NAME is not set"
  exit 1
fi

# Assumes CF CLI is already logged in before running tests.
# To login manually: cf login -a <CF_API_ENDPOINT> -u <username> -p <password> -o <CF_ORG> -s <CF_SPACE>

# Fetch recent logs
cf logs "$APP_NAME" --recent
