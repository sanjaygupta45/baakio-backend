#!/usr/bin/env bash
set -euo pipefail

cd /opt/baakio
if [[ -e .env ]]; then
  echo "/opt/baakio/.env already exists; leaving it untouched" >&2
  exit 1
fi

umask 077
cat > .env <<EOF
DB_HOST=
DB_PORT=
DB_NAME=baakio
DB_USER=
DB_PASSWORD=
AIVEN_CA_HOST_PATH=/opt/baakio/certs/aiven-ca.pem
AIVEN_TRUSTSTORE_HOST_PATH=/opt/baakio/certs/aiven-truststore.p12
AIVEN_TRUSTSTORE_PASSWORD=$(openssl rand -hex 24)
RABBITMQ_HOST=
RABBITMQ_PORT=5671
RABBITMQ_VHOST=
RABBITMQ_USER=
RABBITMQ_PASSWORD=
OTP_HASH_SECRET=$(openssl rand -hex 48)
GCP_PROJECT_ID=
GCS_BUCKET=
FRONTEND_ORIGIN=https://app.example.invalid
API_HOSTNAME=api.example.invalid
FAST2SMS_API_KEY=
FAST2SMS_OTP_TEMPLATE_ID=
MISTRAL_API_KEY=
EOF
chmod 600 .env
echo "Created /opt/baakio/.env with an OTP secret. Set the Aiven and CloudAMQP endpoints, credentials, CA files, and GCP settings before release."
