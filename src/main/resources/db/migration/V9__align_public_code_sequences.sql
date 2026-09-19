DO $$
DECLARE
    max_driver_code INTEGER;
BEGIN
    SELECT COALESCE(MAX((regexp_match(driver_code, '^MOTOR-([0-9]+)$'))[1]::INTEGER), 0)
    INTO max_driver_code
    FROM driver;

    IF max_driver_code = 0 THEN
        PERFORM setval('driver_code_seq', 1, false);
    ELSE
        PERFORM setval('driver_code_seq', max_driver_code, true);
    END IF;
END $$;

DO $$
DECLARE
    max_customer_code INTEGER;
BEGIN
    SELECT COALESCE(MAX((regexp_match(customer_code, '^CLI-([0-9]+)$'))[1]::INTEGER), 0)
    INTO max_customer_code
    FROM customer_reference;

    IF max_customer_code = 0 THEN
        PERFORM setval('customer_code_seq', 1, false);
    ELSE
        PERFORM setval('customer_code_seq', max_customer_code, true);
    END IF;
END $$;
