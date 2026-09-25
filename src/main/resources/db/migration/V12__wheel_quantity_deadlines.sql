ALTER TABLE request_wheel_quantity
    ADD COLUMN IF NOT EXISTS requested_deadline_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_deadline_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deadline_adjustment_reason VARCHAR(240);

CREATE OR REPLACE FUNCTION rucoplan_add_business_days(start_date DATE, business_days INTEGER)
RETURNS DATE
LANGUAGE plpgsql
AS $$
DECLARE
    result DATE := start_date;
    remaining INTEGER := business_days;
BEGIN
    WHILE remaining > 0 LOOP
        result := result + 1;
        IF EXTRACT(ISODOW FROM result) BETWEEN 1 AND 5 THEN
            remaining := remaining - 1;
        END IF;
    END LOOP;
    RETURN result;
END;
$$;

UPDATE request_wheel_quantity quantity
SET
    requested_deadline_at = request.requested_factory_pickup_start,
    effective_deadline_at = CASE
        WHEN quantity.wheel_type = 'BIPARTITE'
             AND quantity.quantity > 0
             AND (
                (
                    rucoplan_add_business_days(
                        (COALESCE(request.actual_factory_arrival_at, request.expected_factory_dropoff_end) AT TIME ZONE 'Europe/Lisbon')::date,
                        15
                    )
                    + (request.requested_factory_pickup_start AT TIME ZONE 'Europe/Lisbon')::time
                ) AT TIME ZONE 'Europe/Lisbon'
             ) > request.requested_factory_pickup_start
            THEN (
                (
                    rucoplan_add_business_days(
                        (COALESCE(request.actual_factory_arrival_at, request.expected_factory_dropoff_end) AT TIME ZONE 'Europe/Lisbon')::date,
                        15
                    )
                    + (request.requested_factory_pickup_start AT TIME ZONE 'Europe/Lisbon')::time
                ) AT TIME ZONE 'Europe/Lisbon'
            )
        ELSE request.requested_factory_pickup_start
    END,
    deadline_adjustment_reason = CASE
        WHEN quantity.wheel_type = 'BIPARTITE'
             AND quantity.quantity > 0
             AND (
                (
                    rucoplan_add_business_days(
                        (COALESCE(request.actual_factory_arrival_at, request.expected_factory_dropoff_end) AT TIME ZONE 'Europe/Lisbon')::date,
                        15
                    )
                    + (request.requested_factory_pickup_start AT TIME ZONE 'Europe/Lisbon')::time
                ) AT TIME ZONE 'Europe/Lisbon'
             ) > request.requested_factory_pickup_start
            THEN 'Prazo mínimo de 15 dias úteis para jantes bipartidas.'
        ELSE NULL
    END
FROM wheel_intake_request request
WHERE quantity.request_id = request.id;

CREATE INDEX IF NOT EXISTS idx_request_wheel_quantity_effective_deadline
    ON request_wheel_quantity (effective_deadline_at);

DROP FUNCTION rucoplan_add_business_days(DATE, INTEGER);
