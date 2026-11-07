-- V17: a product with colours sells from its colours, so its stock must equal the sum of its active colours' stock.
-- Before this change a manager could type a product total that no colour had. This realigns the totals once.
-- Data only. Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_product_stock_totals.sql
UPDATE products p
SET p.stock_quantity = (
    SELECT COALESCE(SUM(v.stock_quantity), 0)
    FROM product_variants v
    WHERE v.product_id = p.id AND v.is_active = 1
)
WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.is_active = 1);
