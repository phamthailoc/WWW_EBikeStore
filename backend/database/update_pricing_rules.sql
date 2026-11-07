-- Replaces the sample pricing rules of the old bike-rental app with the fees of the bike store.
-- Data only; no table changes. Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_pricing_rules.sql
-- Fees an admin has already edited are kept (ON DUPLICATE KEY does not touch amount/status).

DELETE FROM pricing_rules
WHERE code IN ('UNLOCK_FEE', 'MINUTE_RATE', 'HOUR_RATE', 'WRONG_ZONE_FINE', 'OVERDUE_FINE');

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('REGISTRATION_SERVICE','Phí hỗ trợ đăng ký biển số tại cửa hàng','Nhân viên hỗ trợ chủ xe tự kê khai trên VNeID khi nhận xe tại cửa hàng. Không gồm lệ phí nộp Nhà nước.',500000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_SAME_CITY','Phí giao xe tận nhà cùng tỉnh','Giao xe đến địa chỉ trong tỉnh, thành phố có showroom.',200000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_OTHER_PROVINCE','Phí giao xe tận nhà khác tỉnh','Giao xe đến địa chỉ ở tỉnh, thành phố không có showroom.',800000,'VND/xe','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_CENTRAL','Tỷ lệ lệ phí trước bạ (thành phố lớn)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',5,'%','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_OTHER','Tỷ lệ lệ phí trước bạ (khu vực khác)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',2,'%','ACTIVE',NOW())
ON DUPLICATE KEY UPDATE code = code;

-- The store only helps the owner register at pickup (the owner declares on their own VNeID account), so the
-- rule's name and description changed. Amount and status stay as the admin set them.
UPDATE pricing_rules
SET name = 'Phí hỗ trợ đăng ký biển số tại cửa hàng',
    description = 'Nhân viên hỗ trợ chủ xe tự kê khai trên VNeID khi nhận xe tại cửa hàng. Không gồm lệ phí nộp Nhà nước.'
WHERE code = 'REGISTRATION_SERVICE';
