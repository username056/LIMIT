#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file=${COMPOSE_ENV_FILE:-"$root_dir/infra/.env"}
schedule=${BACKUP_CRON_SCHEDULE:-"17 3 * * *"}
marker="# limit-datastore-backup"
cron_tmp=$(mktemp)
trap 'rm -f -- "$cron_tmp"' EXIT

if [[ ! -f "$env_file" ]]; then
  echo "compose environment file not found: $env_file" >&2
  exit 66
fi
if [[ "$schedule" == *$'\n'* || "$schedule" == *$'\r'* ]]; then
  echo "BACKUP_CRON_SCHEDULE must be one cron line" >&2
  exit 64
fi

mkdir -p "$root_dir/infra/state/backups" "$root_dir/infra/state/node-exporter"
chmod 0700 "$root_dir/infra/state/backups"
chmod 0755 "$root_dir/infra/state/node-exporter"

crontab -l 2>/dev/null | grep -vF "$marker" > "$cron_tmp" || true
printf '%s COMPOSE_ENV_FILE=%q bash %q >> %q 2>&1 %s\n' \
  "$schedule" \
  "$env_file" \
  "$root_dir/scripts/backup-datastores.sh" \
  "$root_dir/infra/state/backups/backup.log" \
  "$marker" \
  >> "$cron_tmp"
crontab "$cron_tmp"

echo "database backup cron installed: $schedule"
