#!/bin/sh
set -eu
umask 077

deploy_path=${1:-}
printf '%s' "$deploy_path" | grep -Eq '^/[A-Za-z0-9._/-]+$' || {
  echo "invalid deploy path" >&2
  exit 64
}

infra_dir="$deploy_path/infra"
env_file="$infra_dir/.env"
compose_base="$infra_dir/compose.yml"
compose_prod="$infra_dir/compose.prod.yml"

[ -f "$env_file" ] && [ -r "$env_file" ] && [ -w "$env_file" ] || {
  echo "environment file is missing or not writable" >&2
  exit 66
}
[ -f "$compose_base" ] && [ -f "$compose_prod" ] || {
  echo "compose definition is missing" >&2
  exit 66
}
command -v docker >/dev/null 2>&1 || {
  echo "docker is unavailable" >&2
  exit 69
}
command -v flock >/dev/null 2>&1 || {
  echo "flock is unavailable" >&2
  exit 69
}

if ! updates_file=$(mktemp "${TMPDIR:-/tmp}/limit-env-updates.XXXXXX"); then
  echo "failed to create updates file" >&2
  exit 71
fi
if ! next_file=$(mktemp "$infra_dir/.env.next.XXXXXX"); then
  rm -f -- "$updates_file"
  echo "failed to create next environment file" >&2
  exit 71
fi
cleanup() {
  rm -f -- "$updates_file" "$next_file"
}
trap cleanup EXIT HUP INT TERM

cat > "$updates_file"
[ -s "$updates_file" ] || {
  echo "no environment entries received" >&2
  exit 64
}

while IFS= read -r line || [ -n "$line" ]; do
  key=${line%%=*}
  if [ "$line" = "$key" ]; then
    echo "invalid environment entry" >&2
    exit 64
  fi
  if ! printf '%s' "$key" | grep -Eq '^[A-Z][A-Z0-9_]*$'; then
    echo "invalid environment entry" >&2
    exit 64
  fi
done < "$updates_file"

lock_file="$infra_dir/.env.lock"
# Keep a stable lock inode across runs. The kernel releases fd 9 on exit;
# removing this file could let a new process lock a different inode while a waiter holds this one.
if ! : > "$lock_file"; then
  echo "failed to create environment lock file" >&2
  exit 71
fi
if ! chmod 0600 "$lock_file"; then
  echo "failed to protect environment lock file" >&2
  exit 71
fi
exec 9> "$lock_file"
flock -x 9

awk -v updates_file="$updates_file" '
  function trim(value) {
    sub(/^[[:space:]]+/, "", value)
    sub(/[[:space:]]+$/, "", value)
    return value
  }
  BEGIN {
    while ((getline line < updates_file) > 0) {
      separator = index(line, "=")
      if (separator <= 0) {
        continue
      }
      key = trim(substr(line, 1, separator - 1))
      replacement[key] = line
      if (!(key in ordered)) {
        order[++count] = key
        ordered[key] = 1
      }
    }
    close(updates_file)
  }
  {
    separator = index($0, "=")
    if (separator <= 0) {
      print
      next
    }
    key = trim(substr($0, 1, separator - 1))
    if (key in replacement) {
      if (!(key in emitted)) {
        print replacement[key]
        emitted[key] = 1
      }
      next
    }
    print
  }
  END {
    for (item_index = 1; item_index <= count; item_index++) {
      key = order[item_index]
      if (!(key in emitted)) {
        print replacement[key]
      }
    }
  }
' "$env_file" > "$next_file"

chmod 0600 "$next_file"
env_uid=$(stat -c '%u' "$env_file")
env_gid=$(stat -c '%g' "$env_file")
if [ "$(id -u)" -eq 0 ]; then
  if ! chown "$env_uid:$env_gid" "$next_file"; then
    echo "failed to preserve environment file ownership" >&2
    exit 71
  fi
fi

if ! docker compose \
  --env-file "$next_file" \
  -p limit-prod \
  -f "$compose_base" \
  -f "$compose_prod" \
  config --quiet >/dev/null 2>&1; then
  echo "docker compose config validation failed" >&2
  exit 71
fi

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file="$infra_dir/.env.backup.$timestamp"
cp -p -- "$env_file" "$backup_file"
chmod 0600 "$backup_file"
mv -f -- "$next_file" "$env_file"

applied=""
while IFS= read -r line || [ -n "$line" ]; do
  key=${line%%=*}
  case ",$applied," in
    *",$key,"*) ;;
    *)
      if [ -n "$applied" ]; then
        applied="$applied,$key"
      else
        applied=$key
      fi
      ;;
  esac
done < "$updates_file"

printf 'applied:%s\n' "$applied"
printf 'backup:%s\n' "$backup_file"
