CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE daily_production_settings ADD COLUMN IF NOT EXISTS production_site_id UUID;

WITH pt AS (SELECT id FROM production_site WHERE code = 'PT')
UPDATE daily_production_settings s
SET production_site_id = pt.id
FROM pt
WHERE s.production_site_id IS NULL;

ALTER TABLE daily_production_settings ALTER COLUMN production_site_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_daily_settings_site') THEN
        ALTER TABLE daily_production_settings
            ADD CONSTRAINT fk_daily_settings_site FOREIGN KEY (production_site_id) REFERENCES production_site (id);
    END IF;
END $$;

ALTER TABLE daily_production_settings DROP CONSTRAINT IF EXISTS daily_production_settings_settings_key_key;
DROP INDEX IF EXISTS uk_daily_settings_site_key;
DROP INDEX IF EXISTS uk_daily_settings_site_date;

CREATE UNIQUE INDEX uk_daily_settings_site_key
    ON daily_production_settings (production_site_id, settings_key);

CREATE UNIQUE INDEX uk_daily_settings_site_date
    ON daily_production_settings (production_site_id, settings_date)
    WHERE settings_date IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_site_normalized_name
    ON customer_reference (production_site_id, normalized_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_reference_id_site
    ON customer_reference (id, production_site_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_production_plan_id_site
    ON production_plan (id, production_site_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wheel_request_id_site
    ON wheel_intake_request (id, production_site_id);

CREATE INDEX IF NOT EXISTS idx_daily_settings_site_date
    ON daily_production_settings (production_site_id, settings_date);

CREATE INDEX IF NOT EXISTS idx_telegram_inbound_update_status
    ON telegram_inbound_update (status, received_at);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_wheel_request_customer_same_site') THEN
        ALTER TABLE wheel_intake_request
            ADD CONSTRAINT fk_wheel_request_customer_same_site
            FOREIGN KEY (customer_reference_id, production_site_id)
            REFERENCES customer_reference (id, production_site_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_plan_item_plan_same_site') THEN
        ALTER TABLE production_plan_item
            ADD CONSTRAINT fk_plan_item_plan_same_site
            FOREIGN KEY (plan_id, production_site_id)
            REFERENCES production_plan (id, production_site_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_plan_item_request_same_site') THEN
        ALTER TABLE production_plan_item
            ADD CONSTRAINT fk_plan_item_request_same_site
            FOREIGN KEY (request_id, production_site_id)
            REFERENCES wheel_intake_request (id, production_site_id);
    END IF;
END $$;
