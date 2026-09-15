# Integration Contracts

## RucoFi Customer Directory

This service never reads RucoFi database tables directly. The current adapter is local and uses `customer_reference`.

Expected future HTTP contract:

- `GET /api/v1/customers/search?q={query}&limit={limit}`
- `GET /api/v1/customers/by-external-id/{externalId}`

Expected response shape:

```json
{
  "externalId": "C1001",
  "officialName": "Oficina Central Braga"
}
```

If multiple customers match a name, RucoFi must return an ambiguity response. Production Planning must not guess.

Configuration:

- `RUCOFI_INTEGRATION_ENABLED`
- `RUCOFI_BASE_URL`
- `RUCOFI_TIMEOUT_MILLIS`

## RucoPI Production Data

This service never reads RucoPI database tables directly. The current adapter is disabled and returns no production data, so the planner uses configured fallback estimates.

Expected future HTTP/event contract:

- Resolve or link by explicit correlation ID, never by customer name and quantity alone.
- Return current work in progress, current production stage, completed quantity and historical duration/throughput estimates.
- Emit or expose changes that should mark affected plans for recalculation.

Suggested endpoints:

- `GET /api/v1/production-jobs/by-correlation/{correlationId}`
- `GET /api/v1/production-kpis/duration-estimates?wheelQuantity={quantity}`
- `GET /api/v1/production-jobs/wip`

Configuration:

- `RUCOPI_INTEGRATION_ENABLED`
- `RUCOPI_BASE_URL`
- `RUCOPI_TIMEOUT_MILLIS`
