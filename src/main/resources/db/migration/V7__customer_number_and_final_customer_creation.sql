CREATE SEQUENCE IF NOT EXISTS customer_number_seq START WITH 1000 INCREMENT BY 1;

ALTER TABLE customer_reference
    ADD COLUMN IF NOT EXISTS customer_number INTEGER,
    ADD COLUMN IF NOT EXISTS tax_identifier VARCHAR(80),
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS locality VARCHAR(120),
    ADD COLUMN IF NOT EXISTS status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS external_system VARCHAR(80),
    ADD COLUMN IF NOT EXISTS external_customer_id VARCHAR(160);

UPDATE customer_reference
SET customer_number = nextval('customer_number_seq')
WHERE customer_number IS NULL;

SELECT setval(
    'customer_number_seq',
    GREATEST((SELECT COALESCE(MAX(customer_number), 999) FROM customer_reference), 999),
    true
);

ALTER TABLE customer_reference
    ALTER COLUMN customer_number SET DEFAULT nextval('customer_number_seq'),
    ALTER COLUMN customer_number SET NOT NULL;

UPDATE customer_reference
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END
WHERE status IS NULL;

ALTER TABLE customer_reference
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_customer_reference_customer_number'
    ) THEN
        ALTER TABLE customer_reference
            ADD CONSTRAINT uk_customer_reference_customer_number UNIQUE (customer_number);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_customer_reference_status'
    ) THEN
        ALTER TABLE customer_reference
            ADD CONSTRAINT ck_customer_reference_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_external_reference
    ON customer_reference (external_system, external_customer_id)
    WHERE external_system IS NOT NULL AND external_customer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customer_reference_customer_number
    ON customer_reference (customer_number);

CREATE INDEX IF NOT EXISTS idx_customer_reference_tax_country
    ON customer_reference (tax_identifier, country_code);

CREATE INDEX IF NOT EXISTS idx_customer_reference_external_ref
    ON customer_reference (external_system, external_customer_id);

ALTER TABLE customer_registration_request
    ADD COLUMN IF NOT EXISTS reserved_customer_number INTEGER;

UPDATE customer_registration_request
SET reserved_customer_number = customer_number::INTEGER
WHERE reserved_customer_number IS NULL
  AND customer_number ~ '^[0-9]+$';

CREATE INDEX IF NOT EXISTS idx_customer_registration_reserved_number
    ON customer_registration_request (reserved_customer_number);

ALTER TABLE telegram_conversation DROP CONSTRAINT IF EXISTS ck_telegram_conversation_state;
ALTER TABLE telegram_conversation ADD CONSTRAINT ck_telegram_conversation_state CHECK (state IN (
    'AWAITING_DRIVER_NAME',
    'IDLE',
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
