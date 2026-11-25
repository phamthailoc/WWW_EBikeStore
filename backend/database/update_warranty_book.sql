-- V20: electronic warranty book. Adds two tables, the column orders.delivered_at (the hand-over time that the warranty
-- counts from) and order_item_units.warranty_months (the length pinned when a vehicle is recorded); the warranty end is
-- computed when read, from those two. Also adds warranty_claims.open_unit_id, which lets the database allow one open request per vehicle.
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
    open_unit_id BIGINT AS (IF(status IN ('NEW', 'RECEIVED', 'REPAIRING'), order_item_unit_id, NULL)) VIRTUAL,
    CONSTRAINT fk_warranty_claims_unit FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_warranty_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_warranty_claims_open_unit UNIQUE (open_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A table that existed before this script may still carry the old status CHECK: drop it (the status is validated by the application).
ALTER TABLE `warranty_claims` DROP CONSTRAINT IF EXISTS `chk_warranty_claims_status`;

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
    CONSTRAINT fk_maintenance_reminders_unit FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE RESTRICT,
    CONSTRAINT uk_maintenance_reminders_unit_milestone UNIQUE (order_item_unit_id, milestone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `orders` ADD COLUMN IF NOT EXISTS `delivered_at` DATETIME NULL;

-- Delivered orders from before V20 have no pinned delivery time; pin it now (shipment time if there is one, else the last change
-- of the order), so the warranty start does not move. The shipment rows are left as they are.

UPDATE `orders` o LEFT JOIN `shipments` s ON s.order_id = o.id
SET o.delivered_at = COALESCE(s.delivered_at, o.updated_at)
WHERE o.status = 'DELIVERED' AND o.delivered_at IS NULL;

-- A vehicle's warranty history must never vanish with the vehicle row: the two foreign keys refuse the delete (RESTRICT)
-- instead of cascading it. Databases made by an earlier version of this file still have ON DELETE CASCADE, so recreate them.
ALTER TABLE `warranty_claims` DROP FOREIGN KEY IF EXISTS `fk_warranty_claims_unit`;
ALTER TABLE `warranty_claims` ADD CONSTRAINT `fk_warranty_claims_unit` FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE RESTRICT;
ALTER TABLE `maintenance_reminders` DROP FOREIGN KEY IF EXISTS `fk_maintenance_reminders_unit`;
ALTER TABLE `maintenance_reminders` ADD CONSTRAINT `fk_maintenance_reminders_unit` FOREIGN KEY (order_item_unit_id) REFERENCES order_item_units(id) ON DELETE RESTRICT;

-- At most one open request (NEW, RECEIVED or REPAIRING) per vehicle, enforced by the database itself: open_unit_id is the
-- vehicle id while the request is open and NULL once it is closed, and NULLs do not collide in a unique key.
-- If this fails with a duplicate-key error, two open requests of one vehicle exist already: close one of them and run it again.
ALTER TABLE `warranty_claims` ADD COLUMN IF NOT EXISTS `open_unit_id` BIGINT AS (IF(status IN ('NEW', 'RECEIVED', 'REPAIRING'), order_item_unit_id, NULL)) VIRTUAL;
CREATE UNIQUE INDEX IF NOT EXISTS `uk_warranty_claims_open_unit` ON `warranty_claims` (open_unit_id);

-- The warranty length of a vehicle is pinned on its own row when the frame number is recorded, so changing
-- product_specifications.warranty_months later does not move the end date of bikes already sold. Vehicles recorded
-- before this change take the product's current length now.
ALTER TABLE `order_item_units` ADD COLUMN IF NOT EXISTS `warranty_months` INT NULL;
UPDATE `order_item_units` u
JOIN `order_items` i ON i.id = u.order_item_id
JOIN `product_specifications` sp ON sp.product_id = i.product_id
SET u.warranty_months = sp.warranty_months
WHERE u.warranty_months IS NULL AND sp.warranty_months IS NOT NULL;

-- Erased accounts used to leave the text "[đã xóa]" in warranty requests; it is now an empty text that the staff page translates.
UPDATE `warranty_claims` SET description = '' WHERE description = '[đã xóa]';
