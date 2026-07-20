#!/usr/bin/env sh
set -eu

input="$(cat)"
sanitized="$(printf '%s' "$input" | sed 's/\.env\.example//g')"

if printf '%s' "$sanitized" | grep -Eiq '(^|[/\\])\.env([.][a-z0-9_-]+)?(["[:space:]]|$)|[.](pem|key|p12|jks)(["[:space:]]|$)'; then
  echo "차단: Secret 또는 실제 환경 파일을 읽거나 수정할 수 없습니다." >&2
  exit 2
fi

if printf '%s' "$input" | grep -Eiq 'git[[:space:]]+reset[[:space:]]+--hard|git[[:space:]]+push([^\n]*)(--force|-f([[:space:]]|$))|rm[[:space:]]+-rf|drop[[:space:]]+(database|schema)|kubectl([^\n]*)[[:space:]]delete'; then
  echo "차단: 파괴적 Git, 파일, DB 또는 인프라 명령은 승인 없이 실행할 수 없습니다." >&2
  exit 2
fi

exit 0

