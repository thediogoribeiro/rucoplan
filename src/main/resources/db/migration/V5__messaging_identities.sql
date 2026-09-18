CREATE TABLE messaging_identity (
    id UUID PRIMARY KEY,
    driver_id UUID REFERENCES driver (id) ON DELETE SET NULL,
    channel VARCHAR(40) NOT NULL,
    integration_key VARCHAR(120) NOT NULL,
    external_user_id VARCHAR(160) NOT NULL,
    external_chat_id VARCHAR(160),
    external_username VARCHAR(255),
    platform_first_name VARCHAR(255),
    platform_last_name VARCHAR(255),
    language_code VARCHAR(20),
    phone_number VARCHAR(40),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    onboarding_status VARCHAR(40) NOT NULL,
    onboarding_completed_at TIMESTAMPTZ,
    blocked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_messaging_identity_channel CHECK (channel IN ('TELEGRAM', 'WHATSAPP')),
    CONSTRAINT ck_messaging_identity_onboarding CHECK (onboarding_status IN (
        'AWAITING_DRIVER_NAME',
        'CONTACT_OPTIONAL',
        'COMPLETED',
        'PENDING_ADMIN_REVIEW',
        'BLOCKED'
    )),
    CONSTRAINT uk_messaging_identity_external_user UNIQUE (channel, integration_key, external_user_id)
);

CREATE INDEX idx_messaging_identity_driver ON messaging_identity (driver_id);
CREATE INDEX idx_messaging_identity_channel ON messaging_identity (channel);
CREATE INDEX idx_messaging_identity_onboarding ON messaging_identity (onboarding_status);
CREATE INDEX idx_messaging_identity_last_seen ON messaging_identity (last_seen_at);
CREATE INDEX idx_messaging_identity_username ON messaging_identity (external_username);

CREATE TABLE messaging_identity_event (
    id UUID PRIMARY KEY,
    messaging_identity_id UUID NOT NULL REFERENCES messaging_identity (id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(160),
    metadata VARCHAR(1000),
    CONSTRAINT ck_messaging_identity_event_type CHECK (event_type IN (
        'FIRST_SEEN',
        'ONBOARDING_STARTED',
        'DRIVER_NAME_REGISTERED',
        'CONTACT_SHARED',
        'ONBOARDING_COMPLETED',
        'PROFILE_UPDATED',
        'IDENTITY_LINKED',
        'IDENTITY_BLOCKED',
        'IDENTITY_REACTIVATED'
    ))
);

CREATE INDEX idx_messaging_identity_event_identity ON messaging_identity_event (messaging_identity_id);
CREATE INDEX idx_messaging_identity_event_type ON messaging_identity_event (event_type);
CREATE INDEX idx_messaging_identity_event_occurred ON messaging_identity_event (occurred_at);

ALTER TABLE telegram_conversation
    ADD COLUMN messaging_identity_id UUID REFERENCES messaging_identity (id);

ALTER TABLE wheel_intake_request
    ADD COLUMN submitted_by_identity_id UUID REFERENCES messaging_identity (id);

CREATE INDEX idx_telegram_conversation_identity ON telegram_conversation (messaging_identity_id);
CREATE INDEX idx_wheel_request_submitted_identity ON wheel_intake_request (submitted_by_identity_id);

INSERT INTO messaging_identity (
    id,
    driver_id,
    channel,
    integration_key,
    external_user_id,
    external_chat_id,
    external_username,
    platform_first_name,
    platform_last_name,
    first_seen_at,
    last_seen_at,
    onboarding_status,
    onboarding_completed_at,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    driver.id,
    'TELEGRAM',
    'RucodelPlanBot',
    driver.telegram_user_id::TEXT,
    driver.telegram_chat_id::TEXT,
    driver.telegram_username,
    driver.telegram_first_name,
    driver.telegram_last_name,
    COALESCE(driver.telegram_linked_at, driver.created_at),
    COALESCE(driver.telegram_last_interaction_at, driver.updated_at),
    'COMPLETED',
    COALESCE(driver.telegram_linked_at, driver.created_at),
    driver.created_at,
    driver.updated_at,
    'MIGRATION',
    'MIGRATION'
FROM driver
WHERE driver.telegram_user_id IS NOT NULL
ON CONFLICT (channel, integration_key, external_user_id) DO NOTHING;

INSERT INTO messaging_identity (
    id,
    driver_id,
    channel,
    integration_key,
    external_user_id,
    external_chat_id,
    first_seen_at,
    last_seen_at,
    onboarding_status,
    onboarding_completed_at,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    conversation.driver_id,
    'TELEGRAM',
    'RucodelPlanBot',
    conversation.telegram_user_id::TEXT,
    conversation.telegram_chat_id::TEXT,
    conversation.created_at,
    conversation.updated_at,
    CASE WHEN conversation.driver_id IS NULL THEN 'AWAITING_DRIVER_NAME' ELSE 'COMPLETED' END,
    CASE WHEN conversation.driver_id IS NULL THEN NULL ELSE conversation.updated_at END,
    conversation.created_at,
    conversation.updated_at,
    'MIGRATION',
    'MIGRATION'
FROM telegram_conversation conversation
WHERE NOT EXISTS (
    SELECT 1
    FROM messaging_identity identity
    WHERE identity.channel = 'TELEGRAM'
      AND identity.integration_key = 'RucodelPlanBot'
      AND identity.external_user_id = conversation.telegram_user_id::TEXT
)
ON CONFLICT (channel, integration_key, external_user_id) DO NOTHING;

UPDATE telegram_conversation conversation
SET messaging_identity_id = identity.id
FROM messaging_identity identity
WHERE identity.channel = 'TELEGRAM'
  AND identity.integration_key = 'RucodelPlanBot'
  AND identity.external_user_id = conversation.telegram_user_id::TEXT;

UPDATE wheel_intake_request request
SET submitted_by_identity_id = identity.id
FROM messaging_identity identity
WHERE request.source = 'TELEGRAM'
  AND request.driver_id = identity.driver_id
  AND identity.channel = 'TELEGRAM'
  AND identity.integration_key = 'RucodelPlanBot';
