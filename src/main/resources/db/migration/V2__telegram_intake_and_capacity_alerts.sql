ALTER TABLE driver
    ADD COLUMN telegram_user_id BIGINT,
    ADD COLUMN telegram_chat_id BIGINT,
    ADD COLUMN telegram_username VARCHAR(255),
    ADD COLUMN telegram_first_name VARCHAR(255),
    ADD COLUMN telegram_last_name VARCHAR(255),
    ADD COLUMN telegram_linked_at TIMESTAMPTZ,
    ADD COLUMN telegram_last_interaction_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_driver_telegram_user_id ON driver (telegram_user_id) WHERE telegram_user_id IS NOT NULL;
CREATE INDEX idx_driver_telegram_user_id ON driver (telegram_user_id);

ALTER TABLE wheel_intake_request
    ADD COLUMN factory_dropoff_slot VARCHAR(40),
    ADD COLUMN factory_pickup_slot VARCHAR(40);

ALTER TABLE wheel_intake_request DROP CONSTRAINT ck_wheel_request_source;
ALTER TABLE wheel_intake_request ADD CONSTRAINT ck_wheel_request_source CHECK (source IN ('WEB', 'WHATSAPP_AGENT', 'TELEGRAM'));

ALTER TABLE wheel_intake_request DROP CONSTRAINT ck_wheel_request_pickup_after_dropoff;
ALTER TABLE wheel_intake_request ADD CONSTRAINT ck_wheel_request_pickup_after_dropoff CHECK (
    requested_factory_pickup_end >= expected_factory_dropoff_end
);

ALTER TABLE wheel_intake_request ADD CONSTRAINT ck_wheel_request_dropoff_slot CHECK (
    factory_dropoff_slot IS NULL OR factory_dropoff_slot IN ('MORNING_09_14', 'AFTERNOON_14_19', 'EVENING_19_OVERNIGHT')
);

ALTER TABLE wheel_intake_request ADD CONSTRAINT ck_wheel_request_pickup_slot CHECK (
    factory_pickup_slot IS NULL OR factory_pickup_slot IN ('MORNING_09_14', 'AFTERNOON_14_19', 'EVENING_19_OVERNIGHT')
);

CREATE TABLE telegram_intake_draft (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES driver (id),
    customer_reference_id UUID REFERENCES customer_reference (id),
    customer_name_snapshot VARCHAR(255),
    customer_candidate_ids VARCHAR(2000),
    wheel_quantity INTEGER,
    factory_dropoff_date DATE,
    factory_dropoff_slot VARCHAR(40),
    ready_date DATE,
    factory_pickup_slot VARCHAR(40),
    notes VARCHAR(1000),
    status VARCHAR(40) NOT NULL,
    confirmed_request_id UUID REFERENCES wheel_intake_request (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_telegram_draft_quantity CHECK (wheel_quantity IS NULL OR wheel_quantity > 0),
    CONSTRAINT ck_telegram_draft_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'CONFIRMED')),
    CONSTRAINT ck_telegram_draft_dropoff_slot CHECK (
        factory_dropoff_slot IS NULL OR factory_dropoff_slot IN ('MORNING_09_14', 'AFTERNOON_14_19', 'EVENING_19_OVERNIGHT')
    ),
    CONSTRAINT ck_telegram_draft_pickup_slot CHECK (
        factory_pickup_slot IS NULL OR factory_pickup_slot IN ('MORNING_09_14', 'AFTERNOON_14_19', 'EVENING_19_OVERNIGHT')
    )
);

CREATE INDEX idx_telegram_draft_driver ON telegram_intake_draft (driver_id);
CREATE INDEX idx_telegram_draft_status ON telegram_intake_draft (status);

CREATE TABLE telegram_conversation (
    id UUID PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    telegram_chat_id BIGINT NOT NULL,
    driver_id UUID REFERENCES driver (id),
    state VARCHAR(60) NOT NULL,
    active_draft_id UUID REFERENCES telegram_intake_draft (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_telegram_conversation_state CHECK (state IN (
        'AWAITING_DRIVER_NAME',
        'IDLE',
        'AWAITING_CUSTOMER',
        'AWAITING_WHEEL_QUANTITY',
        'AWAITING_FACTORY_DROPOFF_DATE',
        'AWAITING_FACTORY_DROPOFF_SLOT',
        'AWAITING_READY_DATE',
        'AWAITING_FACTORY_PICKUP_SLOT',
        'AWAITING_NOTES',
        'AWAITING_CONFIRMATION',
        'AWAITING_CORRECTION_FIELD',
        'AWAITING_NEW_DRAFT_CONFIRMATION'
    ))
);

CREATE INDEX idx_telegram_conversation_user ON telegram_conversation (telegram_user_id);
CREATE INDEX idx_telegram_conversation_driver ON telegram_conversation (driver_id);

CREATE TABLE telegram_inbound_update (
    update_id BIGINT PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    error_summary VARCHAR(1000),
    CONSTRAINT ck_telegram_inbound_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE capacity_alert (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    affected_date DATE NOT NULL,
    required_quantity INTEGER NOT NULL,
    available_capacity INTEGER NOT NULL,
    deficit INTEGER NOT NULL,
    affected_request_ids VARCHAR(4000) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_capacity_alert_type CHECK (type IN ('OVERTIME_REQUIRED')),
    CONSTRAINT ck_capacity_alert_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT ck_capacity_alert_quantities CHECK (
        required_quantity >= 0 AND available_capacity >= 0 AND deficit >= 0
    )
);

CREATE INDEX idx_capacity_alert_status ON capacity_alert (status);
CREATE INDEX idx_capacity_alert_affected_date ON capacity_alert (affected_date);
CREATE UNIQUE INDEX uk_capacity_alert_open_overtime_date ON capacity_alert (type, affected_date)
    WHERE status IN ('ACTIVE', 'ACKNOWLEDGED');
