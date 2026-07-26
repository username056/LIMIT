#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file=${COMPOSE_ENV_FILE:-"$root_dir/infra/.env"}
backup_dir=${BACKUP_LOCAL_DIR:-"$root_dir/infra/state/backups"}
metrics_file=${BACKUP_METRICS_FILE:-"$root_dir/infra/state/node-exporter/limit_backup.prom"}
retention_days=${BACKUP_LOCAL_RETENTION_DAYS:-3}
started_at=$(date +%s)
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
staging_dir=
success=0

read_env_value() {
  local key=$1
  awk -v key="$key" '
    index($0, key "=") == 1 {
      value = substr($0, length(key) + 2)
      found = 1
    }
    END {
      if (found) {
        print value
      }
    }
  ' "$env_file"
}

write_metrics() {
  local exit_code=$1
  local completed_at duration metric_tmp
  completed_at=$(date +%s)
  duration=$((completed_at - started_at))
  mkdir -p "$(dirname -- "$metrics_file")"
  chmod 0755 "$(dirname -- "$metrics_file")"
  metric_tmp="${metrics_file}.tmp.$$"
  {
    printf '# HELP limit_backup_last_success_timestamp_seconds Unix timestamp of the last successful database backup.\n'
    printf '# TYPE limit_backup_last_success_timestamp_seconds gauge\n'
    if [[ "$success" -eq 1 ]]; then
      printf 'limit_backup_last_success_timestamp_seconds %s\n' "$completed_at"
    elif [[ -f "$metrics_file" ]]; then
      awk '/^limit_backup_last_success_timestamp_seconds / { print }' "$metrics_file"
    fi
    printf '# HELP limit_backup_last_exit_code Exit code of the most recent database backup.\n'
    printf '# TYPE limit_backup_last_exit_code gauge\n'
    printf 'limit_backup_last_exit_code %s\n' "$exit_code"
    printf '# HELP limit_backup_last_duration_seconds Duration of the most recent database backup.\n'
    printf '# TYPE limit_backup_last_duration_seconds gauge\n'
    printf 'limit_backup_last_duration_seconds %s\n' "$duration"
  } > "$metric_tmp"
  chmod 0644 "$metric_tmp"
  mv -f -- "$metric_tmp" "$metrics_file"
}

finish() {
  local exit_code=$?
  if [[ -n "$staging_dir" && -d "$staging_dir" ]]; then
    rm -rf -- "$staging_dir"
  fi
  write_metrics "$exit_code"
  exit "$exit_code"
}
trap finish EXIT

if [[ ! -f "$env_file" ]]; then
  echo "compose environment file not found: $env_file" >&2
  exit 66
fi
mkdir -p "$backup_dir"
backup_dir=$(cd -- "$backup_dir" && pwd -P)
if [[ "$backup_dir" == / ]]; then
  echo "BACKUP_LOCAL_DIR cannot be the filesystem root" >&2
  exit 64
fi
if ! [[ "$retention_days" =~ ^[1-9][0-9]*$ ]]; then
  echo "BACKUP_LOCAL_RETENTION_DAYS must be a positive integer" >&2
  exit 64
fi

backup_s3_uri=${BACKUP_S3_URI:-$(read_env_value BACKUP_S3_URI)}
backup_kms_key_id=${BACKUP_KMS_KEY_ID:-$(read_env_value BACKUP_KMS_KEY_ID)}
upload_enabled=${BACKUP_UPLOAD_ENABLED:-$(read_env_value BACKUP_UPLOAD_ENABLED)}
upload_enabled=${upload_enabled:-false}
if [[ "$upload_enabled" != true && "$upload_enabled" != false ]]; then
  echo "BACKUP_UPLOAD_ENABLED must be true or false" >&2
  exit 64
fi

compose=(
  docker compose
  --env-file "$env_file"
  -p limit-prod
  -f "$root_dir/infra/compose.yml"
  -f "$root_dir/infra/compose.prod.yml"
)

chmod 0700 "$backup_dir"
staging_dir=$(mktemp -d "$backup_dir/.staging-${timestamp}-XXXXXX")
chmod 0700 "$staging_dir"

"${compose[@]}" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --quick --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  > "$staging_dir/mysql.sql"

"${compose[@]}" exec -T mongodb sh -ec \
  'exec mongodump --quiet --host 127.0.0.1 --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db limit --archive --gzip' \
  > "$staging_dir/mongodb.archive.gz"

mysql_table_count=$("${compose[@]}" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u "$MYSQL_USER" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''$MYSQL_DATABASE'\'';"')
mongo_collection_count=$("${compose[@]}" exec -T mongodb sh -ec \
  'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin limit --eval "db.getCollectionNames().length"')
if ! [[ "$mysql_table_count" =~ ^[0-9]+$ && "$mongo_collection_count" =~ ^[0-9]+$ ]]; then
  echo "failed to read datastore object counts" >&2
  exit 1
fi

(
  cd "$staging_dir"
  sha256sum mysql.sql mongodb.archive.gz > SHA256SUMS
  {
    printf 'created_at=%s\n' "$timestamp"
    printf 'format_version=1\n'
    printf 'datastores=mysql,mongodb\n'
    printf 'mysql_table_count=%s\n' "$mysql_table_count"
    printf 'mongodb_collection_count=%s\n' "$mongo_collection_count"
  } > MANIFEST
)

archive_path="$backup_dir/limit-datastores-${timestamp}.tar.gz"
tar -C "$staging_dir" -czf "$archive_path" .
chmod 0600 "$archive_path"

if [[ "$upload_enabled" == true ]]; then
  if [[ "$backup_s3_uri" != s3://* || -z "$backup_kms_key_id" ]]; then
    echo "S3 upload requires BACKUP_S3_URI and BACKUP_KMS_KEY_ID" >&2
    exit 64
  fi
  if ! command -v aws >/dev/null 2>&1; then
    echo "aws CLI is required for S3 backup upload" >&2
    exit 69
  fi
  aws s3 cp "$archive_path" "${backup_s3_uri%/}/$(basename -- "$archive_path")" \
    --only-show-errors \
    --sse aws:kms \
    --sse-kms-key-id "$backup_kms_key_id"
fi

find "$backup_dir" -maxdepth 1 -type f -name 'limit-datastores-*.tar.gz' \
  -mtime "+$retention_days" -delete

success=1
echo "database backup completed: $(basename -- "$archive_path")"
