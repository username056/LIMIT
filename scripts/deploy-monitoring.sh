#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file="${COMPOSE_ENV_FILE:-$root_dir/infra/.env}"
nginx_source="$root_dir/infra/nginx/limit.conf"
nginx_target="/etc/nginx/conf.d/limit.conf"
nginx_backup="$root_dir/infra/state/limit.conf.monitoring.previous"

if [[ ! -f "$env_file" ]]; then
  echo "compose environment file not found: $env_file" >&2
  exit 1
fi

compose=(
  docker compose
  --env-file "$env_file"
  -p limit-prod
  -f "$root_dir/infra/compose.yml"
  -f "$root_dir/infra/compose.prod.yml"
)
install -d -m 0755 "$root_dir/infra/state/node-exporter"
find "$root_dir/infra/monitoring" -type d -exec chmod 0755 {} +
find "$root_dir/infra/monitoring" -type f -exec chmod 0644 {} +
services=(
  prometheus loki grafana alertmanager node-exporter cadvisor alloy coturn
  mongodb-exporter redis-exporter nginx-exporter
)
reload_services=(prometheus loki grafana alertmanager alloy)

wait_until_ready() {
  local name=$1
  shift
  local attempts=${MONITORING_READY_ATTEMPTS:-12}
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if "$@" >/dev/null 2>&1; then
      echo "$name is ready"
      return 0
    fi
    sleep "${MONITORING_READY_INTERVAL_SECONDS:-5}"
  done
  echo "$name readiness check failed after $attempts attempts" >&2
  return 1
}

mysql_exporter_config=$(sed -n 's/^MYSQL_EXPORTER_CONFIG_FILE=//p' "$env_file" | tail -n 1)
mysql_exporter_config=${mysql_exporter_config:-$root_dir/infra/secrets/mysql-exporter.my.cnf}
if [[ -f "$mysql_exporter_config" ]] \
  && ! grep -Eiq '^[[:space:]]*user[[:space:]]*=[[:space:]]*disabled_exporter[[:space:]]*$' \
    "$mysql_exporter_config"; then
  services+=(mysql-exporter)
else
  echo "mysql-exporter skipped: provision its least-privilege account first" >&2
fi

monitoring_secret_gid=$(sed -n 's/^MONITORING_SECRET_GID=//p' "$env_file" | tail -n 1)
monitoring_secret_gid=${monitoring_secret_gid:-1000}
alertmanager_smtp_password=$(sed -n 's/^ALERTMANAGER_SMTP_PASSWORD_FILE=//p' "$env_file" | tail -n 1)
alertmanager_smtp_password=${alertmanager_smtp_password:-$root_dir/infra/secrets/alertmanager-smtp-password.example}
if [[ "$alertmanager_smtp_password" != /* ]]; then
  alertmanager_smtp_password="$root_dir/infra/${alertmanager_smtp_password#./}"
fi
if ! [[ "$monitoring_secret_gid" =~ ^[0-9]+$ ]]; then
  echo "MONITORING_SECRET_GID must be numeric" >&2
  exit 64
fi
if [[ ! -f "$alertmanager_smtp_password" ]]; then
  echo "Alertmanager SMTP password file not found" >&2
  exit 66
fi
chgrp "$monitoring_secret_gid" "$alertmanager_smtp_password"
chmod 0640 "$alertmanager_smtp_password"

"${compose[@]}" config --quiet

if [[ -f "$nginx_source" ]]; then
  mkdir -p "$(dirname -- "$nginx_backup")"
  if [[ -f "$nginx_target" ]]; then
    cp "$nginx_target" "$nginx_backup"
  fi
  sudo -n install -m 0644 "$nginx_source" "$nginx_target"
  if ! sudo -n nginx -t; then
    if [[ -f "$nginx_backup" ]]; then
      if ! sudo -n install -m 0644 "$nginx_backup" "$nginx_target" \
        || ! sudo -n nginx -t; then
        echo "CRITICAL: nginx configuration restore failed" >&2
        exit 1
      fi
      echo "nginx configuration rejected; previous file restored" >&2
    else
      echo "nginx configuration rejected; no previous file was available" >&2
    fi
    exit 1
  fi
  sudo -n systemctl reload nginx
fi

"${compose[@]}" up -d "${services[@]}"
"${compose[@]}" restart "${reload_services[@]}"

wait_until_ready Grafana \
  curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
    http://127.0.0.1:3000/api/health
wait_until_ready Prometheus \
  "${compose[@]}" exec -T prometheus \
    wget -qO- http://localhost:9090/-/ready
wait_until_ready Coturn \
  "${compose[@]}" exec -T prometheus \
    wget -qO- http://coturn:9641/
wait_until_ready Loki \
  "${compose[@]}" exec -T loki \
    wget -qO- http://localhost:3100/ready
wait_until_ready Alertmanager \
  "${compose[@]}" exec -T alertmanager \
    wget -qO- http://localhost:9093/-/ready

bash "$root_dir/scripts/install-backup-cron.sh"

echo "monitoring configuration deployed"
