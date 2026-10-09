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
- Hibernate only validates the schema (`ddl-auto=validate`): the tables must already exist in the database.

## Smoke test after the deploy

1. `GET /health` answers 200.
2. `POST /api/v1/authentication/sign-up` and `sign-in` with a test account, then use the token in the other calls.
3. `GET /api/v1/wallets/{id}` of that account answers 200 with balance 0.
