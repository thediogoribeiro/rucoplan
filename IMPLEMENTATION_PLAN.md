# Rucodel Production Planning Implementation Plan

## Repository Inspection

- No `AGENTS.md` file was present under `/Users/thediogoribeiro/dev`, so there were no repository-specific agent instructions to apply.
- Target repository: `/Users/thediogoribeiro/dev/rucoplan`.
  - Current target directory is not a Git repository.
  - Existing contents before implementation were a minimal `pom.xml` using `org.example:rucoplan` and `src/main/java/org/example/Main.java`.
- Reference repositories found:
  - RucoPI: `/Users/thediogoribeiro/dev/rucopi`, Git branch `main`, clean working tree.
  - RucoFi: `/Users/thediogoribeiro/dev/rucofi`, Git branch `main`, with pre-existing modified files. It is treated as read-only and will not be modified.

## Reference Architecture

### Java, Spring Boot and Build

- RucoPI:
  - Java 21.
  - Spring Boot `4.1.0`.
  - Maven.
  - Uses `spring-boot-starter-webmvc`, JPA, validation, PostgreSQL, Flyway, actuator, Spring Boot test starters, H2 for repository tests.
  - Uses JaCoCo.
- RucoFi:
  - Java 21.
  - Spring Boot `3.3.5`.
  - Maven.
  - Uses `spring-boot-starter-web`, JPA, validation, actuator, Flyway, PostgreSQL, H2, Testcontainers PostgreSQL.
  - Uses JaCoCo and Surefire configuration.

Decision: use Java 21 and Spring Boot `3.3.5`. The references disagree on Spring Boot version; RucoFi's version is retained for wider compatibility with Spring Security test support and current SpringDoc/OpenAPI tooling. The package namespace will be `pt.rucodel.productionplanning`, matching RucoPI's Portuguese root package style.

### Backend Organisation

- RucoPI packages: `controller`, `domain`, `dto`, `entity`, `mapper`, `repository`, `service`.
- RucoFi packages: `config`, `controller`, `domain`, `dto/request`, `dto/response`, `entity`, `exception`, `mapper`, `repository`, `service`, plus parser/storage utilities.

Decision: use the fallback/clean layered structure requested by the brief:

- `config`
- `controller`
- `domain`
- `dto`
- `entity`
- `exception`
- `integration`
- `mapper`
- `repository`
- `security`
- `service`

Domain records/classes remain separate from JPA entities. DTO records are used at API boundaries.

### Controller and DTO Conventions

- RucoPI uses versioned `/api/v1/...` endpoints, `@RestController`, `ResponseEntity`, validation annotations, and Java records.
- RucoFi uses mostly unversioned REST endpoints and Java records in `dto/request` and `dto/response`.

Decision: use versioned `/api/v1/...` endpoints, Java records, Jakarta Bean Validation, and `ResponseEntity` where status choice matters.

### Exception Handling

- RucoPI has `@RestControllerAdvice` with structured timestamp/status/error/message/field errors.
- RucoFi has `@RestControllerAdvice` in an `exception` package with stable error codes and path details.

Decision: use a central `GlobalExceptionHandler` in `exception`, with stable error codes, request path, validation details and no silent exception handling.

### Database and Migrations

- Both references use PostgreSQL with Flyway SQL migrations under `src/main/resources/db/migration`.
- Both set `spring.jpa.hibernate.ddl-auto=validate`.
- RucoPI migrations use explicit constraints and indexes with `TIMESTAMPTZ`.
- RucoFi uses Testcontainers for PostgreSQL integration tests.

Decision: use PostgreSQL, Flyway SQL migrations, UUID primary keys, `TIMESTAMPTZ`, database constraints and indexes. Tests will include Testcontainers PostgreSQL where Docker is available and H2 for fast slice tests where practical.

### Frontend

- RucoPI frontend is static HTML/CSS/JS under `src/main/resources/static/dashboard`, with Inter/system fonts, white surfaces, Rucodel red accent, dense dashboard cards, date selector, SSE and fallback polling.
- RucoFi frontend is static HTML/CSS/JS under `src/main/resources/static`, with a left sidebar, simple tables, panels, filters and vanilla API helpers.
- No reference project uses a separate frontend build tool or package manager.

Decision: implement a vanilla static frontend served by Spring Boot from `src/main/resources/static`, with no separate package manager. The admin UI will use RucoFi's sidebar/panel/table structure and RucoPI's red accent, real-time SSE heartbeat and date-dashboard conventions. Driver pages will be mobile-first.

### Authentication

- No Spring Security configuration was found in RucoPI or RucoFi.

Decision: implement Spring Security with BCrypt password hashes and a stateless signed bearer token. Development bootstrap admin is controlled by isolated development environment variables/properties. No production passwords are committed.

### SSE

- RucoPI uses `SseEmitter` with timeout `0L`, initial `dashboard-connected` event, update events after transaction commit, and a 30-second heartbeat sent as an SSE comment.
- RucoPI frontend uses `EventSource`, a named update listener, browser reconnect, and fallback polling every 30 seconds.

Decision: reuse the same SSE service and frontend reconnect/fallback pattern for administrator dashboard updates.

### Scripts and Local Stack

- RucoPI has `scripts/run-postgres.sh` and `scripts/run-rucopi.sh`, using Docker Compose, `.env`, port fallback and Maven.
- RucoFi has `docker-compose.yml`, `scripts/start-backend.sh`, `scripts/start-frontend.sh`, and `scripts/stop-all.sh`.

Decision: provide `docker-compose.yml`, `.env.example`, and executable scripts:

- `scripts/start-postgres.sh`
- `scripts/start-backend.sh`
- `scripts/start-frontend.sh`
- `scripts/start-local-stack.sh`
- `scripts/stop-local-stack.sh`

## Business Decisions

- Internal role/domain name: `Driver`.
- UI label: `Motorista/Vendedor`.
- Application display label is configured through `app.display-name`, defaulting to `Rucodel Production Planning`.
- Technical application name is `production-planning`.
- Work starts when a request is known and ends when finished wheels are picked up from the factory.
- No customer route, delivery, invoicing, payment or customer portal features are implemented.
- Requests are indivisible batches in this MVP. Manual splitting is intentionally deferred; the schema and service boundaries leave room for child batches later.

## Core Models

- `WheelIntakeRequest`
- `Driver`
- `CustomerReference`
- `ProductionPlan`
- `ProductionPlanVersion` concept represented by persisted versioned `ProductionPlan` rows.
- `ProductionPlanItem`
- `DailyProductionSettings`
- `ProductionTimeWindow`
- `PlanningAuditEvent`
- `RequestStatusHistory`
- `WhatsAppIngestionItem`
- `ApplicationUser`

## Planning Algorithm

The planner is deterministic and rule-based:

- `availableAt = actualFactoryArrivalAt OR expectedFactoryDropOffWindowEnd`.
- `dueAt = requestedFactoryPickupWindowStart`.
- `slack = dueAt - max(currentTime, availableAt) - estimatedProductionDuration`.
- Priority order:
  1. Administrator manual priority.
  2. Already overdue commitments.
  3. Negative or smallest remaining slack.
  4. Earliest requested factory pickup.
  5. Physically arrived wheels where urgency is otherwise equivalent.
  6. Oldest registered request.
  7. UUID as final deterministic tie-breaker.

The planner never schedules a batch before factory availability. Expected arrivals use the end of the expected factory drop-off window. Confirmed arrivals use actual arrival time.

Daily target and daily capacity are separate. Urgent work may exceed target up to capacity with an explanation; work beyond capacity is kept visible as overflow.

Every open wheel is assigned to one accounting bucket:

- planned confirmed
- planned tentative
- waiting for arrival
- future workload
- at risk
- capacity overflow

## Integrations

### RucoFi

- No direct database sharing.
- Create `CustomerDirectoryPort`.
- Local adapter is enabled by default for standalone development.
- Future HTTP adapter contract is documented and configured with URL, timeout and enable flags.

### RucoPI

- No direct database sharing.
- Create `ProductionDataPort`.
- Local/disabled adapter is enabled by default.
- Future API/event contract is documented for explicit correlation IDs, WIP, production stage, completed quantity and duration estimates.

### WhatsApp Agent

- No Meta/WhatsApp integration.
- Expose `POST /api/v1/integrations/whatsapp/requests`.
- Authenticate with environment-configured bearer credential.
- Use `externalMessageId` idempotently.
- Ambiguous or unresolved customers create review ingestion items rather than incorrect requests.

## Milestones

1. Replace target skeleton with Maven Spring Boot project.
2. Add database migrations for users, drivers, customers, intake requests, status history, settings, plans, plan items, audit and WhatsApp ingestion.
3. Implement security, bootstrap admin, DTO validation and exception handling.
4. Implement request/customer/settings/admin/driver/WhatsApp APIs.
5. Implement deterministic planning engine, persistence, scheduler, startup recovery, SSE updates and audit.
6. Add standalone/local integration adapters and future contract documentation.
7. Build static Portuguese driver/admin frontend against the real API.
8. Add development seed data under the `dev` profile only.
9. Add backend, planning, integration and frontend static-flow tests.
10. Run Maven tests, package build and local migration checks.

## Assumptions

- Static frontend is acceptable because both references use static frontend assets and no package manager.
- Spring Boot `3.3.5` is acceptable because the two references disagree; this version matches RucoFi's Testcontainers/security compatibility while retaining RucoPI's architecture and SSE pattern.
- Initial local users are development-only. Production deployments must create users through controlled admin processes or environment-provided bootstrap credentials.
- External RucoPI/RucoFi integrations are intentionally disabled by default and documented as ports/contracts.
