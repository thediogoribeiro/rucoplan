ALTER TABLE wheel_intake_request DROP CONSTRAINT IF EXISTS ck_wheel_request_source;
ALTER TABLE wheel_intake_request
    ADD CONSTRAINT ck_wheel_request_source CHECK (source IN ('WEB', 'WHATSAPP_AGENT', 'TELEGRAM', 'WHATSAPP', 'MANUAL'));

ALTER TABLE wheel_intake_request
    ALTER COLUMN driver_id DROP NOT NULL;

ALTER TABLE whatsapp_ingestion_item DROP CONSTRAINT IF EXISTS ck_whatsapp_ingestion_status;
ALTER TABLE whatsapp_ingestion_item
    ADD CONSTRAINT ck_whatsapp_ingestion_status CHECK (status IN (
        'CREATED',
        'DUPLICATE',
        'NEEDS_REVIEW',
        'FAILED',
        'IGNORED'
    ));

ALTER TABLE whatsapp_ingestion_item
    ALTER COLUMN driver_external_id DROP NOT NULL,
    ALTER COLUMN customer_name DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS whatsapp_business_account_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS phone_number_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS sender_wa_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(80),
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS outbound_message_id VARCHAR(180);

CREATE INDEX IF NOT EXISTS idx_whatsapp_ingestion_sender ON whatsapp_ingestion_item (sender_wa_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_ingestion_phone_number ON whatsapp_ingestion_item (phone_number_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_ingestion_processed ON whatsapp_ingestion_item (processed_at);

CREATE TABLE IF NOT EXISTS whatsapp_conversation (
    id UUID PRIMARY KEY,
    messaging_identity_id UUID NOT NULL UNIQUE REFERENCES messaging_identity (id) ON DELETE CASCADE,
    state VARCHAR(80) NOT NULL,
    customer_reference_id UUID REFERENCES customer_reference (id),
    new_customer_name VARCHAR(255),
    new_customer_tax_identifier VARCHAR(80),
    new_customer_country_code VARCHAR(2),
    new_customer_locality VARCHAR(120),
    candidate_customer_ids VARCHAR(2000),
    bipartite_quantity INTEGER,
    washed_quantity INTEGER,
    normal_quantity INTEGER,
    factory_dropoff_date DATE,
    factory_dropoff_slot VARCHAR(40),
    ready_date DATE,
    factory_pickup_slot VARCHAR(40),
    notes VARCHAR(2000),
    confirmed_request_id UUID REFERENCES wheel_intake_request (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_whatsapp_conversation_state CHECK (state IN (
        'AWAITING_DRIVER_NAME',
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
    )),
    CONSTRAINT ck_whatsapp_conversation_quantities CHECK (
        (bipartite_quantity IS NULL OR bipartite_quantity >= 0)
        AND (washed_quantity IS NULL OR washed_quantity >= 0)
        AND (normal_quantity IS NULL OR normal_quantity >= 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_conversation_state ON whatsapp_conversation (state);
CREATE INDEX IF NOT EXISTS idx_whatsapp_conversation_customer ON whatsapp_conversation (customer_reference_id);
