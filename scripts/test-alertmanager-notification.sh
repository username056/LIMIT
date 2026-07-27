#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
env_file=${COMPOSE_ENV_FILE:-"$root_dir/infra/.env"}
alert_name="NotificationDeliveryTest-$(date -u +%Y%m%dT%H%M%SZ)"
starts_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
ends_at=$(date -u -d '+10 minutes' +%Y-%m-%dT%H:%M:%SZ)

if [[ ! -f "$env_file" ]]; then
  echo "compose environment file not found: $env_file" >&2
  exit 66
fi

compose=(
  docker compose
  --env-file "$env_file"
  -p limit-prod
  -f "$root_dir/infra/compose.yml"
  -f "$root_dir/infra/compose.prod.yml"
)

email_total() {
  "${compose[@]}" exec -T alertmanager wget -qO- http://localhost:9093/metrics \
    | awk '/^alertmanager_notifications_total\{integration="email"\}/ { print $2 }'
}

email_failures() {
  "${compose[@]}" exec -T alertmanager wget -qO- http://localhost:9093/metrics \
    | awk '/^alertmanager_notifications_failed_total\{integration="email"\}/ { total += $2 } END { print total + 0 }'
}

before_total=$(email_total)
before_failures=$(email_failures)
payload=$(printf '[{"labels":{"alertname":"%s","severity":"warning","service":"monitoring"},"annotations":{"summary":"Alertmanager delivery verification"},"startsAt":"%s","endsAt":"%s"}]' \
  "$alert_name" "$starts_at" "$ends_at")

printf '%s' "$payload" \
  | "${compose[@]}" exec -T alertmanager wget -qO- \
      --header='Content-Type: application/json' \
      --post-file=- \
      http://localhost:9093/api/v2/alerts

sleep "${ALERT_DELIVERY_WAIT_SECONDS:-40}"
after_total=$(email_total)
failed_total=$(email_failures)

if ! awk "BEGIN { exit !($after_total > $before_total) }"; then
  echo "email notification counter did not increase" >&2
  exit 1
fi
if ! awk "BEGIN { exit !($failed_total == $before_failures) }"; then
  echo "Alertmanager email delivery failure counter increased: before=$before_failures after=$failed_total" >&2
  exit 1
fi

echo "Alertmanager email delivery succeeded: alert=$alert_name"
