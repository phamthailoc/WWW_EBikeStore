-- V13: remember when a store-pickup order became "Chờ lấy" (READY_FOR_PICKUP), so the store may cancel it
-- only after the pickup hold period (app.order.pickup-hold) has passed since the customer was notified.
-- Changes the table structure. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_orders_ready_for_pickup.sql

ALTER TABLE orders ADD COLUMN IF NOT EXISTS ready_for_pickup_at DATETIME NULL;
