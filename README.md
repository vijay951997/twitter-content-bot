# X Content Bot

AI-assisted drafting and publishing for a personal X account, focused on Tamil Nadu politics, TVK public activity, governance and civic issues — in Tamil, English and Tanglish.

**Nothing is ever posted without your explicit approval.** The approval rule is enforced in the domain model, bound to a hash of the exact approved text, and backed by database constraints. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Spring Boot, PostgreSQL, full Flyway schema, auth, settings, audit, React dashboard, Docker | **Done** |
| 2 | OpenAI integration, prompt templates, Tamil/English/Tanglish drafts, validation, duplicate checks, draft persistence | **Done** |
| 3 | News discovery, source registry, robots.txt-aware fetching, AI claim extraction, cross-source verification, drafts from verified news | **Done** |
| 4 | Approve / reject / edit / regenerate workflow, audit history | **Done** |
| 5 | X OAuth 2.0 PKCE, encrypted tokens, publishing queue, retries, idempotency | **Done** |
| 6 | Daily auto-drafting scheduler, notifications UI, analytics | Next |
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
mvn test           # unit tests (113)
mvn verify         # unit + integration tests on real PostgreSQL via Testcontainers (159 total; needs Docker)
                   # OpenAI, news feeds and the X API are replaced by local stub servers — tests never touch the internet

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

All 12 critical negative scenarios from the spec are covered by automated tests: unapproved post, edit after approval (including direct database tampering), rejected post, duplicate publish, X 429, X 401 / expired token, OpenAI timeout, unavailable news source, conflicting sources, publisher running twice at once, and restart with posts mid-send.

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

## News and fact-checking (Phase 3)

Open **News** in the dashboard.

1. **Sources tab:** four starter sources are included: The Hindu (Tamil Nadu), The Hindu (Chennai), BBC Tamil and PIB press releases (off until you test it). Add Tamil dailies, the TN government press-release feed, the Election Commission and official party sites with **+ Add source**. Social media and blog platforms are refused.
2. **Fetch latest news** reads each feed, honours the site's robots.txt, skips unchanged feeds, and keeps only Tamil Nadu-related items for general feeds. Only the title, link, date and later an original AI summary are stored, never article text.
3. **Analyse & fact-check** on an article: the AI writes an original summary and extracts the factual claims. Each claim is then compared with reports from *other publishers* from within 72 hours.

| Claim status | Rule |
|---|---|
| **Verified** | Stated by an official primary source, or reported by two independent publishers (different websites) |
| **Single source** | Reported by one reputable publisher only — drafts must attribute it ("According to …") |
| **Conflicting** | Another publisher reports it differently — shown with both links, never used in drafts |
| **Unverified** | Opinions, predictions, or only low-reliability sources — never used in drafts |

The AI only judges whether two reports say the same thing; the status comes from these fixed rules. Two sections of the same newspaper never count as independent.

4. **Draft from this** builds a draft only from verified or attributed claims. Such a draft can be marked **Verified** only if every fact in it was verified.
5. **Generate today's 3 drafts** (Drafts page) now picks the newest unused fact-checked article for each slot's theme, and falls back to a discussion post when there isn't one.

Automatic fetching and analysis are **off** by default. Turn them on with the settings `news.auto_fetch_enabled` and `news.auto_analyse_enabled`; analysis uses OpenAI credits and is capped by `ai.max_analyses_per_hour` (default 60).

## Reviewing and posting to X (Phases 4 and 5)

**One-time setup**
1. Create an X developer app at console.x.com: Read and write, *Web App, Automated App or Bot*, callback `http://localhost:3000/api/x/callback`.
2. Put `X_CLIENT_ID`, `X_CLIENT_SECRET`, `X_REDIRECT_URI` and a new `APP_TOKEN_ENCRYPTION_KEY` in `.env` (see `.env.example` for the one-line key generator), then `docker compose up -d --force-recreate backend`.
3. Open the dashboard at **http://localhost:3000**, use the same host as the callback, then go to **Settings → Connect X account**. You approve on x.com; the app never sees your password. Tokens are stored AES-256-GCM encrypted.
4. Turn on the **Publishing switch** when you're ready.

**Each draft**
- **Edit**, then **Approve & schedule** (a time in IST, or *Post now*), **Reject**, **Regenerate** or **Save for later**.
- Approval covers the exact text: editing afterwards removes the approval and the schedule.
- You must tick "I've checked these" when a draft has warnings: unverified claims, risk flags, your own edits, similarity to an earlier post, or a link (links cost about 13× more per post on X).
- The publisher runs every 30 seconds. Before sending, it re-checks that the approval is still valid, that the text matches the approved text, and that the publishing switch is on.
- Temporary X errors (429, 5xx) retry with backoff. A post that misses its time by more than an hour goes back to review instead of posting late.
- **History** on each draft shows who generated, edited, approved and published it.

## Content focus

**Settings → Content focus** decides the perspective and topics of new drafts:
- **Voice:** who is speaking. The default is a TVK supporter writing about CM C. Joseph Vijay and the TVK government's schemes.
- **Slot themes:** morning covers new schemes and government orders; afternoon covers CM Vijay updates and party news; evening covers benefits for people.
- **Hashtags:** a list the AI picks 1–3 from, plus at most one scheme- or event-specific tag built from the source. Live "trending" data isn't fetched, because the X API charges per lookup; update the list yourself.
- **Focus keywords** (English and Tamil): matching news is marked ★ and preferred for daily drafts. Tick *Only keep news that matches* to drop everything else.

The honesty rules in the system prompt (`prompts/system-v2.txt`) can't be changed from the dashboard: facts only from sources, no invented figures or quotes, opinions written as opinions, and no personal attacks on opponents. Fact-checking stays neutral whatever the voice.

**TN DIPR** is registered as an official manual source (it has no RSS feed). Paste a DIPR press release into Drafts with its `dipr.tn.gov.in` link: facts from it can then be marked Verified.

## Running on a server

See **[DEPLOY.md](DEPLOY.md)**: automatic HTTPS with Caddy, moving your data from your PC, daily backups, updates and security notes.

## Upgrading an existing install

Extract the new zip over your existing folder (keep your `.env`), then run:

```bash
docker compose up -d --build
```

Database changes apply automatically on startup (Flyway), and existing data is kept.

## External services you will need

- **OpenAI API key** (Phase 2).
- **X developer account and app** with OAuth 2.0 enabled. X API access is pay-per-use: buy credits in the developer console (Billing → Credits). The app never asks for your X password and never automates the X website.

## Project layout

```
backend/    Spring Boot API, Flyway migrations, tests
frontend/   React dashboard, nginx config for Docker
docs/       Architecture, ER diagram, API reference
docker-compose.yml
.env.example
```
