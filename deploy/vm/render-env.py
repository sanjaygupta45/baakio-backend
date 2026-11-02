#!/usr/bin/env python3
"""Read VM runtime secrets without putting their values in shell arguments or logs."""

import os
from pathlib import Path
import re
import subprocess
import sys

PROJECT = "baakio-513506"
SECRETS = {
    "DB_PASSWORD": "baakio-db-password",
    "RABBITMQ_PASSWORD": "baakio-rabbit-password",
    "OTP_HASH_SECRET": "baakio-otp-hash-secret",
    "MISTRAL_API_KEY": "baakio-mistral-api-key",
    "SPRING_MAIL_PASSWORD": "baakio-resend-smtp-key",
    "GOOGLE_CLIENT_ID": "baakio-google-client-id",
}

DEFAULTS = {
    "DB_USER": "baakio",
    "CLOUD_SQL_CONNECTION_NAME": "baakio-513506:asia-south1:baakio-mysql",
    "RABBITMQ_USER": "baakio",
    "GCP_PROJECT_ID": PROJECT,
    "GCS_BUCKET": "baakio-513506-documents",
    "FRONTEND_ORIGIN": "https://baakio.shop",
    "FRONTEND_ADDITIONAL_ORIGIN": "https://www.baakio.shop",
    "EMAIL_AUTH_ENABLED": "true",
    "EMAIL_MAX_REQUESTS_PER_DAY": "80",
    "EMAIL_NOTIFICATION_PROVIDER": "smtp",
    "LEGACY_PHONE_AUTH_ENABLED": "false",
    "SPRING_MAIL_HOST": "smtp.resend.com",
    "SPRING_MAIL_PORT": "587",
    "SPRING_MAIL_USERNAME": "resend",
    "NOTIFICATION_EMAIL_FROM": "login@auth.baakio.shop",
}


def secret(name: str) -> str:
    completed = subprocess.run(
        ["gcloud", "secrets", "versions", "access", "latest", "--secret", name,
         "--project", PROJECT],
        check=True, capture_output=True, text=True,
    )
    return completed.stdout.rstrip("\n")


def quote(value: str) -> str:
    # Compose interpolates dollar signs even when an env file looks quoted.
    # Provider tokens and generated credentials use only these plain characters.
    if not re.fullmatch(r"[A-Za-z0-9._/@:-]+", value):
        raise ValueError("A required setting is empty or contains unsupported characters")
    return value


def main() -> None:
    target = Path(sys.argv[1])
    values = dict(DEFAULTS)
    for key, name in SECRETS.items():
        values[key] = secret(name)
    values["API_IMAGE"] = os.environ["API_IMAGE"]
    content = "".join(f"{key}={quote(value)}\n" for key, value in values.items())
    temporary = target.with_suffix(".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as output:
        output.write(content)
    os.replace(temporary, target)
    os.chmod(target, 0o600)


if __name__ == "__main__":
    main()
