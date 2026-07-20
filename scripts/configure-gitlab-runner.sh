#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "run as root" >&2
  exit 77
fi

mode=${1:-apply}
config=/etc/gitlab-runner/config.toml
backup=/etc/gitlab-runner/config.toml.before-resource-limits

if [[ ! -f "$config" ]]; then
  echo "GitLab Runner config not found" >&2
  exit 66
fi

case "$mode" in
  apply)
    if grep -q '^[[:space:]]*memory = "3g"$' "$config"; then
      gitlab-runner verify
      exit 0
    fi

    if [[ ! -f "$backup" ]]; then
      install -m 0600 "$config" "$backup"
    fi

    temp_config=$(mktemp /etc/gitlab-runner/config.toml.tmp.XXXXXX)
    trap 'rm -f "$temp_config"' EXIT
    awk '
      { print }
      /^[[:space:]]*privileged = true$/ {
        print "    memory = \"3g\""
        print "    memory_swap = \"4g\""
        print "    cpus = \"1.0\""
        print "    service_memory = \"3g\""
        print "    service_memory_swap = \"4g\""
        print "    service_cpus = \"1.0\""
      }
    ' "$config" > "$temp_config"
    chown --reference="$config" "$temp_config"
    chmod --reference="$config" "$temp_config"
    mv "$temp_config" "$config"
    trap - EXIT

    if ! gitlab-runner verify; then
      install -m 0600 "$backup" "$config"
      exit 1
    fi
    ;;
  rollback)
    if [[ ! -f "$backup" ]]; then
      echo "Runner backup not found" >&2
      exit 66
    fi
    install -m 0600 "$backup" "$config"
    gitlab-runner verify
    ;;
  *)
    echo "usage: $0 [apply|rollback]" >&2
    exit 64
    ;;
esac
