#!/usr/bin/env python3
"""Read Flyway's rank from Aiven using the production Compose configuration."""

import json
import os
import subprocess
import sys
from urllib.parse import parse_qs, urlsplit


def main() -> int:
    config = json.load(sys.stdin)
    api = config["services"]["api"]
    settings = api["environment"]
    jdbc_url = settings["DB_URL"]
    if not jdbc_url.startswith("jdbc:mysql://"):
        raise ValueError("DB_URL must be a MySQL JDBC URL")

    parsed = urlsplit(jdbc_url.removeprefix("jdbc:"))
    options = parse_qs(parsed.query)
    if (
        parsed.hostname != settings["DB_HOST"]
        or parsed.port != int(settings["DB_PORT"])
        or parsed.path.lstrip("/") != settings["DB_NAME"]
        or options.get("sslMode") != ["VERIFY_IDENTITY"]
        or options.get("trustCertificateKeyStoreUrl")
        != ["file:/run/baakio/aiven-truststore.p12"]
    ):
        raise ValueError("DB_URL must match the Aiven endpoint and verified TLS truststore")

    ca_file = next(
        volume["source"]
        for volume in api["volumes"]
        if volume["target"] == "/run/baakio/aiven-ca.pem" and volume["read_only"]
    )
    truststore_file = next(
        volume["source"]
        for volume in api["volumes"]
        if volume["target"] == "/run/baakio/aiven-truststore.p12" and volume["read_only"]
    )
    if not os.path.isfile(ca_file):
        raise FileNotFoundError("Aiven CA certificate is missing")
    if not os.path.isfile(truststore_file):
        raise FileNotFoundError("Aiven PKCS12 truststore is missing")

    query = (
        "SET @q = (SELECT IF(COUNT(*) = 0, 'SELECT 0', "
        "'SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history') "
        "FROM information_schema.tables WHERE table_schema = DATABASE() "
        "AND table_name = 'flyway_schema_history'); "
        "PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;"
    )
    command = [
        "docker", "run", "--rm", "--memory=128m",
        "--mount", f"type=bind,src={ca_file},dst=/run/aiven-ca.pem,readonly",
        "-e", "MYSQL_PWD", "mysql:8.4", "mysql", "--protocol=TCP",
        "--ssl-mode=VERIFY_IDENTITY", "--ssl-ca=/run/aiven-ca.pem",
        "--host", settings["DB_HOST"], "--port", settings["DB_PORT"],
        "--user", settings["DB_USER"], "--database", settings["DB_NAME"],
        "--batch", "--skip-column-names", "--execute", query,
    ]
    environment = dict(os.environ, MYSQL_PWD=settings["DB_PASSWORD"])
    result = subprocess.run(command, env=environment, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"Aiven schema query failed: {result.stderr.strip()}")
    rank = result.stdout.strip()
    if not rank.isdecimal():
        raise ValueError("Aiven schema query returned an invalid rank")
    print(rank)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (KeyError, StopIteration, ValueError, FileNotFoundError, RuntimeError) as error:
        print(f"Schema rank unavailable: {error}", file=sys.stderr)
        raise SystemExit(1)
