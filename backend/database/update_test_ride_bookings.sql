-- V23: test-ride bookings. Adds one table; changes no existing table.
-- One row per booking of a bike at a showroom for a one-hour slot. The generated column active_user_slot lets the database itself
-- refuse a second REQUESTED/CONFIRMED booking of the same customer at the same time (NULLs do not collide once a booking is closed).
-- The number of bookings per showroom and slot is held by the application (it locks the showroom row), not by this table.
-- Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_test_ride_bookings.sql

CREATE TABLE IF NOT EXISTS `test_ride_bookings` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    showroom_id BIGINT NOT NULL,
    slot_start DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    contact_phone VARCHAR(20) NOT NULL,
    customer_note VARCHAR(500) NULL,
    staff_note VARCHAR(1000) NULL,
    confirmed_at DATETIME NULL,
    reminder_sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_user_slot VARCHAR(40) AS (IF(status IN ('REQUESTED', 'CONFIRMED'), CONCAT(user_id, '@', slot_start), NULL)) VIRTUAL,
    CONSTRAINT fk_test_ride_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_test_ride_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_ride_showroom FOREIGN KEY (showroom_id) REFERENCES showrooms(id) ON DELETE RESTRICT,
    CONSTRAINT chk_test_ride_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'DONE', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT uk_test_ride_active_user_slot UNIQUE (active_user_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS `idx_test_ride_showroom_slot` ON `test_ride_bookings` (showroom_id, slot_start, status);
CREATE INDEX IF NOT EXISTS `idx_test_ride_user_slot` ON `test_ride_bookings` (user_id, slot_start);
-- the reminder job and the manager's queue read by status and time
CREATE INDEX IF NOT EXISTS `idx_test_ride_status_slot` ON `test_ride_bookings` (status, slot_start);
