#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

fake_bin="$test_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/ssh" <<'EOF'
#!/bin/sh
cat > "$SSH_STDIN_PATH"
printf '%s\n' "$*" > "$SSH_ARGS_PATH"
EOF
chmod +x "$fake_bin/ssh"

key_file="$test_dir/deploy-key"
: > "$key_file"
chmod 0600 "$key_file"

PATH="$fake_bin:$PATH" \
SSH_STDIN_PATH="$test_dir/stdin" \
SSH_ARGS_PATH="$test_dir/args" \
DEPLOY_HOST="ec2.example.test" \
DEPLOY_USER="ubuntu" \
DEPLOY_PATH="/opt/limit" \
DEPLOY_SSH_KEY_FILE="$key_file" \
AWS_REGION="ap-northeast-2" \
AWS_ACCESS_KEY_ID="must-not-be-forwarded" \
AWS_SECRET_ACCESS_KEY="must-not-be-forwarded" \
S3_MEDIA_BUCKET="limit-media-prod" \
S3_UPLOAD_TTL="10m" \
sh "$root_dir/scripts/apply-ci-s3-env-remote.sh"

grep -Fxq "AWS_REGION=ap-northeast-2" "$test_dir/stdin"
grep -Fxq "S3_MEDIA_BUCKET=limit-media-prod" "$test_dir/stdin"
grep -Fxq "S3_UPLOAD_TTL=10m" "$test_dir/stdin"
grep -Fxq "S3_PATH_STYLE_ACCESS=false" "$test_dir/stdin"
grep -Fq "apply-ec2-env-remote.sh" "$test_dir/args"
if grep -Eq 'AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|must-not-be-forwarded' "$test_dir/stdin"; then
  echo "AWS credentials were forwarded to EC2" >&2
  exit 1
fi

if PATH="$fake_bin:$PATH" \
  SSH_STDIN_PATH="$test_dir/missing.stdin" \
  SSH_ARGS_PATH="$test_dir/missing.args" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$key_file" \
  sh "$root_dir/scripts/apply-ci-s3-env-remote.sh" >/dev/null 2>&1; then
  echo "missing S3_MEDIA_BUCKET unexpectedly succeeded" >&2
  exit 1
fi

echo "CI S3 environment apply tests passed"
