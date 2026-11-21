-- V22: "notify me when back in stock". Adds a table; changes no existing table.
-- Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_stock_alerts.sql

CREATE TABLE IF NOT EXISTS `stock_alerts` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at DATETIME NULL,
    CONSTRAINT fk_stock_alerts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_alerts_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT uk_stock_alerts_user_variant UNIQUE (user_id, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS `idx_stock_alerts_variant_pending` ON `stock_alerts` (variant_id, notified_at);
-- the periodic sweep looks for waiting rows (notified_at IS NULL) across all colours
CREATE INDEX IF NOT EXISTS `idx_stock_alerts_pending` ON `stock_alerts` (notified_at, variant_id);
