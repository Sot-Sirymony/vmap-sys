# Vision Mapping Management System

Vision Mapping Management System helps users turn broad dreams into structured execution:

```text
Vision Area -> Dream -> Goal -> Step -> Task -> Partner/Resource -> Progress -> Review
```

The project currently includes a Spring Boot backend, React frontend, JWT authentication, PostgreSQL persistence, dashboard views, task board, partner/obstacle tracking, communication messages, reviews, and Excel export/import validation.

## Tech Stack

Backend:
- Java 17
- Spring Boot 3.5.3
- Maven
- Spring Web, Spring Data JPA, Spring Validation, Spring Security
- PostgreSQL for local/main database
- H2 for tests
- Flyway migrations
- Apache POI for Excel
- JJWT for authentication
- JUnit 5 and Mockito-ready test stack

Frontend:
- React 19.2.7
- React DOM 19.2.7
- Vite
- TypeScript
- React Router
- Fetch API
- Vitest and Testing Library

## Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE vision_mapping;
```

Connection settings are in `backend/src/main/resources/application.yml`. The database URL defaults to `jdbc:postgresql://localhost:5432/vision_mapping` and the username defaults to `postgres`, but the password has no default and must be set via environment variable before the backend will start:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD='your-password'
export JWT_SECRET='change-this-secret'
```

Password recovery email is optional. Without `MAIL_HOST` the backend still issues
valid reset tokens but writes the link to the log at WARN instead of sending it,
which is how the flow is exercised locally:

```bash
export MAIL_HOST=smtp.example.com      # unset = log the link instead of sending
export MAIL_PORT=587
export MAIL_USERNAME='smtp-user'
export MAIL_PASSWORD='smtp-password'
export MAIL_FROM='no-reply@yourdomain'
export PASSWORD_RESET_URL='http://127.0.0.1:5173/reset-password'
export PASSWORD_RESET_TTL_MINUTES=30
```

`DB_PASSWORD` is required — the app fails fast on startup if it isn't set. `JWT_SECRET` should also be overridden with a strong value for anything beyond local development.

## Browser Tests

With the backend running on port 8080 and Chrome installed:

```bash
cd frontend
npm run test:e2e
```

Each run builds the frontend, then creates and deletes its own temporary accounts.

## Version 8 Features

Today, Journal, Assessment, Modules, Plan, milestones, plan PDF, data export, account
deletion, optional daily reminders, and a Khmer language option under Account. See
[docs/VMS_BRD_V8.0.0.md](docs/VMS_BRD_V8.0.0.md) and [docs/CHANGELOG.md](docs/CHANGELOG.md).
Daily reminder emails need `MAIL_HOST`, described above.

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs on:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/api/health
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

Frontend runs on:

```text
http://127.0.0.1:5173/
```

## Demo Account

Mock data was inserted for:

```text
Email: demo.vision.mapping@example.com
Password: Password123
```

## Run Tests

Backend:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm test
```

Frontend production build:

```bash
cd frontend
npm run build
```

### Test coverage (BRD 12)

Coverage on `main` from SonarQube Cloud (lines and branches together), commit `f7e7666`, 7 Nov 2026:

| Area | Coverage |
| --- | --- |
| Whole project | 85.1% |
| Backend | 88.1% |
| Frontend | 83.3% (pages 83.4%, vision map 82.2%, other 83.3%) |

About 945 frontend tests (Vitest and React Testing Library) and 590 backend tests (JUnit 5 and Mockito).
CI fails when coverage falls below the minimum, on either side:

```bash
cd backend && mvn verify                 # tests, JaCoCo report and check (target/site/jacoco/index.html)
cd frontend && npm run test:coverage     # tests with coverage and thresholds (coverage/lcov-report/index.html)
```

| Side | Minimum | Where it is set |
| --- | --- | --- |
| Backend | lines 94%, branches 75% | `backend/pom.xml` (`coverage.line.minimum`, `coverage.branch.minimum`) |
| Frontend | lines 85%, statements 84%, branches 78%, functions 78% | `frontend/vite.config.ts` (`test.coverage.thresholds`) |

The minimums are never lowered without the product owner's approval. New code must also reach 80% on
SonarQube's quality gate.

The figure for the current `main`, by area:

```bash
node scripts/coverage-report.mjs            # or --markdown for a table
```

How the tests are written (BRD 12 COV-NFR-01 to COV-NFR-08):

- **Behaviour, not lines.** Each test checks something a user or the business cares about: what is shown,
  what is saved, what is refused. Tests find elements by role and label, as a user would.
- **No outside calls.** Claude, Google sign-in and mail are mocked or stubbed. The Claude client is tested
  against a local stub of the Claude API (`backend/src/test/java/com/visionmapping/service/ai/ClaudeStubServer.java`).
  Both suites pass with outside network access turned off.
- **Shared helpers.** Frontend page tests use `src/test/render.tsx` (`renderPage`: router, signed-in user and
  toasts), `src/test/apiMocks.ts` (empty answers by default) and `src/test/builders.ts` (valid records).
- **Access control.** Backend flow tests check every resource for 400 on invalid input, 401 without a token
  and 404 for another user's record; `AccessControlSweepTests` covers every route.
- **Both languages.** Each page area has at least one test in Khmer.
- **Not flaky.** No fixed sleeps; long form pages set their own time limit.

### Java code quality (BRD 13)

The backend follows *Effective Java*; the rules are listed in `docs/DATA_MODEL.md` under "Java coding
rules". They are checked on every build:

```bash
cd backend
mvn test -Dtest='EffectiveJavaRulesTest,RecordCopiesTest'   # the guard-rail tests on their own
mvn clean verify                                            # everything; compiler warnings fail the build
```

`EffectiveJavaRulesTest` fails when, for example, a core entity gets a public status setter, a class reads
the system clock directly, a public method returns null without `@Nullable`, or a response builder leaves
a field unset. Fix the code; do not add an exception to the test.

The measures from BRD 13's target-state table, read from the source tree (no build needed):

```bash
node scripts/effective-java-report.mjs            # or --markdown for a table
```

### Plan builder sample run (V11, PB-NFR-08)

Before changing plan builder prompts or the model, run the 20 sample dreams in
`backend/src/test/resources/plan-builder-eval/dreams.json` against the real
Claude API. It costs money, so it only runs with a key:

```bash
cd backend
PLAN_EVAL_ANTHROPIC_KEY=sk-ant-... PLAN_EVAL_MODEL=claude-opus-5-5 mvn test -Dtest=PlanBuilderEvalRunTests
```

It fails on rule breaks (dates outside their limits, Khmer dreams answered in
English) and writes `backend/target/plan-builder-eval.md` with counts and cost.

### Khmer review (V11, PB-NFR-06)

Export the Khmer drafts for a Khmer speaker to review:

```bash
cd frontend
node scripts/export-km-review.mjs > ../docs/km-review-v11.csv
```

## API Summary

**Interactive documentation (Swagger UI).** With the backend running on the `local` profile, open
<http://localhost:8080/swagger-ui.html>. The machine-readable OpenAPI description is at
<http://localhost:8080/v3/api-docs>. To call protected endpoints from the page: run `POST /api/auth/login`,
copy the `token` from the answer, choose **Authorize** and paste it (without `Bearer`).

Swagger is on in the `local` and `test` profiles and **off in production**, because a public map of every
endpoint helps an attacker more than a participant. Set `SWAGGER_ENABLED=true` to turn it on deliberately
(and `SWAGGER_ENABLED=false` to turn it off locally). The docs pages get their own Content-Security-Policy;
every API answer keeps the strict one.

Public:
- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/forgot-password` — email a reset link
- `POST /api/auth/reset-password` — redeem the link and set a new password

Protected:
- `POST /api/auth/change-password` — set a new password, current password required
- `/api/vision-areas`
- `/api/dreams`
- `/api/goals`
- `/api/steps`
- `/api/tasks`
- `/api/partners`
- `/api/communication-messages`
- `/api/reviews`
- `/api/obstacles`
- `/api/progress-logs`
- `/api/dashboard`
- `/api/issue-reports`
- `/api/preferences/appearance`
- `/api/excel/import`
- `/api/excel/export`

Protected endpoints require:

```text
Authorization: Bearer <token>
```

## Excel Import / Export

Export:
- Open `Import / Export`
- Click `Export workbook`
- The app downloads `vision-mapping-export-YYYY-MM-DD.xlsx`

Import:
- Open `Import / Export`
- Choose an `.xlsx` workbook
- Current implementation validates required sheet names and reports row counts.
- Import does not yet create database records.

Required sheets:
- Dashboard
- Vision Areas
- Dreams
- Goals
- Steps
- Tasks
- Partners
- Communication
- Reviews
- Obstacles
- Progress Logs
- Instructions

## Deploying to Render

The repo includes a `render.yaml` blueprint that provisions all three pieces — the Spring Boot API, the React static site, and a managed Postgres database — from a single file.

### One-time setup

1. Push this repo to GitHub (already done if you're reading this from the remote).
2. In the Render dashboard: **New > Blueprint**, point it at the repo. Render reads `render.yaml` and proposes three resources: `vision-mapping-db`, `vision-mapping-backend`, `vision-mapping-frontend`.
3. Apply the blueprint. Render provisions the database first, then builds the backend (via `backend/Dockerfile`) and the frontend (static build from `frontend/`).

### How the environment variables are wired

Everything that differs between local dev and Render is env-driven — nothing in `render.yaml` or the app code needs editing for a first deploy, **except** the two placeholder URLs described below.

| Variable | Where it's set | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Auto-injected from `vision-mapping-db` via `fromDatabase` | Backend builds `spring.datasource.url` from these at runtime (`prod` profile) |
| `JWT_SECRET` | Auto-generated by Render (`generateValue: true`) | Signs auth tokens; never reuse the local dev default in production |
| `SETTINGS_ENCRYPTION_KEY` | Auto-generated by Render (`generateValue: true`) | Encrypts the Claude API key saved on the AI settings page. **Required in `prod` (V10):** at least 32 characters and different from `JWT_SECRET`, or the backend refuses to start. Locally, blank falls back to `JWT_SECRET`. A key saved before V10 still opens; changing this key later means the Claude key must be entered again |
| `JWT_EXPIRATION_MINUTES` | `30` in `render.yaml` (default 30) | V10: access-token lifetime. The frontend renews it in the background with a refresh token |
| `SESSION_REMEMBER_ME_DAYS`, `SESSION_HOURS`, `SESSION_REFRESH_GRACE_SECONDS` | Optional (defaults 30, 12, 30) | V10 sessions: how long a "remember me" session and a tab-only session last, and how long a just-replaced refresh token is treated as two tabs racing rather than theft |
| `SECURITY_ALERT_EMAIL` | Set in the Render dashboard (`sync: false`) | V10: where security alerts are emailed (needs `MAIL_HOST`). Alerts always go to the log as `SECURITY ALERT` lines. See [docs/SECURITY.md](docs/SECURITY.md) |
| `SECURITY_EVENTS_RETENTION_DAYS`, `SECURITY_ALERT_WINDOW_MINUTES`, `SECURITY_ALERT_FAILED_LOGINS_PER_ACCOUNT`, `SECURITY_ALERT_FAILED_LOGINS_PER_IP`, `SECURITY_ALERT_REFUSALS_PER_USER` | Optional (defaults 365, 15, 10, 50, 20) | V10 security log retention and alert thresholds |
| `LOGIN_MAX_FAILURES_PER_ACCOUNT`, `LOGIN_MAX_FAILURES_PER_IP`, `LOGIN_WINDOW_MINUTES`, `LOGIN_LOCK_MINUTES` | Optional (defaults 5, 50, 15, 15) | V10 sign-in limits: wrong passwords per email and per network before a lock, and how long it lasts. A password reset lifts an email's lock |
| `UPLOAD_MAX_FILE_SIZE`, `UPLOAD_MAX_REQUEST_SIZE` | Optional (defaults `10MB`, `11MB`) | V10 upload limit for Excel import; larger files get 413 |
| `ANTHROPIC_API_KEY` | Optional | Fallback Claude API key. A key saved on **Settings → AI settings** (admins only) takes priority and can be changed without a restart |
| `PORT` | Set by Render automatically | Backend binds to it via `server.port: ${PORT:8080}` |
| `SPRING_PROFILES_ACTIVE` | `prod` (in `render.yaml`) | Activates the Postgres-from-parts profile instead of `local`/`test` |
| `CACHE_TYPE`, `REDIS_URL` | Set in `render.yaml`; `REDIS_URL` auto-injected from `vision-mapping-cache` via `fromService` | FR-30 Redis cache. Both unset → the backend runs uncached, exactly as before. Locally: start Redis and set `CACHE_TYPE=redis` to enable |
| `SWAGGER_ENABLED` | Not set in `render.yaml` (off) | Turns on Swagger UI and `/v3/api-docs`. Leave it unset in production unless the API description should be public |
| `CORS_ALLOWED_ORIGINS` | Set in `render.yaml`, defaults to the predicted frontend URL | Comma-separated list; must include the frontend's real Render URL |
| `VITE_API_BASE_URL` | Set in `render.yaml`, defaults to the predicted backend URL + `/api` | Baked into the frontend bundle at **build** time, not runtime |

**The one manual step:** Render assigns each service a URL of the form `https://<service-name>.onrender.com`, but only if that name isn't already taken by another Render user — otherwise it appends a suffix. `render.yaml` *predicts* `vision-mapping-backend`/`vision-mapping-frontend` for these URLs. After the first deploy, check the two actual URLs Render assigned:

- If they match the prediction, you're done.
- If either differs, update the other service's env var to match (`CORS_ALLOWED_ORIGINS` on the backend, `VITE_API_BASE_URL` on the frontend) and redeploy that service. A frontend env var change requires a rebuild (it's compiled into the bundle), not just a restart.

### Local Docker build (optional sanity check before pushing)

```bash
cd backend
docker build -t vision-mapping-backend .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal -e DB_PORT=5432 -e DB_NAME=vision_mapping \
  -e DB_USERNAME=postgres -e DB_PASSWORD='your-password' \
  -e JWT_SECRET='a-long-random-string' \
  -e SETTINGS_ENCRYPTION_KEY='another-long-random-string-of-32-plus-chars' \
  -e CORS_ALLOWED_ORIGINS='http://localhost:5173' \
  vision-mapping-backend
```

## Known Limitations

- Excel import validates workbook structure but does not yet persist rows.
- Tests cover key flows but are not exhaustive.

## Future Improvements

- Full Excel row-to-database import.
- Richer dashboard charts.
- More backend business-rule unit tests.
- Frontend tests for forms and API error states.


https://claude.ai/code/artifact/199cd8cd-8af3-4bd9-b68f-9c2d414def2f?via=auto_preview


** Fixing
DATA-1


TRUNCATE TABLE flyway_schema_history  RESTART IDENTITY CASCADE;
