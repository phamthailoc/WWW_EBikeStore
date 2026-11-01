ALTER TABLE ebike_order.orders
    ADD COLUMN IF NOT EXISTS registration_fee DECIMAL(12, 2) NOT NULL DEFAULT 0;
