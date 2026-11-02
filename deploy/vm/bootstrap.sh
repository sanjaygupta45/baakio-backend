#!/usr/bin/env bash
# One-time setup on an Ubuntu 24.04 VM. Run as root after the VM exists.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo 'Run bootstrap.sh as root.' >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg docker.io docker-compose-v2 caddy python3

install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
  | gpg --dearmor --yes -o /etc/apt/keyrings/cloud.google.gpg
chmod 0644 /etc/apt/keyrings/cloud.google.gpg
echo 'deb [signed-by=/etc/apt/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main' \
  > /etc/apt/sources.list.d/google-cloud-sdk.list
apt-get update
apt-get install -y google-cloud-cli

curl -fsSLo /tmp/add-google-cloud-ops-agent-repo.sh \
  https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
bash /tmp/add-google-cloud-ops-agent-repo.sh --also-install
rm -f /tmp/add-google-cloud-ops-agent-repo.sh

install -d -m 0700 /opt/baakio
systemctl enable --now docker
systemctl enable --now caddy
echo 'Bootstrap complete. Deploy through the GitHub Actions VM workflow.'
