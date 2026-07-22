#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file="$root_dir/infra/.env"
secret_dir="$root_dir/infra/secrets"
image_ref=${BACKEND_BOOTSTRAP_IMAGE:-limit/backend:bootstrap}

if [[ ! -f "$root_dir/infra/compose.yml" || ! -f "$root_dir/infra/compose.prod.yml" ]]; then
  echo "compose files were not found under $root_dir/infra" >&2
  exit 66
fi

generate_secret() {
  openssl rand -hex 24
}

install -d -m 0700 "$secret_dir"
if [[ ! -f "$env_file" ]]; then
  mysql_password=$(generate_secret)
  mysql_root_password=$(generate_secret)
  mongodb_password=$(generate_secret)
  redis_password=$(generate_secret)
  qdrant_api_key=$(generate_secret)
  grafana_admin_password=$(generate_secret)
  jwt_secret=$(openssl rand -base64 48 | tr -d '\n')

  umask 077
  {
    printf 'SPRING_PROFILES_ACTIVE=prod\n'
    printf 'MYSQL_DATABASE=limit\n'
    printf 'MYSQL_USER=limit\n'
    printf 'MYSQL_PASSWORD=%s\n' "$mysql_password"
    printf 'MYSQL_ROOT_PASSWORD=%s\n' "$mysql_root_password"
    printf 'MONGODB_ROOT_USER=limit\n'
    printf 'MONGODB_ROOT_PASSWORD=%s\n' "$mongodb_password"
    printf 'MONGODB_URI=mongodb://limit:%s@mongodb:27017/limit?authSource=admin\n' "$mongodb_password"
    printf 'REDIS_PASSWORD=%s\n' "$redis_password"
    printf 'REFRESH_TOKEN_STORE=redis\n'
    printf 'JWT_SECRET=%s\n' "$jwt_secret"
    printf 'EMAIL_VERIFICATION_STORE=redis\n'
    printf 'EMAIL_VERIFICATION_DELIVERY_ENABLED=false\n'
    printf 'FRONTEND_EMAIL_VERIFICATION_URL=https://l1mit.shop/verify-email\n'
    printf 'MAIL_HOST=\n'
    printf 'MAIL_PORT=587\n'
    printf 'MAIL_USERNAME=\n'
    printf 'MAIL_PASSWORD=\n'
    printf 'MAIL_FROM=\n'
    printf 'INITIAL_ADMIN_ENABLED=false\n'
    printf 'GOOGLE_OAUTH_ENABLED=false\n'
    printf 'GOOGLE_OAUTH_CLIENT_ID=\n'
    printf 'GOOGLE_OAUTH_CLIENT_SECRET=\n'
    printf 'GOOGLE_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/google\n'
    printf 'KAKAO_OAUTH_ENABLED=false\n'
    printf 'KAKAO_OAUTH_CLIENT_ID=\n'
    printf 'KAKAO_OAUTH_CLIENT_SECRET=\n'
    printf 'KAKAO_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/kakao\n'
    printf 'NAVER_OAUTH_ENABLED=false\n'
    printf 'NAVER_OAUTH_CLIENT_ID=\n'
    printf 'NAVER_OAUTH_CLIENT_SECRET=\n'
    printf 'NAVER_OAUTH_REDIRECT_URIS=https://l1mit.shop/auth/callback/naver\n'
    printf 'QDRANT_API_KEY=%s\n' "$qdrant_api_key"
    printf 'GRAFANA_ADMIN_USER=admin\n'
    printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$grafana_admin_password"
    printf 'BACKEND_BLUE_IMAGE=%s\n' "$image_ref"
    printf 'BACKEND_GREEN_IMAGE=%s\n' "$image_ref"
    printf 'QDRANT_API_KEY_FILE=%s/qdrant-api-key\n' "$secret_dir"
    printf 'MYSQL_EXPORTER_CONFIG_FILE=%s/mysql-exporter.my.cnf\n' "$secret_dir"
  } > "$env_file"
  chmod 0600 "$env_file"

  printf '%s\n' "$qdrant_api_key" > "$secret_dir/qdrant-api-key"
  printf '[client]\nuser=disabled_exporter\npassword=%s\nhost=mysql\n' "$(generate_secret)" \
    > "$secret_dir/mysql-exporter.my.cnf"
  chmod 0600 "$secret_dir/qdrant-api-key" "$secret_dir/mysql-exporter.my.cnf"
fi

compose=(
  docker compose
  --env-file "$env_file"
  -p limit-prod
  -f "$root_dir/infra/compose.yml"
  -f "$root_dir/infra/compose.prod.yml"
)

docker build --tag "$image_ref" "$root_dir/backend"
"${compose[@]}" config --quiet
"${compose[@]}" up -d mysql mongodb redis qdrant
"${compose[@]}" up -d backend-blue

ready=false
for _ in $(seq 1 36); do
  if curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
    http://127.0.0.1:8081/actuator/health/readiness \
    | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    ready=true
    break
  fi
  sleep 5
done
if [[ "$ready" != true ]]; then
  echo "backend-blue did not become ready" >&2
  "${compose[@]}" logs --tail 120 backend-blue >&2
  exit 1
fi

"${compose[@]}" up -d prometheus loki grafana alertmanager node-exporter cadvisor alloy nginx-exporter
echo "Limit backend and observability stack are running."
