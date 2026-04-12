#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

WORKSPACE_NAME="${RENDER_WORKSPACE_NAME:-My Workspace}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-ml}"
BACKEND_SERVICE_ID="${RENDER_BACKEND_SERVICE_ID:-srv-d7dciuhf9bms7383qdbg}"
ML_SERVICE_ID="${RENDER_ML_SERVICE_ID:-srv-d7ddh47avr4c73e12d30}"
CLEAR_CACHE="${CLEAR_CACHE:-false}"

if ! command -v git >/dev/null 2>&1; then
  echo "git is required but not found." >&2
  exit 1
fi

if ! command -v render >/dev/null 2>&1; then
  echo "Render CLI is required but not found." >&2
  exit 1
fi

cd "$REPO_ROOT"

echo "[1/5] Ensuring repository is on branch: $DEPLOY_BRANCH"
git fetch origin "$DEPLOY_BRANCH"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "$DEPLOY_BRANCH" ]]; then
  git checkout "$DEPLOY_BRANCH"
fi
git pull --ff-only origin "$DEPLOY_BRANCH"

echo "[2/5] Switching Render workspace: $WORKSPACE_NAME"
render workspace set "$WORKSPACE_NAME" --confirm >/dev/null

DEPLOY_FLAGS=(--wait --confirm)
if [[ "$CLEAR_CACHE" == "true" ]]; then
  DEPLOY_FLAGS+=(--clear-cache)
fi

echo "[3/5] Triggering backend deploy ($BACKEND_SERVICE_ID)"
render deploys create "$BACKEND_SERVICE_ID" "${DEPLOY_FLAGS[@]}"

echo "[4/5] Triggering ML deploy ($ML_SERVICE_ID)"
render deploys create "$ML_SERVICE_ID" "${DEPLOY_FLAGS[@]}"

echo "[5/5] Done. Backend and ML services redeployed from latest $DEPLOY_BRANCH."
