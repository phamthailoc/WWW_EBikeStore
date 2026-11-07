-- B7: remember which discount code an order used, so each customer can use a code once and a cancelled
-- order frees it. Changes the table structure. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_orders_promotion.sql
-- Orders placed before this change keep promotion_id NULL (the code they used was never recorded), so they
-- do not count against the one-use limit.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_id BIGINT NULL;
ALTER TABLE orders ADD CONSTRAINT IF NOT EXISTS fk_orders_promotion
    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE SET NULL;
