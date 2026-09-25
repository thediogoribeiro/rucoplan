ALTER TABLE driver
    ADD COLUMN IF NOT EXISTS rucofi_id VARCHAR(120);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_driver_rucofi_id') THEN
        ALTER TABLE driver ADD CONSTRAINT uk_driver_rucofi_id UNIQUE (rucofi_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_driver_rucofi_id ON driver (rucofi_id);
