#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
env_file=${1:-"$root_dir/infra/.env"}

if [ ! -f "$env_file" ]; then
  echo "compose environment file not found: $env_file" >&2
  exit 66
fi

read_value() {
  sed -n "s/^$1=//p" "$env_file" | tail -n 1
}

upsert_value() {
  key=$1
  value=$2
  if grep -q "^${key}=" "$env_file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$env_file"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$env_file"
  fi
}

turn_username=$(read_value RTC_TURN_USERNAME)
turn_username=${turn_username:-limit-turn}
printf '%s' "$turn_username" | grep -Eq '^[A-Za-z0-9._-]+$'

turn_credential=$(read_value RTC_TURN_CREDENTIAL)
if [ -z "$turn_credential" ]; then
  turn_credential=$(openssl rand -hex 32)
fi

upsert_value RTC_TURN_URL "turn:api.l1mit.shop:3478?transport=udp"
upsert_value RTC_TURN_USERNAME "$turn_username"
upsert_value RTC_TURN_CREDENTIAL "$turn_credential"
upsert_value RTC_TURN_REALM "l1mit.shop"
chmod 0600 "$env_file"

if [ "${APPLY_RTC_FIREWALL:-false}" = "true" ]; then
  sudo -n ufw allow 3478/tcp comment 'Limit Coturn TCP'
  sudo -n ufw allow 3478/udp comment 'Limit Coturn UDP'
  sudo -n ufw allow 49160:49200/udp comment 'Limit Coturn relay UDP'
fi

echo "RTC TURN environment configured; credential value was not printed"
