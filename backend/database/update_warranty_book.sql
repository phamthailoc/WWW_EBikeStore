-- V20: electronic warranty book. Adds two tables; changes no existing table (the warranty start and end are
-- computed when read, from the delivery time and product_specifications.warranty_months).
-- Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_warranty_book.sql

CREATE TABLE IF NOT EXISTS `warranty_claims` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_unit_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    description LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    out_of_warranty TINYINT(1) NOT NULL DEFAULT 0,
    staff_note LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_warranty_claims_unit FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE CASCADE,
    CONSTRAINT fk_warranty_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_warranty_claims_status CHECK (status IN ('NEW', 'RECEIVED', 'REPAIRING', 'DONE', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS `idx_warranty_claims_status` ON `warranty_claims` (status, created_at);
CREATE INDEX IF NOT EXISTS `idx_warranty_claims_unit` ON `warranty_claims` (order_item_unit_id);

-- One row per (vehicle, maintenance milestone already reminded), so the daily job never reminds twice.
CREATE TABLE IF NOT EXISTS `maintenance_reminders` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_unit_id BIGINT NOT NULL,
    milestone VARCHAR(20) NOT NULL,
    due_at DATETIME NOT NULL,
    notified TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_reminders_unit FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_maintenance_reminders_unit_milestone UNIQUE (order_item_unit_id, milestone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
