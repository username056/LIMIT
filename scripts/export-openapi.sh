#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
output_file="${1:-backend/build/openapi/current.json}"

case "$output_file" in
  /*) ;;
  *) output_file="$root_dir/$output_file" ;;
esac

mkdir -p "$(dirname -- "$output_file")"
cd "$root_dir/backend"
./gradlew openApiExport -PopenApiOutput="$output_file" --no-daemon
