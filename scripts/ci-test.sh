#!/bin/sh
set -eu

scope="${1:-all}"
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

run_backend_compile() {
  cd "$root_dir/backend"
  ./gradlew classes --no-daemon
}

run_backend_unit() {
  cd "$root_dir/backend"
  ./gradlew test --no-daemon
}

run_backend_integration() {
  cd "$root_dir/backend"
  ./gradlew integrationTest --no-daemon
}

run_backend_infrastructure() {
  cd "$root_dir/backend"
  ./gradlew infrastructureTest --no-daemon
}

run_backend_package() {
  cd "$root_dir/backend"
  ./gradlew bootJar -x test -x integrationTest --no-daemon
}

run_backend_scripts() {
  cd "$root_dir"
  bash -n scripts/deploy-blue-green.sh
  bash -n scripts/deploy-remote.sh
  bash -n scripts/rollback-blue-green.sh
  bash -n scripts/rollback-remote.sh
  bash -n scripts/sync-deploy-files.sh
  bash -n scripts/smoke-test.sh
  bash scripts/rollback-blue-green.test.sh
}

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
  backend-compile) run_backend_compile ;;
  backend-unit) run_backend_unit ;;
  backend-integration) run_backend_integration ;;
  backend-infrastructure) run_backend_infrastructure ;;
  backend-package) run_backend_package ;;
  backend-scripts) run_backend_scripts ;;
  backend) run_backend ;;
  frontend) run_frontend ;;
  all)
    run_backend
    run_frontend
    ;;
  *)
    echo "usage: $0 [backend-compile|backend-unit|backend-integration|backend-infrastructure|backend-package|backend-scripts|backend|frontend|all]" >&2
    exit 64
    ;;
esac
