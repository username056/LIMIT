#!/bin/sh
set -eu

: "${DEPLOY_HOST:?DEPLOY_HOST is required}"
: "${DEPLOY_USER:?DEPLOY_USER is required}"
: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${DEPLOY_SSH_KEY_FILE:?DEPLOY_SSH_KEY_FILE is required}"

printf '%s' "$DEPLOY_HOST" | grep -Eq '^[A-Za-z0-9.-]+$'
printf '%s' "$DEPLOY_USER" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]*$'
printf '%s' "$DEPLOY_PATH" | grep -Eq '^/[A-Za-z0-9._/-]+$'
if [ ! -f "$DEPLOY_SSH_KEY_FILE" ] || [ ! -r "$DEPLOY_SSH_KEY_FILE" ]; then
  echo "deploy SSH key file is missing or unreadable" >&2
  exit 1
fi
key_mode=$(stat -c '%a' "$DEPLOY_SSH_KEY_FILE" 2>/dev/null || true)
if [ "$key_mode" != 600 ] && [ "$key_mode" != 400 ]; then
  echo "deploy SSH key file mode must be 0600 or 0400" >&2
  exit 1
fi

diagnostic_sql=$(cat <<'SQL'
SELECT 'flyway_history' AS diagnostic_section;
SELECT
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    execution_time,
    success
FROM flyway_schema_history
WHERE version = '20260802'
ORDER BY installed_rank;

SELECT 'seller_columns' AS diagnostic_section;
SELECT
    ordinal_position,
    column_name,
    column_type,
    is_nullable,
    COALESCE(CAST(column_default AS CHAR), '<NULL>') AS column_default,
    extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'seller'
ORDER BY ordinal_position;

SELECT 'seller_indexes' AS diagnostic_section;
SELECT
    index_name,
    non_unique,
    seq_in_index,
    column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'seller'
ORDER BY index_name, seq_in_index;

SELECT 'seller_required_columns_without_defaults' AS diagnostic_section;
SELECT
    column_name,
    column_type,
    extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'seller'
  AND is_nullable = 'NO'
  AND column_default IS NULL
  AND extra NOT LIKE '%auto_increment%'
  AND column_name NOT IN (
      'user_id',
      'seller_type',
      'status',
      'country_code',
      'business_name',
      'settlement_bank_name',
      'settlement_account_holder',
      'settlement_account_last4',
      'created_at',
      'updated_at'
  )
ORDER BY ordinal_position;

SET @seller_user_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'user_id'
);
SET @diagnostic_sql = IF(
    @seller_user_id_exists = 1,
    'SELECT
         COUNT(*) AS seller_rows,
         COALESCE(SUM(user_id IS NULL), 0) AS null_user_id_rows,
         COUNT(DISTINCT user_id) AS distinct_user_ids
     FROM seller',
    'SELECT
         0 AS seller_rows,
         0 AS null_user_id_rows,
         0 AS distinct_user_ids'
);
SELECT 'seller_row_counts' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;

SET @diagnostic_sql = IF(
    @seller_user_id_exists = 1,
    'SELECT
         COUNT(*) AS duplicate_user_id_groups,
         COALESCE(SUM(duplicate_count - 1), 0) AS duplicate_excess_rows
     FROM (
         SELECT user_id, COUNT(*) AS duplicate_count
         FROM seller
         WHERE user_id IS NOT NULL
         GROUP BY user_id
         HAVING COUNT(*) > 1
     ) duplicate_groups',
    'SELECT
         0 AS duplicate_user_id_groups,
         0 AS duplicate_excess_rows'
);
SELECT 'seller_user_id_duplicates' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;

SET @member_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_account'
      AND column_name = 'member_type'
);
SET @diagnostic_sql = IF(
    @member_type_exists = 1 AND @seller_user_id_exists = 1,
    'SELECT
         COUNT(*) AS seller_members,
         COALESCE(SUM(profile.user_id IS NULL), 0) AS seller_members_without_profile
     FROM user_account account
     LEFT JOIN seller profile ON profile.user_id = account.user_id
     WHERE account.member_type = ''SELLER''',
    'SELECT
         0 AS seller_members,
         0 AS seller_members_without_profile'
);
SELECT 'seller_member_profile_counts' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;

SET @status_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'status'
);
SET @diagnostic_sql = IF(
    @status_exists = 1,
    'SELECT COUNT(*) AS status_over_20
     FROM seller
     WHERE CHAR_LENGTH(status) > 20',
    'SELECT 0 AS status_over_20'
);
SELECT 'seller_status_length_counts' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;

SET @seller_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_type'
);
SET @diagnostic_sql = IF(
    @seller_type_exists = 1,
    'SELECT COUNT(*) AS seller_type_over_20
     FROM seller
     WHERE CHAR_LENGTH(seller_type) > 20',
    'SELECT 0 AS seller_type_over_20'
);
SELECT 'seller_type_length_counts' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;

SET @seller_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_category'
);
SET @diagnostic_sql = IF(
    @seller_category_exists = 1,
    'SELECT COUNT(*) AS seller_category_over_20
     FROM seller
     WHERE CHAR_LENGTH(seller_category) > 20',
    'SELECT 0 AS seller_category_over_20'
);
SELECT 'seller_category_length_counts' AS diagnostic_section;
PREPARE diagnostic_statement FROM @diagnostic_sql;
EXECUTE diagnostic_statement;
DEALLOCATE PREPARE diagnostic_statement;
SQL
)

remote_command=$(printf '%s' \
  "cd '$DEPLOY_PATH' && docker compose --env-file infra/.env -p limit-prod -f infra/compose.yml -f infra/compose.prod.yml exec -T mysql sh -ec 'MYSQL_PWD=\"\$MYSQL_PASSWORD\" exec mysql --batch --raw --connect-timeout=5 -u \"\$MYSQL_USER\" \"\$MYSQL_DATABASE\"'")

printf '%s\n' "$diagnostic_sql" \
  | ssh -i "$DEPLOY_SSH_KEY_FILE" \
      -o BatchMode=yes \
      -o IdentitiesOnly=yes \
      -o StrictHostKeyChecking=yes \
      "$DEPLOY_USER@$DEPLOY_HOST" \
      "$remote_command"
