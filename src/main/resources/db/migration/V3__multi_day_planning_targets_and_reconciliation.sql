ALTER TABLE wheel_intake_request
    ADD COLUMN completed_wheel_quantity INTEGER NOT NULL DEFAULT 0;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE wheel_intake_request ADD CONSTRAINT ck_wheel_request_completed_quantity CHECK (
    completed_wheel_quantity >= 0
);

ALTER TABLE production_plan
    ADD COLUMN status VARCHAR(40) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN minimum_target_snapshot INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN maximum_target_snapshot INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_completed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_remaining INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN overtime_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN below_minimum_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN carried_over_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN advanced_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN closed_by VARCHAR(160),
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopened_by VARCHAR(160),
    ADD COLUMN reopen_reason VARCHAR(1000);

UPDATE production_plan
SET minimum_target_snapshot = target_used,
    maximum_target_snapshot = capacity_used
WHERE minimum_target_snapshot = 0 AND maximum_target_snapshot = 0;

ALTER TABLE production_plan ADD CONSTRAINT ck_production_plan_status CHECK (status IN (
    'DRAFT',
    'PROVISIONAL',
    'PUBLISHED',
    'IN_PROGRESS',
    'AWAITING_RECONCILIATION',
    'CLOSED'
));

ALTER TABLE production_plan ADD CONSTRAINT ck_production_plan_reconciliation_totals CHECK (
    total_completed >= 0
    AND total_remaining >= 0
    AND overtime_quantity >= 0
    AND below_minimum_quantity >= 0
    AND carried_over_quantity >= 0
    AND advanced_quantity >= 0
);

ALTER TABLE production_plan_item
    ADD COLUMN completed_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN remaining_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN request_total_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN request_remaining_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN carried_over BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN advanced_from_future BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN operational_notes VARCHAR(1000),
    ADD COLUMN line_status VARCHAR(40) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE production_plan_item
SET remaining_quantity = quantity,
    request_total_quantity = quantity,
    request_remaining_quantity = quantity
WHERE request_total_quantity = 0;

ALTER TABLE production_plan_item ADD CONSTRAINT ck_plan_item_reconciliation_quantities CHECK (
    completed_quantity >= 0
    AND remaining_quantity >= 0
    AND request_total_quantity >= 0
    AND request_remaining_quantity >= 0
    AND quantity = completed_quantity + remaining_quantity
);

ALTER TABLE production_plan_item ADD CONSTRAINT ck_plan_item_line_status CHECK (line_status IN (
    'PLANNED',
    'PARTIALLY_COMPLETED',
    'COMPLETED',
    'CARRIED_OVER'
));

CREATE TABLE production_target_configuration (
    id UUID PRIMARY KEY,
    minimum_daily_target INTEGER NOT NULL,
    regular_daily_capacity INTEGER NOT NULL,
    effective_from DATE NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_target_configuration_values CHECK (
        minimum_daily_target >= 0
        AND regular_daily_capacity > 0
        AND minimum_daily_target <= regular_daily_capacity
    )
);

CREATE INDEX idx_target_configuration_effective_from ON production_target_configuration (effective_from);

INSERT INTO production_target_configuration (
    id,
    minimum_daily_target,
    regular_daily_capacity,
    effective_from,
    created_by,
    created_at
)
SELECT
    gen_random_uuid(),
    daily_target,
    CASE WHEN daily_capacity <= 0 THEN 1 ELSE daily_capacity END,
    COALESCE(settings_date, CURRENT_DATE),
    'MIGRATION',
    now()
FROM daily_production_settings
WHERE daily_target <= CASE WHEN daily_capacity <= 0 THEN 1 ELSE daily_capacity END;

CREATE TABLE planning_run (
    id UUID PRIMARY KEY,
    trigger VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    affected_date_from DATE NOT NULL,
    affected_date_to DATE NOT NULL,
    actor VARCHAR(160) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    CONSTRAINT ck_planning_run_trigger CHECK (trigger IN (
        'SCHEDULED',
        'STARTUP_RECOVERY',
        'MANUAL',
        'AUTOMATIC_RECALCULATION'
    )),
    CONSTRAINT ck_planning_run_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'))
);
