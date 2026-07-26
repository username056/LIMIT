#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/apply-ec2-env.XXXXXX")
trap 'rm -rf -- "$test_dir"' EXIT HUP INT TERM

deploy_path="$test_dir/deploy"
fake_bin="$test_dir/bin"
mkdir -p "$deploy_path/infra" "$fake_bin"

cat > "$deploy_path/infra/.env" <<'EOF'
MAIL_HOST=old.example.test
MAIL_PORT = 25
UNCHANGED=value
MAIL_HOST=duplicate.example.test
EOF
chmod 0600 "$deploy_path/infra/.env"
touch "$deploy_path/infra/compose.yml" "$deploy_path/infra/compose.prod.yml"
cp "$deploy_path/infra/.env" "$test_dir/original.env"

cat > "$fake_bin/docker" <<'EOF'
#!/bin/sh
if [ "${FAIL_COMPOSE:-false}" = true ]; then
  echo "invalid rendered value=compose-leaked-value" >&2
  exit 1
fi
printf '%s\n' "$*" > "$DOCKER_ARGS_FILE"
EOF
chmod +x "$fake_bin/docker"

output=$(
  printf '%s\n' \
    'MAIL_HOST=new.example.test' \
    'MAIL_PORT=587' \
    'MAIL_PASSWORD=do-not-print-value' |
    PATH="$fake_bin:$PATH" DOCKER_ARGS_FILE="$test_dir/docker.args" \
      sh "$root_dir/scripts/apply-ec2-env-remote.sh" "$deploy_path"
)

grep -Fq 'MAIL_HOST=new.example.test' "$deploy_path/infra/.env"
grep -Fq 'MAIL_PORT=587' "$deploy_path/infra/.env"
grep -Fq 'MAIL_PASSWORD=do-not-print-value' "$deploy_path/infra/.env"
grep -Fq 'UNCHANGED=value' "$deploy_path/infra/.env"
[ "$(grep -c '^MAIL_HOST=' "$deploy_path/infra/.env")" -eq 1 ]
[ "$(grep -Ec '^MAIL_PORT[[:space:]]*=' "$deploy_path/infra/.env")" -eq 1 ]
[ "$(stat -c '%a' "$deploy_path/infra/.env")" = 600 ]
printf '%s' "$output" | grep -Fq 'applied:MAIL_HOST,MAIL_PORT,MAIL_PASSWORD'
if printf '%s' "$output" | grep -Fq 'do-not-print-value'; then
  echo "environment value leaked to output" >&2
  exit 1
fi
grep -Fq -- '--env-file' "$test_dir/docker.args"

backup_file=$(find "$deploy_path/infra" -maxdepth 1 -type f -name '.env.backup.*' -print)
[ -n "$backup_file" ]
cmp -s "$test_dir/original.env" "$backup_file"
[ "$(stat -c '%a' "$backup_file")" = 600 ]

cp "$deploy_path/infra/.env" "$test_dir/before-invalid.env"
if printf '%s\n' 'bad-key=value' |
  PATH="$fake_bin:$PATH" DOCKER_ARGS_FILE="$test_dir/docker-invalid.args" \
    sh "$root_dir/scripts/apply-ec2-env-remote.sh" "$deploy_path" >/dev/null 2>&1; then
  echo "invalid key unexpectedly succeeded" >&2
  exit 1
fi
cmp -s "$test_dir/before-invalid.env" "$deploy_path/infra/.env"

cp "$deploy_path/infra/.env" "$test_dir/before-compose-failure.env"
if compose_failure_output=$(printf '%s\n' 'MAIL_PORT=999' |
  PATH="$fake_bin:$PATH" FAIL_COMPOSE=true DOCKER_ARGS_FILE="$test_dir/docker-failure.args" \
    sh "$root_dir/scripts/apply-ec2-env-remote.sh" "$deploy_path" 2>&1); then
  echo "compose validation failure unexpectedly succeeded" >&2
  exit 1
fi
if printf '%s' "$compose_failure_output" | grep -Fq 'compose-leaked-value'; then
  echo "compose validation output leaked a value" >&2
  exit 1
fi
cmp -s "$test_dir/before-compose-failure.env" "$deploy_path/infra/.env"

echo "apply-ec2-env remote tests passed"
