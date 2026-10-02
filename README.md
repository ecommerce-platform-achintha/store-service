# store-service

Stores of the marketplace: store profiles, bank master data and store bank accounts, courier master data, store
couriers and shipping templates, platform settings, the holiday calendar, merchant score and metrics, store reviews
and replies, and the platform fee ledger. The contract is [`docs/marketplace-design.md`](docs/marketplace-design.md)
(sections 1, 3, 4, 7, 9, 10 and 11 apply here).

- Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Maven
- PostgreSQL, schema managed by **Flyway** (`src/main/resources/db/migration`), `ddl-auto=validate`
- Kafka: publishes `store-events` through a transactional outbox, consumes `user-events` and `order-events`
- JWTs validated against user-service's JWKS (RS256, issuer, audience, 30 s skew); deny-by-default security
- Config from the Config Server (`optional:configserver:http://localhost:8888`), registers with Eureka
- Port **8084**

## Prerequisites

| Dependency | Default location | Notes |
|---|---|---|
| Config Server | `http://localhost:8888` | `../config-server`, serves `config-repo/store-service.yml` |
| Eureka (service-registry) | `http://localhost:8761` | `../service-registry`; user-service is called through it (`lb://user-service`) |
| user-service | `http://localhost:8081` | JWKS (`/.well-known/jwks.json`), service tokens and security state |
| PostgreSQL | `localhost:5435`, db `storedb`, user/pass `storeservice` | `docker compose up -d` |
| Kafka | `localhost:9092` | the shared broker from `../order-service`. Optional: without it, events wait in the `outbox` table |

JDK 25, Maven 3.9+ and Docker (docker-compose, Testcontainers) are needed too.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5435` / `storedb` | JDBC URL parts |
| `DB_USERNAME` / `DB_PASSWORD` | `storeservice` / `storeservice` | DB credentials (set `DB_PASSWORD` outside local) |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Config Server |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka `defaultZone` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka (outbox relay and consumers) |
| `KAFKA_TOPIC_USER_EVENTS` / `_STORE_EVENTS` / `_ORDER_EVENTS` | `user-events` / `store-events` / `order-events` | topic names |
| `JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | user-service public keys |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `user-service` / `marketplace` | expected `iss` / `aud` |
| `JWT_CLOCK_SKEW_SECONDS` | `30` | `exp`/`nbf` tolerance |
| `SERVICE_CLIENT_SECRET_STORE` | `local` profile: dev placeholder from the Config Server; otherwise **required** (secret) | client secret for user-service's `POST /internal/auth/service-token` (same value as in user-service) |
| `ACCOUNT_NUMBER_ENCRYPTION_KEY` | none (**required**, secret) | AES-256-GCM key for store bank account numbers: base64 of 32 random bytes |
| `SHEDLOCK_ENABLED` | `true` | scheduler locks (one replica runs the outbox relay at a time) |
| `APP_TIMEZONE` | `Asia/Colombo` | holiday dates and display |

Generate the encryption key once per environment and keep it in your secret store (losing it makes the stored
account numbers unreadable; never commit it):

```bash
export ACCOUNT_NUMBER_ENCRYPTION_KEY="$(openssl rand -base64 32)"
```

Other settings (Config Server or `application.yml`): `app.settings.cache-ttl` (PT60S), `app.outbox.relay-interval`
(PT1S), `app.outbox.batch-size` (100), `app.stores.max-bank-accounts` (10), `security.state-cache.max-size`
(100000) / `.ttl` (PT10M), `security.internal.refresh-before-expiry` (PT30S), `resilience4j.*.userService`,
`app.ports.notification` / `app.ports.image-storage` (`mock`).

The service refuses to start without the encryption key, and outside the `local` profile when the service client
secret is blank or still the `LOCAL-DEV-ONLY` placeholder.

## Run locally

```bash
(cd ../config-server && mvn spring-boot:run)          # own terminal
(cd ../service-registry && mvn spring-boot:run)       # own terminal
(cd ../user-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run)   # JWKS + internal endpoints
docker compose up -d                                  # PostgreSQL on 5435
export ACCOUNT_NUMBER_ENCRYPTION_KEY="$(openssl rand -base64 32)"   # or your stored key
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Swagger UI: http://localhost:8084/swagger-ui.html (use **Authorize** with an access token from user-service).
Health: http://localhost:8084/actuator/health. Only `health` and `info` are exposed.

The database starts empty apart from the platform settings. As the super admin, add banks and couriers first
(`POST /api/admin/banks`, `POST /api/admin/couriers`), e.g.:

```bash
curl -X POST localhost:8084/api/admin/banks -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{"code":"BOC","name":"Bank of Ceylon"}'
curl -X POST localhost:8084/api/admin/couriers -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{"code":"DOMEX","name":"Domex","codSupported":true,
  "trackingUrlTemplate":"https://track.example.com/?no={trackingNumber}","trackingNumberRegex":"^[0-9]{8,12}$"}'
```

## Docker

`docker-compose.yml` runs PostgreSQL only (`docker compose up -d`, `docker compose down -v` to drop the data). The
`Dockerfile` builds the service image (multi-stage, non-root user, port 8084):

```bash
docker build -t store-service .
docker run --rm -p 8084:8084 --add-host host.docker.internal:host-gateway \
  -e DB_HOST=host.docker.internal -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka/ -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e JWKS_URI=http://host.docker.internal:8081/.well-known/jwks.json \
  -e ACCOUNT_NUMBER_ENCRYPTION_KEY -e SERVICE_CLIENT_SECRET_STORE store-service
```

## Security

- Every request with a bearer token is validated against user-service's JWKS: RS256 only, `iss`, `aud`, `exp`/`nbf`
  with 30 s skew, a subject and a known `role`. Identity always comes from the token (`sub`, `storeId`, `perms`).
- **SecurityStateFilter** rejects revoked tokens with 401 `TOKEN_REVOKED`: a Caffeine cache (user id → token version
  and real status) is fed by `user-events` (`UserSecurityChanged`, `UserStatusChanged`); on a miss, or when a token is
  newer than the cache, it asks user-service `GET /internal/users/{id}/security-state` with a service token. If
  user-service cannot be reached the token is accepted (staleness bounded by the 10-minute token lifetime).
- **ServiceTokenProvider** gets a `ROLE_SERVICE` token from user-service (client credentials) and reuses it until 30 s
  before it expires. user-service calls go through Feign + Resilience4j (retry, circuit breaker, 3 s time limit).
- Prefix rules (section 3.4), deny-by-default, repeated with `@PreAuthorize` on every controller:

| Prefix | Who |
|---|---|
| `/api/public/**` (GET) | anyone |
| `/api/customer/**` | `ROLE_CUSTOMER` |
| `/api/merchant/**` | `ROLE_MERCHANT`, or `ROLE_ASSISTANT` with the route's permission (here only `REVIEW_REPLY` on `/api/merchant/reviews/**`; the store profile has no assistant permission) |
| `/api/merchant/owner/**` | `ROLE_MERCHANT` only |
| `/api/admin/**` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` (bank/courier master writes: super admin only) |
| `/api/super-admin/**` | `ROLE_SUPER_ADMIN` |
| `/internal/**` | `ROLE_SERVICE` (never routed by the gateway, not in the OpenAPI document) |

- Every merchant-side query is scoped by the JWT `storeId`; another store's ids answer 404.
- Bank account numbers are AES-256-GCM encrypted (`v1:` + base64(IV ‖ ciphertext ‖ tag), random IV each time),
  shown as `****1234` everywhere except `GET /internal/stores/{id}/bank-accounts`, and never logged. Every bank
  account change writes an audit row with the number masked.
- Free text (store profile, reviews, replies, reasons, names) is sanitized with the OWASP HTML sanitizer.
- Every admin, super-admin, owner and assistant mutation writes an append-only `audit_log` row.

## Stores and visibility

The store row is created under the merchant's JWT `storeId` when the merchant first saves the profile
(`PUT /api/merchant/store`). The store name is unique (case-insensitive) and gets a URL `slug`.

| Owner status (from user-service) | `published` | `acceptingOrders` | Public search / detail |
|---|---|---|---|
| `PENDING_APPROVAL`, `REJECTED` | false | false | hidden |
| `ACTIVE` with a complete profile (name, contact phone and email, address, district) | true | true | listed |
| `BAN_GRACE` (D4, silent) | true | **false** | hidden; the merchant's own view looks unchanged |
| `BANNED` | **false** | false | hidden |

Every change of the two flags emits `StoreVisibilityChanged`. The merchant's view never shows `acceptingOrders` or
the owner status, so a ban in its grace period stays invisible to the merchant.

## Merchant score, metrics and fees

- Consumed from `order-events`; each event id is recorded in `processed_events` in the same transaction.
- Score deltas come from the settings: completed `+1`, response timeout `-2`, late shipment `-1`, late verification
  `-1`, complaint upheld `-3`. A delta applies at most once per (store, order, type) (unique index).
- When the score falls below `score.merchant.at-risk-threshold` (-5), `at_risk_since` is set and admins are alerted
  through `NotificationPort`; nothing automatic happens to the merchant (D2). `GET /api/admin/merchants/at-risk`.
- Metrics: orders received, quoted, rejected, acceptance rate, average first-response time, cancellations and rate,
  late shipments/verifications, upheld complaints, completed orders, revenue.
- Fee ledger (D11): an `ORDER_FEE` line of `fees.per-order-lkr` per completed order (once per order), super-admin
  credits as negative lines. Nothing is invoiced or enforced (`fees.enforced` only accepts `false`).

## API

All `/api` lists are paged: `page`, `size` (max 50, admin lists 100), `sort=field,asc|desc` (whitelisted fields).
Stores are addressed by `publicId` (`STR-YYMM-XXXXXX`), reviews by `REV-`, bank accounts by `BAC-`, shipping
templates by `SHP-`, holidays by `HOL-`, credits by `CRD-`; banks and couriers by their short `code`. UUIDs only
appear in events and internal APIs.

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/api/public/stores` | public | search visible stores: `q` (name), sort `name`, `rating`, `newest` |
| GET | `/api/public/stores/{publicId}` | public | public fields only (no contact, owner or bank data) |
| GET | `/api/public/stores/{publicId}/reviews` | public | visible reviews with the store's reply; sort `createdAt`, `rating` |
| GET | `/api/merchant/store` | merchant | own store profile (404 until created) |
| PUT | `/api/merchant/store` | merchant | create/replace profile: `name, description, contactPhone, contactEmail, addressLine, city, district, logoKey` |
| PUT | `/api/merchant/store/cod` | merchant | `{enabled}` cash on delivery for the store |
| GET | `/api/merchant/owner/bank-accounts/banks` | merchant (owner) | active banks to choose from |
| GET / POST | `/api/merchant/owner/bank-accounts` | merchant (owner) | list (masked) / add `{bankCode, branch, accountName, accountNumber, active?}` (max 10) |
| GET / PUT / DELETE | `/api/merchant/owner/bank-accounts/{publicId}` | merchant (owner) | one account (masked) / change (omit `accountNumber` to keep it) / remove |
| GET / PUT | `/api/merchant/owner/couriers` | merchant (owner) | enabled couriers / replace with `{courierCodes[]}` from the master list |
| GET | `/api/merchant/owner/couriers/available` | merchant (owner) | active master couriers |
| GET / POST | `/api/merchant/owner/shipping-templates` | merchant (owner) | list / add `{courierCode, name, ruleType: FLAT\|PER_DISTRICT\|WEIGHT_BAND, baseAmount, rates[{district\|maxWeightKg, amount}], active?}` (one per enabled courier) |
| GET / PUT / DELETE | `/api/merchant/owner/shipping-templates/{publicId}` | merchant (owner) | one template |
| GET | `/api/merchant/owner/metrics` | merchant (owner) | score, acceptance rate, average response time, cancellation rate, late shipments, completed orders, revenue |
| GET | `/api/merchant/reviews` | merchant, assistant with `REVIEW_REPLY` | reviews of the own store (hidden ones included) |
| POST | `/api/merchant/reviews/{publicId}/reply` | merchant, assistant with `REVIEW_REPLY` | `{text}`, one reply per review |
| POST | `/api/customer/stores/{publicId}/reviews` | customer | `{orderPublicId, rating 1-5, text?, attachmentKeys[]?}` for an own completed order, once per order |
| GET | `/api/admin/stores` | admin | all stores (`q`), owner status, both flags, score |
| GET | `/api/admin/stores/{publicId}` | admin | one store |
| GET | `/api/admin/stores/{publicId}/score` | admin | score details (all metrics) |
| GET | `/api/admin/stores/{publicId}/score-events` | admin | score history |
| GET | `/api/admin/stores/{publicId}/reviews` | admin | all reviews incl. hidden |
| POST | `/api/admin/stores/reviews/{publicId}/hide` / `unhide` | admin | `{reason}`; the rating is recomputed |
| GET | `/api/admin/merchants/at-risk` | admin | stores whose score is below the threshold |
| GET | `/api/admin/banks`, `/api/admin/banks/{code}` | admin | bank master data |
| POST / PUT / DELETE | `/api/admin/banks[/{code}]` | super admin | `{code, name, active?}` / `{name, active}` / delete if unused (`BANK_IN_USE`) |
| GET | `/api/admin/couriers`, `/api/admin/couriers/{code}` | admin | courier master data |
| POST / PUT / DELETE | `/api/admin/couriers[/{code}]` | super admin | `{code, name, trackingUrlTemplate (https, {trackingNumber}), trackingNumberRegex, codSupported, active?}` / delete if unused |
| GET | `/api/super-admin/settings` | super admin | every setting with type, default and range |
| PUT | `/api/super-admin/settings/{key}` | super admin | `{value, reason?}`, validated, audited, history, `SettingsChanged` |
| GET | `/api/super-admin/settings/{key}/history` | super admin | change history |
| GET / POST | `/api/super-admin/holidays` | super admin | list (`from`, `to`) / add `{date, name, type: PUBLIC\|POYA}` (one per date) |
| PUT / DELETE | `/api/super-admin/holidays/{publicId}` | super admin | change / remove (`HolidaysChanged`) |
| POST | `/api/super-admin/merchants/{storePublicId}/credits` | super admin | `{amount, reason}` |
| GET | `/api/super-admin/merchants/{storePublicId}/ledger` | super admin | fee ledger lines and balance |

Internal (`ROLE_SERVICE`, for order-service and the other services):

| Method | Path | Description |
|---|---|---|
| GET | `/internal/stores/{id}` | `id, publicId, name, slug, published, acceptingOrders, visible, codEnabled` |
| GET | `/internal/stores/by-public-id/{publicId}` | same, by public id (checkout sends `storePublicId`) |
| GET | `/internal/stores/{id}/bank-accounts` | active accounts with the **full** number (only for the order's customer), `Cache-Control: no-store` |
| GET | `/internal/stores/{id}/couriers` | enabled couriers with `trackingUrlTemplate`, `trackingNumberRegex`, `codSupported` |
| GET | `/internal/stores/{id}/shipping-estimate?courier=&district=&weight=` | estimate (`weight` in kg); 400 `COURIER_NOT_ENABLED`, 422 `SHIPPING_ESTIMATE_UNAVAILABLE` |
| GET | `/internal/settings` | `[{key, value, type}]` for every setting |
| GET | `/internal/holidays?from=&to=` | holidays in the range (ISO dates, at most 800 days) |

Errors keep the usual shape plus a stable `code` (`timestamp, status, error, code, message, path, fieldErrors`).
Codes: `VALIDATION_FAILED, MALFORMED_REQUEST, BAD_REQUEST, INVALID_SORT, NOT_FOUND, METHOD_NOT_ALLOWED,
UNSUPPORTED_MEDIA_TYPE, CONCURRENT_MODIFICATION, INTERNAL_ERROR, SERVICE_UNAVAILABLE, UNAUTHORIZED, ACCESS_DENIED,
TOKEN_REVOKED, ACCOUNT_BANNED, STORE_NAME_TAKEN, STORE_PROFILE_REQUIRED,
BANK_CODE_TAKEN, BANK_NOT_AVAILABLE, BANK_IN_USE, BANK_ACCOUNT_LIMIT_REACHED, COURIER_CODE_TAKEN,
COURIER_NOT_AVAILABLE, COURIER_IN_USE, COURIER_NOT_ENABLED, SHIPPING_TEMPLATE_EXISTS, SHIPPING_ESTIMATE_UNAVAILABLE,
UNKNOWN_SETTING, INVALID_SETTING_VALUE, HOLIDAY_DATE_TAKEN, REVIEW_ALREADY_EXISTS,
REPLY_ALREADY_EXISTS`.

## Platform settings (section 7)

Seeded by `V1__init.sql`; types and ranges in `SettingKey`. Reads are cached for 60 s; an update drops this
replica's cache on commit and emits `SettingsChanged`.

| Key | Default | Type, range |
|---|---|---|
| `timers.merchant-response-hours` / `customer-confirmation-hours` / `payment-submission-hours` / `payment-verification-hours` | 24 | int 1..720 |
| `timers.ship-by-hours` | 72 | int 1..720 |
| `timers.auto-complete-days` | 7 | int 1..60 |
| `timers.ban-grace-days` | 14 | int 1..90 |
| `score.customer.completed` / `.declined` / `.expired` | 1 / -1 / -1 | int 0..100 / -100..0 |
| `cod.objection-window-days` | 7 | int 1..60 |
| `score.customer.cod-refusal-limit` / `.cod-suspension-months` | 3 / 3 | int 1..20 / 1..36 |
| `score.merchant.completed` | 1 | int 0..100 |
| `score.merchant.response-timeout` / `.late-shipment` / `.late-verification` / `.complaint-upheld` | -2 / -1 / -1 / -3 | int -100..0 |
| `score.merchant.at-risk-threshold` | -5 | int -10000..10000 |
| `orders.max-open-unconfirmed-per-customer` | 10 | int 1..1000 |
| `orders.max-quote-revisions` | 2 | int 0..20 |
| `complaints.window-days` | 30 | int 1..365 |
| `merchant.max-application-attempts` | 3 | int 1..20 |
| `auth.access-token-minutes` / `.refresh-days` / `.max-failed-logins` / `.lockout-minutes` | 10 / 14 / 5 / 15 | int 1..60 / 1..90 / 1..50 / 1..1440 |
| `fees.monthly-lkr` / `.per-order-lkr` | 500.00 / 10.00 | decimal (2 places) 0..1000000 / 0..100000 |
| `fees.enforced` | false | boolean, only `false` accepted (D11) |

## Events

### Published: `store-events` (outbox)

Written to the `outbox` table in the same transaction as the change; a relay (every second, ShedLock) publishes them
in order and marks them published (at-least-once: consumers de-duplicate by `eventId`).

| eventType | Key | When |
|---|---|---|
| `StoreVisibilityChanged` | store UUID | store created, profile completion changed the flags, owner status changed (`change`: `CREATED`, `PROFILE_UPDATED`, `OWNER_<STATUS>`) |
| `SettingsChanged` | `00000000-…` | a setting was updated (`settingKey`, `settingValue`) |
| `HolidaysChanged` | `00000000-…` | a holiday was added, moved or removed (`holidayDate`, `change`) |

```json
{"eventId":"…","eventType":"StoreVisibilityChanged","occurredAt":"2026-10-01T06:42:25Z","aggregateId":"…",
 "storeId":"…","storePublicId":"STR-2610-7K2M9Q","storeName":"Lanka Crafts","published":true,
 "acceptingOrders":false,"visible":false,"codEnabled":true,"change":"OWNER_BAN_GRACE"}
```

### Consumed (group `store-service`)

`user-events` (from user-service): `UserSecurityChanged` and `UserStatusChanged` update the security-state cache;
`UserStatusChanged` of a `ROLE_MERCHANT` with a `storeId` drives the store visibility.

`order-events` (from order-service). Fields read (others are ignored): `eventId, eventType, occurredAt, orderId,
orderPublicId, storeId, storePublicId, customerId, status, terminalStatus, reason, grandTotal, placedAt,
quoteRevision, merchantPenalty, decision`.

| eventType | Effect |
|---|---|
| `OrderPlaced` | orders received +1 |
| `OrderQuoted` | first quote (`quoteRevision` absent or 1): orders quoted +1, response time `occurredAt - placedAt` |
| `OrderCancelled` | cancellations +1; `terminalStatus=REJECTED_BY_MERCHANT`: rejection + response time; `EXPIRED_MERCHANT`: response-timeout penalty |
| `OrderCompleted` | completed +1 score, revenue `+grandTotal`, `ORDER_FEE` ledger line, review eligibility for `customerId` |
| `ComplaintDecided` | `decision=UPHELD`: complaint-upheld penalty |
| `OrderShipmentOverdue` / `OrderVerificationOverdue` | late-shipment / late-verification penalty |
| any, with `merchantPenalty` = `RESPONSE_TIMEOUT` \| `LATE_SHIPMENT` \| `LATE_VERIFICATION` \| `COMPLAINT_UPHELD` | that penalty (once per order) |

A message that is not valid JSON is logged and skipped; a failing handler is retried with exponential back-off for
about a minute, then logged and skipped.

## Mock ports

`ports.NotificationPort` (admin alerts) and `ports.ImageStoragePort` (store logos, review attachments: accepts every
key, returns a placeholder URL) have mock adapters that log and always succeed, selected by `app.ports.notification`
/ `app.ports.image-storage` (`mock`).

## Tests

```bash
mvn clean package        # Docker required (Testcontainers PostgreSQL); Kafka is embedded, user-service is WireMock
```

- `AuthorizationMatrixTest`: every endpoint × anonymous, customer, merchant, assistant (all / no permissions), admin,
  super admin and service → expected 200/201/400/401/403/404
- `OwnershipTest`: merchant A cannot read or change store B's bank accounts, templates or reviews (404); assistants
  need `REVIEW_REPLY` and never reach owner-only routes
- `SecurityStateFilterTest`: revoked tokens (event-fed cache), cache miss → user-service with a cached service token,
  unknown user, newer token refresh, invalid audience/expiry/signature
- `PlatformSettingsIntegrationTest`, `SettingValidatorTest`: seeded defaults = contract, type/range validation,
  history, audit, `SettingsChanged`, 60 s cache
- `BankAccountEncryptionTest`, `AccountNumberCipherTest`: encrypted at rest, masked reads and audit, internal full
  number, tamper detection, key checks
- `OrderEventConsumerTest`, `UserEventConsumerTest`: idempotency, score deltas from settings, at-risk alert, metrics,
  fee ledger, ban grace / ban / unban visibility, consumption from Kafka
- `StoreProfileIntegrationTest`, `ShippingEstimateTest`, `StoreReviewIntegrationTest`, `HolidayIntegrationTest`,
  `OutboxRelayIntegrationTest`, `SlugsTest`
