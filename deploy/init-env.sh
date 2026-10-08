#!/usr/bin/env bash
set -euo pipefail

cd /opt/baakio
if [[ -e .env ]]; then
  echo "/opt/baakio/.env already exists; leaving it untouched" >&2
  exit 1
fi

umask 077
cat > .env <<EOF
DB_PASSWORD=$(openssl rand -hex 32)
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 32)
RABBITMQ_PASSWORD=$(openssl rand -hex 32)
OTP_HASH_SECRET=$(openssl rand -hex 48)
OCI_OBJECT_STORAGE_NAMESPACE=bmqc9sofhut1
OCI_OBJECT_STORAGE_BUCKET=baakio
FRONTEND_ORIGIN=https://app.example.invalid
API_HOSTNAME=api.example.invalid
FAST2SMS_API_KEY=
FAST2SMS_OTP_TEMPLATE_ID=
MISTRAL_API_KEY=
EOF
chmod 600 .env
echo "Created /opt/baakio/.env with generated secrets. Set real HTTPS origins and provider credentials before public use."
