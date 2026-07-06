# minibank

[![CI](https://github.com/MiguelCorre/minibank/actions/workflows/ci.yml/badge.svg)](https://github.com/MiguelCorre/minibank/actions/workflows/ci.yml)

A small but production-minded **modular monolith** for core banking flows: accounts, deposits,
and **idempotent money transfers** with a double-entry ledger.

Built with **Java 21** and **Spring Boot 3.5**.

## Why this project

It demonstrates the patterns that matter in payments/banking backends:

- **Idempotent transfers** — `POST /api/transfers` requires an `Idempotency-Key` header.
  Replaying the same key returns the original transfer and never debits twice. A unique
  constraint on the key is the backstop for concurrent duplicates (surfaced as `409`).
- **Deadlock-free concurrency** — both accounts are locked with `PESSIMISTIC_WRITE` in a
  stable (UUID) order, so two opposite transfers running concurrently cannot deadlock.
  Accounts also carry a `@Version` column for optimistic-lock detection elsewhere.
- **Double-entry ledger** — every transfer writes an immutable DEBIT and CREDIT entry with
  the balance after the movement; deposits write a single CREDIT. Balances are auditable.
- **RFC 7807 error responses** — a sealed `DomainException` hierarchy is mapped to HTTP
  statuses with an exhaustive pattern-matching `switch` (Java 21): adding a new domain
  error fails compilation until it is mapped.
- **Stateless JWT security** — register/login issue short-lived (15 min) HS256 access
  tokens via Spring Security's own `JwtEncoder`/`JwtDecoder` (no third-party JWT library);
  `/api/**` requires a bearer token, passwords are BCrypt-hashed.
- **Rotating refresh tokens** — opaque 256-bit tokens (only the SHA-256 hash is stored)
  with single-use rotation; presenting an already-rotated token is treated as theft and
  revokes the user's whole token family. Failed logins are rate-limited (5 per 15 min,
  then `429`).
- **Resource-level authorization** — accounts belong to the customer who opened them;
  someone else's account behaves exactly like a missing one (`404`, no existence leak).
  Anyone may still be the *destination* of a transfer, like a regular bank payment.
- **Daily transfer limits** — outgoing transfers are capped per account per UTC day
  (configurable via `DAILY_TRANSFER_LIMIT`, default 1000.00); exceeding it returns `422`.
- **OpenAPI documentation** — springdoc generates the spec from the code; Swagger UI at
  `/swagger-ui.html` with the bearer scheme wired in.
- **Transactional outbox** — `TransferCompleted` domain events are stored in the same
  transaction as the transfer; a scheduled relay publishes them with at-least-once
  semantics (a log line here — a Kafka producer in production, same pattern).
- **Statements** — per-period account statements as **CSV or PDF** (OpenPDF) with opening
  and closing balances; the ledger endpoint is paginated.
- **Versioned schema with Flyway** — migrations own the database
  (`src/main/resources/db/migration`), Hibernate runs in `validate` mode.
- **Modular monolith layout** — `account`, `transfer`, `ledger`, `auth` and `common`
  packages with one-way dependencies; each module could be extracted to a service later
  without redesign.
- **Java 21 features** — records for DTOs, sealed classes, pattern matching for switch,
  and virtual threads enabled (`spring.threads.virtual.enabled=true`).

## Requirements

- JDK 21 (Maven wrapper included, no Maven install needed)
- PostgreSQL — either a local instance or the provided Docker Compose service
- Docker (for Testcontainers-based tests and/or the Compose database)

## Run

With a local PostgreSQL on `localhost:5432` (database `minibank`, user/password `postgres`):

```bash
./mvnw spring-boot:run
```

Or start the database with Docker Compose (mapped to host port **5433** to avoid clashing
with a locally installed PostgreSQL):

```bash
docker compose up -d
DB_URL=jdbc:postgresql://localhost:5433/minibank ./mvnw spring-boot:run
```

Connection settings are overridable via `DB_URL`, `DB_USER` and `DB_PASSWORD`; the JWT
signing key via `JWT_SECRET`. The app starts on `http://localhost:8080` and seeds a demo
user (`demo@minibank.dev` / `demo1234`) plus two demo accounts on first run.

### Single-jar deployment

```bash
./mvnw package -Pwith-frontend
java -jar target/minibank-0.1.0.jar
```

The profile builds the Angular app and embeds it under `/static`; Spring serves the SPA
(with an index.html fallback for client-side routes) and the API from one origin.

### Docker / cloud deployment

A multi-stage [`Dockerfile`](Dockerfile) builds everything (frontend included) and runs
the jar as a non-root user on a JRE-only image:

```bash
docker build -t minibank .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/minibank \
  -e JWT_SECRET=$(openssl rand -hex 32) minibank
```

[`fly.toml`](fly.toml) is included for Fly.io — `fly launch --no-deploy`, set the
`JWT_SECRET`/`DB_*` secrets (a managed PostgreSQL like Fly Postgres or Neon works), then
`fly deploy`. Any Docker-based host (Railway, Render, Cloud Run) works the same way.

## Frontend

An **Angular 22** SPA lives in [`frontend/`](frontend) — login/registration, accounts
dashboard, account detail with ledger, deposits, and transfers with a client-generated
`Idempotency-Key` (kept across retries, renewed after success). Standalone components,
signals, zoneless change detection, a functional HTTP interceptor for the bearer token
and route guards for the session.

```bash
cd frontend
npm ci
npm start        # dev server on :4200, /api proxied to the backend on :8080
```

## API

Interactive documentation: **Swagger UI** at `http://localhost:8080/swagger-ui.html`
(OpenAPI spec at `/v3/api-docs`).

All `/api/**` endpoints except the anonymous auth endpoints require an
`Authorization: Bearer <token>` header. Account resources are scoped to their owner —
other customers' accounts return `404`.

| Method | Path                              | Description                                  |
|--------|-----------------------------------|----------------------------------------------|
| POST   | `/api/auth/register`              | Create a user (`email`, `password`, `displayName`) |
| POST   | `/api/auth/login`                 | Credentials → access + refresh token pair    |
| POST   | `/api/auth/refresh`               | Rotate the refresh token, new pair           |
| POST   | `/api/auth/logout`                | Revoke the refresh token                     |
| GET    | `/api/auth/me`                    | Current user from the token                  |
| POST   | `/api/accounts`                   | Open an account (`holderName`, `currency`)   |
| GET    | `/api/accounts`                   | List accounts, newest first                  |
| GET    | `/api/accounts/{id}`              | Account details and balance                  |
| POST   | `/api/accounts/{id}/deposits`     | Deposit funds (`amount`)                     |
| GET    | `/api/accounts/{id}/ledger`       | Ledger entries, newest first (paginated: `page`, `size`) |
| GET    | `/api/accounts/{id}/statement`    | CSV/PDF statement (`from`, `to`, `format`)   |
| POST   | `/api/transfers`                  | Transfer (requires `Idempotency-Key` header) |
| GET    | `/api/transfers/{id}`             | Transfer details                             |
| GET    | `/actuator/health`                | Health check                                 |

### Example session

```bash
# open two accounts
FROM=$(curl -s -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"holderName":"Alice","currency":"EUR"}' | jq -r .id)
TO=$(curl -s -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"holderName":"Bob","currency":"EUR"}' | jq -r .id)

# fund the source account
curl -s -X POST localhost:8080/api/accounts/$FROM/deposits \
  -H 'Content-Type: application/json' -d '{"amount":500.00}'

# transfer — replaying this exact command is safe (same Idempotency-Key)
curl -s -X POST localhost:8080/api/transfers \
  -H 'Content-Type: application/json' -H "Idempotency-Key: demo-001" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amount\":150.00,\"description\":\"rent\"}"

# audit trail
curl -s localhost:8080/api/accounts/$FROM/ledger | jq
```

## Tests

```bash
./mvnw test
```

Tests run against a **real PostgreSQL via Testcontainers 2** (Docker required) — the
`@ServiceConnection` container replaces the datasource automatically, so what is tested is
what runs in production. Note: Testcontainers 1.x cannot talk to Docker Engine 29+
(minimum API version raised to 1.44), which is why this project pins Testcontainers 2.x
over the version managed by the Spring Boot BOM.

### BDD acceptance tests (Cucumber)

Business-readable specifications live in
[`src/test/resources/features`](src/test/resources/features) — authentication (including
refresh-token rotation and login rate limiting), accounts, and transfers (idempotent
replay, insufficient funds, cross-currency rejection). They boot the full application via
`@CucumberContextConfiguration` and drive it through MockMvc; an HTML report is written to
`target/cucumber-report.html` on every run. Integration tests (`@SpringBootTest` + MockMvc) cover the happy path, idempotent replay,
insufficient funds (422), unknown accounts (404), same-account transfers (400) and the
missing idempotency header (400).

## Design notes

The reasoning behind every non-obvious choice is recorded in
[docs/DECISIONS.md](docs/DECISIONS.md); agent/contributor operational notes live in
[CLAUDE.md](CLAUDE.md).

- **Money** is `BigDecimal` with scale 2 and `DECIMAL(19,2)` columns. Currency is per
  account; cross-currency transfers are rejected (`422`) — FX would be its own module.
- **PostgreSQL everywhere** — the app, the Compose service and the Testcontainers tests
  all use PostgreSQL 17, so pessimistic locking and constraint behaviour are exercised on
  the same engine that runs in production. CI (GitHub Actions) runs the full suite on
  every push.
- **JWT in localStorage** on the frontend keeps the demo simple; a production build would
  weigh httpOnly cookies + CSRF against it. Tokens expire after 1h and the UI redirects to
  the login page on 401.
