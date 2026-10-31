ALTER TABLE ebike_order.orders
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS cancellation_review_note TEXT,
    ADD COLUMN IF NOT EXISTS cancellation_requested_from_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS cancellation_requested_by BIGINT REFERENCES ebike_auth.users(id),
    ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_reviewed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_cancellation_requested_at
    ON ebike_order.orders(cancellation_requested_at)
    WHERE cancellation_requested_at IS NOT NULL;
