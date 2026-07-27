#!/bin/sh
set -eu

zero_sha=0000000000000000000000000000000000000000
head_ref=${2:-${CI_COMMIT_SHA:-HEAD}}
base_ref=${1:-}

if [ -z "$base_ref" ]; then
  if [ -n "${CI_MERGE_REQUEST_DIFF_BASE_SHA:-}" ] && [ "$CI_MERGE_REQUEST_DIFF_BASE_SHA" != "$zero_sha" ]; then
    base_ref=$CI_MERGE_REQUEST_DIFF_BASE_SHA
  elif [ -n "${CI_COMMIT_BEFORE_SHA:-}" ] && [ "$CI_COMMIT_BEFORE_SHA" != "$zero_sha" ]; then
    base_ref=$CI_COMMIT_BEFORE_SHA
  elif git rev-parse --verify origin/dev^{commit} >/dev/null 2>&1; then
    base_ref=origin/dev
  elif git rev-parse --verify HEAD^ >/dev/null 2>&1; then
    base_ref=HEAD^
  else
    echo "backend logging guard: no base commit is available" >&2
    exit 2
  fi
fi

if ! git rev-parse --verify "$base_ref^{commit}" >/dev/null 2>&1; then
  echo "backend logging guard: base commit is unavailable: $base_ref" >&2
  echo "fetch full history or pass an explicit base commit" >&2
  exit 2
fi
if ! git rev-parse --verify "$head_ref^{commit}" >/dev/null 2>&1; then
  echo "backend logging guard: head commit is unavailable: $head_ref" >&2
  exit 2
fi

changed_files=$(mktemp "${TMPDIR:-/tmp}/backend-logging-files.XXXXXX")
failures_file=$(mktemp "${TMPDIR:-/tmp}/backend-logging-failures.XXXXXX")
trap 'rm -f -- "$changed_files" "$failures_file"' EXIT HUP INT TERM
git diff --name-only --diff-filter=ACMR "$base_ref" "$head_ref" -- \
  backend/src/main/java > "$changed_files"

checked=0
while IFS= read -r file; do
  case "$file" in
    backend/src/main/java/com/c203/limit/domain/*/service/*.java|\
    backend/src/main/java/com/c203/limit/domain/*/bootstrap/*.java|\
    backend/src/main/java/com/c203/limit/domain/*/event/*.java)
      ;;
    *)
      continue
      ;;
  esac

  if ! source=$(git show "$head_ref:$file" 2>/dev/null); then
    echo "backend logging guard: cannot inspect $file at $head_ref" >&2
    exit 2
  fi
  if ! printf '%s\n' "$source" | grep -Eq '^[[:space:]]*@(Service|Component|Scheduled|EventListener|KafkaListener)([[:space:](]|$)'; then
    continue
  fi

  checked=$((checked + 1))
  has_logger=false
  has_operational_log=false
  if printf '%s\n' "$source" | grep -Eq \
    '^[[:space:]]*@Slf4j([[:space:](]|$)|^[[:space:]]*(private|protected|public)?[[:space:]]*((static|final)[[:space:]]+)*Logger[[:space:]]+log[[:space:]]*(=|;)'; then
    has_logger=true
  fi
  if printf '%s\n' "$source" | grep -Eq '^[[:space:]]*log\.(info|warn|error)[[:space:]]*\('; then
    has_operational_log=true
  fi

  if [ "$has_logger" != true ] || [ "$has_operational_log" != true ]; then
    printf '%s\n' "$file" >> "$failures_file"
  fi
done < "$changed_files"

if [ -s "$failures_file" ]; then
  echo "backend logging guard failed" >&2
  echo "Changed service/bootstrap/event classes must declare an SLF4J 'log' logger" >&2
  echo "and contain at least one log.info, log.warn, or log.error call." >&2
  echo "Production runs at INFO, so debug/trace-only logging does not satisfy the policy." >&2
  cat "$failures_file" >&2
  echo "See docs/runbook/backend-logging.md." >&2
  exit 1
fi

echo "backend logging guard passed ($checked observable class(es) checked)"
