#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <backup.tar.gz|s3://bucket/key>" >&2
  exit 64
fi

source_ref=$1
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
drill_id="limit-restore-drill-${timestamp}-$$"
mysql_container="${drill_id}-mysql"
mongo_container="${drill_id}-mongodb"
work_dir=$(mktemp -d)
downloaded_archive=

cleanup_container() {
  local name=$1
  case "$name" in
    limit-restore-drill-*) docker rm -f "$name" >/dev/null 2>&1 || true ;;
    *) echo "refusing to remove unexpected container: $name" >&2 ;;
  esac
}

cleanup() {
  cleanup_container "$mysql_container"
  cleanup_container "$mongo_container"
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

if [[ "$source_ref" == s3://* ]]; then
  command -v aws >/dev/null 2>&1 || {
    echo "aws CLI is required to download an S3 backup" >&2
    exit 69
  }
  downloaded_archive="$work_dir/backup.tar.gz"
  aws s3 cp "$source_ref" "$downloaded_archive" --only-show-errors
  archive_path=$downloaded_archive
else
  archive_path=$(realpath "$source_ref")
fi

if [[ ! -f "$archive_path" ]]; then
  echo "backup archive not found: $archive_path" >&2
  exit 66
fi

extract_dir="$work_dir/extracted"
mkdir -p "$extract_dir"
tar -xzf "$archive_path" -C "$extract_dir"
(
  cd "$extract_dir"
  sha256sum -c SHA256SUMS
)

drill_password=$(openssl rand -hex 24)
docker run -d --name "$mysql_container" \
  -e MYSQL_ROOT_PASSWORD="$drill_password" \
  -e MYSQL_DATABASE=limit \
  mysql:8.4 >/dev/null
docker run -d --name "$mongo_container" \
  -e MONGO_INITDB_ROOT_USERNAME=restore \
  -e MONGO_INITDB_ROOT_PASSWORD="$drill_password" \
  mongo:8.0 >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$mysql_container" sh -ec \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME = '\''limit'\'';" | grep -qx limit' \
    >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec "$mysql_container" sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME = '\''limit'\'';" | grep -qx limit' \
  >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$mongo_container" mongosh --quiet \
    --username restore \
    --password "$drill_password" \
    --authenticationDatabase admin \
    --eval 'quit(db.adminCommand({ ping: 1 }).ok ? 0 : 2)' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec "$mongo_container" mongosh --quiet \
  --username restore \
  --password "$drill_password" \
  --authenticationDatabase admin \
  --eval 'quit(db.adminCommand({ ping: 1 }).ok ? 0 : 2)' >/dev/null

docker exec -i "$mysql_container" sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot limit' \
  < "$extract_dir/mysql.sql"
docker exec -i "$mongo_container" mongorestore --quiet \
  --username restore \
  --password "$drill_password" \
  --authenticationDatabase admin \
  --archive \
  --gzip \
  < "$extract_dir/mongodb.archive.gz"

mysql_table_count=$(docker exec "$mysql_container" sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''limit'\'';"')
mongo_collection_count=$(docker exec "$mongo_container" mongosh --quiet \
  --username restore \
  --password "$drill_password" \
  --authenticationDatabase admin \
  limit \
  --eval 'db.getCollectionNames().length')
expected_mysql_table_count=$(sed -n 's/^mysql_table_count=//p' "$extract_dir/MANIFEST")
expected_mongo_collection_count=$(sed -n 's/^mongodb_collection_count=//p' "$extract_dir/MANIFEST")

if ! [[ "$expected_mysql_table_count" =~ ^[0-9]+$ && "$expected_mongo_collection_count" =~ ^[0-9]+$ ]]; then
  echo "restore drill failed: backup manifest has invalid object counts" >&2
  exit 1
fi
if [[ "$mysql_table_count" != "$expected_mysql_table_count" ]]; then
  echo "restore drill failed: MySQL table count mismatch" >&2
  exit 1
fi
if [[ "$mongo_collection_count" != "$expected_mongo_collection_count" ]]; then
  echo "restore drill failed: MongoDB collection count mismatch" >&2
  exit 1
fi

echo "restore drill passed: mysql_tables=$mysql_table_count mongodb_collections=$mongo_collection_count"
