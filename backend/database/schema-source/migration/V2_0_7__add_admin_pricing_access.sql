CREATE SCHEMA IF NOT EXISTS ebike_admin;

ALTER TABLE ebike_auth.users
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE ebike_auth.users
    ADD CONSTRAINT chk_users_account_status
    CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'LOCKED')) NOT VALID;

INSERT INTO ebike_auth.roles (name, description)
VALUES
    ('OPERATOR', 'Operations staff account'),
    ('SUPPORT', 'Customer support staff account'),
    ('MAINTENANCE', 'Maintenance staff account')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS ebike_admin.pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    amount DECIMAL(12, 2) NOT NULL,
    unit VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pricing_rules_status CHECK (status IN ('ACTIVE', 'SCHEDULED', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_pricing_rules_status ON ebike_admin.pricing_rules(status);

CREATE TABLE IF NOT EXISTS ebike_admin.promotions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    campaign_name VARCHAR(180) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value DECIMAL(12, 2) NOT NULL,
    max_discount_amount DECIMAL(12, 2),
    usage_count INT NOT NULL DEFAULT 0,
    usage_limit INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_promotions_status CHECK (status IN ('LIVE', 'WAITING', 'EXPIRED', 'DISABLED')),
    CONSTRAINT chk_promotions_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT'))
);

CREATE INDEX IF NOT EXISTS idx_promotions_status ON ebike_admin.promotions(status);
CREATE INDEX IF NOT EXISTS idx_promotions_code ON ebike_admin.promotions(code);

CREATE TABLE IF NOT EXISTS ebike_admin.admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(150) NOT NULL,
    action VARCHAR(150) NOT NULL,
    target VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at ON ebike_admin.admin_audit_logs(created_at DESC);

INSERT INTO ebike_admin.pricing_rules (code, name, description, amount, unit, status, effective_from)
VALUES
    ('UNLOCK_FEE', 'Phí mở khóa', 'Thu một lần khi người dùng bắt đầu chuyến đi.', 5000, 'VND/lượt', 'ACTIVE', CURRENT_TIMESTAMP),
    ('MINUTE_RATE', 'Giá thuê theo phút', 'Áp dụng cho chuyến đi ngắn và tính lũy tiến theo thời gian.', 1800, 'VND/phút', 'ACTIVE', CURRENT_TIMESTAMP),
    ('HOUR_RATE', 'Gói theo giờ', 'Mức trần cho khách thuê xe trong thời gian dài.', 45000, 'VND/giờ', 'SCHEDULED', CURRENT_TIMESTAMP + INTERVAL '5 days'),
    ('WRONG_ZONE_FINE', 'Phạt trả sai khu vực', 'Áp dụng khi xe được trả ngoài vùng hợp lệ.', 30000, 'VND/lần', 'ACTIVE', CURRENT_TIMESTAMP),
    ('OVERDUE_FINE', 'Phạt quá hạn', 'Thu thêm khi chuyến đi vượt thời gian giữ xe cho phép.', 12000, 'VND/15 phút', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ebike_admin.promotions (
    code,
    campaign_name,
    discount_type,
    discount_value,
    max_discount_amount,
    usage_count,
    usage_limit,
    status,
    starts_at,
    ends_at
)
VALUES
    ('KINETIC20', 'Chào hè xanh', 'PERCENTAGE', 20, 25000, 326, 800, 'LIVE', '2026-06-01 00:00:00', '2026-06-30 23:59:59'),
    ('STUDENT15', 'Sinh viên đi học', 'FIXED_AMOUNT', 15000, NULL, 1020, 2000, 'LIVE', '2026-05-15 00:00:00', '2026-07-15 23:59:59'),
    ('WEEKEND', 'Cuối tuần', 'PERCENTAGE', 10, NULL, 0, 500, 'WAITING', '2026-06-08 00:00:00', '2026-06-30 23:59:59')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ebike_admin.admin_audit_logs (actor, action, target, ip_address)
VALUES
    ('System', 'khởi tạo dữ liệu admin', 'pricing rules và promotions', '127.0.0.1')
ON CONFLICT DO NOTHING;
