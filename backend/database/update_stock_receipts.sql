-- V26: stock receipts and the stock ledger. Adds three tables; changes no existing table.
-- Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_stock_receipts.sql

CREATE TABLE IF NOT EXISTS `stock_receipts` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_name VARCHAR(150) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_receipts_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS `idx_stock_receipts_created` ON `stock_receipts` (created_at);
CREATE TABLE IF NOT EXISTS `stock_receipt_lines` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    quantity INT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    color_name VARCHAR(100) NULL,
    CONSTRAINT fk_stock_receipt_lines_receipt FOREIGN KEY (receipt_id) REFERENCES stock_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_receipt_lines_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_receipt_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT chk_stock_receipt_lines_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS `idx_stock_receipt_lines_receipt` ON `stock_receipt_lines` (receipt_id);
CREATE TABLE IF NOT EXISTS `stock_movements` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    quantity_change INT NOT NULL,
    balance_after INT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    order_id BIGINT NULL,
    order_number VARCHAR(50) NULL,
    receipt_id BIGINT NULL,
    actor_user_id BIGINT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movements_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_movements_receipt FOREIGN KEY (receipt_id) REFERENCES stock_receipts(id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_stock_movements_reason CHECK (reason IN ('RECEIPT', 'SALE', 'CANCEL_RESTORE', 'ADJUSTMENT', 'OPENING')),
    CONSTRAINT chk_stock_movements_change CHECK (quantity_change <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS `idx_stock_movements_product` ON `stock_movements` (product_id, id);
CREATE INDEX IF NOT EXISTS `idx_stock_movements_variant` ON `stock_movements` (variant_id, id);
CREATE INDEX IF NOT EXISTS `idx_stock_movements_order` ON `stock_movements` (order_id);
CREATE INDEX IF NOT EXISTS `idx_stock_movements_receipt` ON `stock_movements` (receipt_id);

-- V26: stock that exists before the ledger starts is written as one OPENING line per colour (or per bike without colours),
-- so that the lines of a colour add up to its stock. A colour that already has a line is left alone.
INSERT INTO stock_movements(product_id, variant_id, quantity_change, balance_after, reason)
SELECT v.product_id, v.id, v.stock_quantity, v.stock_quantity, 'OPENING'
FROM product_variants v
WHERE v.stock_quantity > 0 AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.variant_id = v.id);
INSERT INTO stock_movements(product_id, variant_id, quantity_change, balance_after, reason)
SELECT p.id, NULL, p.stock_quantity, p.stock_quantity, 'OPENING'
FROM products p
WHERE p.stock_quantity > 0
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.is_active = 1)
  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = p.id AND m.variant_id IS NULL);
