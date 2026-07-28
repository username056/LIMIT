#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
localhost_origin="http://localhost:5173"

grep -Fq "'$localhost_origin' \$http_origin;" "$root_dir/infra/nginx/limit.conf"
grep -Fq \
  'RTC_ALLOWED_ORIGIN_PATTERNS: ${RTC_ALLOWED_ORIGIN_PATTERNS:-http://localhost:5173,https://l1mit.shop,https://www.l1mit.shop}' \
  "$root_dir/infra/compose.prod.yml"
grep -Fq \
  'http://localhost:5173,https://l1mit.shop,https://www.l1mit.shop' \
  "$root_dir/backend/src/main/java/com/c203/limit/domain/chat/config/ChatWebSocketConfig.java"

if grep -Eq 'Access-Control-Allow-Origin[[:space:]]+["'"'"']?\*' \
  "$root_dir/infra/nginx/limit.conf"; then
  echo "credentialed CORS must not use a wildcard origin" >&2
  exit 1
fi

echo "CORS origin contract tests passed"
