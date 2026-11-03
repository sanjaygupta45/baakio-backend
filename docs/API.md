# Baakio API routes

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
