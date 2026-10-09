# Deploy on Render

SkillSwap runs as a **Web Service** with **Runtime: Docker** (the `Dockerfile` at the root of the repository).

## Environment variables

| Variable | Required | Value |
|---|---|---|
| `DATABASE_URL` | yes | **Internal Database URL** of the Render PostgreSQL (`postgres://user:password@host:5432/db`). The application converts it into a JDBC URL. |
| `TOKEN_SETTINGS_SECRET` | yes | At least 32 characters. The same value as the C# API keeps the tokens already issued valid. |
| `TOKEN_SETTINGS_EXPIRATION_DAYS` | no | Default 7. |
| `CLOUDINARY_CLOUD_NAME` | yes | The application does not start without it. |
| `CLOUDINARY_API_KEY` | yes | Same. |
| `CLOUDINARY_API_SECRET` | yes | Same. |
| `GEMINI_API_KEY` | yes | The application does not start without it. |
| `GEMINI_MODEL` | no | Default `gemini-3.5-flash`. |
| `GEMINI_FALLBACK_MODELS` | no | Comma-separated, tried in order. |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated front-end origins. Empty = any origin. |
| `REVENUECAT_API_KEY` | no | Secret API key (`sk_...`) of the RevenueCat project. **Empty = purchases are simulated** (nothing is charged). |
| `REVENUECAT_WEBHOOK_AUTH` | with RevenueCat | The exact *Authorization header value* of the RevenueCat webhook. Empty = every notification gets 401. |
| `REVENUECAT_ENTITLEMENT_ID` | no | Entitlement that grants the monthly plan. Default `premium`. |
| `REVENUECAT_ACCEPT_SANDBOX` | no | Apply test purchases (`environment: SANDBOX`). Default `true`; set `false` once the app is in production. |
| `BILLING_EXPIRATION_CHECK_INTERVAL` | no | How often the subscriptions whose paid period ended are checked with RevenueCat. Default `1h`. |
| `BILLING_SIMULATED_PERIOD` | no | Length of a period of the simulated gateway (no API key). Default `30d`; e.g. `10m` to demo the expiration. |
| `BREVO_API_KEY` | no | API key (`xkeysib-...`) of Brevo, used to send the verification email. **Empty = emails are not sent**, they are written to the log (with the link). |
| `EMAIL_SENDER_ADDRESS` | with Brevo | Sender address, verified in Brevo (*Senders, Domains & Dedicated IPs*). Without it the emails are only logged too. |
| `EMAIL_SENDER_NAME` | no | Sender name. Default `SkillSwap`. |
| `APP_VERIFICATION_BASE_URL` | no | Public URL of this backend, used in the verification link (`<url>/api/v1/authentication/verify-email?token=...`). Default `https://skillswap-webservices-java.onrender.com`. |
| `APP_VERIFICATION_TOKEN_TTL` | no | How long a verification link is valid. Default `24h`. |
| `APP_VERIFICATION_RESEND_COOLDOWN` | no | Minimum time between two verification emails to the same account. Default `2m`. |
| `FIREBASE_CREDENTIALS_BASE64` | no | Service account JSON of the Firebase project in Base64 (`base64 -w0 service-account.json`), for the push notifications. **Empty (or invalid) = push notifications are not sent**, they are written to the log. |

Not used anymore (delete them): `SEED_COORDINATOR_*`, `ASPNETCORE_*`, `ConnectionStrings__*`.

## Service settings

- Health Check Path: `/health`
- Auto-Deploy: on the `main` (or `develop`) branch you choose.
- Hibernate only validates the schema (`ddl-auto=validate`). The schema is owned by the Flyway migrations in
  `src/main/resources/db/migration`, which run on every start before Hibernate validates.

## Database migrations (Flyway)

- `V1__baseline_schema.sql` is the schema the C# API created (plus the two appeal columns of
  `verification_cases`, added by hand). An empty database gets everything from it.
- The Render database already has those tables. With `spring.flyway.baseline-on-migrate=true` and
  `spring.flyway.baseline-version=1`, the first start creates `flyway_schema_history` with V1 marked as
  **baseline** (not executed) and applies only V2 and later. Nothing has to be done by hand.
- Check it after that first deploy: the log shows `Successfully baselined schema with version: 1`, and
  `SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank;` lists `1 | BASELINE` followed
  by the later versions.
- A schema change is always a new `V<n>__description.sql`. A migration that was already applied is never edited:
  Flyway checks its checksum and stops the start.
- The `__EFMigrationsHistory` table of the C# API stays in the database; neither Flyway nor Hibernate uses it.

## Email verification (Brevo)

Render's free plan blocks outbound SMTP (ports 25, 465 and 587), so the verification email is sent through the
**Brevo transactional email HTTP API** (`POST https://api.brevo.com/v3/smtp/email`, header `api-key`), which works
over HTTPS. Brevo's free plan sends up to 300 emails a day.

1. Create a Brevo account, then *Senders, Domains & Dedicated IPs > Senders > Add a sender* and verify the address
   (or authenticate a domain). Put that address in `EMAIL_SENDER_ADDRESS`.
2. *SMTP & API > API keys > Generate a new API key*. Put it in `BREVO_API_KEY` (Render environment only).
3. Redeploy. The log shows `Emails are sent through Brevo from <address>.`; without the key it shows
   `BREVO_API_KEY is not set: emails are NOT sent, they are written to the log.`

How it works:

- `POST /api/v1/authentication/sign-up` creates the account **unverified** and, once it is saved, sends an email in
  Spanish with the link `<APP_VERIFICATION_BASE_URL>/api/v1/authentication/verify-email?token=...`. Only the SHA-256
  of the token is stored; it expires after `APP_VERIFICATION_TOKEN_TTL` and works once.
- Opening the link (`GET`) verifies the account and shows a small page; apps can send the token with
  `POST /api/v1/authentication/verify-email` `{"token": "..."}` instead (200, 400 `InvalidVerificationToken`,
  410 `VerificationTokenExpired`).
- `POST /api/v1/authentication/sign-in` of an unverified account with the right password answers **403
  `EmailNotVerified`** and sends the email again (at most once per `APP_VERIFICATION_RESEND_COOLDOWN`; each new email
  replaces the previous link).
- `POST /api/v1/authentication/resend-verification` `{"email": "..."}` always answers 202 with the same message, so
  it does not reveal which emails are registered.
- Migration `V6__email_verification.sql` marks **every account that existed before it as verified**, so the demo
  accounts keep signing in. Accounts created after it must verify their email.
- Without Brevo (local, tests, demos) the email is written to the log; copy the link from there.

## Push notifications (Firebase Cloud Messaging)

The backend sends the push notifications with the Firebase Admin SDK (FCM HTTP v1 API). The mobile apps register
the FCM token of the device once the student grants the notification permission.

1. In the Firebase console of the project the apps use: *Project settings > Service accounts > Generate new private
   key*. It downloads a JSON file: **never commit it**.
2. Encode it in one line, `base64 -w0 service-account.json` (macOS: `base64 -i service-account.json`), and put the
   result in `FIREBASE_CREDENTIALS_BASE64` (Render environment only). Delete the local file afterwards.
3. Redeploy. The log shows `Push notifications are sent through Firebase Cloud Messaging.`; without the variable it
   shows `FIREBASE_CREDENTIALS_BASE64 is not set: push notifications are NOT sent, they are written to the log.`

API for the apps:

- `PUT /api/v1/users/me/device-token` `{"token": "<FCM registration token>"}` after the permission is granted and on
  every token refresh (204; 400 `InvalidDeviceToken`). A token registered before by another account (a shared
  device) is moved to the new account.
- `DELETE /api/v1/users/me/device-token` when the permission is denied or revoked, and on sign-out (204).
- When a certificate becomes `Verified` or `Rejected` (US16), the owner receives a notification in Spanish (with
  the reason when it is rejected) and the data `type=CertificateVerificationResolved`, `certificateId`, `status`.
  Without a token nothing is sent; the status is always available in `GET /api/v1/certificates/{id}`. A token that
  FCM reports as unregistered is forgotten.

## Monthly subscription (RevenueCat)

RevenueCat is not a payment processor: Google Play Billing charges the student, and RevenueCat validates the
purchase with Google Play (through a Google Cloud service account) and tells the backend about it. The backend
never trusts the app: it asks RevenueCat for the state of the student, whose **App User ID is the user id of the
API** (the app must call `Purchases.logIn("<userId>")` after sign-in).

### Dashboard

1. **Product and entitlement:** create the monthly subscription product (S/ 29.90) in Google Play Console, import
   it in RevenueCat and attach it to an entitlement with the identifier `premium` (or set
   `REVENUECAT_ENTITLEMENT_ID`).
2. **API key:** *Project settings > API keys > + New secret API key*. Put it in `REVENUECAT_API_KEY` (Render
   environment only, never in the app or the repository).
3. **Webhook:** *Project settings > Integrations > Webhooks > + Add new webhook*:
   - Webhook URL: `https://<service>.onrender.com/api/v1/subscriptions/webhooks/revenuecat`
   - Authorization header value: a long random value, e.g. `Bearer ` followed by `openssl rand -hex 32`. Put
     **the same exact text** in `REVENUECAT_WEBHOOK_AUTH`; the backend compares it in constant time.
   - Environment: *Both* while testing (sandbox purchases, Test Store); *Production only* later, or keep both
     and set `REVENUECAT_ACCEPT_SANDBOX=false`.
4. **Send a test event:** on the webhook page, *Send test event*. The backend answers 200 with
   `{"outcome":"Ignored"}`; a 401 means the Authorization value differs.

### How the backend applies it

- `POST /api/v1/subscriptions`: the app calls it right after the purchase; the backend reads
  `GET https://api.revenuecat.com/v1/subscribers/{userId}` and activates the subscription with the period of the
  `premium` entitlement (422 if RevenueCat reports no active purchase).
- The webhook (`INITIAL_PURCHASE`, `RENEWAL`, `CANCELLATION`, `UNCANCELLATION`, `EXPIRATION`, `BILLING_ISSUE`,
  `PRODUCT_CHANGE`...) is applied once per `event.id` (table `processed_webhook_events`). Whatever the type, the
  state is read again from RevenueCat, as RevenueCat recommends; `TEST` events are only acknowledged. When
  RevenueCat does not answer, the webhook gets 503 and RevenueCat retries it (5, 10, 20, 40 and 80 minutes later).
- `PATCH /api/v1/subscriptions/{id}/cancel` stops the renewals in Google Play through RevenueCat; the plan is kept
  until the end of the paid period. A periodic check (`BILLING_EXPIRATION_CHECK_INTERVAL`) renews or expires the
  subscriptions whose period ended, in case a webhook was lost.

### Testing without Google Play

- **Without RevenueCat at all:** leave `REVENUECAT_API_KEY` empty. Every `POST /api/v1/subscriptions` is an
  approved purchase of `BILLING_SIMULATED_PERIOD` (state in memory; it is lost on restart).
- **RevenueCat Test Store:** RevenueCat can simulate purchases without any Google Play setup (Android SDK 9.9.0 or
  later, using the Test Store API key in the app). The purchases reach the entitlement and the webhooks like real
  ones, with `environment: SANDBOX`, so keep `REVENUECAT_ACCEPT_SANDBOX=true` while testing.

## Smoke test after the deploy

1. `GET /health` answers 200.
2. `POST /api/v1/authentication/sign-up` with a test account, open the link of the verification email (or copy it
   from the log without Brevo), then `sign-in` and use the token in the other calls.
3. `GET /api/v1/wallets/{id}` of that account answers 200 with balance 0.
4. `GET /api/v1/subscriptions/{id}` of that account answers 200 with `"plan": "Free"`.
5. From the RevenueCat dashboard, *Send test event* to the webhook: 200.
