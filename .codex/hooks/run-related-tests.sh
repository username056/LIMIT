#!/usr/bin/env sh
set -eu

cat >/dev/null

root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  base="HEAD"
  if git rev-parse --verify origin/dev >/dev/null 2>&1; then
    base="$(git merge-base HEAD origin/dev)"
  fi
  changed="$(
    git diff --name-only "$base" 2>/dev/null || true
    git ls-files --others --exclude-standard -- frontend backend 2>/dev/null || true
  )"
else
  changed="frontend backend"
fi

if printf '%s\n' "$changed" | grep -Eq '^frontend/'; then
  if [ -d frontend/node_modules ]; then
    (cd frontend && npm run lint && npm run test && npm run build)
  else
    echo "프론트 검증 건너뜀: frontend/node_modules가 없습니다. npm install 후 실행하세요."
  fi
fi

if printf '%s\n' "$changed" | grep -Eq '^backend/'; then
  if [ -x backend/gradlew ]; then
    (cd backend && ./gradlew test bootJar)
  elif command -v gradle >/dev/null 2>&1; then
    (cd backend && gradle test bootJar)
  else
    echo "백엔드 검증 건너뜀: Gradle Wrapper를 실행할 수 없고 전역 Gradle도 없습니다."
  fi
fi
