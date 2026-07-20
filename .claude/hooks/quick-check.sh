#!/usr/bin/env sh
set -eu

cd "${CLAUDE_PROJECT_DIR:-.}"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  files="$(git diff --name-only; git diff --cached --name-only; git ls-files --others --exclude-standard)"
else
  files="$(find frontend backend docs -type f 2>/dev/null || true)"
fi

for file in $files; do
  [ -f "$file" ] || continue
  case "$file" in
    *.png|*.jpg|*.jpeg|*.gif|*.jar|*.class) continue ;;
  esac
  if grep -Eni '(api[_-]?key|secret|token|password)[[:space:]]*[:=][[:space:]]*["'"'][^$<{][^"'"']{7,}["'"']' "$file" >/dev/null 2>&1; then
    echo "검사 실패: $file 에 하드코딩된 Secret 의심 값이 있습니다." >&2
    exit 1
  fi
done

echo "빠른 검사 통과: 변경 파일에서 명백한 Secret 패턴을 찾지 못했습니다."

