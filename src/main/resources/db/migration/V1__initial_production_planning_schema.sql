CREATE TABLE driver (
    id UUID PRIMARY KEY,
    external_id VARCHAR(120) UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_driver_external_id ON driver (external_id);
CREATE INDEX idx_driver_active ON driver (active);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    driver_id UUID REFERENCES driver (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_user_role CHECK (role IN ('DRIVER', 'ADMIN')),
    CONSTRAINT ck_app_user_driver_role CHECK (
        (role = 'DRIVER' AND driver_id IS NOT NULL)
        OR (role = 'ADMIN')
    )
);

CREATE INDEX idx_app_user_username ON app_user (username);
CREATE INDEX idx_app_user_role ON app_user (role);

CREATE TABLE customer_reference (
    id UUID PRIMARY KEY,
    external_id VARCHAR(120) UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_reference_external_id ON customer_reference (external_id);
CREATE INDEX idx_customer_reference_name ON customer_reference (name);

CREATE TABLE wheel_intake_request (
    id UUID PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    external_source_reference VARCHAR(160),
    external_message_id VARCHAR(180) UNIQUE,
    customer_reference_id UUID NOT NULL REFERENCES customer_reference (id),
    customer_external_id VARCHAR(120),
    customer_name_snapshot VARCHAR(255) NOT NULL,
    driver_id UUID NOT NULL REFERENCES driver (id),
    expected_wheel_quantity INTEGER NOT NULL,
    actual_received_wheel_quantity INTEGER,
    quantity_discrepancy_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    expected_factory_dropoff_start TIMESTAMPTZ NOT NULL,
    expected_factory_dropoff_end TIMESTAMPTZ NOT NULL,
    requested_factory_pickup_start TIMESTAMPTZ NOT NULL,
    requested_factory_pickup_end TIMESTAMPTZ NOT NULL,
    actual_factory_arrival_at TIMESTAMPTZ,
    actual_pickup_from_factory_at TIMESTAMPTZ,
    rucopi_production_job_id VARCHAR(160),
    notes VARCHAR(2000),
    lifecycle_status VARCHAR(40) NOT NULL,
    manual_priority INTEGER,
    planning_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wheel_request_source CHECK (source IN ('WEB', 'WHATSAPP_AGENT')),
    CONSTRAINT ck_wheel_request_status CHECK (lifecycle_status IN (
        'REGISTERED',
        'ARRIVED_AT_FACTORY',
        'IN_PRODUCTION',
        'READY_FOR_PICKUP',
        'PICKED_UP_FROM_FACTORY',
        'CANCELLED'
    )),
    CONSTRAINT ck_wheel_request_expected_quantity CHECK (expected_wheel_quantity > 0),
    CONSTRAINT ck_wheel_request_received_quantity CHECK (
        actual_received_wheel_quantity IS NULL OR actual_received_wheel_quantity > 0
    ),
    CONSTRAINT ck_wheel_request_dropoff_window CHECK (
        expected_factory_dropoff_start <= expected_factory_dropoff_end
    ),
    CONSTRAINT ck_wheel_request_pickup_window CHECK (
        requested_factory_pickup_start <= requested_factory_pickup_end
    ),
    CONSTRAINT ck_wheel_request_pickup_after_dropoff CHECK (
        requested_factory_pickup_start > expected_factory_dropoff_end
    ),
    CONSTRAINT ck_wheel_request_manual_priority CHECK (
        manual_priority IS NULL OR manual_priority > 0
    )
);

CREATE INDEX idx_wheel_request_driver_status ON wheel_intake_request (driver_id, lifecycle_status);
CREATE INDEX idx_wheel_request_pickup_start ON wheel_intake_request (requested_factory_pickup_start);
CREATE INDEX idx_wheel_request_dropoff_end ON wheel_intake_request (expected_factory_dropoff_end);
CREATE INDEX idx_wheel_request_status ON wheel_intake_request (lifecycle_status);
CREATE INDEX idx_wheel_request_external_message ON wheel_intake_request (external_message_id);

CREATE TABLE request_status_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES wheel_intake_request (id) ON DELETE CASCADE,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    actor_user_id UUID REFERENCES app_user (id),
    actor_label VARCHAR(160) NOT NULL,
    reason VARCHAR(1000)
);

CREATE INDEX idx_status_history_request_time ON request_status_history (request_id, changed_at);

CREATE TABLE daily_production_settings (
    id UUID PRIMARY KEY,
    settings_key VARCHAR(80) NOT NULL UNIQUE,
    settings_date DATE,
    daily_capacity INTEGER NOT NULL,
    daily_target INTEGER NOT NULL,
    fallback_minutes_per_wheel INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_daily_settings_capacity CHECK (daily_capacity >= 0),
    CONSTRAINT ck_daily_settings_target CHECK (daily_target >= 0),
    CONSTRAINT ck_daily_settings_fallback_minutes CHECK (fallback_minutes_per_wheel > 0)
);

CREATE INDEX idx_daily_settings_date ON daily_production_settings (settings_date);

CREATE TABLE production_time_window (
    id UUID PRIMARY KEY,
    settings_id UUID NOT NULL REFERENCES daily_production_settings (id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    cutoff_time TIME NOT NULL,
    sort_order INTEGER NOT NULL,
    CONSTRAINT uk_production_time_window_settings_order UNIQUE (settings_id, sort_order),
    CONSTRAINT uk_production_time_window_settings_label UNIQUE (settings_id, label)
);

CREATE TABLE production_plan (
    id UUID PRIMARY KEY,
    planning_date DATE NOT NULL,
    version_number INTEGER NOT NULL,
    current_plan BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL,
    generation_trigger VARCHAR(40) NOT NULL,
    capacity_used INTEGER NOT NULL,
    target_used INTEGER NOT NULL,
    total_known_wheels INTEGER NOT NULL,
    total_planned INTEGER NOT NULL,
    total_waiting_for_arrival INTEGER NOT NULL,
    total_future_workload INTEGER NOT NULL,
    total_at_risk INTEGER NOT NULL,
    total_over_capacity INTEGER NOT NULL,
    fallback_estimates_used BOOLEAN NOT NULL,
    requires_recalculation BOOLEAN NOT NULL DEFAULT FALSE,
    warning VARCHAR(1000),
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_production_plan_date_version UNIQUE (planning_date, version_number),
    CONSTRAINT ck_production_plan_trigger CHECK (generation_trigger IN (
        'SCHEDULED',
        'STARTUP_RECOVERY',
        'MANUAL',
        'AUTOMATIC_RECALCULATION'
    )),
    CONSTRAINT ck_production_plan_totals CHECK (
        capacity_used >= 0
        AND target_used >= 0
        AND total_known_wheels >= 0
        AND total_planned >= 0
        AND total_waiting_for_arrival >= 0
        AND total_future_workload >= 0
        AND total_at_risk >= 0
        AND total_over_capacity >= 0
    )
);

CREATE INDEX idx_production_plan_date_version ON production_plan (planning_date, version_number);
CREATE INDEX idx_production_plan_current ON production_plan (planning_date, current_plan);

CREATE TABLE production_plan_item (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES production_plan (id) ON DELETE CASCADE,
    request_id UUID NOT NULL REFERENCES wheel_intake_request (id),
    customer_name VARCHAR(255) NOT NULL,
    driver_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    availability_at TIMESTAMPTZ,
    required_ready_at TIMESTAMPTZ,
    assigned_production_date DATE,
    assigned_window_label VARCHAR(120),
    availability_classification VARCHAR(40) NOT NULL,
    risk_classification VARCHAR(40) NOT NULL,
    priority_score NUMERIC(12, 2) NOT NULL,
    priority_explanation VARCHAR(1500) NOT NULL,
    manually_prioritised BOOLEAN NOT NULL,
    locked BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_plan_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_plan_item_availability CHECK (availability_classification IN (
        'CONFIRMED',
        'TENTATIVE',
        'WAITING_FOR_ARRIVAL'
    )),
    CONSTRAINT ck_plan_item_risk CHECK (risk_classification IN (
        'ON_TRACK',
        'AT_RISK',
        'OVERDUE',
        'OVER_CAPACITY',
        'MISSING_INFORMATION'
    ))
);

CREATE INDEX idx_plan_item_plan ON production_plan_item (plan_id);
CREATE INDEX idx_plan_item_request ON production_plan_item (request_id);
CREATE INDEX idx_plan_item_risk ON production_plan_item (risk_classification);

CREATE TABLE planning_audit_event (
    id UUID PRIMARY KEY,
    plan_id UUID,
    request_id UUID,
    event_type VARCHAR(80) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    detail VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_planning_audit_created ON planning_audit_event (created_at);
CREATE INDEX idx_planning_audit_request ON planning_audit_event (request_id);
CREATE INDEX idx_planning_audit_plan ON planning_audit_event (plan_id);

CREATE TABLE whatsapp_ingestion_item (
    id UUID PRIMARY KEY,
    external_message_id VARCHAR(180) NOT NULL UNIQUE,
    driver_external_id VARCHAR(120) NOT NULL,
    customer_external_id VARCHAR(120),
    customer_name VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(1000),
    request_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_whatsapp_ingestion_status CHECK (status IN (
        'CREATED',
        'DUPLICATE',
        'NEEDS_REVIEW',
        'FAILED'
    ))
);

CREATE INDEX idx_whatsapp_ingestion_status ON whatsapp_ingestion_item (status);
CREATE INDEX idx_whatsapp_ingestion_message ON whatsapp_ingestion_item (external_message_id);
