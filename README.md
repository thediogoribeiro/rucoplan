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

If no suitable customer is selected, the driver must confirm the new customer name. Rucoplan does not invent customer numbers, tax identifiers or placeholder values. When there is not enough information to create a definitive customer, a `PENDING_REVIEW` customer registration request is created and the wheel request can continue with the customer shown as pending validation. Administrators can later link that pending request to an existing customer or reject it without losing the associated wheel requests.

## Technology

- Java 21.
- Spring Boot.
- Maven.
- PostgreSQL.
- Flyway migrations.
- Static HTML, CSS and JavaScript frontend served by the backend.

## Local Development

Use the project scripts and environment examples included in the repository to start the application locally. Do not commit local environment files or real integration credentials.

Detailed environment-specific setup notes should be kept outside public documentation.

## Tests

Run the backend and static frontend checks with:

```bash
mvn test
```

PostgreSQL migration tests require a working Docker/Testcontainers environment.

## Documentation

Additional integration notes are available in:

- [`docs/INTEGRATION_CONTRACTS.md`](docs/INTEGRATION_CONTRACTS.md)

## Security Notes

Do not commit:

- Real bot tokens.
- Webhook secrets.
- Local environment files.
- Production database credentials.
- Private operational credentials.

All sensitive configuration must be supplied by the runtime environment.
