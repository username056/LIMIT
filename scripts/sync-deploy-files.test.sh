#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT

fake_bin="$test_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/ssh" <<'EOF'
#!/bin/sh
cat > "$ARCHIVE_PATH"
printf '%s\n' "$*" > "$SSH_ARGS_PATH"
EOF
chmod +x "$fake_bin/ssh"

key_file="$test_dir/deploy-key"
: > "$key_file"
chmod 0600 "$key_file"

run_sync() {
  scope=$1
  archive=$2
  args=$3
  PATH="$fake_bin:$PATH" \
    ARCHIVE_PATH="$archive" \
    SSH_ARGS_PATH="$args" \
    DEPLOY_HOST="ec2.example.test" \
    DEPLOY_USER="ubuntu" \
    DEPLOY_PATH="/opt/limit" \
    DEPLOY_SSH_KEY_FILE="$key_file" \
    sh "$root_dir/scripts/sync-deploy-files.sh" "$scope"
}

monitoring_archive="$test_dir/monitoring.tar.gz"
run_sync monitoring "$monitoring_archive" "$test_dir/monitoring.args"
tar -tzf "$monitoring_archive" > "$test_dir/monitoring.files"
grep -Fxq "scripts/deploy-monitoring.sh" "$test_dir/monitoring.files"
grep -Fxq "infra/monitoring/" "$test_dir/monitoring.files"
grep -Fxq "infra/monitoring/grafana/assets/vue.svg" "$test_dir/monitoring.files"
grep -Fxq "infra/nginx/limit.conf" "$test_dir/monitoring.files"
grep -Fq "/var/www/limit-grafana-assets" "$test_dir/monitoring.args"
grep -Fq "find" "$test_dir/monitoring.args"
if grep -Fxq "scripts/deploy-blue-green.sh" "$test_dir/monitoring.files"; then
  echo "monitoring scope included application deployment files" >&2
  exit 1
fi
if grep -Fq "/var/www/limit-admin" "$test_dir/monitoring.args"; then
  echo "monitoring scope included admin asset installation" >&2
  exit 1
fi

all_archive="$test_dir/all.tar.gz"
run_sync all "$all_archive" "$test_dir/all.args"
tar -tzf "$all_archive" > "$test_dir/all.files"
grep -Fxq "scripts/deploy-blue-green.sh" "$test_dir/all.files"
grep -Fxq "scripts/deploy-monitoring.sh" "$test_dir/all.files"
grep -Fxq "infra/admin/" "$test_dir/all.files"
grep -Fq "/var/www/limit-admin" "$test_dir/all.args"
grep -Fq "/var/www/limit-grafana-assets" "$test_dir/all.args"

if run_sync invalid "$test_dir/invalid.tar.gz" "$test_dir/invalid.args"; then
  echo "invalid scope unexpectedly succeeded" >&2
  exit 1
fi

if PATH="$fake_bin:$PATH" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$test_dir/missing-key" \
  sh "$root_dir/scripts/sync-deploy-files.sh" monitoring; then
  echo "missing SSH key unexpectedly succeeded" >&2
  exit 1
fi

insecure_key="$test_dir/insecure-key"
: > "$insecure_key"
chmod 0644 "$insecure_key"
if PATH="$fake_bin:$PATH" \
  ARCHIVE_PATH="$test_dir/insecure.tar.gz" \
  SSH_ARGS_PATH="$test_dir/insecure.args" \
  DEPLOY_HOST="ec2.example.test" \
  DEPLOY_USER="ubuntu" \
  DEPLOY_PATH="/opt/limit" \
  DEPLOY_SSH_KEY_FILE="$insecure_key" \
  sh "$root_dir/scripts/sync-deploy-files.sh" monitoring; then
  echo "insecure SSH key mode unexpectedly succeeded" >&2
  exit 1
fi

echo "sync-deploy-files scope tests passed"
