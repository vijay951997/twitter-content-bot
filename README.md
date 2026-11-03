# X Content Bot

AI-assisted drafting and publishing for a personal X account, focused on Tamil Nadu politics, TVK public activity, governance and civic issues — in Tamil, English and Tanglish.

**Nothing is ever posted without your explicit approval.** The approval rule is enforced in the domain model, bound to a hash of the exact approved text, and backed by database constraints. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Spring Boot, PostgreSQL, full Flyway schema, auth, settings, audit, React dashboard, Docker | **Done** |
| 2 | OpenAI integration, prompt templates, Tamil/English/Tanglish drafts, validation, duplicate checks, draft persistence | **Done** |
| 3 | News discovery, source registry, claim verification | Next |
| 4 | Approve / reject / edit / regenerate workflow, audit history | |
| 5 | X OAuth 2.0 PKCE, publishing queue, retries, idempotency | |
| 6 | Scheduler, notifications, analytics | |
| 7 | Security review, hardening, production docs | |

## Stack

Java 21 · Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) · PostgreSQL 16 · Flyway · React 19 · TypeScript · Vite 6 · Tailwind CSS 4 · Docker Compose · JUnit 5 · Mockito · Testcontainers

## Quick start (Docker)

Requires Docker with Compose.

```bash
cp .env.example .env
# Edit .env: set SPRING_DATASOURCE_PASSWORD and APP_ADMIN_PASSWORD (min 12 chars)
docker compose up -d --build
```

Open http://localhost:3000 and sign in with `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`.

| Service | URL |
|---|---|
| Dashboard | http://localhost:3000 |
| API (direct) | http://localhost:8080 |
| Health | http://localhost:3000/actuator/health |

All ports bind to `127.0.0.1` only.

## Local development (without Docker for the app)

Prerequisites: JDK 21, Maven 3.9+, Node 20+, and a PostgreSQL 16 database (the compose `postgres` service is easiest: `docker compose up -d postgres`).

```bash
# Backend
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/xbot
export SPRING_DATASOURCE_USERNAME=xbot
export SPRING_DATASOURCE_PASSWORD=<your db password>
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD=<at least 12 characters>
mvn spring-boot:run

# Frontend (second terminal) — proxies /api to :8080
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Tests

```bash
cd backend
mvn test           # unit tests (79)
mvn verify         # unit + integration tests on real PostgreSQL via Testcontainers (101 total; needs Docker)
                   # OpenAI is replaced by a local stub server — tests never call the real API

cd frontend
npm test           # vitest
npm run build      # type-check + production build
```

What the critical tests cover today:

- An unapproved draft cannot reach `SCHEDULED`, `PUBLISHING` or `PUBLISHED`.
- A rejected or published post cannot move again (no re-publish).
- Editing an approved/scheduled post sends it back to review.
- Database refuses a queue entry without an approval, a second "published" row for the same post, a second live approval, and any edit or delete of audit history.
- API rejects unauthenticated calls, logins and writes without CSRF, invalid schedules and language mixes; settings changes are audited; system status never exposes secrets.

The remaining negative scenarios in the spec (HTTP 429/401 from X, OpenAI timeouts, scheduler running twice, restarts with pending posts) are tested in the phases that introduce those integrations.

## Configuration

All configuration is via environment variables (see `.env.example`). Secrets are never committed or logged.

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_*` | PostgreSQL connection |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | First admin; only used when the users table is empty |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | Both required for draft generation. Use a model that supports Structured Outputs |
| `OPENAI_TIMEOUT_SECONDS`, `OPENAI_MAX_RETRIES` | Optional (defaults 60 and 2) |
| `X_CLIENT_ID`, `X_CLIENT_SECRET`, `X_REDIRECT_URI` | Phase 5 (X developer app with OAuth 2.0 enabled) |
| `APP_TIMEZONE`, `POST_SCHEDULE_*`, `ENABLED_LANGUAGES` | **Initial** values seeded on first start; afterwards edit them in the dashboard |
| `SESSION_COOKIE_SECURE` | `true` when served over HTTPS |
| `APP_CORS_ALLOWED_ORIGINS` | Dev server origin only |

Run with `SPRING_PROFILES_ACTIVE=prod` for structured JSON logs.

## Generating drafts (Phase 2)

Open **Drafts** in the dashboard:

1. Pick a slot (morning / afternoon / evening) and language, or leave language on *Auto* to follow your language mix.
2. Paste **source material**, the facts from a news report or press release, and the **source links**. Only those facts and links can appear in the draft.
3. Click **Generate drafts**. Each draft is checked before it is saved:
   - fits X's 280-character limit (Tamil counts 1 per character, links 23)
   - written in the requested language
   - links only to the URLs you supplied; invented links are removed or the draft is discarded
   - not a repeat of a draft from the last 30 days
   - verification is capped: the AI reading your notes is shown as "Supported by your sources", never "Verified"

Without source material the AI writes a discussion post with no factual claims. **Generate today's 3 discussion drafts** does that for all three slots.

All drafts are saved as `GENERATED`. Nothing is published; approval arrives in Phase 4. Generation is capped at 30 requests per hour (`ai.max_generations_per_hour`) to protect API spend.

## Upgrading an existing install

Extract the new zip over your existing folder (keep your `.env`), then run:

```bash
docker compose up -d --build
```

Database changes apply automatically on startup (Flyway), and existing data is kept.

## External services you will need

- **OpenAI API key** (Phase 2).
- **X developer account and app** with OAuth 2.0 enabled (Phase 5). Posting and metrics access depend on your X API plan; check the current plan limits on the X developer portal before Phase 5. The app never asks for your X password and never automates the X website.

## Project layout

```
backend/    Spring Boot API, Flyway migrations, tests
frontend/   React dashboard, nginx config for Docker
docs/       Architecture, ER diagram, API reference
docker-compose.yml
.env.example
```
