#!/bin/sh
set -eu

: "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
: "${SONAR_TOKEN:?SONAR_TOKEN is required}"

organization="${SONAR_ORGANIZATION:-sungw0o}"
project_key="${SONAR_PROJECT_KEY:-limit}"
gate_name="${SONAR_QUALITY_GATE_NAME:-Limit balanced}"

api_get() {
  endpoint="$1"
  shift
  curl --fail --silent --show-error --get \
    --header "Authorization: Bearer $SONAR_TOKEN" \
    "$SONAR_HOST_URL/api/$endpoint" "$@"
}

api_post() {
  endpoint="$1"
  shift
  curl --fail --silent --show-error --request POST \
    --header "Authorization: Bearer $SONAR_TOKEN" \
    "$SONAR_HOST_URL/api/$endpoint" "$@"
}

if ! gate_json=$(api_get qualitygates/show \
  --data-urlencode "organization=$organization" \
  --data-urlencode "name=$gate_name"); then
  current_gate_json=$(api_get qualitygates/get_by_project \
    --data-urlencode "organization=$organization" \
    --data-urlencode "project=$project_key")
  source_gate_id=$(printf '%s' "$current_gate_json" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2)
  test -n "$source_gate_id"

  api_post qualitygates/copy \
    --data-urlencode "organization=$organization" \
    --data-urlencode "id=$source_gate_id" \
    --data-urlencode "name=$gate_name" > /dev/null

  gate_json=$(api_get qualitygates/show \
    --data-urlencode "organization=$organization" \
    --data-urlencode "name=$gate_name")
fi

gate_id=$(printf '%s' "$gate_json" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2)
coverage_condition=$(printf '%s' "$gate_json" \
  | tr '{' '\n' \
  | grep '"metric":"new_coverage"' \
  | grep -o '"id":[0-9]*' \
  | head -n 1 \
  | cut -d: -f2)
test -n "$gate_id"
test -n "$coverage_condition"

api_post qualitygates/update_condition \
  --data-urlencode "organization=$organization" \
  --data-urlencode "id=$coverage_condition" \
  --data-urlencode "metric=new_coverage" \
  --data-urlencode "op=LT" \
  --data-urlencode "error=65" > /dev/null

api_post qualitygates/select \
  --data-urlencode "organization=$organization" \
  --data-urlencode "gateId=$gate_id" \
  --data-urlencode "projectKey=$project_key" > /dev/null

echo "Sonar Quality Gate configured: new-code coverage >= 65%."
