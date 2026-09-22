# Baakio backend

Spring Boot REST API for the Baakio shop ledger. This app owns authentication, shop scoping, money calculations, and persistence. MySQL is the source of truth; posted purchases and confirmed payments are append-only in this first slice.

## Local run

1. Copy `.env.example` to `.env`.
2. Start MySQL, RabbitMQ, Redis, and the API with `docker compose up --build`.
3. Run the separate frontend from `../baakio-fe` with `npm install` and `npm run dev`.
4. Register an account holder and business in the client. The local `dev` profile accepts `222222` as the OTP.

The example Compose setup publishes MySQL on `127.0.0.1:3307` to avoid conflicts with a local MySQL server. Change `MYSQL_HOST_PORT` if that port is already in use.
Set the OCI Object Storage values in `.env` before starting the API. Scan images always use a private OCI Object Storage bucket; without a valid OCI config and bucket, the API will fail fast during startup. Set `MISTRAL_API_KEY` to enable bill extraction. Without a key, uploaded jobs become `FAILED` and the shopkeeper can use manual purchase entry.

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
- Account holders authenticate with a one-time code; passwords are not used.
- Accounts created before the phone migration have no verified phone number. Backfill each account holder's verified number before deploying OTP login to existing data.
- OTP challenges store an HMAC hash, expire after five minutes by default, allow at most five attempts, and have a resend cooldown and per-phone request limit.
- Notifications use a provider strategy behind `NotificationProvider`. `NotificationDispatcher` routes each channel to a configured provider ID, so SMS, WhatsApp, and email implementations can change independently.
- `Fast2SmsClient` owns the Fast2SMS HTTP contract. The Fast2SMS provider supports the OTP and quick SMS routes plus WhatsApp templates. SMTP email is enabled when `SPRING_MAIL_HOST` is configured.
- Financial POSTs require `Idempotency-Key`, lock the customer row, validate the current balance, write the event and audit row in one transaction, and return fresh server-computed balances.
- Itemized totals use `BigDecimal`; line totals round to paise with `HALF_UP`. Payments and promise allocations are checked independently so allocations do not reduce debt twice.
- Bill scans use MySQL `scan_job`, `scan_draft`, `scan_attempt`, and `outbox_event` tables. The outbox relay marks an event published only after a RabbitMQ publisher confirm and successful routing. Duplicate deliveries are guarded by a processing claim; stale claims are retried up to three times.
- Scan images are private OCI Object Storage objects in every environment. The OCI adapter uses config-file credentials plus namespace, bucket, and region settings. Never make the OCI bucket public. The retention job deletes unkept images after `SCAN_RETENTION_DAYS` (30 by default).
- The `ai` layer owns Mistral extraction, bounded retries, field validation, and editable draft calculation. The service worker owns storage retrieval and persistence; the AI layer never posts financial records.
- Mistral OCR only proposes fields. Draft totals and final line totals are recalculated in the application; confirmation calls the existing financial purchase posting service and changes outstanding only after that transaction commits. The shopkeeper must explicitly confirm any `paidNow` amount.
- RabbitMQ is the scan queue. Redis is included in Compose only for optional disposable read caching; it is not used for ledger or balance decisions.
- Session writes require the `X-XSRF-TOKEN` cookie/header pair. Browser requests use the configured exact `FRONTEND_ORIGIN` and credentials.

## API routes

All routes use `/api/v1`. CSRF bootstrap, OTP requests, login, and registration are public. An account holder signs in and manages a business; each business tracks its own customers, purchases, balances, and payments. Customers are not account holders and do not need to sign in.

| Method | Route | Purpose |
|---|---|---|
| GET | `/csrf` | CSRF token bootstrap for browser writes |
| POST | `/auth/otp/request` | Request a login or registration code with `phoneNumber`, `purpose` (`LOGIN` or `REGISTER`), and `preferredLocale` |
| POST | `/auth/login` | Verify `phoneNumber`, `challengeId`, and `otp`, then create a session |
| POST | `/auth/register` | Verify OTP and create an account holder and business with `displayName`, `businessName`, optional `email`, and `preferredLocale` |
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

For non-dev deployments, configure `OTP_HASH_SECRET`, `FAST2SMS_API_KEY`, and an approved `FAST2SMS_OTP_TEMPLATE_ID`. Configure `OTP_CHANNEL=SMS` to send codes over SMS or `OTP_CHANNEL=WHATSAPP` with the approved WhatsApp phone number ID and template. Email notification delivery is available through Spring Mail; the current phone authentication flow sends OTPs through SMS or WhatsApp. Route SMS, WhatsApp, and email by setting `SMS_NOTIFICATION_PROVIDER`, `WHATSAPP_NOTIFICATION_PROVIDER`, and `EMAIL_NOTIFICATION_PROVIDER` to a registered provider ID.

For document storage, set `OCI_CONFIG_FILE` (or mount the standard OCI config path), `OCI_PROFILE`, `OCI_REGION`, `OCI_OBJECT_STORAGE_NAMESPACE`, and `OCI_OBJECT_STORAGE_BUCKET`. Grant the API identity only the required object read/write/delete permissions on the private bucket. Set `MISTRAL_API_KEY` to activate OCR extraction.

See the design pack at `../baakio-design-pack` for the product, UX, and domain rules.
