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
| `REVIEW_DEADLINE_CHECK_INTERVAL` | no | How often the assigned verification cases whose review deadline passed are reassigned to another verifier (the breach counts in the reliability of the original one). Default `15m`. |
| `MODERATION_ASSIGNMENT_RETRY_INTERVAL` | no | How often the suspicious certificates escalated while no verifier was available are offered again to a Verificador senior (or another verifier). Default `15m`. |

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
2. `POST /api/v1/authentication/sign-up` and `sign-in` with a test account, then use the token in the other calls.
3. `GET /api/v1/wallets/{id}` of that account answers 200 with balance 0.
4. `GET /api/v1/subscriptions/{id}` of that account answers 200 with `"plan": "Free"`.
5. From the RevenueCat dashboard, *Send test event* to the webhook: 200.
