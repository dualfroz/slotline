# slotline

A multi-tenant reservation platform built with Spring Boot. Resources (rooms,
courts, equipment) are booked for time slots, and the core guarantee is that a
single slot can never be double-booked, even under heavy concurrency.

The project is deliberately focused on the parts that are hard to get right in a
booking system: concurrency, transactions, security, and a documented API.

## Features

- No double-booking under concurrency. Booking takes a pessimistic lock on the
  resource row, runs an overlap check and inserts in one transaction; a clash is
  rejected with `409 Conflict`. Details below.
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
  real reservation and notified. Promotion takes the same resource lock and runs
  the same hours, blackout, overlap and buffer checks as a normal booking; a
  waiter whose slot fails them is skipped and stays waiting.
- Notifications. A `NotificationChannel` abstraction fans out on booking,
  cancellation and waitlist promotion. The email channel logs the message; the
  webhook channel is enabled only when `slotline.notifications.webhook.url` is
  set. Tests register a recording channel, so nothing is sent.
- Resource groups. A nestable group hierarchy per organization; resources are
  assigned to a group and listed per group.
- Admin reports. Occupancy of a resource over a time window (reservation count,
  booked minutes clamped to the window, utilization percent) and the full
  reservation history including cancelled slots. ADMIN only, tenant-scoped.
- iCal export. `GET /api/resources/{id}/calendar.ics` and
  `GET /api/reservations/calendar.ics` return an RFC 5545 feed of active slots.
- Stateless JWT security with USER and ADMIN roles, RFC 7807 error responses and
  a Swagger UI for the whole API.

## Tech stack

- Java 21, Spring Boot 3.5
- Spring Web, Spring Data JPA, Spring Security (stateless JWT)
- Flyway for schema migrations (vendor-specific: H2 for dev/test, PostgreSQL for deploy)
- H2 in-memory (dev/test), PostgreSQL (deploy profile)
- springdoc-openapi for Swagger UI
- Testcontainers for the PostgreSQL migration test
- Maven Wrapper (`./mvnw`) - no local Maven install required

## Running it

Prerequisites: a JDK 21 or newer. The build uses the Maven Wrapper, so Maven
itself does not need to be installed.

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
  config/        SecurityConfig, OpenApiConfig, SlotlineProperties, AdminBootstrap
  security/      JwtService, JwtAuthenticationFilter, AuthenticatedUser, RestAuthenticationHandlers
  organization/  Organization (tenant), OrganizationService, OrganizationController, dto/
  user/          User, Role, AuthService, AuthController, dto/
  resource/      Resource, ResourceGroup, their services and controllers, dto/
  availability/  OperatingHours, BlackoutDate, AvailabilityService, AvailabilityController, dto/
  reservation/   Reservation, WaitlistEntry, ReservationService, WaitlistService, ReservationController, dto/
  recurrence/    RecurrenceExpander (RRULE subset to concrete occurrences)
  notification/  NotificationService, NotificationChannel, email and webhook channels
  report/        AdminReportService, AdminReportController, dto/
  ical/          ICalService, ICalController
  common/        domain exceptions + GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

- Controllers are thin; all logic and transaction boundaries live in the services.
- Requests and responses are immutable records; entities never leave the service layer.
- Errors are returned as `application/problem+json` (ProblemDetail), including
  field-level validation violations.

Migrations live in `src/main/resources/db/migration/{h2,postgresql}`:
`V1__init.sql` creates users, resources and reservations;
`V2__organizations_groups_availability_waitlist_series.sql` adds organizations,
resource groups, operating hours, blackout dates, the waitlist and the
recurring-series column.

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

Recurring series take the same lock once and check every occurrence inside the
same transaction, so a series cannot slip past a concurrent single booking.

This is verified by `ReservationConcurrencyTest`: 12 threads are released
simultaneously against the same slot and the test asserts exactly one success,
eleven `409`s, and exactly one active reservation in the database. Removing the
pessimistic lock makes the test fail (all 12 succeed), so the test genuinely
exercises the guarantee.

## API

Authentication is a Bearer JWT obtained from `/api/auth/login`.

| Method | Path                                                  | Access              | Notes                              |
|--------|-------------------------------------------------------|---------------------|------------------------------------|
| POST   | /api/auth/register                                    | public              | creates a USER account (201)       |
| POST   | /api/auth/login                                       | public              | returns a JWT                      |
| GET    | /api/organizations                                    | ADMIN               | list organizations                 |
| POST   | /api/organizations                                    | ADMIN               | create organization (201)          |
| GET    | /api/resources                                        | USER, ADMIN         | list resources                     |
| GET    | /api/resources/{id}                                   | USER, ADMIN         | single resource                    |
| POST   | /api/resources                                        | ADMIN               | create resource (201)              |
| PUT    | /api/resources/{id}                                   | ADMIN               | update resource                    |
| DELETE | /api/resources/{id}                                   | ADMIN               | delete resource (204)              |
| GET    | /api/resources/{id}/operating-hours                   | USER, ADMIN         | weekly opening hours               |
| PUT    | /api/resources/{id}/operating-hours                   | ADMIN               | replace opening hours              |
| GET    | /api/resources/{id}/blackouts                         | USER, ADMIN         | blackout date ranges               |
| POST   | /api/resources/{id}/blackouts                         | ADMIN               | add blackout (201)                 |
| DELETE | /api/resources/{id}/blackouts/{blackoutId}            | ADMIN               | remove blackout (204)              |
| GET    | /api/resources/{resourceId}/reservations              | USER, ADMIN         | active reservations                |
| GET    | /api/resources/{id}/calendar.ics                      | USER, ADMIN         | iCal feed of a resource            |
| GET    | /api/resource-groups                                  | USER, ADMIN         | list groups                        |
| POST   | /api/resource-groups                                  | ADMIN               | create group, optionally nested    |
| POST   | /api/resource-groups/{groupId}/resources/{resourceId} | ADMIN               | assign resource to group           |
| GET    | /api/resource-groups/{groupId}/resources              | USER, ADMIN         | resources directly in a group      |
| POST   | /api/reservations                                     | USER, ADMIN         | book a slot (201), 409 or 422      |
| POST   | /api/reservations/recurring                           | USER, ADMIN         | book a recurring series (201)      |
| GET    | /api/reservations/mine                                | USER, ADMIN         | caller's reservations              |
| POST   | /api/reservations/{id}/cancel                         | owner/ADMIN         | cancel, promotes the next waiter   |
| POST   | /api/reservations/waitlist                            | USER, ADMIN         | join the waitlist for a slot (201) |
| GET    | /api/reservations/waitlist/mine                       | USER, ADMIN         | caller's waitlist entries          |
| GET    | /api/reservations/calendar.ics                        | USER, ADMIN         | iCal feed of the caller's bookings |
| GET    | /api/admin/reports/resources/{id}/occupancy           | ADMIN               | `from` and `until` query params    |
| GET    | /api/admin/reports/resources/{id}/history             | ADMIN               | all reservations, any status       |

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

# occupancy of the resource over a window (ADMIN only)
curl -s "http://localhost:8080/api/admin/reports/resources/1/occupancy?from=2026-09-01T08:00:00Z&until=2026-09-01T18:00:00Z" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Testing

```bash
./mvnw test
```

Suite:

- `JwtServiceTest` - token issue/parse round trip and rejection of tokens signed with another key.
- `ReservationApiTest` - full HTTP flow through Spring Security: register, login, role enforcement (USER cannot create resources), book, 409 on conflict, cancel, and re-book after cancel.
- `ReservationConcurrencyTest` - the concurrency guarantee described above.
- `TenantIsolationTest` - a tenant can neither see nor book another tenant's resources.
- `RecurrenceExpanderTest` - RRULE expansion: daily with interval and count, weekly by day, `UNTIL` boundary, rejection of unbounded rules.
- `RecurringReservationTest` - a weekly series books distinct slots; a series with one conflicting slot is rolled back whole.
- `AvailabilityRulesTest` - operating hours, buffer and blackout ranges reject bookings with 422.
- `WaitlistPromotionTest` - cancelling promotes the oldest waiter and sends a notification; a waiter whose slot breaks operating hours or a blackout is skipped and stays waiting.
- `WaitlistPromotionConcurrencyTest` - promotion waits for the resource lock held by a concurrent booking and does not double-book the slot it took.
- `ResourceGroupApiTest` - nested groups, resource assignment, per-group listing, role enforcement, validation, and tenant isolation of groups.
- `AdminReportApiTest` - occupancy clamped to the window with cancelled slots excluded, history including cancelled slots, ADMIN-only access, and tenant isolation.
- `ICalExportTest` - resource and user feeds contain the booked slot as a VEVENT.
- `PostgresMigrationTest` - boots a real PostgreSQL container with Testcontainers, applies the `postgresql` migrations and asserts the partial unique index keeps a single ACTIVE reservation per slot. It self-skips (JUnit assumption) when Docker is not reachable, so the H2 suite always runs.
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

| Variable                            | Default              | Purpose                              |
|-------------------------------------|----------------------|--------------------------------------|
| SLOTLINE_JWT_SECRET                 | dev-only placeholder | HMAC signing key (set in prod)       |
| SLOTLINE_ADMIN_USERNAME             | admin                | seeded admin username                |
| SLOTLINE_ADMIN_PASSWORD             | admin12345           | seeded admin password (set in prod)  |
| SLOTLINE_NOTIFICATIONS_WEBHOOK_URL  | (unset)              | enables the webhook notification channel |
| SPRING_PROFILES_ACTIVE              | (dev / H2)           | set to `postgres` for deploy         |
| SLOTLINE_DB_URL                     | localhost postgres   | JDBC url (postgres profile)          |
| SLOTLINE_DB_USERNAME                | slotline             | DB user (postgres profile)           |
| SLOTLINE_DB_PASSWORD                | slotline             | DB password (postgres profile)       |

The dev JWT secret and admin password are for local use only and must be
overridden before any real deployment.

## Limitations

- Payments and a separate frontend are out of scope; Swagger UI is the console.
- The email notification channel only logs messages; real delivery needs an SMTP
  implementation of `NotificationChannel` or the webhook channel.
- Calendar integration is a one-way iCal export; there is no two-way sync with
  external calendars.
- Recurrence supports the RRULE subset listed above (`DAILY` and `WEEKLY` only).
- Group listing returns resources assigned directly to a group, not those of its
  child groups.
- Any organization `ADMIN` can list and create organizations; there is no separate
  platform-admin role.

## License

MIT, see [LICENSE](LICENSE).
