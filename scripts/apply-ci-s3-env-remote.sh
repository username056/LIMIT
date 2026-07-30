#!/bin/sh
set -eu

: "${DEPLOY_HOST:?DEPLOY_HOST is required}"
: "${DEPLOY_USER:?DEPLOY_USER is required}"
: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${DEPLOY_SSH_KEY_FILE:?DEPLOY_SSH_KEY_FILE is required}"
: "${S3_MEDIA_BUCKET:?S3_MEDIA_BUCKET is required}"

aws_region=${AWS_REGION:-ap-northeast-2}
s3_endpoint=${S3_ENDPOINT:-}
s3_path_style_access=${S3_PATH_STYLE_ACCESS:-false}
s3_upload_ttl=${S3_UPLOAD_TTL:-600s}
s3_download_ttl=${S3_DOWNLOAD_TTL:-300s}
s3_public_base_url=${S3_PUBLIC_BASE_URL:-}
s3_upload_cleanup_delay_ms=${S3_UPLOAD_CLEANUP_DELAY_MS:-3600000}
s3_upload_cleanup_initial_delay_ms=${S3_UPLOAD_CLEANUP_INITIAL_DELAY_MS:-60000}
s3_ffprobe_enabled=${S3_FFPROBE_ENABLED:-false}
s3_ffprobe_executable=${S3_FFPROBE_EXECUTABLE:-ffprobe}
s3_ffprobe_timeout=${S3_FFPROBE_TIMEOUT:-15s}

printf '%s' "$DEPLOY_HOST" | grep -Eq '^[A-Za-z0-9.-]+$'
printf '%s' "$DEPLOY_USER" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]*$'
printf '%s' "$DEPLOY_PATH" | grep -Eq '^/[A-Za-z0-9._/-]+$'

for value in \
  "$aws_region" \
  "$S3_MEDIA_BUCKET" \
  "$s3_endpoint" \
  "$s3_path_style_access" \
  "$s3_upload_ttl" \
  "$s3_download_ttl" \
  "$s3_public_base_url" \
  "$s3_upload_cleanup_delay_ms" \
  "$s3_upload_cleanup_initial_delay_ms" \
  "$s3_ffprobe_enabled" \
  "$s3_ffprobe_executable" \
  "$s3_ffprobe_timeout"; do
  if printf '%s' "$value" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    echo "S3 environment values must not contain control characters" >&2
    exit 64
  fi
done

{
  printf 'AWS_REGION=%s\n' "$aws_region"
  printf 'S3_MEDIA_BUCKET=%s\n' "$S3_MEDIA_BUCKET"
  printf 'S3_ENDPOINT=%s\n' "$s3_endpoint"
  printf 'S3_PATH_STYLE_ACCESS=%s\n' "$s3_path_style_access"
  printf 'S3_UPLOAD_TTL=%s\n' "$s3_upload_ttl"
  printf 'S3_DOWNLOAD_TTL=%s\n' "$s3_download_ttl"
  printf 'S3_PUBLIC_BASE_URL=%s\n' "$s3_public_base_url"
  printf 'S3_UPLOAD_CLEANUP_DELAY_MS=%s\n' "$s3_upload_cleanup_delay_ms"
  printf 'S3_UPLOAD_CLEANUP_INITIAL_DELAY_MS=%s\n' "$s3_upload_cleanup_initial_delay_ms"
  printf 'S3_FFPROBE_ENABLED=%s\n' "$s3_ffprobe_enabled"
  printf 'S3_FFPROBE_EXECUTABLE=%s\n' "$s3_ffprobe_executable"
  printf 'S3_FFPROBE_TIMEOUT=%s\n' "$s3_ffprobe_timeout"
} | ssh -i "$DEPLOY_SSH_KEY_FILE" \
  -o BatchMode=yes \
  -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=yes \
  "$DEPLOY_USER@$DEPLOY_HOST" \
  "sh '$DEPLOY_PATH/scripts/apply-ec2-env-remote.sh' '$DEPLOY_PATH'"
