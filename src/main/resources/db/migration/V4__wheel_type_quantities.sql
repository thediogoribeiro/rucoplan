CREATE TABLE request_wheel_quantity (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES wheel_intake_request (id) ON DELETE CASCADE,
    wheel_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    completed_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    CONSTRAINT uk_request_wheel_quantity_type UNIQUE (request_id, wheel_type),
    CONSTRAINT ck_request_wheel_quantity_type CHECK (wheel_type IN ('BIPARTITE', 'WASHED', 'NORMAL')),
    CONSTRAINT ck_request_wheel_quantity_values CHECK (
        quantity >= 0
        AND completed_quantity >= 0
        AND completed_quantity <= quantity
    )
);

CREATE INDEX idx_request_wheel_quantity_request ON request_wheel_quantity (request_id);
CREATE INDEX idx_request_wheel_quantity_type ON request_wheel_quantity (wheel_type);

INSERT INTO request_wheel_quantity (
    id,
    request_id,
    wheel_type,
    quantity,
    completed_quantity,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    request.id,
    type.wheel_type,
    CASE WHEN type.wheel_type = 'NORMAL' THEN request.expected_wheel_quantity ELSE 0 END,
    CASE WHEN type.wheel_type = 'NORMAL' THEN LEAST(request.completed_wheel_quantity, request.expected_wheel_quantity) ELSE 0 END,
    request.created_at,
    request.updated_at,
    'MIGRATION',
    'MIGRATION'
FROM wheel_intake_request request
CROSS JOIN (VALUES ('BIPARTITE'), ('WASHED'), ('NORMAL')) AS type(wheel_type);

ALTER TABLE telegram_intake_draft DROP CONSTRAINT ck_telegram_draft_quantity;
ALTER TABLE telegram_intake_draft ADD CONSTRAINT ck_telegram_draft_quantity CHECK (
    wheel_quantity IS NULL OR wheel_quantity >= 0
);

CREATE TABLE telegram_intake_draft_wheel_quantity (
    id UUID PRIMARY KEY,
    draft_id UUID NOT NULL REFERENCES telegram_intake_draft (id) ON DELETE CASCADE,
    wheel_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    CONSTRAINT uk_telegram_draft_wheel_quantity_type UNIQUE (draft_id, wheel_type),
    CONSTRAINT ck_telegram_draft_wheel_quantity_type CHECK (wheel_type IN ('BIPARTITE', 'WASHED', 'NORMAL')),
    CONSTRAINT ck_telegram_draft_wheel_quantity_value CHECK (quantity >= 0)
);

CREATE INDEX idx_telegram_draft_wheel_quantity_draft ON telegram_intake_draft_wheel_quantity (draft_id);

INSERT INTO telegram_intake_draft_wheel_quantity (
    id,
    draft_id,
    wheel_type,
    quantity,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    draft.id,
    type.wheel_type,
    CASE WHEN type.wheel_type = 'NORMAL' THEN COALESCE(draft.wheel_quantity, 0) ELSE 0 END,
    draft.created_at,
    draft.updated_at,
    'MIGRATION',
    'MIGRATION'
FROM telegram_intake_draft draft
CROSS JOIN (VALUES ('BIPARTITE'), ('WASHED'), ('NORMAL')) AS type(wheel_type)
WHERE draft.wheel_quantity IS NOT NULL;

UPDATE telegram_conversation
SET state = 'AWAITING_BIPARTITE_QUANTITY'
WHERE state = 'AWAITING_WHEEL_QUANTITY';

ALTER TABLE telegram_conversation DROP CONSTRAINT ck_telegram_conversation_state;
ALTER TABLE telegram_conversation ADD CONSTRAINT ck_telegram_conversation_state CHECK (state IN (
    'AWAITING_DRIVER_NAME',
    'IDLE',
    'AWAITING_CUSTOMER',
    'AWAITING_BIPARTITE_QUANTITY',
    'AWAITING_WASHED_QUANTITY',
    'AWAITING_NORMAL_QUANTITY',
    'AWAITING_FACTORY_DROPOFF_DATE',
    'AWAITING_FACTORY_DROPOFF_SLOT',
    'AWAITING_READY_DATE',
    'AWAITING_FACTORY_PICKUP_SLOT',
    'AWAITING_NOTES',
    'AWAITING_CONFIRMATION',
    'AWAITING_CORRECTION_FIELD',
    'AWAITING_NEW_DRAFT_CONFIRMATION'
));

CREATE TABLE production_plan_item_wheel_quantity (
    id UUID PRIMARY KEY,
    plan_item_id UUID NOT NULL REFERENCES production_plan_item (id) ON DELETE CASCADE,
    wheel_type VARCHAR(40) NOT NULL,
    planned_quantity INTEGER NOT NULL,
    completed_quantity INTEGER NOT NULL DEFAULT 0,
    remaining_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    CONSTRAINT uk_plan_item_wheel_quantity_type UNIQUE (plan_item_id, wheel_type),
    CONSTRAINT ck_plan_item_wheel_quantity_type CHECK (wheel_type IN ('BIPARTITE', 'WASHED', 'NORMAL')),
    CONSTRAINT ck_plan_item_wheel_quantity_values CHECK (
        planned_quantity >= 0
        AND completed_quantity >= 0
        AND remaining_quantity >= 0
        AND planned_quantity = completed_quantity + remaining_quantity
    )
);

CREATE INDEX idx_plan_item_wheel_quantity_item ON production_plan_item_wheel_quantity (plan_item_id);

INSERT INTO production_plan_item_wheel_quantity (
    id,
    plan_item_id,
    wheel_type,
    planned_quantity,
    completed_quantity,
    remaining_quantity,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    item.id,
    type.wheel_type,
    CASE WHEN type.wheel_type = 'NORMAL' THEN item.quantity ELSE 0 END,
    CASE WHEN type.wheel_type = 'NORMAL' THEN item.completed_quantity ELSE 0 END,
    CASE WHEN type.wheel_type = 'NORMAL' THEN item.remaining_quantity ELSE 0 END,
    item.created_at,
    item.created_at,
    'MIGRATION',
    'MIGRATION'
FROM production_plan_item item
CROSS JOIN (VALUES ('BIPARTITE'), ('WASHED'), ('NORMAL')) AS type(wheel_type);
