-- V33: "how much does an electric bike save" calculator on the bike page. Adds five pricing rules; no table is created or changed.
-- The calculator compares the cost of charging the bike (battery size and range from the bike's specification, price per kWh from the rule below)
-- with the cost of petrol for a motorbike of the same class (price per litre and litres per 100 km from the rules below).
-- A rule that is INACTIVE or not yet in effect makes the calculator use the default shown here instead.
-- It also corrects the 12 V battery of one bike (see below). A rule that exists keeps its amount, status and date (ON DUPLICATE KEY only refreshes name and description). Safe to run more than once.
-- Apply with: mysql -u root -p --default-character-set=utf8mb4 ebike_db < update_savings_calculator.sql

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('SAVINGS_ELECTRICITY_PRICE','Giá điện dùng để tính tiền sạc (bậc trung bình)','Giá một kWh điện sinh hoạt (đã gồm thuế) dùng trong công cụ "Xe điện tiết kiệm bao nhiêu" ở trang xe. Chỉ để ước tính, không cộng vào đơn.',2900,'VND/kWh','ACTIVE',NOW()),
('SAVINGS_PETROL_PRICE','Giá xăng dùng để so sánh tiền xăng','Giá một lít xăng dùng trong công cụ "Xe điện tiết kiệm bao nhiêu". Chỉ để ước tính, không cộng vào đơn.',22000,'VND/lít','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_SCOOTER','Mức tiêu hao xăng so với Xe tay ga điện','Dùng cho Xe tay ga điện (E_SCOOTER): lít xăng một xe tay ga cùng phân khúc chạy 100 km.',2,'lít/100 km','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_BIKE','Mức tiêu hao xăng so với Xe đạp điện','Dùng cho Xe đạp điện (E_BIKE): lít xăng một xe máy số nhỏ chạy 100 km.',1.8,'lít/100 km','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_MOTORBIKE','Mức tiêu hao xăng so với Xe máy điện','Dùng cho Xe máy điện (E_MOTORBIKE, cỡ 50cc, 35-45 km/h): lít xăng một xe máy 50cc chạy 100 km.',1.8,'lít/100 km','ACTIVE',NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- The electric motorbikes are the slow 50cc class, so their default was lowered from 2.5 to 1.8 litres/100 km (not above the scooters' 2).
-- Only a value still at the old 2.5 is changed; one an admin has set is kept.
UPDATE pricing_rules SET amount = 1.8
WHERE code = 'SAVINGS_PETROL_USE_E_MOTORBIKE' AND amount = 2.5;

-- DIBAO Tesla E was loaded with a 12 V battery; every bike of its line has 60 V. At 12 V the calculator showed a charging cost about five times too low.
-- Only the wrong value is replaced; a voltage an admin has edited since is kept.
UPDATE product_specifications s JOIN products p ON p.id = s.product_id
SET s.battery_voltage_v = 60
WHERE p.slug = 'kinetic-tesla-e' AND s.battery_voltage_v = 12;
