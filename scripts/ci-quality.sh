#!/bin/sh
set -eu

: "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
: "${SONAR_TOKEN:?SONAR_TOKEN is required}"

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

sonar-scanner \
  -Dsonar.host.url="$SONAR_HOST_URL" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.qualitygate.wait=true
