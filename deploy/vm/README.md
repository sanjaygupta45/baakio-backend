# Baakio single-VM production runbook

This deployment targets Google Cloud project `baakio-513506`, zone
`asia-south1-a`. The frontend stays on Vercel at `https://baakio.shop`; the VM
serves only `https://api.baakio.shop`. A single VM is a single point of failure.

```mermaid
flowchart LR
  B[Browser] --> V[Vercel frontend<br/>baakio.shop]
  V -->|HTTPS, session, CSRF| C[Caddy<br/>api.baakio.shop]
  subgraph VM[Compute Engine e2-medium]
    C --> A[Java API :8080]
    A --> P[Cloud SQL Auth Proxy]
    A --> R[RabbitMQ scan queue]
  end
  P --> M[(Cloud SQL MySQL 8.4<br/>db-f1-micro)]
  A --> G[Private bill-image bucket]
  A --> X[Resend SMTP and Mistral OCR]
  M -. managed daily backups .-> K[Cloud SQL backup storage]
```

## 1. Price and access checks

Sign in to the **new** Google account with `gcloud auth login`, then check that
`gcloud projects describe baakio-513506` and
`gcloud billing projects describe baakio-513506` succeed. The CLI account in the
development workspace was `sanjaygupta7212@gmail.com` and did not have access
to the new project at implementation time.

Before provisioning, use the [Google Cloud pricing
calculator](https://cloud.google.com/products/calculator) for a continuously
running `e2-medium` in Mumbai, a 20 GB balanced persistent disk, reserved
external IPv4 address, a `db-f1-micro` Cloud SQL MySQL instance with 10 GB SSD
and managed backups, image storage, and expected egress. Include
Resend, Mistral OCR, and Vercel in the overall operating estimate. Trial
credits end; the cost continues afterward. Set a monthly project billing budget
with 50%, 80%, and 100% alerts. Budget alerts do not stop spending.

For a 730-hour month, the initial planning estimate is roughly **US$45–60 per
month** for the VM, Cloud SQL shared-core instance, 20 GB VM disk, 10 GB
database disk, in-use external IPv4 address, and small backup/image storage.
Traffic, extra storage, taxes, Resend, Mistral, and Vercel are additional. This
is a planning range; verify the Mumbai prices in the calculator and set the
billing budget before leaving the services running.

On 2 November 2026, the project received a ₹6,000 monthly budget with 50%,
80%, and 100% alerts. It excludes trial credits when measuring spend and is
scoped to `baakio-513506`. Cloud SQL and the VM were provisioned with reserved
VM IP `34.14.159.101`; DNS cutover is still a separate step.

## 2. Provision the new project

From the backend repository root, run `bash deploy/vm/provision-cloud.sh` after
reviewing its names, region, firewall rules, and IAM grants. It creates the VM,
private document bucket, Cloud SQL instance and `baakio` database, Artifact
Registry, secrets, dedicated VM and GitHub deployment identities, Workload
Identity Federation, and a reserved IP. It generates strong database, RabbitMQ,
and OTP secrets without printing them. The VM service account reads only the
runtime secrets, connects to Cloud SQL through the Auth Proxy, manages bill
images, and pulls images. The GitHub identity is limited
to this repository's `main` branch.

The script prints the reserved IP and two GitHub Actions variable values. Add
`GCP_WIF_PROVIDER` and `GCP_DEPLOY_SERVICE_ACCOUNT` under the backend GitHub
repository's **Settings → Secrets and variables → Actions → Variables**.
Those variables were added to the repository on 2 November 2026.

The following three secrets are created **without versions**. Add their real
values through Secret Manager in the new project before deploying:

| Secret | Value |
| --- | --- |
| `baakio-mistral-api-key` | Mistral API key for bill extraction |
| `baakio-resend-smtp-key` | Resend SMTP key for email codes |
| `baakio-google-client-id` | New Google OAuth Web client ID |

The script also generates `baakio-db-password`, `baakio-db-root-password`,
`baakio-rabbit-password`, and `baakio-otp-hash-secret`. The VM cannot read the
root password. Its MySQL connection uses the application user's password and
the VM service account's Cloud SQL Client role; no service-account key is used.

Keep the existing `auth.baakio.shop` sender domain verified with Resend. The
API expects `login@auth.baakio.shop`. Do not put these values in Git, GitHub
Actions variables, shell command arguments, or VM metadata.

## 3. Configure Google sign-in

In the new project's **Google Auth Platform**, set up branding and an External
audience for the public app, then create an OAuth **Web application** client.
Authorized JavaScript origins are `https://baakio.shop` and
`https://www.baakio.shop`; add `http://localhost:5173` and
`http://127.0.0.1:5173` only if the same client is also used locally. This app
receives the ID token in a JavaScript callback, so it needs no redirect URI or
client secret. Publish the consent configuration when ready for public users.
Add the client ID as the version of `baakio-google-client-id` in Secret Manager.
The backend exposes it through `/api/v1/auth/config` and validates it as the
token audience. The frontend production API URL already points to
`https://api.baakio.shop`.

## 4. Bootstrap and deploy

Use the new account's CLI to copy and run the one-time setup script over IAP:

```sh
gcloud compute scp --project=baakio-513506 --zone=asia-south1-a --tunnel-through-iap \
  deploy/vm/bootstrap.sh baakio-api-vm:/tmp/baakio-bootstrap.sh
gcloud compute ssh baakio-api-vm --project=baakio-513506 --zone=asia-south1-a \
  --tunnel-through-iap --command='sudo bash /tmp/baakio-bootstrap.sh'
```

Push the backend's new `deploy-vm.yml` workflow to `main`, or dispatch it from
`main`. It verifies Maven, pushes a commit-tagged image, copies the release
files over IAP, pulls secrets on the VM, starts the Compose stack, and checks
`/actuator/health` locally before reloading Caddy. The API is bound to
`127.0.0.1:8080`; the Cloud SQL Auth Proxy and RabbitMQ have no host ports.
The proxy uses the VM's IAM identity to connect to Cloud SQL over an encrypted
channel, without authorized networks. MySQL still checks the application user
and database password. JDBC retrieves the MySQL 8.4 authentication public key
on the private Docker network; the proxy encrypts its Cloud SQL connection.
Caddy blocks the
health endpoint from public traffic. The frontend Vercel workflow stays as is.

**Do not push the deployment workflow until the project, secrets, VM bootstrap,
and GitHub variables are ready.** A push to `main` triggers deployment.

## 5. Cut over DNS and verify

Read the reserved IP from `gcloud compute addresses describe baakio-api-ip
--project=baakio-513506 --region=asia-south1 --format='value(address)'`. Set
the GoDaddy `api` A record to that IP after the local API health check passes.
Caddy obtains and renews the public certificate once DNS points at the VM and
ports 80/443 are reachable. Remove any obsolete AAAA record for `api` unless
IPv6 is configured on the VM. Verify the certificate and
`https://api.baakio.shop/api/v1/auth/config` after DNS propagation.

Perform a browser check from `https://baakio.shop`: Google sign-in and email
code, authenticated account retrieval, a ledger write with CSRF and
idempotency, and one image scan through OCR and confirmation. Confirm that
bill images are private in the new bucket. If the API fails at cutover, restore
the previous `api` DNS A record while investigating. As this is a fresh
deployment, there is no database migration from the old project.

After the new site has operated successfully through the chosen observation
period, review the old `baakio-511105` Cloud Run service and load balancer for
shutdown so they do not keep billing. Retain old database and image data until
their retention or deletion is decided separately.

## 6. Backups, monitoring, and recovery

Cloud SQL takes a managed backup daily starting at 02:30 UTC and retains 28
daily recovery points. This includes at least four weekly recovery points
without a VM dump job or separate backup bucket. Point-in-time recovery is off
to keep the pilot cost low. Check the most recent backup in Cloud SQL and test
restoring one into a disposable instance before launch. Set a Cloud Monitoring
alert for failed or missing Cloud SQL backups. Shared-core `db-f1-micro` is a
low-cost pilot size with no Cloud SQL SLA; increase its tier if database CPU,
memory, or latency measurements demand it.

The bootstrap installs Google's Ops Agent for VM memory, disk, and log
telemetry. The five-minute `baakio-watch` timer checks API health, all three
containers, free memory, and disk usage; inspect failures
with `journalctl -u baakio-watch.service`. Create Cloud Monitoring alerts for
external API uptime, VM memory, disk usage, VM availability, Cloud SQL backup
status, and failed watch services. During the pilot,
resize from `e2-medium` to `e2-standard-2` if available RAM stays low, swap is
used, a container is killed for memory, or representative requests are slow.

To test recovery, restore a managed backup into a disposable Cloud SQL instance,
connect a disposable API VM, and confirm Flyway validation, login, and ledger
reads. For a VM replacement, attach the new VM service account, bootstrap it,
point it at the existing Cloud SQL instance and private bill-image bucket,
deploy the same image commit, and change the `api` A record to the replacement
VM's reserved IP. The RabbitMQ queue can be recreated; scan job state and the
outbox live in Cloud SQL, so recovery retries unfinished work.
