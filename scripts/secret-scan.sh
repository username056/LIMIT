#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if git ls-files --cached --others --exclude-standard \
  | grep -E '(^|/)\.env$|\.(pem|key|p12|jks)$' >/dev/null; then
  echo "secret-like file is tracked or eligible to be committed" >&2
  exit 1
fi

if command -v gitleaks >/dev/null 2>&1; then
  zero_sha=0000000000000000000000000000000000000000
  scan_base=
  if [ -n "${CI_MERGE_REQUEST_DIFF_BASE_SHA:-}" ]; then
    scan_base=$CI_MERGE_REQUEST_DIFF_BASE_SHA
  elif [ -n "${CI_COMMIT_BEFORE_SHA:-}" ] && [ "$CI_COMMIT_BEFORE_SHA" != "$zero_sha" ]; then
    scan_base=$CI_COMMIT_BEFORE_SHA
  fi

  if [ -n "$scan_base" ] \
    && [ -n "${CI_COMMIT_SHA:-}" ] \
    && git cat-file -e "${scan_base}^{commit}" 2>/dev/null; then
    # 이미 이전 pipeline에서 검증한 전체 이력을 매번 다시 읽지 않고 이번 변경
    # commit 범위만 검사한다. 로컬·신규 브랜치 fallback은 전체 이력을 유지한다.
    gitleaks git --redact --no-banner --log-opts="${scan_base}..${CI_COMMIT_SHA}"
  else
    gitleaks git --redact --no-banner
  fi
else
  echo "gitleaks is not installed; filename guard only" >&2
fi
