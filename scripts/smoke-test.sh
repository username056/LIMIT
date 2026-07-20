#!/bin/sh
set -eu

base_url="${1:-${SMOKE_BASE_URL:-}}"
if [ -z "$base_url" ]; then
  echo "usage: $0 <base-url>" >&2
  exit 64
fi

curl_flags="--fail --silent --show-error --connect-timeout 5 --max-time 15"
readiness=$(curl $curl_flags "${base_url%/}/health")

printf '%s' "$readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'

echo "readiness smoke test passed"
