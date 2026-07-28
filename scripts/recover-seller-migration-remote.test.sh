#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

fake_bin="$test_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/ssh" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" > "$SSH_ARGS_PATH"
EOF
chmod +x "$fake_bin/ssh"

key_file="$test_dir/deploy-key"
: > "$key_file"
chmod 0600 "$key_file"
image_ref="registry.example.test/limit/backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

PATH="$fake_bin:$PATH" \
SSH_ARGS_PATH="$test_dir/ssh.args" \
DEPLOY_HOST="ec2.example.test" \
DEPLOY_USER="ubuntu" \
DEPLOY_PATH="/opt/limit" \
DEPLOY_SSH_KEY_FILE="$key_file" \
SMOKE_BASE_URL="https://api.example.test" \
  sh "$root_dir/scripts/recover-seller-migration-remote.sh" "$image_ref"

grep -Fq -- "-o BatchMode=yes" "$test_dir/ssh.args"
grep -Fq -- "-o IdentitiesOnly=yes" "$test_dir/ssh.args"
grep -Fq -- "-o StrictHostKeyChecking=yes" "$test_dir/ssh.args"
grep -Fq "cd '/opt/limit'" "$test_dir/ssh.args"
grep -Fq "scripts/recover-seller-migration.sh '$image_ref' 'https://api.example.test'" \
  "$test_dir/ssh.args"

insecure_key="$test_dir/insecure-key"
: > "$insecure_key"
chmod 0644 "$insecure_key"
if PATH="$fake_bin:$PATH" \
  SSH_ARGS_PATH="$test_dir/insecure.args" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$insecure_key" \
  SMOKE_BASE_URL="https://api.example.test" \
    sh "$root_dir/scripts/recover-seller-migration-remote.sh" "$image_ref"; then
  echo "insecure SSH key unexpectedly accepted" >&2
  exit 1
fi

echo "seller migration remote recovery script tests passed"
