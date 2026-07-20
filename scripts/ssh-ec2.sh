#!/usr/bin/env bash
set -euo pipefail

host_name="${LIMIT_EC2_HOST:-i15c203.p.ssafy.io}"
user_name="${LIMIT_EC2_USER:-ubuntu}"
user_profile="${USERPROFILE:-${HOME}}"
key_path="${LIMIT_EC2_KEY_PATH:-${user_profile}/.ssh/I15C203T.pem}"

[[ "$host_name" =~ ^[A-Za-z0-9.-]+$ ]] || {
  echo "invalid EC2 host: $host_name" >&2
  exit 64
}

[[ "$user_name" =~ ^[A-Za-z_][A-Za-z0-9_-]*$ ]] || {
  echo "invalid EC2 user: $user_name" >&2
  exit 64
}

command -v ssh >/dev/null 2>&1 || {
  echo "OpenSSH client is required." >&2
  exit 69
}

[[ -f "$key_path" ]] || {
  echo "EC2 key not found: $key_path" >&2
  exit 66
}

chmod 600 "$key_path" 2>/dev/null || true

echo "Connecting to EC2: ${user_name}@${host_name}"
exec ssh \
  -i "$key_path" \
  -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=accept-new \
  -o ConnectTimeout=10 \
  "${user_name}@${host_name}"
