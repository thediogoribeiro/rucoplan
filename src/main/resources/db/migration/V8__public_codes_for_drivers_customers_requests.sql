CREATE SEQUENCE IF NOT EXISTS driver_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS customer_code_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE driver
    ADD COLUMN IF NOT EXISTS driver_code VARCHAR(20);

ALTER TABLE customer_reference
    ADD COLUMN IF NOT EXISTS customer_code VARCHAR(20);

ALTER TABLE wheel_intake_request
    ADD COLUMN IF NOT EXISTS request_code VARCHAR(15);

UPDATE driver
SET driver_code = 'MOTOR-' || lpad(
        (regexp_replace(external_id, '\D', '', 'g'))::INTEGER::TEXT,
        GREATEST(3, length((regexp_replace(external_id, '\D', '', 'g'))::INTEGER::TEXT)),
        '0'
    )
WHERE driver_code IS NULL
  AND external_id ~ '^D[0-9]+$';

SELECT setval(
    'driver_code_seq',
    GREATEST(
        (SELECT COALESCE(MAX((regexp_match(driver_code, '^MOTOR-([0-9]+)$'))[1]::INTEGER), 0) FROM driver),
        1
    ),
    true
);

WITH generated_driver_codes AS (
    SELECT id, nextval('driver_code_seq') AS code_number
    FROM driver
    WHERE driver_code IS NULL
    ORDER BY id
)
UPDATE driver
SET driver_code = 'MOTOR-' || lpad(
        generated_driver_codes.code_number::TEXT,
        GREATEST(3, length(generated_driver_codes.code_number::TEXT)),
        '0'
    )
FROM generated_driver_codes
WHERE driver.id = generated_driver_codes.id;

SELECT setval(
    'driver_code_seq',
    GREATEST(
        (SELECT COALESCE(MAX((regexp_match(driver_code, '^MOTOR-([0-9]+)$'))[1]::INTEGER), 0) FROM driver),
        1
    ),
    true
);

WITH unique_customer_numbers AS (
    SELECT customer_number
    FROM customer_reference
    WHERE customer_number IS NOT NULL
    GROUP BY customer_number
    HAVING COUNT(*) = 1
)
UPDATE customer_reference customer
SET customer_code = 'CLI-' || lpad(
        customer.customer_number::TEXT,
        GREATEST(3, length(customer.customer_number::TEXT)),
        '0'
    )
FROM unique_customer_numbers unique_numbers
WHERE customer.customer_code IS NULL
  AND customer.customer_number = unique_numbers.customer_number;

SELECT setval(
    'customer_code_seq',
    GREATEST(
        (SELECT COALESCE(MAX((regexp_match(customer_code, '^CLI-([0-9]+)$'))[1]::INTEGER), 0) FROM customer_reference),
        1
    ),
    true
);

WITH generated_customer_codes AS (
    SELECT id, nextval('customer_code_seq') AS code_number
    FROM customer_reference
    WHERE customer_code IS NULL
    ORDER BY id
)
UPDATE customer_reference
SET customer_code = 'CLI-' || lpad(
        generated_customer_codes.code_number::TEXT,
        GREATEST(3, length(generated_customer_codes.code_number::TEXT)),
        '0'
    )
FROM generated_customer_codes
WHERE customer_reference.id = generated_customer_codes.id;

SELECT setval(
    'customer_code_seq',
    GREATEST(
        (SELECT COALESCE(MAX((regexp_match(customer_code, '^CLI-([0-9]+)$'))[1]::INTEGER), 0) FROM customer_reference),
        1
    ),
    true
);

UPDATE wheel_intake_request
SET request_code = 'REQ-' || upper(substr(md5(id::TEXT || '-rucoplan-request-code'), 1, 11))
WHERE request_code IS NULL;

ALTER TABLE driver
    ALTER COLUMN driver_code SET NOT NULL;

ALTER TABLE customer_reference
    ALTER COLUMN customer_code SET NOT NULL;

ALTER TABLE wheel_intake_request
    ALTER COLUMN request_code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_driver_driver_code') THEN
        ALTER TABLE driver ADD CONSTRAINT uk_driver_driver_code UNIQUE (driver_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_customer_reference_customer_code') THEN
        ALTER TABLE customer_reference ADD CONSTRAINT uk_customer_reference_customer_code UNIQUE (customer_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_wheel_request_request_code') THEN
        ALTER TABLE wheel_intake_request ADD CONSTRAINT uk_wheel_request_request_code UNIQUE (request_code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_wheel_request_request_code_format') THEN
        ALTER TABLE wheel_intake_request
            ADD CONSTRAINT ck_wheel_request_request_code_format
            CHECK (request_code ~ '^REQ-[A-Z0-9]{1,11}$' AND length(request_code) <= 15);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_driver_code ON driver (driver_code);
CREATE INDEX IF NOT EXISTS idx_customer_reference_customer_code ON customer_reference (customer_code);
CREATE INDEX IF NOT EXISTS idx_wheel_request_code ON wheel_intake_request (request_code);
