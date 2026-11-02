#!/usr/bin/env bash
# One-time Google Cloud setup. Requires an account with project and IAM admin access.
# Review the current pricing estimate and billing account before running.
set -euo pipefail

project=baakio-513506
region=asia-south1
zone=asia-south1-a
repo=sanjaygupta45/Baakio-be
vm=baakio-api-vm
vm_sa="baakio-vm@$project.iam.gserviceaccount.com"
deploy_sa="baakio-github-deploy@$project.iam.gserviceaccount.com"
operator=$(gcloud config get-value account)

gcloud projects describe "$project" --format='value(projectId)' >/dev/null
if [[ $(gcloud billing projects describe "$project" --format='value(billingEnabled)' | tr '[:upper:]' '[:lower:]') != true ]]; then
  echo "Billing is not enabled for $project." >&2
  exit 1
fi

gcloud services enable --project "$project" \
  compute.googleapis.com artifactregistry.googleapis.com storage.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com iam.googleapis.com iamcredentials.googleapis.com \
  iap.googleapis.com cloudresourcemanager.googleapis.com monitoring.googleapis.com \
  logging.googleapis.com oslogin.googleapis.com

if ! gcloud artifacts repositories describe baakio --project "$project" --location "$region" >/dev/null 2>&1; then
  gcloud artifacts repositories create baakio --project "$project" --location "$region" \
    --repository-format=docker --description='Baakio backend images'
fi

bucket="$project-documents"
if ! gcloud storage buckets describe "gs://$bucket" --project "$project" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://$bucket" --project "$project" \
    --location "$region" --uniform-bucket-level-access --public-access-prevention
fi
gcloud storage buckets update "gs://$bucket" --public-access-prevention

for account in baakio-vm baakio-github-deploy; do
  if ! gcloud iam service-accounts describe "$account@$project.iam.gserviceaccount.com" \
      --project "$project" >/dev/null 2>&1; then
    gcloud iam service-accounts create "$account" --project "$project"
  fi
done

gcloud storage buckets add-iam-policy-binding "gs://$project-documents" \
  --member="serviceAccount:$vm_sa" --role=roles/storage.objectAdmin >/dev/null
gcloud artifacts repositories add-iam-policy-binding baakio --project "$project" \
  --location "$region" --member="serviceAccount:$vm_sa" \
  --role=roles/artifactregistry.reader >/dev/null
for attempt in {1..12}; do
  if gcloud artifacts repositories add-iam-policy-binding baakio --project "$project" \
      --location "$region" --member="serviceAccount:$deploy_sa" \
      --role=roles/artifactregistry.writer >/dev/null; then
    break
  fi
  if [[ $attempt -eq 12 ]]; then
    echo 'Deployment service account IAM grant did not become available.' >&2
    exit 1
  fi
  sleep 5
done
for role in roles/logging.logWriter roles/monitoring.metricWriter; do
  gcloud projects add-iam-policy-binding "$project" \
    --member="serviceAccount:$vm_sa" --role="$role" >/dev/null
done
gcloud projects add-iam-policy-binding "$project" \
  --member="serviceAccount:$vm_sa" --role=roles/cloudsql.client >/dev/null

for name in baakio-db-password baakio-db-root-password baakio-rabbit-password \
  baakio-otp-hash-secret baakio-mistral-api-key baakio-resend-smtp-key \
  baakio-google-client-id; do
  if ! gcloud secrets describe "$name" --project "$project" >/dev/null 2>&1; then
    gcloud secrets create "$name" --project "$project" --replication-policy=automatic
  fi
  if [[ $name != baakio-db-root-password ]]; then
    gcloud secrets add-iam-policy-binding "$name" --project "$project" \
      --member="serviceAccount:$vm_sa" --role=roles/secretmanager.secretAccessor >/dev/null
  fi
done

for name in baakio-db-password baakio-db-root-password baakio-rabbit-password \
  baakio-otp-hash-secret; do
  if ! gcloud secrets versions list "$name" --project "$project" \
      --filter='state=enabled' --format='value(name)' | grep -q .; then
    openssl rand -hex 32 | gcloud secrets versions add "$name" \
      --project "$project" --data-file=- >/dev/null
  fi
done

sql_instance=baakio-mysql
if ! gcloud sql instances describe "$sql_instance" --project "$project" >/dev/null 2>&1; then
  gcloud sql instances create "$sql_instance" --project "$project" \
    --region "$region" --database-version=MYSQL_8_4 --edition=enterprise \
    --tier=db-f1-micro --availability-type=ZONAL --storage-type=SSD \
    --storage-size=10 --storage-auto-increase --storage-auto-increase-limit=20 \
    --backup-start-time=02:30 --retained-backups-count=28 \
    --no-enable-point-in-time-recovery --deletion-protection
fi
if ! gcloud sql databases describe baakio --instance "$sql_instance" \
    --project "$project" >/dev/null 2>&1; then
  gcloud sql databases create baakio --instance "$sql_instance" --project "$project"
fi

# Use the Cloud SQL Admin API so passwords never appear in shell arguments or logs.
BAAKIO_PROJECT="$project" BAAKIO_SQL_INSTANCE="$sql_instance" \
  python3 - <<'PY'
import json
import os
import subprocess
from urllib.request import Request, urlopen

project = os.environ["BAAKIO_PROJECT"]
instance = os.environ["BAAKIO_SQL_INSTANCE"]
base = f"https://sqladmin.googleapis.com/sql/v1beta4/projects/{project}/instances/{instance}/users"
token = subprocess.check_output(["gcloud", "auth", "print-access-token"], text=True).strip()

def password(secret):
    return subprocess.check_output(
        ["gcloud", "secrets", "versions", "access", "latest", "--secret", secret,
         "--project", project], text=True).strip()

def update(method, url, body):
    request = Request(url, data=json.dumps(body).encode(), method=method,
                      headers={"Authorization": f"Bearer {token}",
                               "Content-Type": "application/json"})
    with urlopen(request, timeout=30) as response:
        operation = json.load(response)["name"]
    subprocess.run(["gcloud", "sql", "operations", "wait", operation,
                    "--project", project, "--timeout=600", "--quiet"], check=True)

update("PUT", base + "?name=root&host=%25", {"password": password("baakio-db-root-password")})

users = subprocess.check_output(
    ["gcloud", "sql", "users", "list", "--instance", instance,
     "--project", project, "--format=value(name)"], text=True).splitlines()
if "baakio" not in users:
    update("POST", base, {"name": "baakio", "host": "%",
                           "password": password("baakio-db-password")})
PY

if ! gcloud compute networks describe baakio-vpc --project "$project" >/dev/null 2>&1; then
  gcloud compute networks create baakio-vpc --project "$project" --subnet-mode=custom
fi
if ! gcloud compute networks subnets describe baakio-subnet --project "$project" \
    --region "$region" >/dev/null 2>&1; then
  gcloud compute networks subnets create baakio-subnet --project "$project" \
    --network=baakio-vpc --region "$region" --range=10.20.0.0/24
fi
if ! gcloud compute firewall-rules describe baakio-web --project "$project" >/dev/null 2>&1; then
  gcloud compute firewall-rules create baakio-web --project "$project" \
    --network=baakio-vpc --direction=INGRESS --action=ALLOW \
    --rules=tcp:80,tcp:443 --source-ranges=0.0.0.0/0 --target-tags=baakio-api
fi
if ! gcloud compute firewall-rules describe baakio-iap-ssh --project "$project" >/dev/null 2>&1; then
  gcloud compute firewall-rules create baakio-iap-ssh --project "$project" \
    --network=baakio-vpc --direction=INGRESS --action=ALLOW \
    --rules=tcp:22 --source-ranges=35.235.240.0/20 --target-tags=baakio-api
fi
if ! gcloud compute addresses describe baakio-api-ip --project "$project" \
    --region "$region" >/dev/null 2>&1; then
  gcloud compute addresses create baakio-api-ip --project "$project" --region "$region"
fi
if ! gcloud compute instances describe "$vm" --project "$project" --zone "$zone" >/dev/null 2>&1; then
  gcloud compute instances create "$vm" --project "$project" --zone "$zone" \
    --machine-type=e2-medium --image-family=ubuntu-2404-lts-amd64 \
    --image-project=ubuntu-os-cloud --boot-disk-size=20GB \
    --boot-disk-type=pd-balanced --network=baakio-vpc --subnet=baakio-subnet \
    --address=baakio-api-ip --service-account="$vm_sa" --scopes=cloud-platform \
    --metadata=enable-oslogin=TRUE --tags=baakio-api
fi

if ! gcloud iam workload-identity-pools describe baakio-github --project "$project" \
    --location=global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create baakio-github --project "$project" \
    --location=global --display-name='Baakio GitHub Actions'
fi
if ! gcloud iam workload-identity-pools providers describe backend-main \
    --project "$project" --location=global --workload-identity-pool=baakio-github \
    >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc backend-main \
    --project "$project" --location=global --workload-identity-pool=baakio-github \
    --issuer-uri=https://token.actions.githubusercontent.com \
    --attribute-mapping='google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
    --attribute-condition="assertion.repository=='$repo' && assertion.ref=='refs/heads/main'"
fi
project_number=$(gcloud projects describe "$project" --format='value(projectNumber)')
provider="projects/$project_number/locations/global/workloadIdentityPools/baakio-github/providers/backend-main"
principal="principalSet://iam.googleapis.com/projects/$project_number/locations/global/workloadIdentityPools/baakio-github/attribute.repository/$repo"
gcloud iam service-accounts add-iam-policy-binding "$deploy_sa" --project "$project" \
  --member="$principal" --role=roles/iam.workloadIdentityUser >/dev/null
for role in roles/compute.viewer roles/compute.osAdminLogin roles/iap.tunnelResourceAccessor; do
  gcloud projects add-iam-policy-binding "$project" \
    --member="serviceAccount:$deploy_sa" --role="$role" >/dev/null
done
gcloud iam service-accounts add-iam-policy-binding "$vm_sa" --project "$project" \
  --member="serviceAccount:$deploy_sa" --role=roles/iam.serviceAccountUser >/dev/null
for role in roles/compute.osAdminLogin roles/iap.tunnelResourceAccessor; do
  gcloud projects add-iam-policy-binding "$project" \
    --member="user:$operator" --role="$role" >/dev/null
done
gcloud iam service-accounts add-iam-policy-binding "$vm_sa" --project "$project" \
  --member="user:$operator" --role=roles/iam.serviceAccountUser >/dev/null

address=$(gcloud compute addresses describe baakio-api-ip --project "$project" \
  --region "$region" --format='value(address)')
cat <<EOF
Provisioning complete.
Reserved IP: $address
GitHub variable GCP_WIF_PROVIDER: $provider
GitHub variable GCP_DEPLOY_SERVICE_ACCOUNT: $deploy_sa
Cloud SQL instance: $sql_instance (db-f1-micro, MySQL 8.4, 28 daily backups)
Next: add versions for Mistral, Resend SMTP, and Google client-ID secrets;
bootstrap the VM; configure a billing budget and monitoring alerts; then run the deployment workflow.
EOF
