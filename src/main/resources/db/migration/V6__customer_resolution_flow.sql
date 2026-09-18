ALTER TABLE customer_reference
    ADD COLUMN normalized_name VARCHAR(255);

UPDATE customer_reference
SET normalized_name = lower(
    trim(
        regexp_replace(
            translate(
                translate(name, 'ÁÀÂÃÄÅáàâãäåÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ', 'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'),
                '.,;:/\|_+*=?!()[]{}"“”‘’`´~^',
                '                            '
            ),
            '\s+',
            ' ',
            'g'
        )
    )
)
WHERE normalized_name IS NULL;

ALTER TABLE customer_reference
    ALTER COLUMN normalized_name SET NOT NULL;

CREATE INDEX idx_customer_reference_normalized_name ON customer_reference (normalized_name);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_customer_reference_normalized_trgm
    ON customer_reference USING gin (normalized_name gin_trgm_ops);

CREATE TABLE customer_registration_request (
    id UUID PRIMARY KEY,
    proposed_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    customer_number VARCHAR(120),
    tax_identifier VARCHAR(80),
    country_code VARCHAR(2),
    locality VARCHAR(120),
    requested_by_driver_id UUID NOT NULL REFERENCES driver (id),
    requested_by_identity_id UUID REFERENCES messaging_identity (id),
    conversation_id UUID REFERENCES telegram_conversation (id),
    status VARCHAR(40) NOT NULL,
    matched_customer_id UUID REFERENCES customer_reference (id),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(160),
    review_notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_registration_status CHECK (status IN (
        'PENDING_REVIEW',
        'APPROVED',
        'LINKED_TO_EXISTING',
        'REJECTED',
        'CANCELLED'
    ))
);

CREATE INDEX idx_customer_registration_status ON customer_registration_request (status);
CREATE INDEX idx_customer_registration_normalized ON customer_registration_request (normalized_name);
CREATE INDEX idx_customer_registration_driver ON customer_registration_request (requested_by_driver_id);

ALTER TABLE telegram_intake_draft
    ADD COLUMN customer_registration_request_id UUID REFERENCES customer_registration_request (id);

ALTER TABLE wheel_intake_request
    ADD COLUMN customer_registration_request_id UUID REFERENCES customer_registration_request (id);

ALTER TABLE wheel_intake_request
    ALTER COLUMN customer_reference_id DROP NOT NULL;

ALTER TABLE wheel_intake_request
    ADD CONSTRAINT ck_wheel_request_customer_reference CHECK (
        customer_reference_id IS NOT NULL OR customer_registration_request_id IS NOT NULL
    );

CREATE INDEX idx_telegram_draft_customer_registration ON telegram_intake_draft (customer_registration_request_id);
CREATE INDEX idx_wheel_request_customer_registration ON wheel_intake_request (customer_registration_request_id);

CREATE TABLE conversation_customer_candidate (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES telegram_conversation (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    option_type VARCHAR(40) NOT NULL,
    customer_id UUID REFERENCES customer_reference (id),
    customer_name_snapshot VARCHAR(255),
    original_search_text VARCHAR(255) NOT NULL,
    normalized_search_text VARCHAR(255) NOT NULL,
    similarity_score DOUBLE PRECISION,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_conversation_customer_candidate_position UNIQUE (conversation_id, position),
    CONSTRAINT ck_conversation_customer_candidate_type CHECK (option_type IN (
        'EXISTING_CUSTOMER',
        'CREATE_NEW_CUSTOMER',
        'CORRECT_NAME'
    )),
    CONSTRAINT ck_conversation_customer_candidate_customer CHECK (
        (option_type = 'EXISTING_CUSTOMER' AND customer_id IS NOT NULL)
        OR (option_type <> 'EXISTING_CUSTOMER' AND customer_id IS NULL)
    )
);

CREATE INDEX idx_conversation_customer_candidate_conversation ON conversation_customer_candidate (conversation_id);
CREATE INDEX idx_conversation_customer_candidate_customer ON conversation_customer_candidate (customer_id);

ALTER TABLE telegram_conversation DROP CONSTRAINT ck_telegram_conversation_state;
ALTER TABLE telegram_conversation ADD CONSTRAINT ck_telegram_conversation_state CHECK (state IN (
    'AWAITING_DRIVER_NAME',
    'IDLE',
    'AWAITING_CUSTOMER',
    'AWAITING_CUSTOMER_NAME',
    'AWAITING_CUSTOMER_SELECTION',
    'AWAITING_NEW_CUSTOMER_CONFIRMATION',
    'AWAITING_NEW_CUSTOMER_DETAILS',
    'AWAITING_NEW_CUSTOMER_FINAL_CONFIRMATION',
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

UPDATE telegram_conversation
SET state = 'AWAITING_CUSTOMER_NAME'
WHERE state = 'AWAITING_CUSTOMER';
