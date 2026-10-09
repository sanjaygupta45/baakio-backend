#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash bootstrap-vm.sh" >&2
  exit 1
fi

operator="${SUDO_USER:-}"
if [[ -z "$operator" || "$operator" == root ]]; then
  echo "Run with sudo from the VM SSH account so the deployment directory can be owned by that user" >&2
  exit 1
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl gnupg python3
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nSigned-By: /etc/apt/keyrings/docker.asc\n' \
  "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.sources
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
usermod -aG docker "$operator"

curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
  | gpg --dearmor --yes -o /usr/share/keyrings/cloud.google.gpg
printf 'deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main\n' \
  > /etc/apt/sources.list.d/google-cloud-sdk.list
apt-get update
DEBIAN_FRONTEND=noninteractive CLOUDSDK_SKIP_PY_COMPILATION=1 apt-get install -y google-cloud-cli

install -d -m 0700 -o "$operator" -g "$(id -gn "$operator")" /opt/baakio
echo "Bootstrap complete. Reconnect as $operator to activate Docker group membership."
