#!/usr/bin/env sh
set -eu

input="$(cat)"
sanitized="$(printf '%s' "$input" | sed 's/\.env\.example//g')"

if printf '%s' "$sanitized" | grep -Eiq '(^|[/\\])\.env([.][a-z0-9_-]+)?(["[:space:]]|$)|[.](pem|key|p12|jks)(["[:space:]]|$)'; then
  echo "Blocked: Secret or real environment files cannot be read or modified." >&2
  exit 2
fi

if printf '%s' "$input" | grep -Eiq 'git[[:space:]]+reset[[:space:]]+--hard|git[[:space:]]+push([^\n]*)(--force|-f([[:space:]]|$))|rm[[:space:]]+-rf|drop[[:space:]]+(database|schema)|kubectl([^\n]*)[[:space:]]delete'; then
  echo "Blocked: destructive Git, file, database, or infrastructure command." >&2
  exit 2
fi

exit 0

