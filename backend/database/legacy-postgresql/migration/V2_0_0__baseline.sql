-- =====================================================
-- V2 baseline for local/dev environments
-- Consolidates schema and essential seed data from V1_0_0..V1_0_23
-- Product catalog seed remains external in backend/data-backup/
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_auth;
CREATE SCHEMA IF NOT EXISTS ebike_product;
CREATE SCHEMA IF NOT EXISTS ebike_order;
CREATE SCHEMA IF NOT EXISTS ebike_chatbot;
CREATE SCHEMA IF NOT EXISTS ebike_user;

CREATE TABLE IF NOT EXISTS ebike_auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    email_verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON ebike_auth.users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON ebike_auth.users(username);
CREATE INDEX IF NOT EXISTS idx_users_active ON ebike_auth.users(is_active);

CREATE TABLE IF NOT EXISTS ebike_auth.roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS ebike_auth.permissions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    module_name VARCHAR(50) NOT NULL,
    action_name VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE INDEX IF NOT EXISTS idx_permissions_code ON ebike_auth.permissions(code);
CREATE INDEX IF NOT EXISTS idx_permissions_module_action ON ebike_auth.permissions(module_name, action_name);

CREATE TABLE IF NOT EXISTS ebike_auth.user_roles (
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES ebike_auth.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS ebike_auth.role_permissions (
    role_id INT NOT NULL REFERENCES ebike_auth.roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES ebike_auth.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS ebike_auth.user_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES ebike_auth.permissions(id) ON DELETE CASCADE,
    granted BOOLEAN DEFAULT TRUE,
    UNIQUE (user_id, permission_id)
);

CREATE TABLE IF NOT EXISTS ebike_auth.authentication_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES ebike_auth.users(id),
    login_time TIMESTAMP,
    logout_time TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE TABLE IF NOT EXISTS ebike_product.categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS ebike_product.products (
    id BIGSERIAL PRIMARY KEY,
    category_id INT NOT NULL REFERENCES ebike_product.categories(id),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    discount_price DECIMAL(10, 2),
    stock_quantity INT DEFAULT 0,
    rating DECIMAL(3, 2) DEFAULT 0,
    review_count INT DEFAULT 0,
    is_featured BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_products_category ON ebike_product.products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_slug ON ebike_product.products(slug);
CREATE INDEX IF NOT EXISTS idx_products_active ON ebike_product.products(is_active);

CREATE TABLE IF NOT EXISTS ebike_product.reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title VARCHAR(255),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, user_id)
);

CREATE TABLE IF NOT EXISTS ebike_product.product_specifications (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT UNIQUE NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    model_code VARCHAR(100),
    brand VARCHAR(100) NOT NULL DEFAULT 'Yadea-style',
    vehicle_type VARCHAR(50) NOT NULL DEFAULT 'E_SCOOTER',
    battery_type VARCHAR(50),
    battery_capacity_ah DECIMAL(8, 2),
    battery_voltage_v DECIMAL(8, 2),
    charging_time_hours DECIMAL(5, 2),
    max_speed_kmh DECIMAL(6, 2),
    max_range_km DECIMAL(6, 2),
    motor_power_watts INT,
    peak_motor_power_watts INT,
    max_load_kg DECIMAL(8, 2),
    product_weight_kg DECIMAL(8, 2),
    wheel_size_inch DECIMAL(4, 2),
    brake_type VARCHAR(50),
    drive_type VARCHAR(50),
    water_resistance_rating VARCHAR(20),
    frame_material VARCHAR(100),
    suspension_type VARCHAR(100),
    display_type VARCHAR(100),
    smart_features TEXT,
    warranty_months INT,
    dimensions_mm VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS ebike_product.product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    sku VARCHAR(100) UNIQUE NOT NULL,
    variant_name VARCHAR(150) NOT NULL,
    color_name VARCHAR(100),
    color_hex VARCHAR(20),
    battery_capacity_ah DECIMAL(8, 2),
    additional_price DECIMAL(10, 2) DEFAULT 0,
    stock_quantity INT DEFAULT 0,
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_product_variants_product_id ON ebike_product.product_variants(product_id);
CREATE INDEX IF NOT EXISTS idx_product_variants_sku ON ebike_product.product_variants(sku);

CREATE TABLE IF NOT EXISTS ebike_product.product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES ebike_product.product_variants(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    alt_text VARCHAR(255),
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    storage_bucket VARCHAR(255),
    storage_region VARCHAR(100),
    s3_key TEXT,
    mime_type VARCHAR(100),
    file_size BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_images_storage_provider CHECK (storage_provider IN ('LOCAL', 'S3')),
    CONSTRAINT chk_product_images_status CHECK (status IN ('ACTIVE', 'PENDING_DELETE', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON ebike_product.product_images(product_id);
CREATE INDEX IF NOT EXISTS idx_product_images_variant_id ON ebike_product.product_images(variant_id);
CREATE INDEX IF NOT EXISTS idx_product_images_status ON ebike_product.product_images(status);
CREATE INDEX IF NOT EXISTS idx_product_images_storage_provider ON ebike_product.product_images(storage_provider);
CREATE INDEX IF NOT EXISTS idx_product_images_s3_key ON ebike_product.product_images(s3_key) WHERE s3_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_product_images_active_product ON ebike_product.product_images(product_id, sort_order) WHERE status = 'ACTIVE';

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

CREATE TABLE IF NOT EXISTS ebike_order.orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES ebike_auth.users(id),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    shipping_fee DECIMAL(12, 2) DEFAULT 0,
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL,
    registration_fee DECIMAL(12, 2) NOT NULL DEFAULT 0,
    notes TEXT,
    customer_email VARCHAR(255),
    customer_identity_number VARCHAR(30),
    cancellation_reason TEXT,
    cancellation_review_note TEXT,
    cancellation_requested_from_status VARCHAR(30),
    cancellation_requested_by BIGINT REFERENCES ebike_auth.users(id),
    cancellation_requested_at TIMESTAMP,
    cancellation_reviewed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_user ON ebike_order.orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON ebike_order.orders(status);

CREATE TABLE IF NOT EXISTS ebike_order.order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id),
    product_variant_id BIGINT REFERENCES ebike_product.product_variants(id),
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_product_variant ON ebike_order.order_items(product_variant_id);

CREATE TABLE IF NOT EXISTS ebike_order.payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100),
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'VND',
    provider_txn_id VARCHAR(100),
    provider_response TEXT,
    vnpay_response_code VARCHAR(10),
    vnpay_bank_code VARCHAR(30),
    vnpay_card_type VARCHAR(30),
    vnpay_secure_hash VARCHAR(256),
    paid_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_transaction_reference
    ON ebike_order.payments(transaction_reference)
    WHERE transaction_reference IS NOT NULL;

CREATE TABLE IF NOT EXISTS ebike_order.shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    shipment_status VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(255),
    phone_number VARCHAR(30),
    shipping_address TEXT,
    recipient_email VARCHAR(255),
    pickup_district VARCHAR(100),
    detailed_address TEXT,
    pickup_showroom_id BIGINT REFERENCES ebike_order.showrooms(id),
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ebike_user.user_addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    address_type VARCHAR(20) CHECK (address_type IN ('billing', 'shipping')),
    street VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    is_default BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ebike_user.user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    theme VARCHAR(20) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'en',
    notifications_enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS ebike_user.user_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_favorites_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_user_favorites_user_id ON ebike_user.user_favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_user_favorites_product_id ON ebike_user.user_favorites(product_id);

DELETE FROM ebike_auth.role_permissions rp
USING ebike_auth.permissions p
WHERE rp.permission_id = p.id
  AND p.code IN (
      'PRODUCT_VIEW',
      'ORDER_VIEW',
      'ORDER_UPDATE',
      'USER_MANAGE',
      'SYSTEM_MANAGE',
      'cart:view',
      'cart:update'
  );

DELETE FROM ebike_auth.user_permissions up
USING ebike_auth.permissions p
WHERE up.permission_id = p.id
  AND p.code IN (
      'PRODUCT_VIEW',
      'ORDER_VIEW',
      'ORDER_UPDATE',
      'USER_MANAGE',
      'SYSTEM_MANAGE',
      'cart:view',
      'cart:update'
  );

DELETE FROM ebike_auth.permissions
WHERE code IN (
    'PRODUCT_VIEW',
    'ORDER_VIEW',
    'ORDER_UPDATE',
    'USER_MANAGE',
    'SYSTEM_MANAGE',
    'cart:view',
    'cart:update'
);

INSERT INTO ebike_auth.roles (name, description)
VALUES
    ('CUSTOMER', 'Customer account'),
    ('MANAGER', 'Store manager account'),
    ('ADMIN', 'System administrator account')
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO ebike_auth.permissions (code, name, module_name, action_name, description)
VALUES
    ('product:view', 'View products', 'PRODUCT', 'VIEW', 'View product catalog and details'),
    ('product:search', 'Search products', 'PRODUCT', 'SEARCH', 'Search and filter product catalog'),
    ('category:view', 'View categories', 'CATEGORY', 'VIEW', 'View product categories'),
    ('review:view', 'View reviews', 'REVIEW', 'VIEW', 'View product reviews'),
    ('order:create', 'Create orders', 'ORDER', 'CREATE', 'Create own orders'),
    ('order:view-own', 'View own orders', 'ORDER', 'VIEW_OWN', 'View orders owned by the current user'),
    ('order:cancel-own', 'Cancel own orders', 'ORDER', 'CANCEL_OWN', 'Cancel own pending orders'),
    ('payment:create', 'Create payments', 'PAYMENT', 'CREATE', 'Create payment requests for own orders'),
    ('payment:view-own', 'View own payments', 'PAYMENT', 'VIEW_OWN', 'View own payment information'),
    ('favorite:view', 'View favorites', 'FAVORITE', 'VIEW', 'View own favorite products'),
    ('favorite:update', 'Update favorites', 'FAVORITE', 'UPDATE', 'Add and remove own favorite products'),
    ('profile:view', 'View profile', 'PROFILE', 'VIEW', 'View own profile and preferences'),
    ('profile:update', 'Update profile', 'PROFILE', 'UPDATE', 'Update own profile, addresses, and preferences'),
    ('review:create', 'Create reviews', 'REVIEW', 'CREATE', 'Create product reviews'),
    ('product:create', 'Create products', 'PRODUCT', 'CREATE', 'Create products and product images'),
    ('product:update', 'Update products', 'PRODUCT', 'UPDATE', 'Update products, stock, and product images'),
    ('product:delete', 'Delete products', 'PRODUCT', 'DELETE', 'Delete products and product images'),
    ('product:manage-stock', 'Manage stock', 'PRODUCT', 'MANAGE_STOCK', 'Manage product stock quantities'),
    ('category:manage', 'Manage categories', 'CATEGORY', 'MANAGE', 'Create, update, and delete product categories'),
    ('review:moderate', 'Moderate reviews', 'REVIEW', 'MODERATE', 'Moderate product reviews'),
    ('order:view-all', 'View all orders', 'ORDER', 'VIEW_ALL', 'View all customer orders'),
    ('order:update-status', 'Update order status', 'ORDER', 'UPDATE_STATUS', 'Update order fulfillment status'),
    ('order:assign-shipment', 'Assign shipment', 'ORDER', 'ASSIGN_SHIPMENT', 'Assign and update order shipment information'),
    ('payment:verify', 'Verify payments', 'PAYMENT', 'VERIFY', 'Verify payment results and callbacks'),
    ('payment:refund', 'Refund payments', 'PAYMENT', 'REFUND', 'Refund customer payments'),
    ('shipment:manage', 'Manage shipments', 'SHIPMENT', 'MANAGE', 'Manage shipment lifecycle'),
    ('user:view', 'View users', 'USER', 'VIEW', 'View customer account data'),
    ('user:manage', 'Manage users', 'USER', 'MANAGE', 'Manage user accounts'),
    ('role:manage', 'Manage roles', 'ROLE', 'MANAGE', 'Manage role assignments'),
    ('permission:manage', 'Manage permissions', 'PERMISSION', 'MANAGE', 'Manage permissions and direct user grants'),
    ('chatbot:use', 'Use chatbot', 'CHATBOT', 'USE', 'Ask chatbot questions'),
    ('chatbot:view-history', 'View chatbot history', 'CHATBOT', 'VIEW_HISTORY', 'View chatbot conversation history'),
    ('chatbot:manage-faq', 'Manage chatbot FAQ', 'CHATBOT', 'MANAGE_FAQ', 'Manage FAQ knowledge base'),
    ('chatbot:manage-knowledge', 'Manage chatbot knowledge', 'CHATBOT', 'MANAGE_KNOWLEDGE', 'Manage chatbot product knowledge'),
    ('chatbot:configure', 'Configure chatbot', 'CHATBOT', 'CONFIGURE', 'Configure chatbot providers and behavior'),
    ('dashboard:view', 'View dashboard', 'DASHBOARD', 'VIEW', 'View management dashboards'),
    ('report:view', 'View reports', 'REPORT', 'VIEW', 'View business reports'),
    ('system:config', 'Configure system', 'SYSTEM', 'CONFIG', 'Configure system settings'),
    ('audit-log:view', 'View audit logs', 'AUDIT_LOG', 'VIEW', 'View audit and authentication logs')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    module_name = EXCLUDED.module_name,
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT ur.user_id, manager_role.id
FROM ebike_auth.user_roles ur
JOIN ebike_auth.roles staff_role ON staff_role.id = ur.role_id AND staff_role.name = 'STAFF'
JOIN ebike_auth.roles manager_role ON manager_role.name = 'MANAGER'
ON CONFLICT DO NOTHING;

DELETE FROM ebike_auth.user_roles ur
USING ebike_auth.roles r
WHERE ur.role_id = r.id
  AND r.name = 'STAFF';

DELETE FROM ebike_auth.role_permissions rp
USING ebike_auth.roles r
WHERE rp.role_id = r.id
  AND r.name = 'STAFF';

DELETE FROM ebike_auth.roles
WHERE name = 'STAFF';

INSERT INTO ebike_auth.users (
    username,
    email,
    password_hash,
    first_name,
    last_name,
    is_active,
    is_verified,
    email_verified_at
)
SELECT
    'manager.user',
    'manager.user@example.com',
    '$2a$10$tRiY3ac6Bnx5EHBkjP5ek.ofn3urim7dE36HV4uY3MpPWPficRNoy',
    'Store',
    'Manager',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'manager.user' OR email = 'manager.user@example.com'
);

INSERT INTO ebike_auth.users (
    username,
    email,
    password_hash,
    first_name,
    last_name,
    is_active,
    is_verified,
    email_verified_at
)
SELECT
    'admin.user',
    'admin.user@example.com',
    '$2a$10$jLEp29fpNpvnAQDGZppNJeZpILS2AQXQiJunqbwJhjI3cBpDaF5Ba',
    'System',
    'Admin',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'admin.user' OR email = 'admin.user@example.com'
);

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'MANAGER'
WHERE u.username = 'manager.user'
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'ADMIN'
WHERE u.username = 'admin.user'
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p ON (
    (r.name = 'CUSTOMER' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'order:create',
        'order:view-own',
        'order:cancel-own',
        'payment:create',
        'payment:view-own',
        'favorite:view',
        'favorite:update',
        'profile:view',
        'profile:update',
        'review:create',
        'chatbot:use',
        'chatbot:view-history'
    ))
    OR (r.name = 'MANAGER' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'favorite:view',
        'favorite:update',
        'product:create',
        'product:update',
        'product:delete',
        'product:manage-stock',
        'category:manage',
        'review:moderate',
        'order:view-all',
        'order:update-status',
        'order:assign-shipment',
        'payment:verify',
        'payment:refund',
        'shipment:manage',
        'user:view',
        'user:manage',
        'chatbot:use',
        'chatbot:view-history',
        'chatbot:manage-faq',
        'chatbot:manage-knowledge',
        'dashboard:view',
        'report:view'
    ))
    OR (r.name = 'ADMIN' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'order:create',
        'order:view-own',
        'order:cancel-own',
        'payment:create',
        'payment:view-own',
        'favorite:view',
        'favorite:update',
        'profile:view',
        'profile:update',
        'review:create',
        'product:create',
        'product:update',
        'product:delete',
        'product:manage-stock',
        'category:manage',
        'review:moderate',
        'order:view-all',
        'order:update-status',
        'order:assign-shipment',
        'payment:verify',
        'payment:refund',
        'shipment:manage',
        'user:view',
        'user:manage',
        'role:manage',
        'permission:manage',
        'chatbot:use',
        'chatbot:view-history',
        'chatbot:manage-faq',
        'chatbot:manage-knowledge',
        'chatbot:configure',
        'dashboard:view',
        'report:view',
        'system:config',
        'audit-log:view'
    ))
)
ON CONFLICT DO NOTHING;

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Quan 1 - Nguyen Thai Hoc', 'Quan 1', '187 Nguyen Thai Hoc, Phuong Pham Ngu Lao, Quan 1, TP. Ho Chi Minh', '028 3920 1234', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Quan 1 - Nguyen Thai Hoc');

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Quan 3 - Cach Mang Thang 8', 'Quan 3', '420 Cach Mang Thang 8, Phuong 11, Quan 3, TP. Ho Chi Minh', '028 3931 5678', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Quan 3 - Cach Mang Thang 8');

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Quan 7 - Nguyen Thi Thap', 'Quan 7', '1021 Nguyen Thi Thap, Phuong Tan Phu, Quan 7, TP. Ho Chi Minh', '028 3776 8899', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Quan 7 - Nguyen Thi Thap');

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Go Vap - Quang Trung', 'Go Vap', '588 Quang Trung, Phuong 11, Go Vap, TP. Ho Chi Minh', '028 3589 4567', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Go Vap - Quang Trung');

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Thu Duc - Vo Van Ngan', 'Thu Duc', '216 Vo Van Ngan, Phuong Binh Tho, Thu Duc, TP. Ho Chi Minh', '028 3722 7788', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Thu Duc - Vo Van Ngan');

INSERT INTO ebike_order.showrooms (name, district, address, phone, opening_hours, is_active)
SELECT 'KINETIC Tan Binh - Cong Hoa', 'Tan Binh', '45 Cong Hoa, Phuong 4, Tan Binh, TP. Ho Chi Minh', '028 3811 2233', '08:00 - 21:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ebike_order.showrooms WHERE name = 'KINETIC Tan Binh - Cong Hoa');
