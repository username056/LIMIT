#!/bin/sh
set -eu

: "${DEPLOY_HOST:?DEPLOY_HOST is required}"
: "${DEPLOY_USER:?DEPLOY_USER is required}"
: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${DEPLOY_SSH_KEY_FILE:?DEPLOY_SSH_KEY_FILE is required}"

printf '%s' "$DEPLOY_HOST" | grep -Eq '^[A-Za-z0-9.-]+$'
printf '%s' "$DEPLOY_USER" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]*$'
printf '%s' "$DEPLOY_PATH" | grep -Eq '^/[A-Za-z0-9._/-]+$'

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

tar -czf - \
  scripts/deploy-blue-green.sh \
  scripts/rollback-blue-green.sh \
  scripts/smoke-test.sh \
  infra/compose.yml \
  infra/compose.prod.yml \
  infra/admin \
  infra/monitoring \
  | ssh -i "$DEPLOY_SSH_KEY_FILE" \
      -o BatchMode=yes \
      -o IdentitiesOnly=yes \
      -o StrictHostKeyChecking=yes \
      "$DEPLOY_USER@$DEPLOY_HOST" \
      "install -d -m 0755 '$DEPLOY_PATH/scripts' '$DEPLOY_PATH/infra' && tar -xzf - -C '$DEPLOY_PATH' && sudo -n install -d -m 0755 /var/www/limit-admin /var/www/limit-grafana-assets && sudo -n install -m 0644 '$DEPLOY_PATH/infra/admin/index.html' '$DEPLOY_PATH/infra/admin/admin.css' '$DEPLOY_PATH/infra/admin/admin.js' /var/www/limit-admin/ && asset_dir='$DEPLOY_PATH/infra/monitoring/grafana/assets' && find \"\$asset_dir\" -maxdepth 1 -type f -name '*.svg' -print -quit | grep -q . && find \"\$asset_dir\" -maxdepth 1 -type f -name '*.svg' -exec sudo -n install -m 0644 '{}' /var/www/limit-grafana-assets/ ';'"
