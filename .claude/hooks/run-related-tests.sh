#!/usr/bin/env sh
set -eu

cd "${CLAUDE_PROJECT_DIR:-.}"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  changed="$(git diff --name-only HEAD 2>/dev/null || true)"
else
  changed="frontend backend"
fi

if printf '%s' "$changed" | grep -q '^frontend/\|frontend'; then
  if [ -d frontend/node_modules ]; then
    (cd frontend && npm run lint && npm run test && npm run build)
  else
    echo "프론트 검증 건너뜀: frontend/node_modules가 없습니다. npm install 후 실행하세요."
  fi
fi

if printf '%s' "$changed" | grep -q '^backend/\|backend'; then
  if command -v gradle >/dev/null 2>&1; then
    (cd backend && gradle test bootJar)
  else
    echo "백엔드 검증 건너뜀: Gradle 8.14 이상 또는 9가 필요합니다."
  fi
fi

