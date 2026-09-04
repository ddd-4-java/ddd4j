ALTER TABLE sample_order_outbox
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ;

ALTER TABLE sample_order_outbox
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(255);

ALTER TABLE sample_order_outbox
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

UPDATE sample_order_outbox
SET available_at = occurred_at
WHERE available_at IS NULL;

ALTER TABLE sample_order_outbox
    ALTER COLUMN available_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sample_order_outbox_delivery
    ON sample_order_outbox (status, available_at, lease_until);
