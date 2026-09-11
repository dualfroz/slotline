# slotline

A small reservation platform built with Spring Boot. Resources (rooms, courts,
equipment) are booked for time slots, and the core guarantee is that a single
slot can never be double-booked, even under heavy concurrency.

The project is deliberately focused on the parts that are hard to get right in a
booking system: concurrency, transactions, security, and a documented API.

## Tech stack

- Java 21, Spring Boot 3.5
- Spring Web, Spring Data JPA, Spring Security (stateless JWT)
- Flyway for schema migrations (vendor-specific: H2 for dev/test, PostgreSQL for deploy)
- H2 in-memory (dev/test), PostgreSQL (deploy profile)
- springdoc-openapi for Swagger UI
- Maven Wrapper (`./mvnw`) - no local Maven install required

## Running it

Prerequisites: a JDK 21. The build uses the Maven Wrapper, so Maven itself does
not need to be installed.

```bash
# dev profile (H2 in-memory), starts on http://localhost:8080
./mvnw spring-boot:run

# run the test suite (unit + integration + the concurrency test)
./mvnw test
```

On startup a default ADMIN account is seeded if none exists (see Configuration).

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Architecture

Layered, one package per bounded slice:

```
com.slotline
  config/       SecurityConfig, OpenApiConfig, SlotlineProperties, AdminBootstrap
  security/     JwtService, JwtAuthenticationFilter, AuthenticatedUser, RestAuthenticationHandlers
  user/         User, Role, AuthService, AuthController, dto/
  resource/     Resource, ResourceService, ResourceController, dto/
  reservation/  Reservation, ReservationService, ReservationController, dto/
  common/       domain exceptions + GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

- Controllers are thin; all logic and transaction boundaries live in the services.
- Requests and responses are immutable records; entities never leave the service layer.
- Errors are returned as `application/problem+json` (ProblemDetail), including
  field-level validation violations.

## No double-booking under concurrency (the core feature)

Booking a slot runs inside a single transaction in `ReservationService.create`:

1. The target resource row is loaded with a pessimistic write lock
   (`SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`), which serializes all
   concurrent bookings for that one resource.
2. An overlap query looks for any active reservation on the resource where
   `existing.start < new.end AND existing.end > new.start`.
3. If an overlap exists, the booking is rejected with `409 Conflict`; otherwise the
   reservation is inserted.

Because the resource row is locked for the duration of the check-and-insert, two
concurrent requests for overlapping slots cannot both pass the overlap check.
The lock is released on commit, so the loser sees the committed reservation and
gets a clean `409`.

Extra layers of defense:

- `@Version` optimistic locking on `Resource` and `Reservation` protects against
  lost updates on other concurrent operations (for example a concurrent cancel).
  Any optimistic-lock failure is also mapped to `409`.
- The PostgreSQL migration adds a partial unique index
  `(resource_id, start_time, end_time) WHERE status = 'ACTIVE'` as a
  database-level backstop in production.

Why pessimistic locking for the booking path rather than optimistic alone: it is
deterministic across H2 and PostgreSQL and it correctly rejects arbitrary
overlapping slots, not just byte-identical ones. Optimistic `@Version` is kept as
a second layer, and the unique index is the last line of defense in production.

This is verified by `ReservationConcurrencyTest`: 12 threads are released
simultaneously against the same slot and the test asserts exactly one success,
eleven `409`s, and exactly one active reservation in the database. Removing the
pessimistic lock makes the test fail (all 12 succeed), so the test genuinely
exercises the guarantee.

## API

Authentication is a Bearer JWT obtained from `/api/auth/login`.

| Method | Path                                     | Access      | Notes                         |
|--------|------------------------------------------|-------------|-------------------------------|
| POST   | /api/auth/register                       | public      | creates a USER account (201)  |
| POST   | /api/auth/login                          | public      | returns a JWT                 |
| GET    | /api/resources                           | USER, ADMIN | list resources                |
| GET    | /api/resources/{id}                      | USER, ADMIN | single resource               |
| POST   | /api/resources                           | ADMIN       | create resource (201)         |
| PUT    | /api/resources/{id}                      | ADMIN       | update resource               |
| DELETE | /api/resources/{id}                      | ADMIN       | delete resource (204)         |
| POST   | /api/reservations                        | USER, ADMIN | book a slot (201) or 409      |
| GET    | /api/reservations/mine                   | USER, ADMIN | caller's reservations         |
| POST   | /api/reservations/{id}/cancel            | owner/ADMIN | cancel a reservation          |
| GET    | /api/resources/{resourceId}/reservations | USER, ADMIN | active reservations           |

## curl examples

An end-to-end script lives at `scripts/smoke.sh` (happy path plus the 409
conflict). It expects a running instance:

```bash
BASE_URL=http://localhost:8080 ./scripts/smoke.sh
```

The individual calls:

```bash
# log in as the seeded admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# create a resource (ADMIN only)
curl -s -X POST http://localhost:8080/api/resources \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Court 1","description":"Outdoor tennis court"}'

# register and log in as a normal user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}'
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# book a slot -> 201
curl -s -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"resourceId":1,"startTime":"2026-09-01T10:00:00Z","endTime":"2026-09-01T11:00:00Z"}'

# book the same slot again -> 409 Conflict
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"resourceId":1,"startTime":"2026-09-01T10:00:00Z","endTime":"2026-09-01T11:00:00Z"}'
```

## Testing

```bash
./mvnw test
```

Suite:

- `JwtServiceTest` - token issue/parse round trip and rejection of tokens signed with another key.
- `ReservationApiTest` - full HTTP flow through Spring Security: register, login, role enforcement (USER cannot create resources), book, 409 on conflict, cancel, and re-book after cancel.
- `ReservationConcurrencyTest` - the concurrency guarantee described above.
- `SlotlineApplicationTests` - context loads.

## Docker

Multi-stage build (JDK 21 to build, JRE 21 to run):

```bash
docker build -t slotline:latest .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=postgres \
  -e SLOTLINE_DB_URL=jdbc:postgresql://db:5432/slotline \
  -e SLOTLINE_DB_USERNAME=slotline \
  -e SLOTLINE_DB_PASSWORD=secret \
  -e SLOTLINE_JWT_SECRET=please-change-this-to-a-long-random-secret \
  slotline:latest
```

Without `SPRING_PROFILES_ACTIVE=postgres` the container runs on the in-memory H2
database (useful for a quick demo, not for real data).

## Configuration

All values are overridable by environment variable:

| Variable                  | Default                | Purpose                            |
|---------------------------|------------------------|------------------------------------|
| SLOTLINE_JWT_SECRET       | dev-only placeholder   | HMAC signing key (set in prod)     |
| SLOTLINE_ADMIN_USERNAME   | admin                  | seeded admin username              |
| SLOTLINE_ADMIN_PASSWORD   | admin12345             | seeded admin password (set in prod)|
| SPRING_PROFILES_ACTIVE    | (dev / H2)             | set to `postgres` for deploy       |
| SLOTLINE_DB_URL           | localhost postgres     | JDBC url (postgres profile)        |
| SLOTLINE_DB_USERNAME       | slotline               | DB user (postgres profile)         |
| SLOTLINE_DB_PASSWORD       | slotline               | DB password (postgres profile)     |

The dev JWT secret and admin password are for local use only and must be
overridden before any real deployment.

## Wave 2 (depth)

Wave 2 is additive: the v1 anti-double-booking core (pessimistic lock plus
overlap check plus the PostgreSQL partial unique index) is untouched and its
concurrency test still passes. New capabilities:

- Multi-tenant. Every user, resource and reservation belongs to an
  `Organization`. The JWT carries an `org` claim and all resource and
  reservation queries are scoped to the caller's organization, so a tenant can
  neither see nor book another tenant's resources (cross-tenant access is a 404).
  Registration accepts an optional `organizationSlug`; without one the account
  joins the seeded `default` organization.
- Recurring reservations. `POST /api/reservations/recurring` takes an RRULE
  subset (`FREQ=DAILY|WEEKLY`, `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY`), expands it
  into concrete slots in the resource time zone, and books the whole series in
  one transaction (all-or-nothing: any conflict rolls the series back).
- Availability engine. Per-resource operating hours, blackout date ranges and a
  buffer (minutes required between reservations). A booking outside the rules is
  rejected with `422 Unprocessable Entity`; a straight slot clash is still `409`.
- Waitlist. `POST /api/reservations/waitlist` records interest in a taken slot.
  When a reservation is cancelled the oldest matching waiter is promoted into a
  real reservation and notified.
- Notifications. A `NotificationChannel` abstraction (email channel logs;
  webhook channel is enabled only when `slotline.notifications.webhook.url` is
  set) fans out on booking, cancellation and waitlist promotion. Fully testable
  without sending anything by registering a recording channel.
- Resource groups. Nestable `resource-group` hierarchy with resource assignment.
- Admin reports. `GET /api/admin/reports/resources/{id}/occupancy?from=..&until=..`
  and `.../history` (ADMIN only, tenant-scoped).
- iCal export. `GET /api/resources/{id}/calendar.ics` and
  `GET /api/reservations/calendar.ics` return an RFC 5545 feed of active slots.

New endpoints (all under the existing JWT security):

| Method | Path                                                | Access      |
|--------|-----------------------------------------------------|-------------|
| GET    | /api/organizations                                  | ADMIN       |
| POST   | /api/organizations                                  | ADMIN       |
| POST   | /api/reservations/recurring                          | USER, ADMIN |
| POST   | /api/reservations/waitlist                           | USER, ADMIN |
| GET    | /api/reservations/waitlist/mine                      | USER, ADMIN |
| GET    | /api/reservations/calendar.ics                       | USER, ADMIN |
| GET    | /api/resources/{id}/calendar.ics                     | USER, ADMIN |
| GET/PUT| /api/resources/{id}/operating-hours                  | GET all, PUT ADMIN |
| GET/POST| /api/resources/{id}/blackouts                       | GET all, POST ADMIN |
| DELETE | /api/resources/{id}/blackouts/{blackoutId}           | ADMIN       |
| GET/POST| /api/resource-groups                                | GET all, POST ADMIN |
| POST   | /api/resource-groups/{groupId}/resources/{resourceId}| ADMIN       |
| GET    | /api/resource-groups/{groupId}/resources             | USER, ADMIN |
| GET    | /api/admin/reports/resources/{id}/occupancy          | ADMIN       |
| GET    | /api/admin/reports/resources/{id}/history            | ADMIN       |

### PostgreSQL migration verification (Testcontainers)

`PostgresMigrationTest` boots a real PostgreSQL container, applies the
`postgresql` migrations and asserts the partial unique index enforces a single
ACTIVE reservation per slot. It self-skips (JUnit assumption) when Docker is not
reachable, so the H2 suite always runs.

## Non-goals (v1)

Payments, a separate frontend (Swagger UI is the console), notifications, and
external calendar sync are intentionally out of scope.
