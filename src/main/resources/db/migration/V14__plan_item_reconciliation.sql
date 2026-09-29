CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE production_plan_item
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reopened_by VARCHAR(160);

ALTER TABLE production_plan_item
    DROP CONSTRAINT IF EXISTS ck_plan_item_line_status;

UPDATE production_plan_item
SET line_status = CASE line_status
    WHEN 'PLANNED' THEN 'OPEN'
    WHEN 'COMPLETED' THEN 'CLOSED_COMPLETE'
    WHEN 'PARTIALLY_COMPLETED' THEN 'CLOSED_PARTIAL'
    WHEN 'CARRIED_OVER' THEN 'CLOSED_PARTIAL'
    ELSE line_status
END;

ALTER TABLE production_plan_item
    ALTER COLUMN line_status SET DEFAULT 'OPEN';

ALTER TABLE production_plan_item ADD CONSTRAINT ck_plan_item_line_status CHECK (line_status IN (
    'OPEN',
    'CLOSED_COMPLETE',
    'CLOSED_PARTIAL',
    'REOPENED',
    'PLANNED',
    'PARTIALLY_COMPLETED',
    'COMPLETED',
    'CARRIED_OVER'
));

CREATE TABLE IF NOT EXISTS production_plan_item_reconciliation (
    id UUID PRIMARY KEY,
    production_plan_id UUID NOT NULL REFERENCES production_plan (id),
    production_plan_item_id UUID NOT NULL REFERENCES production_plan_item (id),
    request_id UUID NOT NULL REFERENCES wheel_intake_request (id),
    planning_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    revision INTEGER NOT NULL,
    closed_by VARCHAR(160),
    closed_at TIMESTAMPTZ,
    reopened_by VARCHAR(160),
    reopened_at TIMESTAMPTZ,
    reopen_reason VARCHAR(1000),
    reverted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    CONSTRAINT ck_item_reconciliation_status CHECK (status IN (
        'CLOSED_COMPLETE',
        'CLOSED_PARTIAL',
        'REOPENED'
    )),
    CONSTRAINT ck_item_reconciliation_revision CHECK (revision > 0),
    CONSTRAINT uk_item_reconciliation_revision UNIQUE (production_plan_item_id, revision)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_item_reconciliation_active
    ON production_plan_item_reconciliation (production_plan_item_id)
    WHERE reverted = FALSE;

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_plan
    ON production_plan_item_reconciliation (production_plan_id);

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_item
    ON production_plan_item_reconciliation (production_plan_item_id);

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_request
    ON production_plan_item_reconciliation (request_id);

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_date
    ON production_plan_item_reconciliation (planning_date);

CREATE TABLE IF NOT EXISTS production_plan_item_reconciliation_quantity (
    id UUID PRIMARY KEY,
    reconciliation_id UUID NOT NULL REFERENCES production_plan_item_reconciliation (id) ON DELETE CASCADE,
    wheel_type VARCHAR(40) NOT NULL,
    planned_quantity INTEGER NOT NULL,
    completed_quantity INTEGER NOT NULL,
    remaining_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    CONSTRAINT uk_item_reconciliation_quantity_type UNIQUE (reconciliation_id, wheel_type),
    CONSTRAINT ck_item_reconciliation_quantity_type CHECK (wheel_type IN ('BIPARTITE', 'WASHED', 'NORMAL')),
    CONSTRAINT ck_item_reconciliation_quantity_values CHECK (
        planned_quantity >= 0
        AND completed_quantity >= 0
        AND remaining_quantity >= 0
        AND planned_quantity = completed_quantity + remaining_quantity
    )
);

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_quantity_parent
    ON production_plan_item_reconciliation_quantity (reconciliation_id);
