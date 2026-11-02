#!/usr/bin/env bash
# Local checks are logged by systemd; forward failures to Cloud Monitoring.
set -euo pipefail

cd /opt/baakio
failures=()
if ! curl --silent --fail --max-time 5 http://127.0.0.1:8080/actuator/health \
    | grep -q '"status":"UP"'; then
  failures+=(api)
fi
running=$(docker compose --env-file runtime.env -f compose.yaml ps \
  --services --status running | wc -l | tr -d ' ')
if [[ $running -ne 3 ]]; then
  failures+=(containers)
fi
restarts=0
while IFS= read -r container; do
  [[ -n $container ]] || continue
  count=$(docker inspect --format '{{.RestartCount}}' "$container")
  restarts=$((restarts + count))
done < <(docker compose --env-file runtime.env -f compose.yaml ps -q)
previous_restarts=$(cat restart-counts 2>/dev/null || echo "$restarts")
if [[ $restarts -gt $previous_restarts ]]; then
  failures+=(restarts)
fi
printf '%s\n' "$restarts" > restart-counts
available_kb=$(awk '$1 == "MemAvailable:" {print $2}' /proc/meminfo)
if [[ $available_kb -lt 307200 ]]; then
  failures+=(memory)
fi
disk_percent=$(df -P / | awk 'NR == 2 {gsub("%", "", $5); print $5}')
if [[ $disk_percent -ge 85 ]]; then
  failures+=(disk)
fi
if [[ ${#failures[@]} -ne 0 ]]; then
  logger -t baakio-watch "Health check failed: ${failures[*]}"
  printf 'Baakio health check failed: %s\n' "${failures[*]}" >&2
  exit 1
fi
echo "Baakio healthy: ${running} containers, ${available_kb} KB available RAM, ${disk_percent}% disk used"
