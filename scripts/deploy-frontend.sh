#!/bin/sh
set -eu

# Git Bash rewrites arguments beginning with / before invoking aws.exe.
case "$(uname -s 2>/dev/null || true)" in
  MINGW* | MSYS*) export MSYS_NO_PATHCONV=1 ;;
esac

usage() {
  cat >&2 <<'EOF'
usage:
  deploy-frontend.sh deploy <dist-dir> <release-id> <bucket> <distribution-id>
  deploy-frontend.sh rollback <release-id> <bucket> <distribution-id>
EOF
  exit 64
}

validate_release_id() {
  release_id=$1
  printf '%s' "$release_id" | grep -Eq '^[A-Za-z0-9._-]{1,128}$' || {
    echo "release-id may contain only letters, numbers, dot, underscore, and hyphen" >&2
    exit 64
  }
}

promote_directory() {
  source_dir=$1
  bucket=$2

  test -f "$source_dir/index.html" || {
    echo "missing frontend entrypoint: $source_dir/index.html" >&2
    exit 66
  }

  if test -d "$source_dir/assets"; then
    aws s3 cp "$source_dir/assets/" "s3://$bucket/assets/" \
      --recursive \
      --only-show-errors \
      --cache-control "public,max-age=31536000,immutable"
  fi

  aws s3 cp "$source_dir/" "s3://$bucket/" \
    --recursive \
    --exclude "assets/*" \
    --exclude "index.html" \
    --exclude "releases/*" \
    --only-show-errors \
    --cache-control "public,max-age=3600,must-revalidate"

  # index.html is uploaded last so it never points at assets that are not present yet.
  aws s3 cp "$source_dir/index.html" "s3://$bucket/index.html" \
    --only-show-errors \
    --content-type "text/html; charset=utf-8" \
    --cache-control "no-cache,no-store,must-revalidate"
}

invalidate_entrypoint() {
  distribution_id=$1
  aws cloudfront create-invalidation \
    --distribution-id "$distribution_id" \
    --paths "/" "/index.html" \
    --query 'Invalidation.Id' \
    --output text
}

wait_for_invalidation() {
  distribution_id=$1
  invalidation_id=$2
  aws cloudfront wait invalidation-completed \
    --distribution-id "$distribution_id" \
    --id "$invalidation_id"
}

action=${1:-}

case "$action" in
  deploy)
    test "$#" -eq 5 || usage
    dist_dir=$2
    release_id=$3
    bucket=$4
    distribution_id=$5
    validate_release_id "$release_id"
    test -d "$dist_dir" || {
      echo "dist directory does not exist: $dist_dir" >&2
      exit 66
    }

    aws s3 sync "$dist_dir/" "s3://$bucket/releases/$release_id/" \
      --only-show-errors \
      --cache-control "private,no-cache,no-store,must-revalidate"
    promote_directory "$dist_dir" "$bucket"
    invalidation_id=$(invalidate_entrypoint "$distribution_id")
    wait_for_invalidation "$distribution_id" "$invalidation_id"
    echo "frontend release $release_id deployed; invalidation=$invalidation_id"
    ;;
  rollback)
    test "$#" -eq 4 || usage
    release_id=$2
    bucket=$3
    distribution_id=$4
    validate_release_id "$release_id"
    rollback_parent=${TMPDIR:-/tmp}
    rollback_dir=$(mktemp -d "$rollback_parent/limit-frontend-rollback.XXXXXX")
    case "$rollback_dir" in
      "$rollback_parent"/limit-frontend-rollback.*) ;;
      *) echo "unexpected rollback directory: $rollback_dir" >&2; exit 70 ;;
    esac
    cleanup() {
      rm -rf -- "$rollback_dir"
    }
    trap cleanup EXIT HUP INT TERM

    aws s3 sync "s3://$bucket/releases/$release_id/" "$rollback_dir/" --only-show-errors
    promote_directory "$rollback_dir" "$bucket"
    invalidation_id=$(invalidate_entrypoint "$distribution_id")
    wait_for_invalidation "$distribution_id" "$invalidation_id"
    echo "frontend rolled back to $release_id; invalidation=$invalidation_id"
    ;;
  *) usage ;;
esac
