-- =====================================================
-- Add selected product variant to order items
-- =====================================================

ALTER TABLE ebike_order.order_items
ADD COLUMN IF NOT EXISTS product_variant_id BIGINT;

ALTER TABLE ebike_order.order_items
ADD CONSTRAINT fk_order_items_product_variant
FOREIGN KEY (product_variant_id)
REFERENCES ebike_product.product_variants(id);

CREATE INDEX IF NOT EXISTS idx_order_items_product_variant
ON ebike_order.order_items(product_variant_id);
