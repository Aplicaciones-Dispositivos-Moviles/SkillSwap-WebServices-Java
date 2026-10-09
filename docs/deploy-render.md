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

## Smoke test after the deploy

1. `GET /health` answers 200.
2. `POST /api/v1/authentication/sign-up` and `sign-in` with a test account, then use the token in the other calls.
3. `GET /api/v1/wallets/{id}` of that account answers 200 with balance 0.
