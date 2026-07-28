#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

fake_bin="$test_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/ssh" <<'EOF'
#!/bin/sh
cat > "$SQL_CAPTURE_PATH"
printf '%s\n' "$*" > "$SSH_ARGS_PATH"
EOF
chmod +x "$fake_bin/ssh"

key_file="$test_dir/deploy-key"
: > "$key_file"
chmod 0600 "$key_file"

PATH="$fake_bin:$PATH" \
  SQL_CAPTURE_PATH="$test_dir/diagnostic.sql" \
  SSH_ARGS_PATH="$test_dir/ssh.args" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$key_file" \
  sh "$root_dir/scripts/diagnose-seller-migration-remote.sh"

grep -Fq "WHERE version = '20260802'" "$test_dir/diagnostic.sql"
grep -Fq "duplicate_user_id_groups" "$test_dir/diagnostic.sql"
grep -Fq "seller_members_without_profile" "$test_dir/diagnostic.sql"
grep -Fq "seller_required_columns_without_defaults" "$test_dir/diagnostic.sql"
if grep -Fq "SELECT *" "$test_dir/diagnostic.sql"; then
  echo "diagnostic SQL must not select complete rows" >&2
  exit 1
fi
if grep -Eq 'account\.(email|nickname|phone)|profile\.(business_name|settlement_account_holder)' \
  "$test_dir/diagnostic.sql"; then
  echo "diagnostic SQL must not select personal data" >&2
  exit 1
fi

grep -Fq -- "-o BatchMode=yes" "$test_dir/ssh.args"
grep -Fq -- "-o IdentitiesOnly=yes" "$test_dir/ssh.args"
grep -Fq -- "-o StrictHostKeyChecking=yes" "$test_dir/ssh.args"
grep -Fq "docker compose --env-file infra/.env -p limit-prod" "$test_dir/ssh.args"
grep -Fq "MYSQL_PWD=\"\$MYSQL_PASSWORD\"" "$test_dir/ssh.args"

insecure_key="$test_dir/insecure-key"
: > "$insecure_key"
chmod 0644 "$insecure_key"
if PATH="$fake_bin:$PATH" \
  SQL_CAPTURE_PATH="$test_dir/insecure.sql" \
  SSH_ARGS_PATH="$test_dir/insecure.args" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$insecure_key" \
  sh "$root_dir/scripts/diagnose-seller-migration-remote.sh"; then
  echo "insecure SSH key unexpectedly accepted" >&2
  exit 1
fi

echo "seller migration diagnostic script tests passed"
