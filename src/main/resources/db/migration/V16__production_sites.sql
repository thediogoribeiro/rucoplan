CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS production_site (
    id UUID PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_production_site_code CHECK (code IN ('PT', 'LUX'))
);

INSERT INTO production_site (id, code, display_name, timezone, active, created_at, updated_at, created_by, updated_by, version)
VALUES
    (gen_random_uuid(), 'PT', 'Portugal', 'Europe/Lisbon', TRUE, now(), now(), 'SYSTEM', 'SYSTEM', 0),
    (gen_random_uuid(), 'LUX', 'Luxemburgo', 'Europe/Luxembourg', TRUE, now(), now(), 'SYSTEM', 'SYSTEM', 0)
ON CONFLICT (code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    timezone = EXCLUDED.timezone,
    active = EXCLUDED.active,
    updated_at = now();

CREATE TABLE IF NOT EXISTS application_user_site (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    production_site_id UUID NOT NULL REFERENCES production_site (id),
    role VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    CONSTRAINT uk_application_user_site UNIQUE (user_id, production_site_id)
);

CREATE TABLE IF NOT EXISTS driver_production_site (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES driver (id) ON DELETE CASCADE,
    production_site_id UUID NOT NULL REFERENCES production_site (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    association_source VARCHAR(40) NOT NULL,
    associated_at TIMESTAMPTZ NOT NULL,
    associated_by VARCHAR(160) NOT NULL,
    CONSTRAINT uk_driver_production_site UNIQUE (driver_id, production_site_id),
    CONSTRAINT ck_driver_site_association_source CHECK (association_source IN (
        'ADMIN',
        'TELEGRAM_SELECTION',
        'WHATSAPP_SELECTION',
        'MANUAL_IMPORT',
        'MIGRATION'
    ))
);

INSERT INTO application_user_site (id, user_id, production_site_id, role, active, created_at, created_by)
SELECT gen_random_uuid(), u.id, s.id, u.role, TRUE, now(), 'MIGRATION'
FROM app_user u
JOIN production_site s ON s.code = 'PT'
ON CONFLICT (user_id, production_site_id) DO NOTHING;

INSERT INTO application_user_site (id, user_id, production_site_id, role, active, created_at, created_by)
SELECT gen_random_uuid(), u.id, s.id, u.role, TRUE, now(), 'MIGRATION'
FROM app_user u
JOIN production_site s ON s.code = 'LUX'
WHERE u.role = 'ADMIN'
ON CONFLICT (user_id, production_site_id) DO NOTHING;

INSERT INTO driver_production_site (id, driver_id, production_site_id, active, association_source, associated_at, associated_by)
SELECT gen_random_uuid(), d.id, s.id, TRUE, 'MIGRATION', now(), 'MIGRATION'
FROM driver d
JOIN production_site s ON s.code = 'PT'
ON CONFLICT (driver_id, production_site_id) DO NOTHING;

ALTER TABLE customer_reference ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE customer_registration_request ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE wheel_intake_request ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE production_target_configuration ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE production_plan ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE production_plan_item ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE production_plan_item_reconciliation ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE planning_run ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE capacity_alert ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE planning_audit_event ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE telegram_intake_draft ADD COLUMN IF NOT EXISTS production_site_id UUID;
ALTER TABLE whatsapp_conversation ADD COLUMN IF NOT EXISTS production_site_id UUID;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE customer_reference c SET production_site_id = pt.id
FROM pt
WHERE c.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE customer_registration_request r SET production_site_id = pt.id
FROM pt
WHERE r.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE wheel_intake_request r SET production_site_id = pt.id
FROM pt
WHERE r.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE production_target_configuration t SET production_site_id = pt.id
FROM pt
WHERE t.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE production_plan p SET production_site_id = pt.id
FROM pt
WHERE p.production_site_id IS NULL;

UPDATE production_plan_item i
SET production_site_id = p.production_site_id
FROM production_plan p
WHERE i.plan_id = p.id
  AND i.production_site_id IS NULL;

UPDATE production_plan_item_reconciliation r
SET production_site_id = p.production_site_id
FROM production_plan p
WHERE r.production_plan_id = p.id
  AND r.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE planning_run r SET production_site_id = pt.id
FROM pt
WHERE r.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE capacity_alert a SET production_site_id = pt.id
FROM pt
WHERE a.production_site_id IS NULL;

UPDATE planning_audit_event a
SET production_site_id = p.production_site_id
FROM production_plan p
WHERE a.plan_id = p.id
  AND a.production_site_id IS NULL;

UPDATE planning_audit_event a
SET production_site_id = r.production_site_id
FROM wheel_intake_request r
WHERE a.request_id = r.id
  AND a.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE telegram_intake_draft d SET production_site_id = pt.id
FROM pt
WHERE d.production_site_id IS NULL;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE whatsapp_conversation c SET production_site_id = pt.id
FROM pt
WHERE c.production_site_id IS NULL;

ALTER TABLE customer_reference ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE customer_registration_request ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE wheel_intake_request ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE production_target_configuration ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE production_plan ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE production_plan_item ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE production_plan_item_reconciliation ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE planning_run ALTER COLUMN production_site_id SET NOT NULL;
ALTER TABLE capacity_alert ALTER COLUMN production_site_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_reference_site') THEN
        ALTER TABLE customer_reference
            ADD CONSTRAINT fk_customer_reference_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_customer_registration_site') THEN
        ALTER TABLE customer_registration_request
            ADD CONSTRAINT fk_customer_registration_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_wheel_request_site') THEN
        ALTER TABLE wheel_intake_request
            ADD CONSTRAINT fk_wheel_request_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_target_configuration_site') THEN
        ALTER TABLE production_target_configuration
            ADD CONSTRAINT fk_target_configuration_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_production_plan_site') THEN
        ALTER TABLE production_plan
            ADD CONSTRAINT fk_production_plan_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_plan_item_site') THEN
        ALTER TABLE production_plan_item
            ADD CONSTRAINT fk_plan_item_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_item_reconciliation_site') THEN
        ALTER TABLE production_plan_item_reconciliation
            ADD CONSTRAINT fk_item_reconciliation_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_planning_run_site') THEN
        ALTER TABLE planning_run
            ADD CONSTRAINT fk_planning_run_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_capacity_alert_site') THEN
        ALTER TABLE capacity_alert
            ADD CONSTRAINT fk_capacity_alert_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_planning_audit_site') THEN
        ALTER TABLE planning_audit_event
            ADD CONSTRAINT fk_planning_audit_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_draft_site') THEN
        ALTER TABLE telegram_intake_draft
            ADD CONSTRAINT fk_telegram_draft_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_whatsapp_conversation_site') THEN
        ALTER TABLE whatsapp_conversation
            ADD CONSTRAINT fk_whatsapp_conversation_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
END $$;

ALTER TABLE customer_reference DROP CONSTRAINT IF EXISTS customer_reference_external_id_key;
ALTER TABLE customer_reference DROP CONSTRAINT IF EXISTS uk_customer_reference_customer_code;
ALTER TABLE customer_reference DROP CONSTRAINT IF EXISTS uk_customer_reference_customer_number;
ALTER TABLE production_plan DROP CONSTRAINT IF EXISTS uk_production_plan_date_version;

DROP INDEX IF EXISTS uk_customer_reference_external_reference;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_site_external_id
    ON customer_reference (production_site_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_site_customer_code
    ON customer_reference (production_site_id, customer_code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_site_customer_number
    ON customer_reference (production_site_id, customer_number)
    WHERE customer_number IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_site_external_reference
    ON customer_reference (production_site_id, external_system, external_customer_id)
    WHERE external_system IS NOT NULL AND external_customer_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_production_plan_site_date_version
    ON production_plan (production_site_id, planning_date, version_number);

CREATE UNIQUE INDEX IF NOT EXISTS uk_production_plan_site_current
    ON production_plan (production_site_id, planning_date)
    WHERE current_plan = TRUE;

CREATE INDEX IF NOT EXISTS idx_customer_reference_site_normalized_name
    ON customer_reference (production_site_id, normalized_name);

CREATE INDEX IF NOT EXISTS idx_customer_reference_site_tax_country
    ON customer_reference (production_site_id, tax_identifier, country_code);

CREATE INDEX IF NOT EXISTS idx_wheel_request_site_status
    ON wheel_intake_request (production_site_id, lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_wheel_request_site_pickup
    ON wheel_intake_request (production_site_id, requested_factory_pickup_start);

CREATE INDEX IF NOT EXISTS idx_wheel_request_site_dropoff
    ON wheel_intake_request (production_site_id, expected_factory_dropoff_end);

CREATE INDEX IF NOT EXISTS idx_target_configuration_site_effective_from
    ON production_target_configuration (production_site_id, effective_from DESC);

CREATE INDEX IF NOT EXISTS idx_production_plan_site_date_version
    ON production_plan (production_site_id, planning_date, version_number);

CREATE INDEX IF NOT EXISTS idx_plan_item_site_plan
    ON production_plan_item (production_site_id, plan_id);

CREATE INDEX IF NOT EXISTS idx_item_reconciliation_site_date
    ON production_plan_item_reconciliation (production_site_id, planning_date);

CREATE INDEX IF NOT EXISTS idx_capacity_alert_site_status
    ON capacity_alert (production_site_id, status);

CREATE INDEX IF NOT EXISTS idx_planning_run_site_started
    ON planning_run (production_site_id, started_at DESC);

ALTER TABLE telegram_conversation DROP CONSTRAINT IF EXISTS ck_telegram_conversation_state;
ALTER TABLE telegram_conversation ADD CONSTRAINT ck_telegram_conversation_state CHECK (state IN (
    'AWAITING_DRIVER_NAME',
    'IDLE',
    'AWAITING_PRODUCTION_SITE',
    'AWAITING_CUSTOMER',
    'AWAITING_CUSTOMER_NAME',
    'AWAITING_CUSTOMER_SELECTION',
    'AWAITING_NEW_CUSTOMER_CONFIRMATION',
    'AWAITING_NEW_CUSTOMER_NAME_CONFIRMATION',
    'AWAITING_NEW_CUSTOMER_TAX_IDENTIFIER',
    'AWAITING_NEW_CUSTOMER_COUNTRY',
    'AWAITING_NEW_CUSTOMER_LOCALITY',
    'AWAITING_NEW_CUSTOMER_DETAILS',
    'AWAITING_NEW_CUSTOMER_FINAL_CONFIRMATION',
    'AWAITING_NEW_CUSTOMER_FIELD_TO_CORRECT',
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

ALTER TABLE whatsapp_conversation DROP CONSTRAINT IF EXISTS ck_whatsapp_conversation_state;
ALTER TABLE whatsapp_conversation ADD CONSTRAINT ck_whatsapp_conversation_state CHECK (state IN (
    'AWAITING_DRIVER_NAME',
    'AWAITING_PRODUCTION_SITE',
    'AWAITING_CUSTOMER_NAME',
    'AWAITING_CUSTOMER_OPTION',
    'AWAITING_NEW_CUSTOMER_TAX_IDENTIFIER',
    'AWAITING_NEW_CUSTOMER_COUNTRY',
    'AWAITING_NEW_CUSTOMER_LOCALITY',
    'AWAITING_NEW_CUSTOMER_CONFIRMATION',
    'AWAITING_BIPARTITE_QUANTITY',
    'AWAITING_WASHED_QUANTITY',
    'AWAITING_NORMAL_QUANTITY',
    'AWAITING_FACTORY_DROPOFF_DATE',
    'AWAITING_FACTORY_DROPOFF_SLOT',
    'AWAITING_READY_DATE',
    'AWAITING_FACTORY_PICKUP_SLOT',
    'AWAITING_NOTES',
    'AWAITING_CONFIRMATION',
    'IDLE'
));
