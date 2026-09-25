ALTER TABLE wheel_intake_request
    DROP CONSTRAINT IF EXISTS ck_wheel_request_status;

UPDATE wheel_intake_request
SET lifecycle_status = 'CONFIRMED'
WHERE lifecycle_status = 'REGISTERED';

ALTER TABLE wheel_intake_request
    ADD CONSTRAINT ck_wheel_request_status CHECK (lifecycle_status IN (
        'CONFIRMED',
        'REGISTERED',
        'ARRIVED_AT_FACTORY',
        'IN_PRODUCTION',
        'READY_FOR_PICKUP',
        'PICKED_UP_FROM_FACTORY',
        'CANCELLED'
    ));
