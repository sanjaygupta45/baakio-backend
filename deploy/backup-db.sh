#!/usr/bin/env bash
set -euo pipefail

cd /opt/baakio
umask 077
backup_file="$(mktemp /tmp/baakio-mysql-XXXXXX.sql.gz)"
trap 'rm -f "$backup_file"' EXIT

image="${1:-$(cat state/current-image)}"
BACKEND_IMAGE="$image" docker compose --env-file .env -f compose.prod.yaml exec -T mysql \
  sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  | gzip > "$backup_file"

object_name="backups/mysql/$(date -u +%Y-%m-%dT%H-%M-%SZ).sql.gz"
bucket="$(BACKEND_IMAGE="$image" docker compose --env-file .env -f compose.prod.yaml config --format json \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["api"]["environment"]["GCS_BUCKET"])')"
[[ "$bucket" =~ ^[a-z0-9][a-z0-9._-]+$ ]] || { echo "Invalid GCS_BUCKET" >&2; exit 1; }
gcloud storage cp "$backup_file" "gs://$bucket/$object_name" --content-type=application/gzip --quiet >/dev/null
echo "Uploaded private MySQL backup: $object_name"
