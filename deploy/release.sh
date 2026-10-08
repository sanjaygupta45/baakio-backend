#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ @sha256:[[:xdigit:]]{64}$ ]]; then
  echo "Usage: $0 registry/repository@sha256:<64 hex characters>" >&2
  exit 2
fi

cd /opt/baakio
umask 077
mkdir -p state
exec 9>state/release.lock
flock -n 9 || { echo "Another release is running" >&2; exit 1; }

candidate="$1"
previous="$(cat state/current-image 2>/dev/null || true)"

compose() {
  BACKEND_IMAGE="$1" docker compose --env-file .env -f compose.prod.yaml "${@:2}"
}

schema_rank() {
  compose "$candidate" exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history"' \
    2>/dev/null || echo 0
}

check_release() {
  local code
  for _ in $(seq 1 100); do
    if curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health/readiness >/dev/null \
      && curl -fsS --max-time 5 http://127.0.0.1:8080/api/v1/csrf \
        | python3 -c 'import json,sys; assert json.load(sys.stdin)["token"]' \
      && code="$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/account-holder)" \
      && [[ "$code" == 401 || "$code" == 403 ]]; then
      return 0
    fi
    sleep 3
  done
  return 1
}

compose "$candidate" config --quiet
compose "$candidate" up -d mysql rabbitmq
before="$(schema_rank)"
if [[ "$before" != 0 ]]; then
  /opt/baakio/backup-db.sh "$candidate"
fi
registry_token="$(oci container-registry access-token get --auth instance_principal \
  --region ap-mumbai-1 --service bom.ocir.io \
  --query 'data.token' --raw-output)"
printf '%s' "$registry_token" | docker login bom.ocir.io --username BEARER_TOKEN --password-stdin >/dev/null
unset registry_token
docker pull "$candidate"
docker logout bom.ocir.io >/dev/null
compose "$candidate" up -d --no-deps api

if check_release; then
  if [[ -n "$previous" && "$previous" != "$candidate" ]]; then
    printf '%s\n' "$previous" > state/previous-image
  fi
  printf '%s\n' "$candidate" > state/current-image.tmp
  mv state/current-image.tmp state/current-image
  printf '%s %s schema_rank=%s\n' "$(date -u +%FT%TZ)" "$candidate" "$(schema_rank)" >> state/releases.log
  rm -f state/RECOVERY_REQUIRED
  echo "Release healthy: $candidate"
  exit 0
fi

after="$(schema_rank)"
echo "Readiness or API check failed; schema rank $before -> $after" >&2
if [[ -n "$previous" && ( "$before" == "$after" || "${ROLLBACK_DB_COMPATIBLE:-false}" == true ) ]]; then
  compose "$previous" up -d --no-deps api
  if check_release; then
    echo "Restored previous app image: $previous" >&2
    exit 1
  fi
  echo "Previous app image did not recover service" >&2
fi

compose "$candidate" stop api
printf 'Failed image: %s\nPrevious image: %s\nSchema rank: %s -> %s\n' \
  "$candidate" "$previous" "$before" "$after" > state/RECOVERY_REQUIRED
echo "Controlled recovery required; API stopped. Inspect state/RECOVERY_REQUIRED and database before retrying." >&2
exit 1
