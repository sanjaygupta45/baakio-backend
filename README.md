# Baakio backend

## Cloud Run deployment

The backend deploys from GitHub Actions whenever `main` is pushed, and can also be
started with **Run workflow**. The workflow runs the Maven test suite, builds the
container, pushes it to Artifact Registry, and updates the `baakio-api` Cloud Run
service in project `baakio-511105` (`asia-south1`). GitHub authenticates through
Workload Identity Federation; no service-account key is stored in GitHub.

Production resources are the `baakio` Artifact Registry repository, private
`gs://baakio-511105-documents` bucket, and Secret Manager secrets named
`baakio-db-url`, `baakio-db-user`, `baakio-db-password`,
`baakio-amqp-addresses`, `baakio-otp-hash-secret`, and
`baakio-mistral-api-key`. The Cloud Run runtime
identity can read only those secrets and manage objects in that bucket. MySQL
is configured with a JDBC URL and TLS required; RabbitMQ uses the managed
`amqps://` address. Redis is not used by the current backend.

The API's scheduled jobs and RabbitMQ listener require CPU between HTTP
requests. The workflow therefore keeps one Cloud Run instance running with
instance-based billing. This incurs ongoing Cloud Run charges.

The chosen production browser origin is `https://baakio.shop`; the API hostname
is `https://api.baakio.shop`. The workflow sets `FRONTEND_ORIGIN` to the browser
origin, and the frontend production build sets `VITE_API_BASE_URL` to the API
origin. An external Application Load Balancer routes `api.baakio.shop` to the
Cloud Run service in `asia-south1`. Add a GoDaddy A record with host `api` and
value `8.233.193.171`; the Google-managed TLS certificate becomes active after
DNS propagates. The workflow restricts Cloud Run ingress to the load balancer.

Authentication now uses Google Identity Services and email codes. The Cloud Run
workflow configures Resend SMTP with the verified `auth.baakio.shop` domain and
reads the API key from `baakio-resend-smtp-key` in Secret Manager. Email
authentication messages use the branded HTML template in
`src/main/resources/email/auth-otp.html` with a plain-text alternative. The
subject and instructions reflect sign-in, registration, or email linking and
support English and Hindi. The Google OAuth Web client ID is stored in the
GitHub Actions variable `GOOGLE_CLIENT_ID`. The API hostname is live; the
frontend still needs hosting at `baakio.shop` before production browser sign-in
can be tested.
Leave `LEGACY_PHONE_AUTH_ENABLED=false` in production. Bill scanning reads
`MISTRAL_API_KEY` from Secret Manager. If Aiven restricts
inbound IP addresses, Cloud Run will also
need fixed outbound IP routing allowed by Aiven.

Spring Boot REST API for the Baakio shop ledger. This app owns authentication, shop scoping, money calculations, and persistence. MySQL is the source of truth; posted purchases and confirmed payments are append-only in this first slice.

## Local run

1. Copy `.env.example` to `.env`.
2. Start MySQL, RabbitMQ, Redis, and the API with `docker compose up --build`.
3. Run the separate frontend from `../baakio-fe` with `npm install` and `npm run dev`.
4. Register an account holder and business in the client using email. The local `dev` profile displays the code in the UI and sends no email.

For local Google sign-in, create an **OAuth 2.0 Web application** client in the
Google Cloud Console for project `baakio-511105`. Add both
`http://localhost:5173` and `http://127.0.0.1:5173` as authorized JavaScript
origins, then set its **client ID** in `GOOGLE_CLIENT_ID` in `.env`. No client
secret is used. Google sign-in is hidden when the ID is blank. A domain and
email delivery account are not needed to test localhost email codes; the dev
provider returns the code only in the local response. Use the same host spelling
throughout a browser session so session and CSRF cookies remain available.

The example Compose setup publishes MySQL on `127.0.0.1:3307` to avoid conflicts with a local MySQL server. Change `MYSQL_HOST_PORT` if that port is already in use.
Set `GCP_PROJECT_ID`, `GCS_BUCKET`, and `GCP_ADC_HOST_PATH` in `.env` before starting the API. Use a private development bucket separate from production. Run `gcloud auth application-default login` on the host to create the ADC file and set `LOCAL_UID` to `id -u` so the container can read it. Scan images always use private Google Cloud Storage. Set `MISTRAL_API_KEY` to enable bill extraction. Without a key, uploaded jobs become `FAILED` and the shopkeeper can use manual purchase entry.

## Maven commands

- Resolve dependencies and compile: `mvn -DskipTests package`
- Run the API with the local OTP profile: `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`. `application.properties` is the production baseline; `application-dev.properties` overrides it for local development. The dev profile's default JDBC settings match the Compose MySQL host port (`3307`) and `.env.example`; if you customize `.env`, pass the matching `MYSQL_*` or `DB_*` values to the Maven process because Spring does not load Compose's `.env` file itself.
- Create the executable JAR: `mvn package`
- Run the packaged API: `java -jar target/baakio-0.1.0.jar`

The Docker Compose ports bind to localhost. The `dev` profile uses OTP `222222`, returns that code to the local client, and logs only masked notification metadata. It does not send real SMS, WhatsApp, or email. For production, do not activate `dev`; provide `DB_URL`, `DB_USER`, `DB_PASSWORD`, `FRONTEND_ORIGIN`, and a random `OTP_HASH_SECRET` through the deployment environment, set real provider credentials as needed, and use `COOKIE_SECURE=true` behind HTTPS. `.env.example` contains local-development values only; do not use them for real data.

## Backend implementation

- Java 21, Spring Boot 4, Spring Security sessions, Hibernate ORM/JPA, Flyway, and MySQL 8.4.
- Source is organized in horizontal layers: `controller`, `dto`, `model`, `repository`, `service`, `ai`, `messaging`, `config`, and `utils`.
- DTOs are grouped by responsibility under `dto/auth`, `dto/business`, `dto/customer`, `dto/ledger`, `dto/commitment`, `dto/dashboard`, and `dto/reminder`.
- Controllers handle HTTP binding, DTOs define API contracts, services own validation and transactions, and repositories use Hibernate entity operations and JPQL.
- Flyway owns schema changes; Hibernate validates entity mappings against the migrated schema at startup and does not create or update tables.
- Flyway creates `utf8mb4` tables for account holders, businesses, customers, opening balances, purchases/lines, confirmed payments, promises, allocations, idempotency records, and audit events.
- Account holders authenticate with a verified Google ID token or a one-time email code; passwords are not used. Google identities use the immutable Google subject, and email identities use the normalized verified address. Matching email addresses are never merged automatically; the signed-in account holder can link another method in Settings.
- The legacy phone OTP endpoints remain available only when `LEGACY_PHONE_AUTH_ENABLED=true` (local dev can opt in). They are disabled by default in production.
- Email OTP challenges store an HMAC hash, expire after five minutes by default, allow at most five attempts, and have resend, per-email, and global daily limits. MySQL-backed Spring Session JDBC keeps browser sessions valid across Cloud Run instances.
- Notifications use a provider strategy behind `NotificationProvider`. `NotificationDispatcher` routes each channel to a configured provider ID, so SMS, WhatsApp, and email implementations can change independently.
- `Fast2SmsClient` owns the Fast2SMS HTTP contract. The Fast2SMS provider supports the OTP and quick SMS routes plus WhatsApp templates. SMTP email is enabled when `SPRING_MAIL_HOST` is configured.
- Financial POSTs require `Idempotency-Key`, lock the customer row, validate the current balance, write the event and audit row in one transaction, and return fresh server-computed balances.
- Itemized totals use `BigDecimal`; line totals round to paise with `HALF_UP`. Payments and promise allocations are checked independently so allocations do not reduce debt twice.
- Bill scans use MySQL `scan_job`, `scan_draft`, `scan_attempt`, and `outbox_event` tables. The outbox relay marks an event published only after a RabbitMQ publisher confirm and successful routing. Duplicate deliveries are guarded by a processing claim; stale claims are retried up to three times.
- Scan images are private Google Cloud Storage objects in every environment. Local development uses Google ADC; GCE production uses its attached service account. Never make the bucket public. The retention job deletes unkept images after `SCAN_RETENTION_DAYS` (30 by default).
- The `ai` layer owns Mistral extraction, bounded retries, field validation, and editable draft calculation. The service worker owns storage retrieval and persistence; the AI layer never posts financial records.
- Mistral OCR only proposes fields. Draft totals and final line totals are recalculated in the application; confirmation calls the existing financial purchase posting service and changes outstanding only after that transaction commits. The shopkeeper must explicitly confirm any `paidNow` amount.
- RabbitMQ is the scan queue. Redis is included in Compose only for optional disposable read caching; it is not used for ledger or balance decisions.
- Session writes require the `X-XSRF-TOKEN` cookie/header pair. Browser requests use the configured exact `FRONTEND_ORIGIN` and credentials.

## API routes

All routes use `/api/v1`. CSRF bootstrap, authentication config, Google and email authentication are public. An account holder signs in and manages a business; each business tracks its own customers, purchases, balances, and payments. Customers are not account holders and do not need to sign in.

| Method | Route | Purpose |
|---|---|---|
| GET | `/csrf` | CSRF token bootstrap for browser writes |
| GET | `/auth/config` | Public Google client ID and enabled authentication methods |
| POST | `/auth/google` | Verify a Google ID token and sign in or create a shop |
| POST | `/auth/email/request` | Request an email code with `email`, `purpose` (`LOGIN` or `REGISTER`), and `preferredLocale` |
| POST | `/auth/email/login` | Verify an email code and sign in |
| POST | `/auth/email/register` | Verify an email code and create an account holder and shop |
| POST | `/account-holder/identities/google` | Link a verified Google identity to the current account |
| POST | `/account-holder/identities/email/request` | Request an email linking code |
| POST | `/account-holder/identities/email/verify` | Link a verified email identity |
| POST | `/auth/otp/request`, `/auth/login`, `/auth/register` | Legacy phone OTP routes; off by default outside dev |
| POST | `/auth/logout` | End the current session |
| GET | `/account-holder` | Current signed-in account holder and managed business |
| PATCH | `/account-holder/preferences` | Save the account holder's `en-IN` or `hi-IN` language preference |
| GET | `/business` | Current shop settings |
| PATCH | `/business/payment-details` | Account-holder-confirmed UPI payee information |
| GET, POST | `/customers` | Search/list and create customers |
| GET | `/customers/{id}` | Customer details and computed totals |
| POST | `/customers/{id}/opening-balance` | Add one acknowledged migration balance |
| GET, POST | `/customers/{id}/purchases` | List and post quick/itemized purchases |
| GET, POST | `/customers/{id}/payments` | List and post confirmed payments |
| GET | `/customers/{id}/ledger` | Period opening, activity, and closing statement |
| GET, POST | `/customers/{id}/commitments` | List and create payment promises |
| POST | `/commitments/{id}/reschedule` | Append a date revision |
| GET | `/dashboard` | Total, due today, overdue, and follow-up customers |
| POST | `/customers/{id}/reminder-preview` | Server-derived English/Hindi WhatsApp preview |
| POST | `/customers/{id}/scans` | Upload one JPEG/PNG/WebP bill as multipart `file`; returns `202 Accepted` with `scanId` |
| GET | `/scans/{id}` | Tenant-scoped scan status and reviewable draft |
| GET | `/scans/{id}/image` | Tenant-authorized original bill image; `Cache-Control: no-store` |
| POST | `/scans/{id}/confirm` | Confirm edited lines and draft `version` with an `Idempotency-Key`; posts through normal purchase rules |

Scan upload requires `Idempotency-Key`; the multipart form may include `keepAttachment=true` to retain the original image beyond its review period. Status and image requests return 404 when the scan belongs to another business.

For production, configure `OTP_HASH_SECRET` and `FRONTEND_ORIGIN`. The GitHub Actions workflow enables email codes with Resend SMTP using `login@auth.baakio.shop`; its SMTP password comes from the `baakio-resend-smtp-key` Secret Manager secret. It caps email code requests at 80 per day, below the provider's free daily allowance. Google login stays unavailable until the GitHub Actions `GOOGLE_CLIENT_ID` variable is set to a web client ID authorized for `https://baakio.shop`. Keep `COOKIE_SECURE=true` behind HTTPS. The frontend and API use subdomains of the same site so credentialed browser requests can use the API session cookie; the API sends CSRF tokens through `/api/v1/csrf` for cross-origin writes.

For document storage, set `GCP_PROJECT_ID` and `GCS_BUCKET`. Local development uses `GCP_ADC_HOST_PATH` in Compose; GCE uses its attached service account. Grant the API identity only the required object read/write/delete permissions on the private bucket. Set `MISTRAL_API_KEY` to activate OCR extraction.

See the design pack at `../baakio-design-pack` for the product, UX, and domain rules.
