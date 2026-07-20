#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
case "$test_dir" in
  */tmp.*|*/Temp/tmp.*) ;;
  *) echo "unexpected test directory: $test_dir" >&2; exit 70 ;;
esac
trap 'rm -rf -- "$test_dir"' EXIT

project_dir="$test_dir/project"
fake_bin="$test_dir/bin"
mkdir -p "$project_dir/scripts" "$project_dir/infra/state" "$fake_bin"
cp "$root_dir/scripts/rollback-blue-green.sh" "$project_dir/scripts/rollback-blue-green.sh"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -eu

if [[ "$1" == "ps" ]]; then
  service=""
  for argument in "$@"; do
    case "$argument" in
      label=com.docker.compose.service=*) service="${argument##*=}" ;;
    esac
  done
  if [[ "${DOCKER_SCENARIO:-success}" == "missing-target" && "$service" == "backend-green" ]]; then
    exit 0
  fi
  [[ "$service" == "backend-blue" ]] && printf '%s\n' active-container
  [[ "$service" == "backend-green" ]] && printf '%s\n' target-container
  exit 0
fi

if [[ "$1" == "inspect" ]]; then
  format="$3"
  container="$4"
  case "$format:$container" in
    '{{.State.Running}}':active-container) printf '%s\n' true ;;
    '{{.State.Running}}':target-container)
      [[ "${DOCKER_SCENARIO:-success}" == "target-running" ]] && printf '%s\n' true || printf '%s\n' false
      ;;
    '{{.Config.Image}}':target-container)
      if [[ "${DOCKER_SCENARIO:-success}" == "invalid-digest" ]]; then
        printf '%s\n' registry.example/limit/backend:latest
      else
        printf 'registry.example/limit/backend@sha256:%064d\n' 0
      fi
      ;;
    *) exit 1 ;;
  esac
  exit 0
fi

exit 1
EOF
chmod +x "$fake_bin/docker"

cat > "$project_dir/scripts/deploy-blue-green.sh" <<'EOF'
#!/usr/bin/env bash
set -eu
printf '%s|%s|%s\n' "$1" "$2" "$3" > "$CALL_LOG"
EOF
chmod +x "$project_dir/scripts/deploy-blue-green.sh"

run_rollback() {
  PATH="$fake_bin:$PATH" \
  DEPLOY_STATE_DIR="$project_dir/infra/state" \
  CALL_LOG="$test_dir/call.log" \
  DOCKER_SCENARIO="${1:-success}" \
    bash "$project_dir/scripts/rollback-blue-green.sh" https://api.example.com
}

printf '%s\n' blue > "$project_dir/infra/state/prod.active"
run_rollback success
expected="prod|registry.example/limit/backend@sha256:$(printf '%064d' 0)|https://api.example.com"
[[ "$(<"$test_dir/call.log")" == "$expected" ]]

for scenario in missing-target target-running invalid-digest; do
  rm -f "$test_dir/call.log"
  if run_rollback "$scenario" >/dev/null 2>&1; then
    echo "rollback unexpectedly succeeded for $scenario" >&2
    exit 1
  fi
  [[ ! -e "$test_dir/call.log" ]]
done

printf '%s\n' "rollback-blue-green tests passed"
