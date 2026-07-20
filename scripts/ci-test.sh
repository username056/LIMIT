#!/bin/sh
set -eu

scope="${1:-all}"
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

run_backend() {
  cd "$root_dir/backend"
  ./gradlew test integrationTest jacocoTestReport bootJar --no-daemon
}

run_frontend() {
  cd "$root_dir/frontend"
  npm ci --no-audit --no-fund
  npm run lint
  npm run test
  npm run build
}

case "$scope" in
  backend) run_backend ;;
  frontend) run_frontend ;;
  all)
    run_backend
    run_frontend
    ;;
  *)
    echo "usage: $0 [backend|frontend|all]" >&2
    exit 64
    ;;
esac
