#!/bin/sh
set -eu

: "${IMAGE_REPOSITORY:?IMAGE_REPOSITORY is required}"
image_tag="${IMAGE_TAG:-$(git rev-parse --verify HEAD)}"
image_ref="${IMAGE_REPOSITORY}:${image_tag}"
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
dockerfile="${DOCKERFILE:-$root_dir/backend/Dockerfile}"

if [ ! -f "$dockerfile" ]; then
  echo "dockerfile not found: $dockerfile" >&2
  exit 1
fi

if [ "${PUSH_IMAGE:-false}" = "true" ]; then
  docker buildx build \
    --push \
    --file "$dockerfile" \
    --tag "$image_ref" \
    --cache-from "type=registry,ref=${IMAGE_REPOSITORY}:buildcache" \
    --cache-to "type=registry,ref=${IMAGE_REPOSITORY}:buildcache,mode=max" \
    "$root_dir/backend"

  digest=$(
    docker buildx imagetools inspect "$image_ref" |
      awk '$1 == "Digest:" { print $2; exit }'
  )
  printf '%s' "$digest" | grep -Eq '^sha256:[a-f0-9]{64}$' || {
    echo "invalid image digest: $digest" >&2
    exit 65
  }
  deploy_image="${IMAGE_REPOSITORY}@${digest}"
else
  docker buildx build --load --file "$dockerfile" --tag "$image_ref" "$root_dir/backend"
  deploy_image="$image_ref"
fi

if [ -n "${IMAGE_ENV_FILE:-}" ]; then
  printf 'DEPLOY_IMAGE=%s\n' "$deploy_image" > "$IMAGE_ENV_FILE"
fi

printf '%s\n' "$deploy_image"
