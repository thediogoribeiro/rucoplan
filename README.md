# Rucoplan

Rucoplan is a production-planning application for managing wheel intake requests, daily production plans, capacity targets and shift reconciliation.

The application is designed for a factory workflow where drivers or internal users register wheel intake requests, and administrators plan and validate production work by day.

## Overview

Rucoplan helps administrators:

- Manage wheel intake requests.
- Distinguish wheel quantities by type: bipartite, washed and normal.
- Plan daily production based on availability, deadlines and configured capacity.
- Define minimum and regular daily production targets.
- Identify days where overtime is likely to be required.
- Record completed and pending work at shift closure.
- Carry unfinished work into future plans without duplicating requests.

The application focuses on production planning up to the point where wheels are ready for factory pickup. It does not manage final customer delivery routes or driver logistics after factory pickup.

## Wheel Types

Each request can contain one or more wheel types:

- Bipartite wheels.
- Washed wheels.
- Normal wheels.

The total quantity is calculated from the sum of all wheel types. Production targets currently use the aggregate physical wheel count; there are no different capacity weights or processing durations per wheel type in this version.

## Planning

The planning engine distributes confirmed open work across the planning horizon using deterministic rules:

- Respect factory availability dates.
- Prioritise earlier deadlines.
- Prioritise carried-over pending work.
- Avoid unnecessary production peaks.
- Try to reach the minimum daily target when eligible work exists.
- Allow production above regular capacity when required to meet deadlines.

When a day exceeds the configured regular capacity, the system creates an administrative overtime alert based on the estimated excess quantity.

## Shift Closure

Administrators can compare planned work with actual production results and close each shift by recording completed and pending quantities per request and wheel type.

Pending work keeps the original request, wheel type and deadline, then becomes carried-over work in future planning.

## Integrations

Rucoplan supports structured intake flows through application adapters, including:

- Web forms.
- Telegram bot integration.
- WhatsApp-agent ingestion boundary.

External credentials, webhook secrets and environment-specific endpoints are intentionally not documented in this public README. Configure them through environment variables in the deployment environment.

## Telegram Identity

The Telegram integration identifies a communication identity, not the physical device.

On first private interaction with the bot, Rucoplan stores the Telegram account identifier, chat identifier and profile metadata made available by Telegram. The first onboarding question asks the driver for their name. That name is stored as the driver-provided professional name and is not automatically overwritten when the Telegram profile name changes.

Future interactions from the same Telegram account are recognised through the stable Telegram user identifier, so the driver is not asked for their name every time. Repeated updates are handled idempotently to avoid duplicate drivers, duplicate identities or duplicate requests.

Drivers may optionally share a contact number. This is voluntary, is not required to complete onboarding, and should be masked in administrative views unless the full value is operationally required.

Administrators can review driver records and linked communication identities, correct driver names, block or reactivate identities, and link an identity to an existing driver when that action is explicitly confirmed.

No automatic merge is performed based on equal names, similar names or phone numbers. A single driver can have multiple communication identities, and a future WhatsApp adapter can reuse the same driver model.

Não é possível identificar a marca, o modelo ou o sistema operativo do telemóvel através da Telegram Bot API. A aplicação identifica a conta Telegram, não o dispositivo físico.

## Customer Resolution

After the driver is identified, the Telegram intake flow asks for the customer name. Rucoplan preserves the original text entered by the driver, but uses a normalized search value for matching. Normalization trims and collapses spaces, lowercases text, normalizes Unicode characters, compares without accents, and normalizes punctuation for search only.

Customer names are not treated as unique identities. Exact normalized matches are reused only when there is exactly one active customer. If multiple customers have the same normalized name, or if only similar names are found, the bot shows numbered options and the last option is always to create a new customer.

Approximate matching uses deterministic scoring based on normalized tokens, prefixes and trigram similarity. PostgreSQL deployments get a `pg_trgm` index on the normalized customer name; the application also keeps a portable in-application scorer. The default suggestion limit is 5 and the default similarity threshold is `0.30`; these can be changed with:

- `app.customers.search.suggestion-limit`
- `app.customers.search.similarity-threshold`

Suggested matches are stored against the persistent conversation before they are shown. When a driver replies with `1`, `2` or presses a button, Rucoplan uses the saved option list instead of running the search again, so later database changes cannot alter the meaning of the displayed number.

If no suitable customer is selected, the driver must confirm the new customer name. Rucoplan does not invent customer numbers, tax identifiers or placeholder values. In the Telegram flow, `NIF/VAT`, country and locality are required before final confirmation. The existing `PENDING_REVIEW` registration model remains available for channels or administrative cases where a customer cannot yet be activated definitively.

### New Customer Creation

When the Telegram driver chooses to create a new customer, the bot follows this order:

1. Confirm the customer name originally entered.
2. Ask for `NIF/VAT`.
3. Ask for the country and store it as an ISO country code.
4. Ask for the locality.
5. Reserve the next RucoPlan customer number automatically.
6. Show a final summary.
7. Create the active customer only after final confirmation.

Drivers never type the RucoPlan customer number. It is generated by a PostgreSQL sequence, not by `MAX(customer_number) + 1`, so concurrent creations cannot receive the same number. A cancelled reserved number is not reused.

Customer identifiers are distinct:

- `id` is the technical primary key used by database relations.
- `customer_number` is the RucoPlan customer number shown operationally.
- `external_system` and `external_customer_id` identify an optional future link to RucoPI, RucoFi or another external system.

The current code uses a generic external reference model because no single canonical external customer system is hardwired in Rucoplan. External identifiers are optional, are not asked in Telegram, and are visible only in administrative contexts.

## Technology

- Java 21.
- Spring Boot.
- Maven.
- PostgreSQL.
- Flyway migrations.
- Static HTML, CSS and JavaScript frontend served by the backend.

## Local Development

Rucoplan keeps two concepts separate:

- Environment: `local` for development, `production` for Heroku.
- Production site: `PT` for Portugal, `LUX` for Luxembourg.

Do not use Spring profiles named `pt` or `lux`; they are business units inside the same application and database.

Use the project scripts and environment examples included in the repository to start the application locally. Do not commit local environment files or real integration credentials.

```bash
cp .env.example .env
scripts/start-local-stack.sh local
```

Local demo data is opt-in:

```bash
DEMO_DATA_ENABLED=true SPRING_PROFILES_ACTIVE=local scripts/start-backend.sh
```

To inspect or normalize existing local demo data:

```bash
scripts/reset-local-demo-data.sh --dry-run
scripts/reset-local-demo-data.sh --apply
```

The reset script refuses non-local database hosts, preserves the internal `teste` driver and linked Telegram identity, reassigns demo requests from removed drivers to `teste`, and renames local demo customers to `loja de jantes de AAAA`, `loja de jantes de BBBB`, and so on per production site.

## Heroku Deployment

The application is deployable on Heroku with the Java buildpack and the included `Procfile`. It uses the Heroku `PORT`, PostgreSQL, Flyway migrations, graceful shutdown and forwarded HTTPS headers.

Required Heroku config vars:

```bash
heroku config:set SPRING_PROFILES_ACTIVE=production
heroku config:set APP_ENVIRONMENT=production
heroku config:set PUBLIC_BASE_URL=https://your-app.herokuapp.com
heroku config:set TELEGRAM_ENABLED=false
heroku config:set TELEGRAM_WEBHOOK_AUTO_REGISTER=false
heroku config:set APP_TOKEN_SECRET=replace-with-a-long-random-secret
```

Add PostgreSQL:

```bash
heroku addons:create heroku-postgresql:essential-0
```

Heroku supplies `DATABASE_URL`; the application converts it to Spring JDBC settings at startup. A new production database receives only schema/reference data such as `PT` and `LUX`, plus explicitly configured bootstrap/admin data. Demo customers, drivers, requests, plans, factory arrivals, alerts and fake Telegram/WhatsApp conversations are not seeded in the `production` profile.

First deploy:

```bash
git push heroku main
heroku ps:scale web=1
heroku logs --tail
heroku open /actuator/health
```

To verify production has no demo functional data:

```bash
heroku pg:psql -c "select count(*) from wheel_intake_request"
heroku pg:psql -c "select count(*) from customer_reference"
heroku pg:psql -c "select count(*) from driver"
```

If an existing production database already contains unwanted data, do not run automatic cleanup. Take a backup, inspect counts per table, agree a manual migration/cleanup script, test it against a restored copy, and only then apply it.

## Telegram Webhook

ngrok is only for local development. In local mode, set `TELEGRAM_WEBHOOK_BASE_URL` externally to the current ngrok HTTPS URL:

```bash
TELEGRAM_ENABLED=true TELEGRAM_WEBHOOK_BASE_URL=https://example.ngrok.app scripts/start-local-stack.sh ngrok
scripts/telegram-webhook-register
scripts/telegram-webhook-info
scripts/telegram-webhook-delete
```

In Heroku production, set `PUBLIC_BASE_URL` to the public HTTPS app or custom domain and leave ngrok unset:

```bash
heroku config:set TELEGRAM_ENABLED=true
heroku config:set TELEGRAM_BOT_TOKEN=replace-with-production-bot-token
heroku config:set TELEGRAM_WEBHOOK_SECRET=replace-with-random-secret
heroku config:set TELEGRAM_WEBHOOK_AUTO_REGISTER=true
```

Manual webhook commands use environment variables and never require tokens in source files:

```bash
PUBLIC_BASE_URL=https://your-app.herokuapp.com TELEGRAM_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... scripts/telegram-webhook-register
TELEGRAM_BOT_TOKEN=... scripts/telegram-webhook-info
TELEGRAM_BOT_TOKEN=... scripts/telegram-webhook-delete
```

One Telegram bot can have only one active webhook at a time. Use different bot tokens for local and production, or explicitly switch the webhook when testing.

The backend validates `X-Telegram-Bot-Api-Secret-Token`, rejects invalid secrets, stores each `update_id`, and returns successfully processed duplicate updates without creating duplicate requests.

## Production-Site Isolation

Portugal and Luxembourg share one application and one database, with logical isolation by `production_site_id`. Legacy rows without a site are backfilled to `PT`; new functional rows require a site. Customers are unique by `(production_site_id, normalized_name)`, so PT and LUX can have independent customers with the same demo name. Plans, targets, daily settings, capacity alerts, SSE updates and scheduler runs operate per site. Default targets are independent: PT uses 150/180 and LUX uses 20/60 unless overridden by configured targets.

To verify isolation:

```bash
heroku pg:psql -c "select s.code, count(*) from wheel_intake_request r join production_site s on s.id = r.production_site_id group by s.code"
heroku pg:psql -c "select s.code, sum(expected_wheel_quantity) from wheel_intake_request r join production_site s on s.id = r.production_site_id group by s.code"
```

## Tests

Run the backend and static frontend checks with:

```bash
mvn test
mvn verify
mvn jacoco:report
```

PostgreSQL migration tests require a working Docker/Testcontainers environment.

Coverage reports are generated under `target/site/jacoco/index.html`.

## Documentation

Additional integration notes are available in:

- [`docs/INTEGRATION_CONTRACTS.md`](docs/INTEGRATION_CONTRACTS.md)
- [`docs/production-sites.md`](docs/production-sites.md)
- [`docs/integrations/whatsapp.md`](docs/integrations/whatsapp.md)
- [`docs/manual-order-entry.md`](docs/manual-order-entry.md)

## Security Notes

Do not commit:

- Real bot tokens.
- Webhook secrets.
- Local environment files.
- Production database credentials.
- Private operational credentials.

All sensitive configuration must be supplied by the runtime environment.
