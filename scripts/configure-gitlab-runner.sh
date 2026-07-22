#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "run as root" >&2
  exit 77
fi

mode=${1:-apply}
config=/etc/gitlab-runner/config.toml
backup=/etc/gitlab-runner/config.toml.before-ci-throughput
cache_dir=/var/lib/gitlab-runner/cache
resolver_dir=/etc/systemd/resolved.conf.d
resolver_config=$resolver_dir/gitlab-runner-dns.conf
resolver_backup=$resolver_dir/gitlab-runner-dns.conf.before-ci-throughput
resolver_missing=$resolver_dir/gitlab-runner-dns.conf.missing-before-ci-throughput
target_concurrent=${RUNNER_CONCURRENT:-5}
target_dns=${RUNNER_DNS:-1.1.1.1}

if [[ ! -f "$config" ]]; then
  echo "GitLab Runner config not found" >&2
  exit 66
fi

if [[ ! "$target_concurrent" =~ ^[1-6]$ ]]; then
  echo "RUNNER_CONCURRENT must be between 1 and 6" >&2
  exit 64
fi

if [[ ! "$target_dns" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
  echo "RUNNER_DNS must be an IPv4 address" >&2
  exit 64
fi

restore_resolver() {
  if [[ -f "$resolver_backup" ]]; then
    install -m 0644 "$resolver_backup" "$resolver_config"
  elif [[ -f "$resolver_missing" ]]; then
    rm -f "$resolver_config"
  else
    return
  fi
  systemctl restart systemd-resolved
}

case "$mode" in
  apply)
    if [[ ! -f "$backup" ]]; then
      install -m 0600 "$config" "$backup"
    fi

    install -d -m 0755 "$resolver_dir"
    if [[ ! -e "$resolver_backup" && ! -e "$resolver_missing" ]]; then
      if [[ -f "$resolver_config" ]]; then
        install -m 0600 "$resolver_config" "$resolver_backup"
      else
        install -m 0600 /dev/null "$resolver_missing"
      fi
    fi

    resolver_temp=$(mktemp "$resolver_dir/gitlab-runner-dns.conf.tmp.XXXXXX")
    cat > "$resolver_temp" <<EOF
[Resolve]
DNS=$target_dns 172.26.0.2
FallbackDNS=1.0.0.1
Domains=~.
EOF
    install -m 0644 "$resolver_temp" "$resolver_config"
    rm -f "$resolver_temp"
    systemctl restart systemd-resolved
    if ! resolvectl query lab.ssafy.com >/dev/null; then
      restore_resolver
      exit 1
    fi

    temp_config=$(mktemp /etc/gitlab-runner/config.toml.tmp.XXXXXX)
    trap 'rm -f "$temp_config"' EXIT
    python3 - "$config" "$temp_config" "$target_concurrent" "$target_dns" <<'PY'
from pathlib import Path
import re
import sys

source_path = Path(sys.argv[1])
target_path = Path(sys.argv[2])
concurrent = sys.argv[3]
dns = sys.argv[4]
dns_servers = [dns]
if dns != "172.26.0.2":
    dns_servers.append("172.26.0.2")
dns_config = ", ".join(f'"{server}"' for server in dns_servers)

desired = {
    "dns": f'[{dns_config}]',
    "pull_policy": '"if-not-present"',
    "volumes": '["/var/lib/gitlab-runner/cache:/cache"]',
    "memory": '"2g"',
    "memory_swap": '"3g"',
    "cpus": '"1.25"',
    "service_memory": '"2g"',
    "service_memory_swap": '"3g"',
    "service_cpus": '"1.5"',
}

lines = source_path.read_text().splitlines(keepends=True)
output = []
in_docker = False
docker_sections = 0
section_seen = set()
concurrent_seen = False
runner_sections = 0

def append_missing():
    for key, value in desired.items():
        if key not in section_seen:
            output.append(f"    {key} = {value}\n")

for line in lines:
    stripped = line.strip()

    if re.match(r"^concurrent\s*=", stripped):
        output.append(f"concurrent = {concurrent}\n")
        concurrent_seen = True
        continue

    if stripped == "[[runners]]":
        runner_sections += 1
        output.append(line)
        output.append(f"  request_concurrency = {concurrent}\n")
        continue

    if re.match(r"^request_concurrency\s*=", stripped):
        continue

    if in_docker and stripped.startswith("["):
        append_missing()
        in_docker = False

    if stripped == "[runners.docker]":
        docker_sections += 1
        section_seen = set()
        in_docker = True
        output.append(line)
        continue

    if in_docker:
        match = re.match(r"^(\s*)([A-Za-z_]+)\s*=", line)
        if match and match.group(2) in desired:
            key = match.group(2)
            output.append(f"{match.group(1)}{key} = {desired[key]}\n")
            section_seen.add(key)
            continue

    output.append(line)

if in_docker:
    append_missing()

if not concurrent_seen:
    raise SystemExit("top-level concurrent setting not found")
if runner_sections != 1:
    raise SystemExit(f"expected one runner section, found {runner_sections}")
if docker_sections != 1:
    raise SystemExit(f"expected one runners.docker section, found {docker_sections}")

target_path.write_text("".join(output))
PY
    install -d -m 0770 -o gitlab-runner -g gitlab-runner "$cache_dir"
    chown --reference="$config" "$temp_config"
    chmod --reference="$config" "$temp_config"
    mv "$temp_config" "$config"
    trap - EXIT

    if ! gitlab-runner verify; then
      install -m 0600 "$backup" "$config"
      restore_resolver
      exit 1
    fi
    ;;
  rollback)
    if [[ ! -f "$backup" ]]; then
      echo "Runner throughput backup not found" >&2
      exit 66
    fi
    install -m 0600 "$backup" "$config"
    restore_resolver
    gitlab-runner verify
    ;;
  *)
    echo "usage: $0 [apply|rollback]" >&2
    exit 64
    ;;
esac
