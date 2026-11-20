-- V13: the day until which the store keeps a bike for a customer after telling them it is ready ("Chờ lấy").
-- The store may cancel the order only after this time; the manager can move it when the customer reschedules.
-- Changes the table structure. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_orders_pickup_deadline.sql

ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_deadline_at DATETIME NULL;
