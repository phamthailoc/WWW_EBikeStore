CREATE TABLE IF NOT EXISTS ebike_order.showrooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'TP. Ho Chi Minh',
    district VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(30),
    opening_hours VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE ebike_order.orders
    ADD COLUMN IF NOT EXISTS customer_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_identity_number VARCHAR(30);

ALTER TABLE ebike_order.shipments
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_district VARCHAR(100),
    ADD COLUMN IF NOT EXISTS detailed_address TEXT,
    ADD COLUMN IF NOT EXISTS pickup_showroom_id BIGINT REFERENCES ebike_order.showrooms(id);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Quận 1 - Nguyễn Thái Học', 'Quận 1', '187 Nguyễn Thái Học, Phường Phạm Ngũ Lão, Quận 1, TP. Hồ Chí Minh', '028 3920 1234', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Quận 1 - Nguyễn Thái Học'
);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Quận 3 - Cách Mạng Tháng 8', 'Quận 3', '420 Cách Mạng Tháng 8, Phường 11, Quận 3, TP. Hồ Chí Minh', '028 3931 5678', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Quận 3 - Cách Mạng Tháng 8'
);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Quận 7 - Nguyễn Thị Thập', 'Quận 7', '1021 Nguyễn Thị Thập, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', '028 3776 8899', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Quận 7 - Nguyễn Thị Thập'
);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Gò Vấp - Quang Trung', 'Gò Vấp', '588 Quang Trung, Phường 11, Gò Vấp, TP. Hồ Chí Minh', '028 3589 4567', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Gò Vấp - Quang Trung'
);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Thủ Đức - Võ Văn Ngân', 'Thủ Đức', '216 Võ Văn Ngân, Phường Bình Thọ, Thủ Đức, TP. Hồ Chí Minh', '028 3722 7788', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Thủ Đức - Võ Văn Ngân'
);

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'YADEA Tân Bình - Cộng Hòa', 'Tân Bình', '45 Cộng Hòa, Phường 4, Tân Bình, TP. Hồ Chí Minh', '028 3811 2233', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ebike_order.showrooms
    WHERE name = 'YADEA Tân Bình - Cộng Hòa'
);
