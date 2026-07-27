#!/bin/sh
set -eu

scope="${1:-all}"
: "${DEPLOY_HOST:?DEPLOY_HOST is required}"
: "${DEPLOY_USER:?DEPLOY_USER is required}"
: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${DEPLOY_SSH_KEY_FILE:?DEPLOY_SSH_KEY_FILE is required}"

printf '%s' "$DEPLOY_HOST" | grep -Eq '^[A-Za-z0-9.-]+$'
printf '%s' "$DEPLOY_USER" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]*$'
printf '%s' "$DEPLOY_PATH" | grep -Eq '^/[A-Za-z0-9._/-]+$'
if [ ! -f "$DEPLOY_SSH_KEY_FILE" ] || [ ! -r "$DEPLOY_SSH_KEY_FILE" ]; then
  echo "deploy SSH key file is missing or unreadable: $DEPLOY_SSH_KEY_FILE" >&2
  exit 1
fi
key_mode=$(stat -c '%a' "$DEPLOY_SSH_KEY_FILE" 2>/dev/null || true)
if [ "$key_mode" != 600 ] && [ "$key_mode" != 400 ]; then
  echo "deploy SSH key file mode must be 0600 or 0400: $key_mode" >&2
  exit 1
fi

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

sync_archive() {
  remote_command=$1
  shift
  tar -czf - "$@" \
    | ssh -i "$DEPLOY_SSH_KEY_FILE" \
        -o BatchMode=yes \
        -o IdentitiesOnly=yes \
        -o StrictHostKeyChecking=yes \
        "$DEPLOY_USER@$DEPLOY_HOST" \
        "$remote_command"
}

extract_command="install -d -m 0755 '$DEPLOY_PATH/scripts' '$DEPLOY_PATH/infra' && tar -xzf - -C '$DEPLOY_PATH'"
grafana_assets_command="asset_dir='$DEPLOY_PATH/infra/monitoring/grafana/assets' && sudo -n install -d -m 0755 /var/www/limit-grafana-assets && find \"\$asset_dir\" -maxdepth 1 -type f -name '*.svg' -print -quit | grep -q . && find \"\$asset_dir\" -maxdepth 1 -type f -name '*.svg' -exec sudo -n install -m 0644 '{}' /var/www/limit-grafana-assets/ ';'"

case "$scope" in
  all)
    sync_archive \
      "$extract_command && sudo -n install -d -m 0755 /var/www/limit-admin && sudo -n install -m 0644 '$DEPLOY_PATH/infra/admin/index.html' '$DEPLOY_PATH/infra/admin/admin.css' '$DEPLOY_PATH/infra/admin/admin.js' /var/www/limit-admin/ && $grafana_assets_command" \
      scripts/deploy-blue-green.sh \
      scripts/deploy-monitoring.sh \
      scripts/configure-rtc-turn.sh \
      scripts/backup-datastores.sh \
      scripts/restore-backup-drill.sh \
      scripts/install-backup-cron.sh \
      scripts/test-alertmanager-notification.sh \
      scripts/rollback-blue-green.sh \
      scripts/smoke-test.sh \
      infra/compose.yml \
      infra/compose.prod.yml \
      infra/admin \
      infra/monitoring \
      infra/nginx/limit.conf
    ;;
  monitoring)
    sync_archive \
      "$extract_command && $grafana_assets_command" \
      scripts/deploy-monitoring.sh \
      scripts/configure-rtc-turn.sh \
      scripts/backup-datastores.sh \
      scripts/restore-backup-drill.sh \
      scripts/install-backup-cron.sh \
      scripts/test-alertmanager-notification.sh \
      infra/compose.yml \
      infra/compose.prod.yml \
      infra/monitoring \
      infra/nginx/limit.conf
    ;;
  *)
    echo "usage: $0 [all|monitoring]" >&2
    exit 64
    ;;
esac
