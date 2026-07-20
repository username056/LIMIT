#!/usr/bin/env sh
set -eu

root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root"

files="$(
  git diff --name-only 2>/dev/null || true
  git diff --cached --name-only 2>/dev/null || true
  git ls-files --others --exclude-standard 2>/dev/null || true
)"

for file in $files; do
  [ -f "$file" ] || continue
  case "$file" in
    *.png|*.jpg|*.jpeg|*.gif|*.jar|*.class) continue ;;
  esac
  if grep -Eni '(api[_-]?key|secret|token|password)[[:space:]]*[:=][[:space:]]*["'"'][^$<{][^"'"']{7,}["'"']' "$file" >/dev/null 2>&1; then
    echo "Secret-like value found in $file" >&2
    exit 2
  fi
done

exit 0
