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
  if [ "${VERIFY_BOOT_JAR:-false}" = "true" ]; then
    ./gradlew test bootJar --no-daemon
  else
    ./gradlew test --no-daemon
  fi
}

run_backend_integration() {
  cd "$root_dir/backend"
  ./gradlew integrationTest --no-daemon
}

run_backend_integration_core() {
  cd "$root_dir/backend"
  ./gradlew integrationTest -PintegrationSuite=core --no-daemon
}

run_backend_integration_support() {
  cd "$root_dir/backend"
  ./gradlew integrationTest -PintegrationSuite=support --no-daemon
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
  sh -n scripts/build-image.sh
  bash -n scripts/deploy-blue-green.sh
  bash -n scripts/bootstrap-ec2-stack.sh
  sh -n scripts/apply-ec2-env-remote.sh
  sh -n scripts/apply-ec2-env-remote.test.sh
  sh -n scripts/apply-ci-s3-env-remote.sh
  bash -n scripts/apply-ci-s3-env-remote.test.sh
  sh -n scripts/check-backend-logging.sh
  sh -n scripts/check-backend-logging.test.sh
  bash -n scripts/cors-config.test.sh
  bash -n scripts/deploy-monitoring.sh
  sh -n scripts/configure-rtc-turn.sh
  bash -n scripts/backup-datastores.sh
  bash -n scripts/restore-backup-drill.sh
  bash -n scripts/install-backup-cron.sh
  bash -n scripts/test-alertmanager-notification.sh
  bash -n scripts/operational-readiness.test.sh
  bash -n scripts/deploy-monitoring-remote.sh
  bash -n scripts/deploy-remote.sh
  bash -n scripts/provision-ec2-entrypoints.sh
  bash -n scripts/rollback-blue-green.sh
  bash -n scripts/rollback-remote.sh
  bash -n scripts/sync-deploy-files.sh
  bash -n scripts/sync-deploy-files.test.sh
  bash -n scripts/smoke-test.sh
  grep -Fq 'ports: ["127.0.0.1:8081:8080"]' infra/compose.prod.yml
  grep -Fq 'ports: ["127.0.0.1:8082:8080"]' infra/compose.prod.yml
  grep -Fq -- '- /var/log/nginx:/var/log/nginx:ro' infra/compose.prod.yml
  grep -Fq 'local.file_match "nginx_access"' infra/monitoring/alloy/config.alloy
  grep -Fq 'loki.source.file "nginx_access"' infra/monitoring/alloy/config.alloy
  grep -Fq 'regex         = "limit-(local|prod)"' infra/monitoring/alloy/config.alloy
  grep -Fq 'tail_from_end = true' infra/monitoring/alloy/config.alloy
  grep -Fq 'log_format limit_observability' infra/nginx/limit.conf
  grep -Fq 'path=$uri' infra/nginx/limit.conf
  chat_ws_block=$(awk '
    /^[[:space:]]*location = \/ws[[:space:]]*\{/ {
      in_block = 1
    }
    in_block {
      print
      line = $0
      depth += gsub(/\{/, "{", line)
      line = $0
      depth -= gsub(/\}/, "}", line)
      if (depth == 0) {
        exit
      }
    }
  ' infra/nginx/limit.conf)
  test -n "$chat_ws_block"
  printf '%s\n' "$chat_ws_block" | grep -Fq 'proxy_pass http://limit_backend;'
  printf '%s\n' "$chat_ws_block" | grep -Fq 'proxy_http_version 1.1;'
  printf '%s\n' "$chat_ws_block" | grep -Fq 'proxy_set_header Upgrade $http_upgrade;'
  printf '%s\n' "$chat_ws_block" | grep -Fq 'proxy_set_header Connection $limit_connection_upgrade;'
  if sed -n '/^log_format limit_observability/,/;$/p' infra/nginx/limit.conf \
      | grep -Eq '\$(request_uri|args|remote_addr|http_referer)'; then
    echo "Nginx observability log format must not include query strings or personal data" >&2
    exit 1
  fi
  grep -Fq '"${compose[@]}" logs --tail "${DEPLOY_FAILURE_LOG_LINES:-200}" "$target_service"' scripts/deploy-blue-green.sh
  grep -Fq 'nginx_target="/etc/nginx/conf.d/limit.conf"' scripts/deploy-monitoring.sh
  if grep -Fq '/actuator/prometheus' infra/nginx/limit.conf; then
    echo "Prometheus actuator endpoint must not be exposed through Nginx" >&2
    exit 1
  fi
  bash scripts/sync-deploy-files.test.sh
  bash scripts/cors-config.test.sh
  bash scripts/rollback-blue-green.test.sh
  bash scripts/operational-readiness.test.sh
  sh scripts/apply-ec2-env-remote.test.sh
  bash scripts/apply-ci-s3-env-remote.test.sh
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

run_frontend_lint() {
  cd "$root_dir"
  sh -n scripts/deploy-frontend.sh
  grep -Fq 'aws s3 sync "$source_dir/assets/"' scripts/deploy-frontend.sh
  grep -Fq -- '--size-only' scripts/deploy-frontend.sh
  grep -Fq -- '--exclude "assets/*"' scripts/deploy-frontend.sh
  cd "$root_dir/frontend"
  npm ci --no-audit --no-fund
  npm run lint
}

run_frontend_test() {
  cd "$root_dir/frontend"
  npm ci --no-audit --no-fund
  npm run test
}

run_frontend_build() {
  cd "$root_dir/frontend"
  npm ci --no-audit --no-fund
  npm run build
}

case "$scope" in
  backend-compile) run_backend_compile ;;
  backend-unit) run_backend_unit ;;
  backend-integration) run_backend_integration ;;
  backend-integration-core) run_backend_integration_core ;;
  backend-integration-support) run_backend_integration_support ;;
  backend-infrastructure) run_backend_infrastructure ;;
  backend-package) run_backend_package ;;
  backend-scripts) run_backend_scripts ;;
  backend) run_backend ;;
  frontend-lint) run_frontend_lint ;;
  frontend-test) run_frontend_test ;;
  frontend-build) run_frontend_build ;;
  frontend) run_frontend ;;
  all)
    run_backend
    run_frontend
    ;;
  *)
    echo "usage: $0 [backend-compile|backend-unit|backend-integration|backend-integration-core|backend-integration-support|backend-infrastructure|backend-package|backend-scripts|backend|frontend-lint|frontend-test|frontend-build|frontend|all]" >&2
    exit 64
    ;;
esac
