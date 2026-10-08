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
oci os object put --auth instance_principal --region ap-mumbai-1 \
  --namespace bmqc9sofhut1 --bucket-name baakio --name "$object_name" \
  --file "$backup_file" --content-type application/gzip --force >/dev/null
echo "Uploaded private MySQL backup: $object_name"
