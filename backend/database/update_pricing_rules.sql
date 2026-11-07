-- Replaces the sample pricing rules of the old bike-rental app with the fees of the bike store.
-- Data only; no table changes. Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_pricing_rules.sql
-- Fees an admin has already edited are kept (ON DUPLICATE KEY does not touch amount/status).

DELETE FROM pricing_rules
WHERE code IN ('UNLOCK_FEE', 'MINUTE_RATE', 'HOUR_RATE', 'WRONG_ZONE_FINE', 'OVERDUE_FINE');

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('REGISTRATION_SERVICE','Phí dịch vụ làm hồ sơ đăng ký xe','Phí cửa hàng thu khi làm hộ thủ tục đăng ký xe. Không gồm lệ phí nộp Nhà nước.',500000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_SAME_CITY','Phí giao xe tận nhà cùng tỉnh','Giao xe đến địa chỉ trong tỉnh, thành phố có showroom.',200000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_OTHER_PROVINCE','Phí giao xe tận nhà khác tỉnh','Giao xe đến địa chỉ ở tỉnh, thành phố không có showroom.',800000,'VND/xe','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_CENTRAL','Tỷ lệ lệ phí trước bạ (thành phố lớn)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',5,'%','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_OTHER','Tỷ lệ lệ phí trước bạ (khu vực khác)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',2,'%','ACTIVE',NOW())
ON DUPLICATE KEY UPDATE code = code;
