#!/bin/sh
set -eu

image_ref="${1:-}"
: "${SMOKE_BASE_URL:?SMOKE_BASE_URL is required}"
: "${DEPLOY_HOST:?DEPLOY_HOST is required}"
: "${DEPLOY_USER:?DEPLOY_USER is required}"
: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${DEPLOY_SSH_KEY_FILE:?DEPLOY_SSH_KEY_FILE is required}"

printf '%s' "$DEPLOY_HOST" | grep -Eq '^[A-Za-z0-9.-]+$'
printf '%s' "$DEPLOY_USER" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]*$'
printf '%s' "$DEPLOY_PATH" | grep -Eq '^/[A-Za-z0-9._/-]+$'
printf '%s' "$image_ref" | grep -Eq '^[A-Za-z0-9._/:@-]+@sha256:[a-f0-9]{64}$'
printf '%s' "$SMOKE_BASE_URL" | grep -Eq '^https://[A-Za-z0-9._~:/?&=%+-]+$'
if [ ! -f "$DEPLOY_SSH_KEY_FILE" ] || [ ! -r "$DEPLOY_SSH_KEY_FILE" ]; then
  echo "deploy SSH key file is missing or unreadable" >&2
  exit 1
fi
key_mode=$(stat -c '%a' "$DEPLOY_SSH_KEY_FILE" 2>/dev/null || true)
if [ "$key_mode" != 600 ] && [ "$key_mode" != 400 ]; then
  echo "deploy SSH key file mode must be 0600 or 0400" >&2
  exit 1
fi

ssh -i "$DEPLOY_SSH_KEY_FILE" \
  -o BatchMode=yes \
  -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=yes \
  "$DEPLOY_USER@$DEPLOY_HOST" \
  "cd '$DEPLOY_PATH' && bash scripts/recover-seller-migration.sh '$image_ref' '$SMOKE_BASE_URL'"
