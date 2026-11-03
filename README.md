# Baakio API

Baakio helps small businesses track customer credit, partial payments, and payment promises in one clear ledger. The [frontend README](https://github.com/sanjaygupta45/Baakio-fe#readme) explains the problem and product journey; this repository contains the Spring Boot API and the rules that keep each customer balance trustworthy.

[Website](https://www.baakio.shop/) · [Frontend repository](https://github.com/sanjaygupta45/Baakio-fe) · [Deployment runbook](deploy/vm/README.md)

## From problem to product

For a business that sells on credit, a notebook or message thread can record individual transactions but makes the current position hard to trust. A customer may buy again, pay only part of a balance, promise another payment, or send a photo of a handwritten bill. Baakio turns those events into one customer account with a computed balance, dated history, and a clear follow-up queue. The owner remains responsible for confirming that money was received and that an AI draft matches the bill.

## Features delivered by the API

| Need | What Baakio provides |
| --- | --- |
| Separate business records | Passwordless email or Google sign-in, account holder identity linking, and business-scoped access to customers, scans, and financial data. |
| Start from an existing notebook | A one-time, acknowledged opening balance with a cutoff date for each customer. |
| Record repeat credit sales | Quick-amount and itemized purchases, with server-calculated line totals and purchase totals after discounts, plus optional money paid at purchase time. |
| Track money actually received | Confirmed cash, UPI, bank, or other payments; partial payments reduce the outstanding balance and can be allocated to a promise. |
| Keep commitments visible | Payment promises with an amount and due date, rescheduling history, and fulfilled, due-today, overdue, or upcoming status. A promise never reduces the ledger balance. |
| Know who needs attention | A dashboard with total outstanding, due-today and overdue amounts, and prioritized customer follow-ups, including customers with debt but no promise. |
| Explain an account | Date-range statements with opening balance, purchases, payments, closing balance, and individual movements. |
| Follow up in the customer's language | English or Hindi reminder previews, short or detailed, with a prefilled WhatsApp link and owner-confirmed UPI details when available. The API does not treat opening WhatsApp as delivery. |
| Enter handwritten bills faster | Asynchronous OCR creates a reviewable purchase draft; the owner corrects it and explicitly confirms before it enters the ledger. |

## How it works

```text
React app (Vercel)
    | session cookie + CSRF token
    v
Spring Boot API
    |-- MySQL / Flyway: accounts, customers, ledger, promises, scans
    |-- RabbitMQ: asynchronous bill scan jobs
    |-- Private Google Cloud Storage: uploaded bill images
    `-- Mistral OCR: editable extraction draft
```

- Each account holder manages a business; customer, ledger, and scan reads and writes are scoped to that business.
- MySQL stores the financial source of truth. The server computes balances from an opening entry, posted purchases, and confirmed payments. Payment promises and their allocations are tracked separately so they do not reduce debt twice.
- Financial writes use `BigDecimal`, a customer row lock, an `Idempotency-Key`, and a transaction that includes an audit event. Posted purchases and confirmed payments are append-only in the current implementation.
- Bill uploads enter an asynchronous queue. OCR suggests fields in a draft; the owner edits and confirms the draft before the normal purchase posting path changes the balance.
- Browser authentication uses an email code or a verified Google ID token, server-side sessions, and CSRF protection. Customers do not need accounts or passwords.

## AI bill scan: from image to ledger entry

1. **Upload:** The signed-in owner selects a customer and uploads one JPEG, PNG, or WebP image (up to 10 MB). The API checks business ownership, image content, and the upload's `Idempotency-Key`, then stores the original in a private Google Cloud Storage bucket.
2. **Queue reliably:** A database transaction creates the scan job and an outbox event together. The outbox relay publishes to RabbitMQ and marks the event delivered only after a publisher confirmation and successful routing. A failed publish remains eligible for another attempt.
3. **Extract:** A worker claims the job, reads the private image, and asks Mistral OCR for structured bill fields. The extraction prompt distinguishes a new purchase from a ledger, account summary, or payment receipt and does not infer that a customer paid. Extraction failures receive bounded retries. Stale processing claims are recovered; duplicate queue deliveries cannot create duplicate drafts.
4. **Prepare a reviewable draft:** The application checks that the image appears to be a new purchase and contains usable items. It flags uncertain names, quantities, prices, dates, and mismatched totals. Line totals and the purchase total after the submitted discount are calculated in Java using decimal money rules; OCR's written total is shown for comparison, not accepted as the ledger amount.
5. **Owner review:** The client polls the scan status and displays the authorized original beside editable lines. A failed scan leaves manual purchase entry available. Neither upload nor extraction changes the customer balance.
6. **Post once:** Confirmation requires the current draft version and an `Idempotency-Key`. The service recalculates the edited total, requires explicit confirmation of any `paidNow` amount, and calls the same transactional purchase posting path used by manual entry. Only this successful transaction changes outstanding debt.
7. **Protect the source:** Image access is scoped to the business and served without browser caching. Unkept images are deleted after the retention period (30 days by default); owners can choose to retain an attachment.

## Backend implementation

- **Core stack:** Java 21, Spring Boot 4, Spring Security, Hibernate/JPA, Flyway, MySQL 8.4, and RabbitMQ. Controllers bind HTTP, services own validation and transactions, repositories handle persistence, and DTOs define API contracts.
- **Schema:** Flyway creates and evolves the `utf8mb4` tables; Hibernate validates mappings at startup. MySQL stores account holders, businesses, customers, ledger events, commitments, scan state, idempotency records, and audit events.
- **Authentication:** Email codes and verified Google ID tokens are passwordless options. Google identities use the stable Google subject; email identities use a normalized verified address. They are linked only by the signed-in account holder. Email challenges are HMAC-hashed, expire, and have attempt and request limits.
- **Financial integrity:** Amounts use `BigDecimal` and line totals round to paise. Financial POSTs require an idempotency key, lock the customer row, validate the current balance, and commit the event with an audit row. Promise allocations are validated separately from payments.
- **Session and tenant security:** MySQL-backed Spring Session holds browser sessions. Writes require the CSRF cookie/header pair, credentialed requests are limited to configured frontend origins, and resources are scoped to the signed-in business.
- **Notifications:** A provider strategy supports email, SMS, and WhatsApp integrations. Reminder previews are generated from server-side ledger data; sending remains an owner action in the client.
- **Verification:** The test suite covers authentication flows, security configuration, scan drafting and processing, and scan/financial consistency. The deployment workflow runs Maven verification before publishing an image.

Redis is present in the Compose environment only for optional disposable read caching. It is not used to decide ledger balances.

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

## Deployment

The production design separates the public React site on Vercel from the API at `https://api.baakio.shop`. The API runs in a versioned container on a Google Compute Engine VM. Caddy serves HTTPS and proxies to the Java service; the VM also runs RabbitMQ and the Cloud SQL Auth Proxy on a private Docker network. Cloud SQL hosts MySQL, and a private Cloud Storage bucket holds bill images. The VM's service account reads runtime secrets and accesses Google Cloud services without a stored service-account key.

The release path is automated in [`.github/workflows/deploy-vm.yml`](.github/workflows/deploy-vm.yml):

1. A pull request runs `mvn -B verify`. A push to `main` runs the same verification before deployment.
2. GitHub Actions builds a container tagged with the commit SHA and pushes it to Artifact Registry. Workload Identity Federation gives the workflow short-lived Google Cloud access.
3. The workflow transfers release files to the VM through Identity-Aware Proxy. The release script reads secrets, renders runtime settings, pulls the tagged image, and updates the Compose stack.
4. The VM checks the API's local health endpoint before Caddy is reloaded. If the release or Caddy validation fails, the script restores the previous image and configuration.
5. The workflow checks the public HTTPS API and the frontend origin's credentialed CORS response. The [frontend workflow](https://github.com/sanjaygupta45/Baakio-fe/blob/main/.github/workflows/deploy-vercel.yml) deploys the site to Vercel separately.

Operations are part of the design: Cloud SQL managed backups, a public uptime check, TLS and resource alerts, VM telemetry, and a five-minute watch service cover the pilot deployment. The current single-VM architecture is a single point of failure; the [deployment runbook](deploy/vm/README.md) records provisioning, rollback, monitoring, backup verification, and recovery steps.
