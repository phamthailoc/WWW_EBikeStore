-- V33: "how much does an electric bike save" calculator on the bike page. Adds five pricing rules; no table is created or changed.
-- The calculator compares the cost of charging the bike (battery size and range from the bike's specification, price per kWh from the rule below)
-- with the cost of petrol for a motorbike of the same class (price per litre and litres per 100 km from the rules below).
-- A rule that is INACTIVE or not yet in effect makes the calculator use the default shown here instead.
-- Rules an admin has already edited are kept (ON DUPLICATE KEY does not touch amount/status). Safe to run more than once.
-- Apply with: mysql -u root -p --default-character-set=utf8mb4 ebike_db < update_savings_calculator.sql

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('SAVINGS_ELECTRICITY_PRICE','Giá điện dùng để tính tiền sạc (bậc trung bình)','Giá một kWh điện sinh hoạt (đã gồm thuế) dùng trong công cụ "Xe điện tiết kiệm bao nhiêu" ở trang xe. Chỉ để ước tính, không cộng vào đơn.',2900,'VND/kWh','ACTIVE',NOW()),
('SAVINGS_PETROL_PRICE','Giá xăng dùng để so sánh tiền xăng','Giá một lít xăng dùng trong công cụ "Xe điện tiết kiệm bao nhiêu". Chỉ để ước tính, không cộng vào đơn.',22000,'VND/lít','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_SCOOTER','Mức tiêu hao xăng của xe ga cùng phân khúc','So với xe điện loại xe ga (E_SCOOTER). Lít xăng xe máy chạy 100 km.',2,'lít/100 km','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_BIKE','Mức tiêu hao xăng của xe máy số nhỏ','So với xe đạp điện (E_BIKE). Lít xăng xe máy chạy 100 km.',1.8,'lít/100 km','ACTIVE',NOW()),
('SAVINGS_PETROL_USE_E_MOTORBIKE','Mức tiêu hao xăng của xe máy phân khúc lớn hơn','So với xe máy điện (E_MOTORBIKE). Lít xăng xe máy chạy 100 km.',2.5,'lít/100 km','ACTIVE',NOW())
ON DUPLICATE KEY UPDATE code = code;
