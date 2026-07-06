# Architecture decisions

Lightweight ADRs: what was decided, why, and what it costs. Newest last.

## 1. Modular monolith, not microservices

One deployable with `account`, `transfer`, `ledger`, `auth`, `outbox` and `common`
packages and one-way dependencies. A transfer is one ACID transaction across two
accounts and the ledger — exactly the case where a distributed system would need sagas
for no benefit at this scale. Each module keeps its own entities/services/controllers so
extraction later is mechanical, and the outbox (#8) already provides the integration
seam.

## 2. Testcontainers 2 instead of lowering Docker's API floor

Docker Engine 29 raised the minimum API version to 1.44, which breaks Testcontainers
1.x (the failure is cryptic: `Status 400` with an empty Info body on Windows named
pipes). Two fixes existed: pin `min-api-version: "1.24"` in the Docker daemon, or move
to Testcontainers 2. We chose the upgrade — project policy is latest-of-everything, and
a daemon shim would have silently broken on every teammate's machine. Cost: TC2 changed
coordinates (`testcontainers-postgresql`) and dropped the self-referential generic on
`PostgreSQLContainer`.

## 3. JWTs issued by Spring Security itself

`JwtEncoder`/`JwtDecoder` (Nimbus, HS256 symmetric key) rather than jjwt/auth0
libraries: fewer dependencies, and the resource-server side is the same
`oauth2ResourceServer` machinery a client would use against Keycloak — swap the decoder
for an issuer URI and nothing else changes. Access tokens 15 min; the long session
lives in the refresh token.

## 4. Rotating refresh tokens with reuse detection

Opaque 256-bit tokens, only the SHA-256 hash stored. Each refresh consumes the token
and issues a new one; presenting an already-rotated token revokes the user's whole
token family (theft assumption). Subtlety that cost a failing test: the revocation must
survive the exception that rejects the request — hence
`@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`.

## 5. Flyway owns the schema; Hibernate validates

`ddl-auto: validate` in every environment. Migrations were **generated from the
entities** to guarantee validate passes, then formatted by hand:

```
.\mvnw compile exec:java "-Dexec.mainClass=com.minibank.MinibankApplication" ^
  "-Dexec.args=--spring.profiles.active=test --spring.main.web-application-type=none ^
  --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none ^
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create ^
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/generated-schema.sql"
```

V3 (ownership) deletes pre-ownership demo rows before adding the NOT NULL column — the
project had no production data, and a deterministic reset beats a fake default owner.

## 6. Foreign resources return 404, not 403

A 403 on someone else's account id confirms the account exists — an information leak.
Ownership filters treat foreign accounts exactly like missing ones. Transfer *sources*
must be owned by the caller; *destinations* may be any account (that is how bank
payments work).

## 7. Sealed exception hierarchy mapped by exhaustive switch

`DomainException` is sealed; `ApiExceptionHandler` maps subtypes to HTTP statuses with
a pattern-matching switch and no default. Adding a domain error without deciding its
status is a compile error. This is deliberate friction — keep it.

## 8. Transactional outbox for domain events

`TransferCompleted` is written to `outbox_events` by a synchronous `@EventListener`
inside the transfer's transaction: state change and event commit or roll back together.
A `@Scheduled` relay publishes pending events (at-least-once) — today a log line,
in production a Kafka/SQS producer in the same loop. This is the seam for extracting
notifications/fraud/analytics without dual-write bugs.

## 9. Custom `PageResponse` instead of serializing Spring Data's `Page`

Spring Data's `Page` JSON is an implementation detail that has changed between
versions. The five-field envelope (`content`, `page`, `size`, `totalElements`,
`totalPages`) is a stable public contract and trivially mirrored in the Angular client.

## 10. Idempotency and lock ordering in transfers

Idempotency: lookup by key first (replay returns the original), unique constraint as
the backstop for concurrent duplicates (409). Concurrency: both accounts locked
`PESSIMISTIC_WRITE` in UUID order so opposite concurrent transfers cannot deadlock;
`@Version` on accounts as an extra guard. Daily limits are computed inside the same
transaction by summing the day's outgoing transfers.

## 11. Frontend: silent refresh must not send stale bearers

The Angular interceptor retries a 401 once after refreshing the session (single-flight
so concurrent 401s spend one rotation). Hard-won: the refresh call itself must NOT
carry the stale access token — Spring Security authenticates any presented bearer even
on permitAll endpoints and would 401 the refresh. Anonymous auth paths are explicitly
excluded from token attachment.

## 12. Fly.io postmortem (deploy parked)

The Dockerfile/fly.toml deploy worked end-to-end (login, transfers, refresh rotation
verified in production). Two real findings, then a commercial blocker:

- `shared-cpu-1x` starves the JVM at boot; PostgreSQL times out half-open connections
  (EOF mid-auth) → use `shared-cpu-2x`+ and a 120s health-check grace period.
- `fly postgres create` (postgres-flex) is broken for single-node clusters: repmgrd
  never initializes and the supervisor kills the machine. Run plain
  `postgres:17-alpine` as a regular Fly app with a volume instead.
- **Blocker:** unpaid trial accounts stop every machine at exactly ~301s of uptime
  (documented Fly trial behavior). Deploy resumes if/when a payment method is added —
  everything else is ready.
