#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_parent="$root_dir/.test-tmp"
mkdir -p "$test_parent"
test_dir=$(mktemp -d "$test_parent/seller-recovery.XXXXXX")
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
printf 'docker:%s\n' "$*" >> "$EVENTS_PATH"
case "$*" in
  *"--profile maintenance config --quiet"*)
    printf 'compose-config\n' >> "$EVENTS_PATH"
    ;;
  *"--profile maintenance pull flyway-maintenance"*)
    printf 'flyway-pull\n' >> "$EVENTS_PATH"
    ;;
  *"exec -T mysql"*)
    sql=$(cat)
    case "$sql" in
      *recovery_preflight*)
        printf 'preflight\n' >> "$EVENTS_PATH"
        if [ "${RECOVERY_SCENARIO:-success}" = precondition-fail ]; then
          printf '0|0|0|1|1|0|3\n'
        else
          printf '1|0|0|1|1|0|3\n'
        fi
        ;;
      *"ALTER TABLE seller"*)
        printf 'schema\n' >> "$EVENTS_PATH"
        ;;
      *recovery_schema_after*)
        printf 'schema-after\n' >> "$EVENTS_PATH"
        printf '3\n'
        ;;
      *recovery_repair_after*)
        printf 'repair-after\n' >> "$EVENTS_PATH"
        printf '0|0\n'
        ;;
      *recovery_deployment_after*)
        printf 'deployment-after\n' >> "$EVENTS_PATH"
        printf '0|1|1|0|0|1\n'
        ;;
      *)
        echo "unexpected MySQL recovery query" >&2
        exit 1
        ;;
    esac
    ;;
  *"flyway-maintenance repair"*)
    printf 'repair\n' >> "$EVENTS_PATH"
    ;;
  *)
    echo "unexpected docker call: $*" >&2
    exit 1
    ;;
esac
EOF

cat > "$test_dir/backup.sh" <<'EOF'
#!/usr/bin/env bash
set -eu
printf 'backup\n' >> "$EVENTS_PATH"
EOF

cat > "$test_dir/deploy.sh" <<'EOF'
#!/usr/bin/env bash
set -eu
printf 'deploy:%s\n' "$*" >> "$EVENTS_PATH"
EOF

cat > "$test_dir/smoke.sh" <<'EOF'
#!/usr/bin/env bash
set -eu
printf 'smoke:%s\n' "$*" >> "$EVENTS_PATH"
EOF

chmod +x "$fake_bin/docker" "$test_dir/backup.sh" "$test_dir/deploy.sh" "$test_dir/smoke.sh"
: > "$test_dir/test.env"

maintenance_sql="$root_dir/backend/src/main/resources/db/maintenance/V20260802__prepare_failed_seller_migration.sql"
if grep -Eiq '(^|[;[:space:]])(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP)([;[:space:]]|$)' \
  "$maintenance_sql"; then
  echo "seller recovery compatibility SQL must not modify rows or drop schema objects" >&2
  exit 1
fi

export PATH="$fake_bin:$PATH"
export EVENTS_PATH="$test_dir/events"
image_ref="registry.example.test/limit/backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

COMPOSE_ENV_FILE="$test_dir/test.env" \
BACKUP_SCRIPT="$test_dir/backup.sh" \
DEPLOY_SCRIPT="$test_dir/deploy.sh" \
SMOKE_SCRIPT="$test_dir/smoke.sh" \
  bash "$root_dir/scripts/recover-seller-migration.sh" \
    "$image_ref" "https://api.example.test"

grep -Fq 'preflight' "$EVENTS_PATH"
grep -Fq 'compose-config' "$EVENTS_PATH"
grep -Fq 'flyway-pull' "$EVENTS_PATH"
grep -Fq 'backup' "$EVENTS_PATH"
grep -Fq 'schema' "$EVENTS_PATH"
grep -Fq 'schema-after' "$EVENTS_PATH"
grep -Fq 'repair' "$EVENTS_PATH"
grep -Fq 'repair-after' "$EVENTS_PATH"
grep -Fq "deploy:prod $image_ref https://api.example.test" "$EVENTS_PATH"
grep -Fq 'deployment-after' "$EVENTS_PATH"
[ "$(grep -Fc 'smoke:https://api.example.test' "$EVENTS_PATH")" -eq 2 ]

: > "$EVENTS_PATH"
if RECOVERY_SCENARIO=precondition-fail \
  COMPOSE_ENV_FILE="$test_dir/test.env" \
  BACKUP_SCRIPT="$test_dir/backup.sh" \
  DEPLOY_SCRIPT="$test_dir/deploy.sh" \
  SMOKE_SCRIPT="$test_dir/smoke.sh" \
    bash "$root_dir/scripts/recover-seller-migration.sh" \
      "$image_ref" "https://api.example.test"; then
  echo "seller recovery unexpectedly accepted mismatched preconditions" >&2
  exit 1
fi
if grep -Eq '^(backup|schema|repair|deploy:)' "$EVENTS_PATH"; then
  echo "seller recovery mutated state after a failed precondition" >&2
  exit 1
fi

echo "seller migration recovery script tests passed"
