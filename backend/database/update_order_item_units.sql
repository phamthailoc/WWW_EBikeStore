-- V18: the frame number (số khung) and engine/motor number (số máy) of each vehicle sold, entered by staff when the
-- order is handed over. Adds a table; changes no existing table. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_order_item_units.sql
-- Orders placed before this change have no rows here and stay as they are; only handing over a new order needs them.

CREATE TABLE IF NOT EXISTS `order_item_units` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    frame_number VARCHAR(30) NOT NULL,
    engine_number VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_units_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
    CONSTRAINT uq_order_item_units_frame UNIQUE (frame_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
