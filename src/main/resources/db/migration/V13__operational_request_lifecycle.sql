ALTER TABLE wheel_intake_request
    ADD COLUMN IF NOT EXISTS arrival_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS arrival_confirmed_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS arrival_confirmation_source VARCHAR(80);

ALTER TABLE wheel_intake_request
    DROP CONSTRAINT IF EXISTS ck_wheel_request_status;

UPDATE wheel_intake_request
SET lifecycle_status = CASE
    WHEN lifecycle_status = 'CANCELLED' THEN 'CANCELLED'
    WHEN lifecycle_status = 'PICKED_UP_FROM_FACTORY' THEN 'READY_FOR_PICKUP'
    WHEN COALESCE(completed_wheel_quantity, 0) >= expected_wheel_quantity THEN 'READY_FOR_PICKUP'
    WHEN lifecycle_status = 'READY_FOR_PICKUP' THEN 'READY_FOR_PICKUP'
    WHEN lifecycle_status = 'IN_PRODUCTION' OR COALESCE(completed_wheel_quantity, 0) > 0 THEN 'IN_PRODUCTION'
    WHEN actual_factory_arrival_at IS NOT NULL OR lifecycle_status = 'ARRIVED_AT_FACTORY' THEN 'AT_FACTORY'
    ELSE 'COMMUNICATED'
END;

UPDATE wheel_intake_request
SET arrival_confirmed_at = COALESCE(arrival_confirmed_at, actual_factory_arrival_at),
    arrival_confirmed_by = COALESCE(arrival_confirmed_by, updated_by, 'SYSTEM'),
    arrival_confirmation_source = COALESCE(arrival_confirmation_source, 'MIGRATION')
WHERE actual_factory_arrival_at IS NOT NULL;

ALTER TABLE wheel_intake_request
    ADD CONSTRAINT ck_wheel_request_status CHECK (lifecycle_status IN (
        'COMMUNICATED',
        'AT_FACTORY',
        'IN_PRODUCTION',
        'READY_FOR_PICKUP',
        'CANCELLED'
    ));
