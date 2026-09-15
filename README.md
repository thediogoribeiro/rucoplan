# Rucodel Production Planning

Working label for the production-planning application that complements RucoPI and RucoFi. The public product name is intentionally not final; change `APP_DISPLAY_NAME`/`app.display-name` when a final name exists.

## Architecture

- Java 21, Maven, Spring Boot 3.3.5.
- PostgreSQL 16 with Flyway SQL migrations in `src/main/resources/db/migration`.
- Spring MVC REST API under `/api/v1`.
- Spring Security with BCrypt passwords and signed bearer tokens.
- Static HTML/CSS/JS frontend served by Spring Boot, matching the reference projects' no-package-manager frontend approach.
- Server-sent events for the administrator dashboard, following RucoPI's initial connected event, browser reconnect and 30 second heartbeat pattern.

RucoPI and RucoFi are architectural references only. This app does not read their databases directly.

## Prerequisites

- Java 21.
- Maven 3.9+.
- Docker with Docker Compose.
- curl for the helper scripts.

## Environment

Create a local `.env`:

```bash
cp .env.example .env
chmod 600 .env
```

Important variables:

- `PRODUCTION_PLANNING_DB_URL`
- `PRODUCTION_PLANNING_DB_USERNAME`
- `PRODUCTION_PLANNING_DB_PASSWORD`
- `SERVER_PORT`
- `APP_TOKEN_SECRET`
- `APP_WHATSAPP_INTEGRATION_TOKEN`
- `TELEGRAM_ENABLED`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `TELEGRAM_WEBHOOK_SECRET`
- `TELEGRAM_WEBHOOK_BASE_URL`
- `PLANNING_OVERNIGHT_END_TIME`
- `APP_DISPLAY_NAME`
- `RUCOFI_INTEGRATION_ENABLED`
- `RUCOFI_BASE_URL`
- `RUCOPI_INTEGRATION_ENABLED`
- `RUCOPI_BASE_URL`

Do not use the example secrets in production.

Never commit `.env`, real Telegram tokens or webhook secrets. The Telegram bot token must only be supplied through `TELEGRAM_BOT_TOKEN`.

## Start Locally

Start PostgreSQL only:

```bash
./scripts/start-postgres.sh
```

Start the backend and static frontend:

```bash
./scripts/start-backend.sh
```

Check frontend URLs:

```bash
./scripts/start-frontend.sh
```

Start the full local stack:

```bash
./scripts/start-local-stack.sh
```

Stop Docker services:

```bash
./scripts/stop-local-stack.sh
```

The application runs at `http://localhost:8082/` by default.

## Development Logins

With the `dev` profile and seed data enabled:

- Admin: `admin` / `admin123`
- Driver 1: `driver1` / `driver123`
- Driver 2: `driver2` / `driver123`
- Driver 3: `driver3` / `driver123`

The seed data is in `DevSeedDataConfig` and only runs under the `dev` profile. A separate environment-controlled bootstrap admin is available through:

- `APP_BOOTSTRAP_ADMIN_ENABLED`
- `APP_BOOTSTRAP_ADMIN_USERNAME`
- `APP_BOOTSTRAP_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_ADMIN_DISPLAY_NAME`

## Migrations

Flyway runs automatically when the Spring Boot backend starts. Hibernate uses `ddl-auto=validate`; production schema must come from migrations.

Manual build/migration check:

```bash
mvn test
```

The integration tests run PostgreSQL migrations with Testcontainers when Docker is available.

## Wheel Types

Every intake request stores wheel quantities by type:

- Bipartidas (`BIPARTITE`)
- Lavadas (`WASHED`)
- Normais (`NORMAL`)

The total is always derived by the backend:

```text
totalQuantity = bipartiteQuantity + washedQuantity + normalQuantity
```

The legacy `expectedWheelQuantity` and WhatsApp `wheelQuantity` fields are deprecated compatibility inputs. When `wheelQuantities` is absent, the legacy value is interpreted as `NORMAL`. Existing historical requests are preserved by migration: the previous total becomes `NORMAL`, with `BIPARTITE` and `WASHED` set to zero. Existing draft quantities follow the same rule.

## Tests

Backend tests:

```bash
mvn test
```

Build package:

```bash
mvn clean package
```

Frontend tests are implemented as static-flow checks because the reference frontends use vanilla static assets and no browser test runner.

## API Documentation

OpenAPI document:

- `http://localhost:8082/openapi.yaml`

Health endpoints:

- `http://localhost:8082/actuator/health`
- `http://localhost:8082/actuator/flyway`

## Planning Job

The scheduler runs at 07:00 in `Europe/Lisbon`:

```yaml
app:
  planning:
    daily-cron: "0 0 7 * * *"
```

Generation is idempotent. It creates a plan only when the day has no current plan or the current plan is marked for recalculation. Startup recovery checks the current day after application startup; if the app starts after 07:00 and the current-day plan is missing or dirty, it generates it.

PostgreSQL `pg_try_advisory_xact_lock` protects plan generation so multiple application instances do not create duplicate plans for the same day.

## Telegram Bot

Bot profile:

- Bot: `@RucodelPlanBot`
- Conversation URL: `https://t.me/RucodelPlanBot`
- BotFather is only used to create/configure the bot profile and visible command list. The question flow is implemented in this Java backend.

Backend endpoint:

```text
POST /api/v1/integrations/telegram/webhook
```

Required environment:

```bash
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=RucodelPlanBot
TELEGRAM_WEBHOOK_SECRET=
TELEGRAM_WEBHOOK_BASE_URL=https://your-public-host.example
PLANNING_OVERNIGHT_END_TIME=06:00
```

Register the HTTPS webhook:

```bash
./scripts/register-telegram-webhook.sh
```

Check webhook status:

```bash
./scripts/get-telegram-webhook-info.sh
```

Configure the bot command list shown by Telegram:

```bash
./scripts/set-telegram-commands.sh
```

Available commands:

- `/start` — register, start or resume a request.
- `/novo` — start a new request. Existing drafts are not deleted silently.
- `/cancelar` — cancel the draft in progress.
- `/ajuda` — show instructions.
- `/pedidos` — show recent requests for the current Telegram driver only.

The bot only processes private chats. Group messages instruct the user to open a private conversation with the bot.

On first private interaction, the backend stores the stable numeric `telegram_user_id`, the `telegram_chat_id` and allowed Telegram profile metadata. The username is never used as the primary identifier, because it can change. Existing future interactions are resolved by `telegram_user_id`, so changing the Telegram username does not create a second driver.

The current onboarding is intentionally open: any person who starts a private chat can register as a driver, without invitation code or administrator approval. This is a provisional decision and creates a risk of improper access. Administrators can deactivate the driver afterwards; deactivated drivers cannot create new Telegram requests.

The Telegram conversation state and draft are persisted in PostgreSQL, so in-progress requests survive backend restarts and multiple application instances. Telegram `update_id` is persisted for idempotency. A confirmed Telegram draft uses an idempotency key derived from the draft id, so repeated updates or repeated confirmation clicks do not create duplicate requests.

The Telegram flow asks, in Portuguese from Portugal, for cliente, jantes bipartidas, jantes lavadas, jantes normais, data/janela de entrada na fábrica, data/janela de levantamento na fábrica, notas and final confirmation. The quantity questions are always asked in this order:

1. Bipartidas
2. Lavadas
3. Normais

Each quantity accepts zero, but the sum of the three types must be greater than zero. The final confirmation shows the detail by type and the derived total.

The same channel-neutral wording and quantity validation live in `DriverIntakeConversationService`, so a future WhatsApp conversation adapter can reuse the flow. The current WhatsApp integration remains a structured HTTP ingestion boundary and does not implement an external chat-provider conversation.

The numeric slot mapping is:

- `1` → `MORNING_09_14`
- `2` → `AFTERNOON_14_19`
- `3` → `EVENING_19_OVERNIGHT`

Until a more detailed configuration exists, “madrugada” ends at `06:00`, configurable with `PLANNING_OVERNIGHT_END_TIME`.

Tests use a fake `TelegramBotClient`; they do not call the Telegram API.

## Capacity Alerts

Confirmed requests from the web, WhatsApp agent and Telegram all feed the same daily planning engine. When requests, capacity settings or plan generation change, the backend recalculates estimated capacity viability.

If confirmed open workload due for a date is greater than configured daily capacity, the backend creates an administrator alert of type `OVERTIME_REQUIRED`. The alert shows required quantity, configured capacity, estimated deficit and affected requests. Equal load does not generate an alert. If capacity becomes sufficient or affected requests are cancelled, the active alert is resolved automatically.

Admin endpoints:

```text
GET /api/v1/admin/capacity-alerts
PATCH /api/v1/admin/capacity-alerts/{id}/acknowledge
```

The admin dashboard displays active/acknowledged alerts as an estimate based on configured capacity. The application does not calculate exact overtime hours in this version.

## Multi-Day Production Planning

Administrators use `Administração → Planeamento de Produção` to navigate previous days, today, tomorrow and future dates. The screen highlights the selected day with plan status, planned/completed/remaining totals, target snapshots, carried-over work, advanced work, overtime indication and the last update time.

Plans and dashboard tables show both the aggregate total and the wheel type detail, for example `Total: 100 jantes · 5 bipartidas · 10 lavadas · 85 normais`. Targets remain aggregate: each bipartida, lavada or normal counts as one physical wheel. There are no per-type targets, weights or duration multipliers in this version.

Production targets are versioned by effective date:

- Target mínimo diário: the minimum number of wheels the factory wants to complete on a normal day. The planner pulls eligible future work forward only when wheels are already available and doing so does not harm more urgent work.
- Target máximo diário — capacidade regular: approximate regular-hours capacity. It is not a hard block; urgent work can exceed it, and the excess creates an overtime alert.

Validation rules:

- minimum target must be greater than or equal to zero;
- regular capacity must be greater than zero;
- minimum target cannot be greater than regular capacity.

Planning priority order:

1. Minimise missed deadlines.
2. Prioritise work carried over from previous closed shifts.
3. Avoid exceeding regular capacity.
4. Reach the minimum target when eligible work exists.
5. Distribute work evenly across available days.
6. Avoid unnecessary early work when days are already balanced.

The deterministic tie-breakers are deadline, availability, request creation date and request id. Requests from Telegram and from the web form use the same planning service and can be split across several days without losing the link to the original request.

Equitable distribution reduces avoidable peaks. For example, 450 eligible wheels over three days with minimum 100 and maximum 200 are distributed as 150 / 150 / 150 when deadlines allow. If deadlines require 230 on one day, the plan keeps those 230 and raises an overtime alert.

Shift closure is handled in `Administração → Fecho do turno`. The administrator compares planned work with real completed work, can mark everything as completed, or enter completed and remaining quantities line by line. The invariant is:

```text
quantidade planeada = quantidade concluída + quantidade pendente
```

Closing a shift stores historical reality by wheel type, updates completed quantities on the original requests, keeps pending wheels on the same requests with their original type and deadline, marks pending work as carried over, and recalculates future plans. Repeating the same close request is idempotent. Closed historical plans are not modified by automatic recalculation; an administrator must reopen a closed plan with a reason.

If the previous day is not closed when the next plan is generated, the new plan is marked `PROVISIONAL` and warns that shift closure is pending.

Multi-day administrative endpoints:

```text
GET /api/v1/admin/production-plans?from={date}&to={date}
GET /api/v1/admin/production-plans/{date}
POST /api/v1/admin/production-plans/{date}/recalculate
GET /api/v1/admin/production-plans/{date}/reconciliation
PUT /api/v1/admin/production-plans/{date}/reconciliation
POST /api/v1/admin/production-plans/{date}/close
POST /api/v1/admin/production-plans/{date}/reopen
GET /api/v1/admin/planning-targets
POST /api/v1/admin/planning-targets
```

Plan states are `DRAFT`, `PROVISIONAL`, `PUBLISHED`, `IN_PROGRESS`, `AWAITING_RECONCILIATION` and `CLOSED`. This first version does not calculate exact overtime hours; it reports the excess wheel quantity over regular capacity.

No automatic per-type production duration is applied yet. The operational deadline remains the manual ready date/window for the whole request. The model is prepared for a future policy with wheel type, expected duration, effective date and applicable working days, but no fictitious duration data is created now.

## Planning Algorithm

For each open batch:

```text
availableAt = actualFactoryArrivalAt OR expectedFactoryDropOffWindowEnd
dueAt = requestedFactoryPickupWindowStart
slack = dueAt - max(currentTime, availableAt) - estimatedProductionDuration
```

Priority order:

1. Manual administrator priority.
2. Already overdue commitments.
3. Negative or smallest slack.
4. Earliest factory pickup request.
5. Wheels physically at the factory.
6. Oldest registered request.
7. UUID deterministic tie-breaker.

The planner never schedules wheels before factory availability. Expected arrivals use the end of the expected factory drop-off window. Confirmed arrivals use the actual factory arrival timestamp.

Daily target and capacity are separate. Urgent work can exceed target up to capacity with an explanation. Anything beyond capacity remains visible as overflow. Each open wheel is assigned to exactly one accounting bucket so no quantity disappears from planning.

## Standalone And Integration Modes

Standalone mode is the default:

- RucoFi integration disabled.
- RucoPI integration disabled.
- Local customer references are used.
- Fallback duration estimates are used.

Future integration contracts are documented in `docs/INTEGRATION_CONTRACTS.md`. The code exposes `CustomerDirectoryPort` and `ProductionDataPort` so HTTP/event adapters can replace the local implementations without database coupling.

## WhatsApp-Agent Boundary

Endpoint:

```text
POST /api/v1/integrations/whatsapp/requests
```

Use `X-Integration-Token` or `Authorization: Bearer ...` with `APP_WHATSAPP_INTEGRATION_TOKEN`.

`externalMessageId` is idempotent. Re-sending the same message returns the existing ingestion/request result and does not create duplicate wheels. Ambiguous or unresolved customers are stored as ingestion items requiring administrator review.
