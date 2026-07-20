#!/bin/sh
set -eu

ssh_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$ssh_dir/.env" ]; then
  # shellcheck disable=SC1091
  . "$ssh_dir/.env"
fi

: "${LIMIT_EC2_HOST:?LIMIT_EC2_HOST is required}"
: "${LIMIT_EC2_USER:?LIMIT_EC2_USER is required}"

host=$LIMIT_EC2_HOST
user=$LIMIT_EC2_USER
port=${LIMIT_EC2_SSH_PORT:-22}
key_path=${LIMIT_EC2_KEY_PATH:-"$ssh_dir/server.pem"}

if ! command -v ssh >/dev/null 2>&1; then
  echo "ssh command was not found." >&2
  exit 127
fi

if ! command -v mktemp >/dev/null 2>&1; then
  echo "mktemp command was not found." >&2
  exit 127
fi

if [ ! -f "$key_path" ]; then
  echo "PEM key was not found: $key_path" >&2
  echo "Set LIMIT_EC2_KEY_PATH in SSH/.env or the environment." >&2
  exit 66
fi

temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/limit-ssh.XXXXXX")
temp_key="$temp_dir/server.pem"

cleanup() {
  rm -f -- "$temp_key"
  rmdir -- "$temp_dir" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

cp -- "$key_path" "$temp_key"
chmod 600 "$temp_key"

ssh \
  -p "$port" \
  -i "$temp_key" \
  -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=accept-new \
  "$user@$host" \
  "$@"
