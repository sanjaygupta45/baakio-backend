#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 /path/to/Aiven-project-CA.pem" >&2
  exit 2
fi

cd /opt/baakio
[[ -f .env ]] || { echo "Create /opt/baakio/.env first" >&2; exit 1; }
store_password="$(sed -n 's/^AIVEN_TRUSTSTORE_PASSWORD=//p' .env)"
[[ "$store_password" =~ ^[a-zA-Z0-9]{12,}$ ]] \
  || { echo "Set AIVEN_TRUSTSTORE_PASSWORD to at least 12 URL-safe characters" >&2; exit 1; }
export AIVEN_TRUSTSTORE_PASSWORD="$store_password"

install -d -m 0755 certs
install -m 0644 "$1" certs/aiven-ca.pem
temporary="$(mktemp -d certs/.aiven-truststore.XXXXXX)"
trap 'rm -rf "$temporary"' EXIT
keytool -importcert -noprompt -alias aiven-project-ca \
  -file certs/aiven-ca.pem -keystore "$temporary/aiven-truststore.p12" \
  -storetype PKCS12 -storepass:env AIVEN_TRUSTSTORE_PASSWORD
chmod 0644 "$temporary/aiven-truststore.p12"
mv -f "$temporary/aiven-truststore.p12" certs/aiven-truststore.p12
echo "Aiven CA and PKCS12 truststore are ready in /opt/baakio/certs"
