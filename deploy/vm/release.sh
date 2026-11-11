#!/usr/bin/env bash
# Called by the deployment workflow after copying deploy/vm files to /opt/baakio.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo 'Run release.sh as root.' >&2
  exit 1
fi
if [[ $# -ne 1 || ! $1 =~ ^[a-f0-9]{40}$ ]]; then
  echo 'Expected a 40-character Git commit SHA.' >&2
  exit 1
fi

cd /opt/baakio
export API_IMAGE="asia-south1-docker.pkg.dev/baakio-513506/baakio/api:$1"
rm -f Caddyfile.previous
if [[ -f runtime.env ]]; then
  cp -p runtime.env runtime.env.previous
fi
rollback() {
  status=${1:-$?}
  trap - ERR
  for file in compose.yaml rabbitmq.conf; do
    if [[ -f "$file.previous" ]]; then
      mv -f "$file.previous" "$file"
    fi
  done
  if [[ -f Caddyfile.previous ]]; then
    mv -f Caddyfile.previous /etc/caddy/Caddyfile
    systemctl reload caddy || echo 'Could not reload the previous Caddy configuration.' >&2
  fi
  if [[ -f runtime.env.previous ]]; then
    mv -f runtime.env.previous runtime.env
    unset API_IMAGE
    if docker compose --env-file runtime.env -f compose.yaml up -d --remove-orphans --force-recreate; then
      echo 'Previous image, Compose files, and runtime configuration restored.' >&2
    else
      echo 'Automatic rollback failed; inspect the VM containers.' >&2
    fi
  fi
  exit "$status"
}
trap rollback ERR
python3 render-env.py runtime.env
docker compose --env-file runtime.env -f compose.yaml config --quiet

gcloud auth configure-docker asia-south1-docker.pkg.dev --quiet
docker compose --env-file runtime.env -f compose.yaml pull
docker compose --env-file runtime.env -f compose.yaml up -d --remove-orphans

ready=false
for attempt in {1..90}; do
  if curl --silent --fail --max-time 3 http://127.0.0.1:8080/actuator/health/readiness \
      | grep -q '"status":"UP"'; then
    ready=true
    break
  fi
  sleep 2
done

if [[ $ready != true ]]; then
  echo 'Readiness probe response:' >&2
  if ! curl --silent --show-error --max-time 5 http://127.0.0.1:8080/actuator/health/readiness >&2; then
    echo 'Could not retrieve the readiness probe response.' >&2
  fi
  echo >&2
  docker compose --env-file runtime.env -f compose.yaml ps >&2
  while IFS= read -r container; do
    [[ -n $container ]] || continue
    if ! docker inspect --format='{{.Name}} state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' "$container" >&2; then
      echo "Could not inspect container $container." >&2
    fi
  done < <(docker compose --env-file runtime.env -f compose.yaml ps -q)
  diagnostics=/var/log/baakio-release-failure.log
  if install -d -m 0700 /var/log/baakio \
      && docker compose --env-file runtime.env -f compose.yaml logs --no-color --tail=80 \
          api cloud-sql-proxy rabbitmq >"$diagnostics" 2>&1; then
    chmod 0600 "$diagnostics"
    echo "Bounded service logs saved on the VM at $diagnostics (root-only)." >&2
  else
    echo 'Could not save bounded service logs on the VM.' >&2
  fi
  echo 'New release failed its readiness check.' >&2
  rollback 1
fi

caddy validate --config Caddyfile
cp -p /etc/caddy/Caddyfile Caddyfile.previous
install -m 0644 Caddyfile /etc/caddy/Caddyfile
systemctl reload caddy
docker compose --env-file runtime.env -f compose.yaml ps
trap - ERR
rm -f runtime.env.previous Caddyfile.previous compose.yaml.previous rabbitmq.conf.previous
docker image prune -a --force --filter until=168h >/dev/null || true
echo "Deployed $API_IMAGE"
