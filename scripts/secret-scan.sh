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
  gitleaks git --redact --no-banner
else
  echo "gitleaks is not installed; filename guard only" >&2
fi
