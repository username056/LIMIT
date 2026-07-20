#!/usr/bin/env bash
set -Eeuo pipefail

smoke_base_url="${1:-${SMOKE_BASE_URL:-}}"
if [[ ! "$smoke_base_url" =~ ^https://[^[:space:]]+$ ]]; then
  echo "production rollback requires a valid HTTPS smoke test URL" >&2
  exit 64
fi

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
state_dir="${DEPLOY_STATE_DIR:-$root_dir/infra/state}"
state_file="$state_dir/prod.active"

if [[ ! -f "$state_file" ]]; then
  echo "production active color state not found: $state_file" >&2
  exit 1
fi

active=$(<"$state_file")
case "$active" in
  blue) target="green" ;;
  green) target="blue" ;;
  *)
    echo "invalid production active color: $active" >&2
    exit 1
    ;;
esac

find_service_container() {
  local service="$1"
  local -a container_ids=()
  mapfile -t container_ids < <(
    docker ps -a \
      --filter "label=com.docker.compose.project=limit-prod" \
      --filter "label=com.docker.compose.service=$service" \
      --format '{{.ID}}'
  )

  if [[ "${#container_ids[@]}" -ne 1 || -z "${container_ids[0]}" ]]; then
    echo "expected exactly one limit-prod $service container" >&2
    return 1
  fi
  printf '%s\n' "${container_ids[0]}"
}

active_container=$(find_service_container "backend-$active")
target_container=$(find_service_container "backend-$target")

if [[ "$(docker inspect --format '{{.State.Running}}' "$active_container")" != "true" ]]; then
  echo "active backend-$active container is not running; rollback aborted" >&2
  exit 1
fi
if [[ "$(docker inspect --format '{{.State.Running}}' "$target_container")" != "false" ]]; then
  echo "rollback target backend-$target container is not stopped; rollback aborted" >&2
  exit 1
fi

previous_image=$(docker inspect --format '{{.Config.Image}}' "$target_container")
if [[ ! "$previous_image" =~ ^[^[:space:]]+@sha256:[a-f0-9]{64}$ ]]; then
  echo "rollback target does not contain an immutable image digest" >&2
  exit 1
fi

bash "$root_dir/scripts/deploy-blue-green.sh" prod "$previous_image" "$smoke_base_url"
