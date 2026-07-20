#!/usr/bin/env bash
set -Eeuo pipefail

deploy_env="${1:-}"
image_ref="${2:-}"
smoke_base_url="${3:-${SMOKE_BASE_URL:-}}"

if [[ "$deploy_env" != "dev" && "$deploy_env" != "prod" ]]; then
  echo "usage: $0 <dev|prod> <image-ref>" >&2
  exit 64
fi
if [[ ! "$image_ref" =~ ^[^[:space:]]+(@sha256:[a-f0-9]{64}|:[A-Za-z0-9._-]+)$ ]]; then
  echo "invalid image reference" >&2
  exit 64
fi
if [[ "$deploy_env" == "prod" && ! "$image_ref" =~ @sha256:[a-f0-9]{64}$ ]]; then
  echo "production deployment requires an immutable sha256 digest" >&2
  exit 64
fi
if [[ ! "$smoke_base_url" =~ ^https?://[^[:space:]]+$ ]]; then
  echo "a valid smoke test base URL is required" >&2
  exit 64
fi
if [[ "$deploy_env" == "prod" && ! "$smoke_base_url" =~ ^https:// ]]; then
  echo "production smoke test URL must use HTTPS" >&2
  exit 64
fi

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
state_dir="${DEPLOY_STATE_DIR:-$root_dir/infra/state}"
state_file="$state_dir/${deploy_env}.active"
# dev와 prod 모두 동일한 운영형 Compose 계약을 사용하고 project/state만 분리한다.
overlay="$root_dir/infra/compose.prod.yml"
compose_env_file="${COMPOSE_ENV_FILE:-$root_dir/infra/.env}"
if [[ ! -f "$compose_env_file" ]]; then
  echo "compose environment file not found: $compose_env_file" >&2
  exit 1
fi
compose=(docker compose --env-file "$compose_env_file" -p "limit-${deploy_env}" -f "$root_dir/infra/compose.yml" -f "$overlay")
mkdir -p "$state_dir"

readiness_up() {
  local port="$1"
  curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
    "http://127.0.0.1:${port}/actuator/health/readiness" \
    | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

active=""
if [[ -f "$state_file" ]]; then
  active=$(<"$state_file")
fi
if [[ "$active" != "blue" && "$active" != "green" ]]; then
  if readiness_up 8081; then
    active="blue"
  elif readiness_up 8082; then
    active="green"
  else
    active="none"
  fi
fi

if [[ "$active" == "blue" ]]; then
  target="green"
  old_service="backend-blue"
elif [[ "$active" == "green" ]]; then
  target="blue"
  old_service="backend-green"
else
  target="blue"
  old_service=""
fi

target_service="backend-${target}"
target_port=8081
[[ "$target" == "green" ]] && target_port=8082

existing_blue=$("${compose[@]}" ps -q backend-blue 2>/dev/null || true)
existing_green=$("${compose[@]}" ps -q backend-green 2>/dev/null || true)
blue_image="$image_ref"
green_image="$image_ref"
[[ -n "$existing_blue" ]] && blue_image=$(docker inspect --format '{{.Config.Image}}' "$existing_blue")
[[ -n "$existing_green" ]] && green_image=$(docker inspect --format '{{.Config.Image}}' "$existing_green")
[[ "$target" == "blue" ]] && blue_image="$image_ref"
[[ "$target" == "green" ]] && green_image="$image_ref"
export BACKEND_BLUE_IMAGE="$blue_image" BACKEND_GREEN_IMAGE="$green_image"

cleanup_target() {
  "${compose[@]}" rm --stop --force "$target_service" >/dev/null 2>&1 || true
}
trap cleanup_target ERR

"${compose[@]}" up -d --no-deps "$target_service"

ready=false
for _ in $(seq 1 "${READINESS_ATTEMPTS:-30}"); do
  if readiness_up "$target_port"; then
    ready=true
    break
  fi
  sleep "${READINESS_INTERVAL_SECONDS:-5}"
done
if [[ "$ready" != "true" ]]; then
  echo "$target_service did not become ready" >&2
  exit 1
fi

proxy_kind="${PROXY_KIND:-nginx}"
proxy_config_path="${PROXY_CONFIG_PATH:-/etc/nginx/conf.d/limit-upstream.conf}"
proxy_tmp=$(mktemp)
printf 'upstream limit_backend {\n    server 127.0.0.1:%s;\n    keepalive 32;\n}\n' "$target_port" > "$proxy_tmp"

switch_proxy() {
  sudo -n install -m 0644 "$1" "$proxy_config_path"
  case "$proxy_kind" in
    nginx)
      sudo -n nginx -t
      sudo -n systemctl reload nginx
      ;;
    *)
      echo "unsupported PROXY_KIND: $proxy_kind" >&2
      return 1
      ;;
  esac
}

old_proxy=""
if sudo -n test -f "$proxy_config_path"; then
  old_proxy=$(mktemp)
  sudo -n cp "$proxy_config_path" "$old_proxy"
fi

switch_proxy "$proxy_tmp"
if ! sh "$root_dir/scripts/smoke-test.sh" "$smoke_base_url"; then
  if [[ -n "$old_proxy" ]]; then
    switch_proxy "$old_proxy"
  fi
  echo "smoke test failed; restored the previous upstream" >&2
  exit 1
fi

trap - ERR
printf '%s\n' "$target" > "$state_file"
if [[ -n "$old_service" ]]; then
  if ! "${compose[@]}" stop "$old_service"; then
    echo "new color is active, but the old color could not be stopped" >&2
    exit 1
  fi
fi
rm -f "$proxy_tmp"
[[ -n "$old_proxy" ]] && rm -f "$old_proxy"
echo "deployed $image_ref to $target"
