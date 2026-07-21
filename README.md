# Atlas — Payment Service

> Owns the payment lifecycle; charges an external provider exactly once and heals after crashes.

Part of **[Atlas](https://github.com/atlas-event-lab)**. See the
[payment diagrams](https://github.com/atlas-event-lab/atlas/tree/main/diagrams/payment.md).

## Responsibilities

- On `inventory.reserved` (which carries the `amount`), create a payment and drive it to a
  terminal outcome against the provider.
- Never double-charge; always recover a payment stuck mid-flight after a crash.
- Payment state is **independent** from Booking state.

## Tech

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL (`payment_db`) · Kafka · Keycloak JWT.
The external provider is simulated by WireMock (Fake Payment Provider).

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/payments/{paymentId}` | Fetch a payment |

Payment is primarily event-driven; there is no create-payment REST endpoint.

## Events

**Produces:** `payment.requested`, `payment.succeeded`, `payment.failed`, `payment.timed_out`.

**Consumes:** `inventory.reserved` (single trigger; carries `bookingId` + `amount`).

## State machine

`CREATED → PROCESSING → SUCCEEDED | FAILED | TIMED_OUT`. Transient provider errors are
retried inside `PROCESSING` (5s timeout · 3 attempts · 0s/2s/5s backoff) and do not change
state.

## Data

Owns `payment_db` (database-per-service).

## Patterns

Split two-transaction processing around the provider call · provider `Idempotency-Key` =
`paymentId` (no double charge) · idempotent consumers (`ConsumedEvent`) · transactional
outbox · **stale-PROCESSING recovery sweeper** (ADR-0021) · **DLQ replay** endpoint
(ADR-0022) · per-consumer DLT topics (ADR-0023) · idempotency & provider-call metrics
(ADR-0020).

## Run locally

```bash
docker compose up payment-service wiremock     # provider is required
```

Env: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_ISSUER_URI`,
`PAYMENT_PROVIDER_URL`.

## License

Apache-2.0 — see [`LICENSE`](./LICENSE).
