# CLAUDE.md — agent handoff

Operational guide for AI agents (and humans) continuing this project. Read
[docs/DECISIONS.md](docs/DECISIONS.md) for the *why* behind every non-obvious choice.

## Project

Portfolio project for **Miguel** (GitHub `MiguelCorre`), Senior Java Developer targeting
freelance work in banking/payments. The repo demonstrates a production-minded banking
core: modular monolith, real-database testing, JWT security, BDD, migrations, outbox.

**Owner's standing rule: always prefer upgrading the lagging dependency to the latest
version over adding compatibility shims or downgrades.** Verify latest versions against
Maven Central / npm metadata, not from memory.

## Stack

Java 21 (Temurin) · Spring Boot 3.5.x · PostgreSQL 17 · Flyway · Testcontainers 2 ·
Cucumber 7 · springdoc 2.8.x · OpenPDF 3 · Angular 22 (in `frontend/`, standalone +
signals + zoneless) · Node 24 · GitHub Actions CI (backend + frontend jobs).

## Commands

| Task | Command |
|---|---|
| Backend tests (needs Docker running) | `.\mvnw test` |
| Run backend (needs local PostgreSQL) | `.\mvnw spring-boot:run` → :8080 |
| Frontend dev server | `cd frontend && npm start` → :4200, proxies `/api` |
| Frontend build | `cd frontend && npm run build` |
| Single deployable jar | `.\mvnw package -Pwith-frontend` |
| Docker image | `docker build -t minibank .` |

Demo login: `demo@minibank.dev` / `demo1234` (seeded on first run).
Swagger UI: `http://localhost:8080/swagger-ui.html`. Cucumber HTML report:
`target/cucumber-report.html`.

## Dev machine specifics (Windows 11)

- Fresh shells may lack `JAVA_HOME`: set it from the machine scope
  (`$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')`).
- Docker Desktop is often not running; start
  `C:\Program Files\Docker\Docker\Docker Desktop.exe`, CLI lives at
  `C:\Program Files\Docker\Docker\resources\bin`. Wait for `docker info` to answer
  before running tests.
- Local PostgreSQL 17 service on `localhost:5432`, user/password `postgres`/`postgres`,
  database `minibank`.
- GitHub CLI at `C:\Program Files\GitHub CLI\gh.exe`, authenticated as MiguelCorre.
  After every push, watch CI: `gh run watch <id> --exit-status`.
- `~/.testcontainers.properties` pins the npipe strategy — written by Testcontainers,
  leave it alone.

## Version pins — do NOT "fix" these by downgrading the feature

| Pin | Reason |
|---|---|
| Testcontainers **2.0.5** (`testcontainers.version` + `testcontainers-postgresql`) | 1.x cannot talk to Docker Engine 29+ (min API 1.44); symptom: `Status 400` with empty-Info body from npipe. `PostgreSQLContainer` is no longer generic; package is `org.testcontainers.postgresql`. |
| JUnit **5.14.x** (`junit-jupiter.version`) | Cucumber 7.34+ needs JUnit Platform 1.13+ (`DiscoveryIssueReporter`); Boot 3.5 manages an older line. |
| springdoc **2.8.x** | 3.x targets Spring Boot 4 only. Revisit when Boot is upgraded. |
| OpenPDF **3.x** | Packages are `org.openpdf.text.*` (moved from `com.lowagie`). |

Also: `mvnw` must stay LF (`.gitattributes` enforces it) or Docker/CI builds break.

## Architecture rules

- Packages `account`, `transfer`, `ledger`, `auth`, `outbox`, `common` with one-way
  dependencies; `auth` and `common` are foundational. Keep modules extractable.
- `DomainException` is **sealed**; adding a subtype forces a mapping in
  `ApiExceptionHandler`'s exhaustive switch — that is the point, don't work around it.
- Errors are RFC 7807 problem details.
- **Ownership**: a foreign account behaves as missing → **404, never 403** (no
  existence leak). Transfer *source* must be owned; *destination* may be any account.
- Money is `BigDecimal` scale 2; currency lives on the account.
- Flyway owns the schema (`V1..V4`), Hibernate runs `ddl-auto: validate`. For a new
  entity, generate exact DDL with Hibernate schema-generation (see DECISIONS #5), then
  write the next `V__` migration by hand.
- Domain events go through the transactional outbox (`outbox` module); the relay is
  where a real broker would plug in.
- Frontend: the auth interceptor must **never** attach the bearer token to the anonymous
  auth endpoints (`/api/auth/login|register|refresh|logout`) — the resource server
  401s stale tokens even on permitAll routes, which would break silent refresh.

## Testing conventions

- Integration-first: `@SpringBootTest` + MockMvc against Testcontainers PostgreSQL
  (`TestcontainersConfiguration`, `@ActiveProfiles("test")`).
- BDD: Gherkin in `src/test/resources/features`, glue in
  `com.minibank.cucumber.BankingSteps` (single class; instance state = per-scenario
  isolation; unique emails per scenario to dodge the login rate limiter).
- The outbox relay interval is 24h in the test profile — tests call
  `OutboxRelay.publishPending()` directly.
- Current count: **37 tests** (17 JUnit + 20 Cucumber scenarios). Keep it green.

## Deployment state

- `Dockerfile` (multi-stage, non-root) and `fly.toml` are ready and were verified.
- Fly.io apps `minibank` / `minibank-pg` exist under Miguel's account but are stopped:
  the account is an **unpaid trial and Fly kills every machine at exactly ~301s**.
  Blocked on Miguel adding a payment method (he has chosen not to). Do not debug the
  301s deaths again. Do not use `fly postgres create` (single-node repmgr is broken);
  run the plain `postgres:17-alpine` image as a regular app instead.

## Agreed backlog (priority order)

1. FX / multi-currency module (turn the cross-currency 422 into a feature)
2. Observability: Micrometer + Prometheus registry, structured logging
3. Playwright e2e for the Angular frontend
4. (Parked) Fly.io deploy — waiting on paid account
