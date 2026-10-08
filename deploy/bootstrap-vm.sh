#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash bootstrap-vm.sh" >&2
  exit 1
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl python3-venv
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nSigned-By: /etc/apt/keyrings/docker.asc\n' \
  "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.sources
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
usermod -aG docker ubuntu

python3 -m venv /opt/baakio-oci-cli
/opt/baakio-oci-cli/bin/pip install --disable-pip-version-check oci-cli==3.93.0
ln -sf /opt/baakio-oci-cli/bin/oci /usr/local/bin/oci

install -d -m 0700 -o ubuntu -g ubuntu /opt/baakio
echo "Bootstrap complete. Reconnect as ubuntu to activate Docker group membership."
