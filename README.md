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
- **Modular monolith layout** — `account`, `transfer`, `ledger` and `common` packages with
  one-way dependencies; each module could be extracted to a service later without redesign.
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

Connection settings are overridable via `DB_URL`, `DB_USER` and `DB_PASSWORD`. The app
starts on `http://localhost:8080` and seeds two demo accounts on first run (ids are logged
at startup).

## API

| Method | Path                              | Description                                  |
|--------|-----------------------------------|----------------------------------------------|
| POST   | `/api/accounts`                   | Open an account (`holderName`, `currency`)   |
| GET    | `/api/accounts/{id}`              | Account details and balance                  |
| POST   | `/api/accounts/{id}/deposits`     | Deposit funds (`amount`)                     |
| GET    | `/api/accounts/{id}/ledger`       | Ledger entries, newest first                 |
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
over the version managed by the Spring Boot BOM. Integration tests (`@SpringBootTest` + MockMvc) cover the happy path, idempotent replay,
insufficient funds (422), unknown accounts (404), same-account transfers (400) and the
missing idempotency header (400).

## Design notes

- **Money** is `BigDecimal` with scale 2 and `DECIMAL(19,2)` columns. Currency is per
  account; cross-currency transfers are rejected (`422`) — FX would be its own module.
- **PostgreSQL everywhere** — the app, the Compose service and the Testcontainers tests
  all use PostgreSQL 17, so pessimistic locking and constraint behaviour are exercised on
  the same engine that runs in production. CI (GitHub Actions) runs the full suite on
  every push.
- **No authentication** by design — the focus is the transactional core. Spring Security
  with JWT would sit in front of the controllers without touching the domain.
