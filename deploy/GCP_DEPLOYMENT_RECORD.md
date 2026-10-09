# GCP backend deployment record

Updated: 2026-10-09. This record is for the backend only. All resources are pending; **nothing has been created yet**.

The [managed-service target architecture](TARGET_ARCHITECTURE.md) supersedes
the all-in-one VM sizing and cost estimate below. The existing production
Compose and release scripts still implement the older local MySQL and RabbitMQ
stack; they must be adapted before provisioning for the selected design.

## Preflight

| Check | Result |
| --- | --- |
| Active `gcloud` account | `sanjaygupta7212@gmail.com` |
| Likely target project | `baakio` (`350246327891`), awaiting operator confirmation; no default project is selected |
| Project IAM | Active account has direct `roles/owner` on `baakio` |
| Project billing | `billingEnabled: false`, no linked billing account |
| Visible billing account | `01BC22-8BBDD4-52C722`, `OPEN: False` |
| Location | Mumbai, `asia-south1`, per operator preference |
| Local verification | `mvn -B verify`: 10 tests passed; production Compose config validated with dummy values |
| Resources created | None |

Provisioning must wait until the operator confirms the project and links an open billing account. Recheck identity, billing, enabled APIs, and effective creation permissions before creating resources.

## Previous all-in-one deployment estimate

- One on-demand `e2-medium` Ubuntu VM in `asia-south1` (4 GiB RAM, shared CPU) with a 30 GiB zonal `pd-balanced` boot disk. The Compose services have memory limits for this size. Observe memory after first start and resize if necessary.
- One private, uniform-access Standard Cloud Storage bucket in `asia-south1` for bill scans. Use public access prevention. Consider disabling default seven-day soft delete for short-lived scans when creating it; no database backups are part of this release.
- One private Docker Artifact Registry repository in `asia-south1`.
- One dedicated VM service account with `roles/storage.objectUser` on this bucket and `roles/artifactregistry.reader` on this repository only. Attach it to the VM with the `cloud-platform` scope; store no service-account key.
- SSH restricted to the operator's IP. The API remains bound to VM localhost. Keep Caddy, public 80/443 ingress, the public API hostname, SMS, OCR, and CI publication/deployment setup for later.

## Previous all-in-one recurring cost estimate (USD)

Assuming 730 hours per month and no discounts, taxes, credits, or unusual traffic:

| Resource | Calculation | Estimate/month |
| --- | --- | ---: |
| Mumbai `e2-medium` | $0.04024218/hour × 730 | $29.38 |
| Mumbai 30 GiB `pd-balanced` | $0.000164384/GiB-hour × 30 × 730 | $3.60 |
| In-use external IPv4 on standard VM | $0.005/hour × 730 | $3.65 |
| **Fixed baseline** | | **$36.63** |

The private Standard bucket adds about $0.02 per GiB-month of stored scans, plus operations (regional flat-namespace Class A $0.005/1,000, Class B $0.0004/1,000). Artifact Registry includes 0.5 GiB-month free per billing account, then about $0.10 per GiB-month. VM internet egress and any transfer outside the region vary with usage. Costs start only when resources are created; the baseline is not currently accruing.

Prices checked against [Google's E2 pricing](https://cloud.google.com/products/compute/pricing/general-purpose), [disk pricing](https://cloud.google.com/compute/disks-image-pricing), [IPv4 pricing](https://cloud.google.com/vpc/network-pricing), [Cloud Storage pricing](https://cloud.google.com/storage/pricing), and [Artifact Registry pricing](https://cloud.google.com/artifact-registry/pricing) on 2026-10-09. Select Mumbai in the regional tables for the quoted VM and disk rates.

## Deployment verification to record

After deployment, record the actual project, region/zone, VM and IP, bucket, repository, service account, image digest, release timestamp, and output status of readiness, CSRF bootstrap, and unauthenticated API protection checks. The release script performs those checks on VM localhost.

## GitGuardian review

`deploy/compose.prod.yaml` contains `${DB_PASSWORD}`, `${MYSQL_ROOT_PASSWORD}`, and `${RABBITMQ_PASSWORD}` substitutions, not literal passwords. Real values are generated on the VM in `/opt/baakio/.env` with mode `0600`; `.env` is Git-ignored. VM users with Docker daemon access can inspect container environment variables, so Docker access must stay limited to deployment operators.
