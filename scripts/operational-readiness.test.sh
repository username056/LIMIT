#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_parent="$root_dir/.test-tmp"
mkdir -p "$test_parent"
test_dir=$(mktemp -d "$test_parent/operational.XXXXXX")
fake_bin="$test_dir/bin"
mkdir -p "$fake_bin"

cleanup() {
  case "$test_dir" in
    "$test_parent"/*)
      rm -rf -- "$test_dir"
      rmdir "$test_parent" 2>/dev/null || true
      ;;
    *) echo "refusing to remove unexpected test directory: $test_dir" >&2 ;;
  esac
}
trap cleanup EXIT

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -eu
case "$*" in
  *"exec -T mysql"*"mysql -N"*)
    printf '1\n'
    ;;
  *"exec -T mysql"*)
    printf '%s\n' 'CREATE TABLE restored_table (id BIGINT PRIMARY KEY);'
    ;;
  *"exec -T mongodb"*"mongosh"*)
    printf '0\n'
    ;;
  *"exec -T mongodb"*)
    printf '%s' 'fake-mongodb-archive'
    ;;
  *)
    echo "unexpected docker call: $*" >&2
    exit 1
    ;;
esac
EOF

cat > "$fake_bin/aws" <<'EOF'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$AWS_CALLS"
EOF

cat > "$fake_bin/crontab" <<'EOF'
#!/usr/bin/env bash
set -eu
if [ "${1:-}" = "-l" ]; then
  [ -f "$CRON_STATE" ] && cat "$CRON_STATE"
  exit 0
fi
cp "$1" "$CRON_STATE"
EOF

chmod +x "$fake_bin/docker" "$fake_bin/aws" "$fake_bin/crontab"

env_file="$test_dir/test.env"
cat > "$env_file" <<'EOF'
BACKUP_UPLOAD_ENABLED=true
BACKUP_S3_URI=s3://test-backups/datastores
BACKUP_KMS_KEY_ID=arn:aws:kms:ap-northeast-2:111122223333:key/test
EOF

export AWS_CALLS="$test_dir/aws.calls"
export CRON_STATE="$test_dir/crontab"
export PATH="$fake_bin:$PATH"

COMPOSE_ENV_FILE="$env_file" \
BACKUP_LOCAL_DIR="$test_dir/backups" \
BACKUP_METRICS_FILE="$test_dir/metrics/limit_backup.prom" \
  bash "$root_dir/scripts/backup-datastores.sh"

archive=$(find "$test_dir/backups" -maxdepth 1 -type f -name 'limit-datastores-*.tar.gz' -print -quit)
[ -n "$archive" ]
tar -tzf "$archive" | grep -Fq './mysql.sql'
tar -tzf "$archive" | grep -Fq './mongodb.archive.gz'
grep -Fq 'limit_backup_last_exit_code 0' "$test_dir/metrics/limit_backup.prom"
grep -Fq -- '--sse aws:kms' "$AWS_CALLS"
grep -Fq 's3://test-backups/datastores/' "$AWS_CALLS"

COMPOSE_ENV_FILE="$env_file" \
BACKUP_CRON_SCHEDULE='17 3 * * *' \
  bash "$root_dir/scripts/install-backup-cron.sh"

grep -Fq '# limit-datastore-backup' "$CRON_STATE"
grep -Fq 'backup-datastores.sh' "$CRON_STATE"

if COMPOSE_ENV_FILE="$env_file" BACKUP_LOCAL_DIR=/ \
  bash "$root_dir/scripts/backup-datastores.sh" >/dev/null 2>&1; then
  echo "filesystem root backup directory should have been rejected" >&2
  exit 1
fi

echo "operational readiness script tests passed"
