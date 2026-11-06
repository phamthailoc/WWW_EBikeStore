ALTER TABLE ebike_order.orders
    ADD COLUMN IF NOT EXISTS include_registration_service BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE ebike_order.orders
SET include_registration_service = TRUE
WHERE registration_fee > 0;
