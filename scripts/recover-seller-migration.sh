#!/usr/bin/env bash
set -Eeuo pipefail

image_ref="${1:-}"
smoke_base_url="${2:-${SMOKE_BASE_URL:-}}"

if [[ ! "$image_ref" =~ ^[^[:space:]]+@sha256:[a-f0-9]{64}$ ]]; then
  echo "seller migration recovery requires an immutable image digest" >&2
  exit 64
fi
if [[ ! "$smoke_base_url" =~ ^https://[^[:space:]]+$ ]]; then
  echo "seller migration recovery requires an HTTPS smoke base URL" >&2
  exit 64
fi

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file="${COMPOSE_ENV_FILE:-$root_dir/infra/.env}"
schema_sql_file="${SELLER_RECOVERY_SQL_FILE:-$root_dir/backend/src/main/resources/db/maintenance/V20260802__prepare_failed_seller_migration.sql}"
backup_script="${BACKUP_SCRIPT:-$root_dir/scripts/backup-datastores.sh}"
deploy_script="${DEPLOY_SCRIPT:-$root_dir/scripts/deploy-blue-green.sh}"
smoke_script="${SMOKE_SCRIPT:-$root_dir/scripts/smoke-test.sh}"

for required_file in \
  "$env_file" \
  "$schema_sql_file" \
  "$backup_script" \
  "$deploy_script" \
  "$smoke_script"; do
  if [[ ! -f "$required_file" ]]; then
    echo "seller migration recovery file not found: $required_file" >&2
    exit 66
  fi
done

compose=(
  docker compose
  --env-file "$env_file"
  -p limit-prod
  -f "$root_dir/infra/compose.yml"
  -f "$root_dir/infra/compose.prod.yml"
)

mysql_query() {
  "${compose[@]}" exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --raw --skip-column-names --connect-timeout=5 -u "$MYSQL_USER" "$MYSQL_DATABASE"'
}

"${compose[@]}" --profile maintenance config --quiet
"${compose[@]}" --profile maintenance pull flyway-maintenance

preflight_sql=$(cat <<'SQL'
/* recovery_preflight */
SELECT CONCAT(
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 0),
    '|',
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 1),
    '|',
    (SELECT COUNT(*) FROM seller),
    '|',
    (SELECT COUNT(*) FROM user_account WHERE member_type = 'SELLER'),
    '|',
    (SELECT COUNT(*)
       FROM user_account account
       LEFT JOIN seller profile ON profile.user_id = account.user_id
      WHERE account.member_type = 'SELLER'
        AND profile.user_id IS NULL),
    '|',
    (SELECT COUNT(*)
       FROM (
           SELECT user_id
             FROM seller
            WHERE user_id IS NOT NULL
            GROUP BY user_id
           HAVING COUNT(*) > 1
       ) duplicate_groups),
    '|',
    (SELECT COUNT(*)
       FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'seller'
        AND (
            (column_name = 'approved_at'
             AND column_type = 'datetime(6)'
             AND is_nullable = 'NO'
             AND column_default IS NULL)
            OR
            (column_name = 'product_limit'
             AND column_type = 'int'
             AND is_nullable = 'NO'
             AND column_default IS NULL)
            OR
            (column_name = 'sales_amount_limit'
             AND column_type = 'decimal(38,2)'
             AND is_nullable = 'NO'
             AND column_default IS NULL)
        ))
);
SQL
)

bash "$smoke_script" "$smoke_base_url"
preflight=$(printf '%s\n' "$preflight_sql" | mysql_query)
if [[ "$preflight" != "1|0|0|1|1|0|3" ]]; then
  echo "seller migration recovery precondition mismatch: $preflight" >&2
  exit 1
fi
echo "seller migration recovery preconditions verified"

COMPOSE_ENV_FILE="$env_file" bash "$backup_script"

mysql_query < "$schema_sql_file"

schema_after_sql=$(cat <<'SQL'
/* recovery_schema_after */
SELECT COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'seller'
  AND (
      (column_name = 'approved_at'
       AND column_type = 'datetime(6)'
       AND is_nullable = 'YES'
       AND column_default IS NULL)
      OR
      (column_name = 'product_limit'
       AND column_type = 'int'
       AND is_nullable = 'NO'
       AND CAST(column_default AS DECIMAL(38, 2)) = 0)
      OR
      (column_name = 'sales_amount_limit'
       AND column_type = 'decimal(38,2)'
       AND is_nullable = 'NO'
       AND CAST(column_default AS DECIMAL(38, 2)) = 0)
  );
SQL
)
schema_after=$(printf '%s\n' "$schema_after_sql" | mysql_query)
if [[ "$schema_after" != "3" ]]; then
  echo "seller schema compatibility preparation verification failed: $schema_after" >&2
  exit 1
fi
echo "seller schema compatibility preparation verified"

"${compose[@]}" --profile maintenance run --rm --no-deps flyway-maintenance repair

repair_after_sql=$(cat <<'SQL'
/* recovery_repair_after */
SELECT CONCAT(
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 0),
    '|',
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 1)
);
SQL
)
repair_after=$(printf '%s\n' "$repair_after_sql" | mysql_query)
if [[ "$repair_after" != "0|0" ]]; then
  echo "Flyway repair verification failed: $repair_after" >&2
  exit 1
fi
echo "Flyway repair verified"

COMPOSE_ENV_FILE="$env_file" bash "$deploy_script" prod "$image_ref" "$smoke_base_url"

deployment_after_sql=$(cat <<'SQL'
/* recovery_deployment_after */
SELECT CONCAT(
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 0),
    '|',
    (SELECT COUNT(*) FROM flyway_schema_history
      WHERE version = '20260802' AND success = 1),
    '|',
    (SELECT COUNT(*) FROM seller),
    '|',
    (SELECT COUNT(*)
       FROM user_account account
       LEFT JOIN seller profile ON profile.user_id = account.user_id
      WHERE account.member_type = 'SELLER'
        AND profile.user_id IS NULL),
    '|',
    (SELECT COUNT(*)
       FROM (
           SELECT user_id
             FROM seller
            WHERE user_id IS NOT NULL
            GROUP BY user_id
           HAVING COUNT(*) > 1
       ) duplicate_groups),
    '|',
    (SELECT COUNT(*)
       FROM seller
      WHERE approved_at IS NULL
        AND product_limit = 0
        AND sales_amount_limit = 0)
);
SQL
)
deployment_after=$(printf '%s\n' "$deployment_after_sql" | mysql_query)
if [[ "$deployment_after" != "0|1|1|0|0|1" ]]; then
  echo "seller migration deployment verification failed: $deployment_after" >&2
  exit 1
fi

bash "$smoke_script" "$smoke_base_url"
echo "seller migration repair and deployment completed"
