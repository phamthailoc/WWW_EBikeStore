-- KINETIC E-Bike Store: MariaDB single-database setup
-- Source: backend/database/schema-source and backend/database/catalog/products.json
-- Run this file in HeidiSQL. Images are referenced by relative local paths.
CREATE DATABASE IF NOT EXISTS ebike_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ebike_db;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active TINYINT(1) DEFAULT TRUE,
    is_verified TINYINT(1) DEFAULT FALSE,
    email_verified_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    module_name VARCHAR(50) NOT NULL,
    action_name VARCHAR(50) NOT NULL,
    description LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions` (
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_permissions`;
CREATE TABLE `user_permissions` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted TINYINT(1) DEFAULT TRUE,
    UNIQUE (user_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `authentication_logs`;
CREATE TABLE `authentication_logs` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    login_time DATETIME,
    logout_time DATETIME,
    ip_address VARCHAR(45),
    user_agent LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description LONGTEXT,
    is_active TINYINT(1) DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL REFERENCES categories(id),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    description LONGTEXT,
    price DECIMAL(10, 2) NOT NULL,
    discount_price DECIMAL(10, 2),
    stock_quantity INT DEFAULT 0,
    rating DECIMAL(3, 2) DEFAULT 0,
    review_count INT DEFAULT 0,
    is_featured TINYINT(1) DEFAULT FALSE,
    is_active TINYINT(1) DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title VARCHAR(255),
    comment LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `product_specifications`;
CREATE TABLE `product_specifications` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNIQUE NOT NULL REFERENCES products(id) ON DELETE CASCADE,
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
    smart_features LONGTEXT,
    warranty_months INT,
    dimensions_mm VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `product_variants`;
CREATE TABLE `product_variants` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) UNIQUE NOT NULL,
    variant_name VARCHAR(150) NOT NULL,
    color_name VARCHAR(100),
    color_hex VARCHAR(20),
    battery_capacity_ah DECIMAL(8, 2),
    additional_price DECIMAL(10, 2) DEFAULT 0,
    stock_quantity INT DEFAULT 0,
    is_default TINYINT(1) DEFAULT FALSE,
    is_active TINYINT(1) DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `product_images`;
CREATE TABLE `product_images` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES product_variants(id) ON DELETE CASCADE,
    image_url LONGTEXT NOT NULL,
    alt_text VARCHAR(255),
    sort_order INT DEFAULT 0,
    is_primary TINYINT(1) DEFAULT FALSE,
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    storage_bucket VARCHAR(255),
    storage_region VARCHAR(100),
    s3_key LONGTEXT,
    mime_type VARCHAR(100),
    file_size BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    deleted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_images_storage_provider CHECK (storage_provider IN ('LOCAL', 'S3')),
    CONSTRAINT chk_product_images_status CHECK (status IN ('ACTIVE', 'PENDING_DELETE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `showrooms`;
CREATE TABLE `showrooms` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'TP. Ho Chi Minh',
    district VARCHAR(100) NOT NULL,
    address LONGTEXT NOT NULL,
    phone VARCHAR(30),
    opening_hours VARCHAR(255),
    is_active TINYINT(1) NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    shipping_fee DECIMAL(12, 2) DEFAULT 0,
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL,
    registration_fee DECIMAL(12, 2) NOT NULL DEFAULT 0,
    notes LONGTEXT,
    customer_email VARCHAR(255),
    customer_identity_number VARCHAR(30),
    cancellation_reason LONGTEXT,
    cancellation_review_note LONGTEXT,
    cancellation_requested_from_status VARCHAR(30),
    cancellation_requested_by BIGINT REFERENCES users(id),
    cancellation_requested_at DATETIME,
    cancellation_reviewed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `order_items`;
CREATE TABLE `order_items` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_variant_id BIGINT REFERENCES product_variants(id),
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payments`;
CREATE TABLE `payments` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100),
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'VND',
    provider_txn_id VARCHAR(100),
    provider_response LONGTEXT,
    vnpay_response_code VARCHAR(10),
    vnpay_bank_code VARCHAR(30),
    vnpay_card_type VARCHAR(30),
    vnpay_secure_hash VARCHAR(256),
    paid_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `shipments`;
CREATE TABLE `shipments` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    shipment_status VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(255),
    phone_number VARCHAR(30),
    shipping_address LONGTEXT,
    recipient_email VARCHAR(255),
    pickup_district VARCHAR(100),
    detailed_address LONGTEXT,
    pickup_showroom_id BIGINT REFERENCES showrooms(id),
    tracking_number VARCHAR(100),
    shipped_at DATETIME,
    delivered_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_addresses`;
CREATE TABLE `user_addresses` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address_type VARCHAR(20) CHECK (address_type IN ('billing', 'shipping')),
    street VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    is_default TINYINT(1) DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_preferences`;
CREATE TABLE `user_preferences` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme VARCHAR(20) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'en',
    notifications_enabled TINYINT(1) DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_favorites`;
CREATE TABLE `user_favorites` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_favorites_user_product UNIQUE (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_users_email` ON `users` (email);
CREATE INDEX `idx_users_username` ON `users` (username);
CREATE INDEX `idx_users_active` ON `users` (is_active);
CREATE INDEX `idx_permissions_code` ON `permissions` (code);
CREATE INDEX `idx_permissions_module_action` ON `permissions` (module_name, action_name);
CREATE INDEX `idx_products_category` ON `products` (category_id);
CREATE INDEX `idx_products_slug` ON `products` (slug);
CREATE INDEX `idx_products_active` ON `products` (is_active);
CREATE INDEX `idx_product_variants_product_id` ON `product_variants` (product_id);
CREATE INDEX `idx_product_variants_sku` ON `product_variants` (sku);
CREATE INDEX `idx_product_images_product_id` ON `product_images` (product_id);
CREATE INDEX `idx_product_images_variant_id` ON `product_images` (variant_id);
CREATE INDEX `idx_product_images_status` ON `product_images` (status);
CREATE INDEX `idx_product_images_storage_provider` ON `product_images` (storage_provider);
CREATE INDEX `idx_product_images_s3_key` ON `product_images` (s3_key(191));
CREATE INDEX `idx_product_images_active_product` ON `product_images` (product_id, sort_order);
CREATE INDEX `idx_orders_user` ON `orders` (user_id);
CREATE INDEX `idx_orders_status` ON `orders` (status);
CREATE INDEX `idx_order_items_product_variant` ON `order_items` (product_variant_id);
CREATE UNIQUE INDEX `idx_payments_transaction_reference` ON `payments` (transaction_reference);
CREATE INDEX `idx_user_favorites_user_id` ON `user_favorites` (user_id);
CREATE INDEX `idx_user_favorites_product_id` ON `user_favorites` (product_id);
CREATE INDEX `idx_orders_cancellation_requested_at` ON `orders` (cancellation_requested_at);
ALTER TABLE orders ADD COLUMN include_registration_service TINYINT(1) NOT NULL DEFAULT 0;

DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message LONGTEXT NOT NULL,
    target_url VARCHAR(255),
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    payment_id BIGINT REFERENCES payments(id) ON DELETE SET NULL,
    read_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_notifications_user_created` ON `notifications` (user_id, created_at DESC);
CREATE INDEX `idx_notifications_user_unread` ON `notifications` (user_id);

DROP TABLE IF EXISTS `order_email_verification_sessions`;
CREATE TABLE `order_email_verification_sessions` (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    otp_expires_at DATETIME NOT NULL,
    resend_available_at DATETIME NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at DATETIME,
    consumed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE orders DROP COLUMN customer_identity_number;
CREATE INDEX `idx_order_email_verification_email_created` ON `order_email_verification_sessions` (email, created_at DESC);

DROP TABLE IF EXISTS `pricing_rules`;
CREATE TABLE `pricing_rules` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    description LONGTEXT,
    amount DECIMAL(12, 2) NOT NULL,
    unit VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pricing_rules_status CHECK (status IN ('ACTIVE', 'SCHEDULED', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `promotions`;
CREATE TABLE `promotions` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    campaign_name VARCHAR(180) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value DECIMAL(12, 2) NOT NULL,
    max_discount_amount DECIMAL(12, 2),
    usage_count INT NOT NULL DEFAULT 0,
    usage_limit INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    starts_at DATETIME,
    ends_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_promotions_status CHECK (status IN ('LIVE', 'WAITING', 'EXPIRED', 'DISABLED')),
    CONSTRAINT chk_promotions_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `admin_audit_logs`;
CREATE TABLE `admin_audit_logs` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(150) NOT NULL,
    action VARCHAR(150) NOT NULL,
    target VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE users ADD COLUMN account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX `idx_pricing_rules_status` ON `pricing_rules` (status);
CREATE INDEX `idx_promotions_status` ON `promotions` (status);
CREATE INDEX `idx_promotions_code` ON `promotions` (code);
CREATE INDEX `idx_admin_audit_logs_created_at` ON `admin_audit_logs` (created_at DESC);

INSERT INTO roles(name,description) VALUES ('CUSTOMER','Customer account'),('MANAGER','Store manager account'),('ADMIN','System administrator account'),('OPERATOR','Operations staff account'),('SUPPORT','Customer support staff account'),('MAINTENANCE','Maintenance staff account');
INSERT INTO users(username,email,password_hash,first_name,last_name,is_active,is_verified,email_verified_at) VALUES
('manager.user','manager.user@example.com','$2a$10$tRiY3ac6Bnx5EHBkjP5ek.ofn3urim7dE36HV4uY3MpPWPficRNoy','Store','Manager',1,1,NOW()),
('admin.user','admin.user@example.com','$2a$10$jLEp29fpNpvnAQDGZppNJeZpILS2AQXQiJunqbwJhjI3cBpDaF5Ba','System','Admin',1,1,NOW());
INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.name=IF(u.username='admin.user','ADMIN','MANAGER') WHERE u.username IN ('admin.user','manager.user');
INSERT INTO permissions(code,name,module_name,action_name,description) VALUES
('product:view','View products','PRODUCT','VIEW','View product catalog and details'),
('product:search','Search products','PRODUCT','SEARCH','Search and filter product catalog'),
('category:view','View categories','CATEGORY','VIEW','View product categories'),
('review:view','View reviews','REVIEW','VIEW','View product reviews'),
('order:create','Create orders','ORDER','CREATE','Create own orders'),
('order:view-own','View own orders','ORDER','VIEW_OWN','View orders owned by the current user'),
('order:cancel-own','Cancel own orders','ORDER','CANCEL_OWN','Cancel own pending orders'),
('payment:create','Create payments','PAYMENT','CREATE','Create payment requests for own orders'),
('payment:view-own','View own payments','PAYMENT','VIEW_OWN','View own payment information'),
('favorite:view','View favorites','FAVORITE','VIEW','View own favorite products'),
('favorite:update','Update favorites','FAVORITE','UPDATE','Add and remove own favorite products'),
('profile:view','View profile','PROFILE','VIEW','View own profile and preferences'),
('profile:update','Update profile','PROFILE','UPDATE','Update own profile, addresses, and preferences'),
('review:create','Create reviews','REVIEW','CREATE','Create product reviews'),
('product:create','Create products','PRODUCT','CREATE','Create products and product images'),
('product:update','Update products','PRODUCT','UPDATE','Update products, stock, and product images'),
('product:delete','Delete products','PRODUCT','DELETE','Delete products and product images'),
('product:manage-stock','Manage stock','PRODUCT','MANAGE_STOCK','Manage product stock quantities'),
('category:manage','Manage categories','CATEGORY','MANAGE','Create, update, and delete product categories'),
('review:moderate','Moderate reviews','REVIEW','MODERATE','Moderate product reviews'),
('order:view-all','View all orders','ORDER','VIEW_ALL','View all customer orders'),
('order:update-status','Update order status','ORDER','UPDATE_STATUS','Update order fulfillment status'),
('order:assign-shipment','Assign shipment','ORDER','ASSIGN_SHIPMENT','Assign and update order shipment information'),
('payment:verify','Verify payments','PAYMENT','VERIFY','Verify payment results and callbacks'),
('payment:refund','Refund payments','PAYMENT','REFUND','Refund customer payments'),
('shipment:manage','Manage shipments','SHIPMENT','MANAGE','Manage shipment lifecycle'),
('user:view','View users','USER','VIEW','View customer account data'),
('user:manage','Manage users','USER','MANAGE','Manage user accounts'),
('role:manage','Manage roles','ROLE','MANAGE','Manage role assignments'),
('permission:manage','Manage permissions','PERMISSION','MANAGE','Manage permissions and direct user grants'),
('chatbot:use','Use chatbot','CHATBOT','USE','Ask chatbot questions'),
('chatbot:view-history','View chatbot history','CHATBOT','VIEW_HISTORY','View chatbot conversation history'),
('chatbot:manage-faq','Manage chatbot FAQ','CHATBOT','MANAGE_FAQ','Manage FAQ knowledge base'),
('chatbot:manage-knowledge','Manage chatbot knowledge','CHATBOT','MANAGE_KNOWLEDGE','Manage chatbot product knowledge'),
('chatbot:configure','Configure chatbot','CHATBOT','CONFIGURE','Configure chatbot providers and behavior'),
('dashboard:view','View dashboard','DASHBOARD','VIEW','View management dashboards'),
('report:view','View reports','REPORT','VIEW','View business reports'),
('system:config','Configure system','SYSTEM','CONFIG','Configure system settings'),
('audit-log:view','View audit logs','AUDIT_LOG','VIEW','View audit and authentication logs');
INSERT IGNORE INTO role_permissions(role_id,permission_id) SELECT r.id, p.id
FROM roles r
JOIN permissions p ON (
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
);

INSERT INTO categories(name,slug,description,is_active) VALUES
('Xe đạp điện','xe-dap-dien','Xe đạp điện có bàn đạp.',1),
('Xe điện','xe-dien','Xe sử dụng động cơ điện.',1);

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Amy','kinetic-amy','DIBAO Amy là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',17700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'AMY','DIBAO','E_BIKE','LEAD_ACID',22.0,48,12.0,35.0,70.0,1000,1000,75.0,67.0,NULL,'DRUM',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.510mm x 680mm x 1.095mm' FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-verified-c01','Xanh lam','Xanh lam','#607f91',22.0,0,10,1,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-verified-c02','Trắng','Trắng','#eeeeda',22.0,0,10,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-verified-c03','Nâu','Nâu','#a38478',22.0,0,10,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-verified-c04','Xanh bạc hà','Xanh bạc hà','#88aaa0',22.0,0,9,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-verified-c05','Xám','Xám','#858c8a',22.0,0,9,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-amy/source-01.png','DIBAO Amy - Xanh lam',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-verified-c01' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-amy/source-02.png','DIBAO Amy - Trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-verified-c02' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-amy/source-03.png','DIBAO Amy - Nâu',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-verified-c03' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-amy/source-04.png','DIBAO Amy - Xanh bạc hà',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-verified-c04' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-amy/source-05.png','DIBAO Amy - Xám',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-verified-c05' WHERE p.slug='kinetic-amy';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Ella','kinetic-ella','DIBAO Ella là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',19500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'ELLA','DIBAO','E_BIKE','LEAD_ACID',22.0,60,12.0,45.0,70.0,1400,1400,75.0,84.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.690mm x 675mm x 1.075mm' FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-verified-c01','Hồng','Hồng','#d9bacb',22.0,0,10,1,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-verified-c02','Xanh ngọc nhạt','Xanh ngọc nhạt','#9cafa7',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-verified-c03','Trắng','Trắng','#eceff1',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-verified-c04','Kem','Kem','#e9dfc3',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-verified-c05','Đen','Đen','#202524',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ella/source-01.png','DIBAO Ella - Hồng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-verified-c01' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ella/source-02.png','DIBAO Ella - Xanh ngọc nhạt',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-verified-c02' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ella/source-03.png','DIBAO Ella - Trắng',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-verified-c03' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ella/source-04.png','DIBAO Ella - Kem',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-verified-c04' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ella/source-06.png','DIBAO Ella - Đen',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-verified-c05' WHERE p.slug='kinetic-ella';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Queen','kinetic-queen','DIBAO Queen là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',16800000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'QUEEN','DIBAO','E_BIKE','LEAD_ACID',22.0,60,12.0,45.0,80.0,1550,1550,130.0,86.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.590mm x 690mm x 1.100mm' FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c01','Xanh lam','Xanh lam','#657f8f/#ede5cf',22.0,0,8,1,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c02','Xanh nhạt','Xanh nhạt','#b5d4d9/#ede5cf',22.0,0,8,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c03','Xanh ngọc','Xanh ngọc','#83b3ad/#ede5cf',22.0,0,8,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c04','Xanh đậm','Xanh đậm','#354d63/#ede5cf',22.0,0,8,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c05','Xám','Xám','#9a9b8e/#ede5cf',22.0,0,8,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-verified-c06','Trắng xanh','Trắng xanh','#d1e4e9/#ede5cf',22.0,0,8,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-01.png','DIBAO Queen - Xanh lam',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c01' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-02.png','DIBAO Queen - Xanh nhạt',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c02' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-03.png','DIBAO Queen - Xanh ngọc',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c03' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-04.png','DIBAO Queen - Xanh đậm',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c04' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-05.png','DIBAO Queen - Xám',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c05' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-queen/source-06.png','DIBAO Queen - Trắng xanh',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-verified-c06' WHERE p.slug='kinetic-queen';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Ryan','kinetic-ryan','DIBAO Ryan là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',17700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'RYAN','DIBAO','E_BIKE','LEAD_ACID',22.0,60,12.0,45.0,70.0,1550,1550,130.0,88.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.650mm x 660mm x 1.055mm' FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-verified-c01','Đen','Đen','#27353b',22.0,0,10,1,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-verified-c02','Xanh bạc hà','Xanh bạc hà','#a9c9c6',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-verified-c03','Trắng','Trắng','#dce6e9',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-verified-c04','Xám','Xám','#8a9191',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-verified-c05','Xanh lam','Xanh lam','#547e98',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-01.png','DIBAO Ryan - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c01' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-02.png','DIBAO Ryan - Xanh bạc hà',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c02' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-03.png','DIBAO Ryan - Trắng',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c03' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-04.png','DIBAO Ryan - Trắng',41,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c03' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-05.png','DIBAO Ryan - Xám',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c04' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ryan/source-06.png','DIBAO Ryan - Xanh lam',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-verified-c05' WHERE p.slug='kinetic-ryan';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO S One','kinetic-s-one','DIBAO S One là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',15900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'S-ONE','DIBAO','E_BIKE','LEAD_ACID',22.0,60,12.0,45.0,70.0,1550,1550,75.0,89.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.645mm x 710mm x 1.060mm' FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c01','Hồng','Hồng','#d794a5',22.0,0,8,1,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c02','Be','Be','#bdb0a0',22.0,0,8,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c03','Đen','Đen','#16191b',22.0,0,8,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c04','Xanh đậm','Xanh đậm','#384951',22.0,0,8,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c05','Xanh xám','Xanh xám','#95aaa0',22.0,0,8,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-verified-c06','Trắng kem','Trắng kem','#ded5c6',22.0,0,8,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-01.png','DIBAO S One - Hồng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c01' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-02.png','DIBAO S One - Be',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c02' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-03.png','DIBAO S One - Đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c03' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-04.png','DIBAO S One - Xanh đậm',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c04' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-05.png','DIBAO S One - Xanh xám',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c05' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-s-one/source-06.png','DIBAO S One - Trắng kem',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-verified-c06' WHERE p.slug='kinetic-s-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Creer E','kinetic-creer-e','DIBAO Creer E là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',21500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'CREER-E','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,13.0,50.0,90.0,1400,1400,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.720mm x 660mm x 1.160mm' FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c01','Đen kem','Đen kem','#252c26/#e5e4c8',23.0,0,8,1,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c02','Cam trắng','Cam trắng','#d66c29/#f2f2e9',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c03','Trắng','Trắng','#e7ebe5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c04','Đỏ trắng','Đỏ trắng','#a92232/#f2f2e9',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c05','Vàng trắng','Vàng trắng','#e4c326/#f2f2e9',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-verified-c06','Kem','Kem','#d8d5ae',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-01.jpg','DIBAO Creer E - Đen kem',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c01' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-02.jpg','DIBAO Creer E - Cam trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c02' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-03.jpg','DIBAO Creer E - Trắng',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c03' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-04.jpg','DIBAO Creer E - Đỏ trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c04' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-05.jpg','DIBAO Creer E - Vàng trắng',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c05' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-e/source-06.jpg','DIBAO Creer E - Kem',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-verified-c06' WHERE p.slug='kinetic-creer-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Creer Nile','kinetic-creer-nile','DIBAO Creer Nile là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'CREER-NILE','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,13.0,50.0,90.0,1400,1400,150.0,96.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.700mm x 660mm x 1.070mm' FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c01','Xanh lam trắng','Xanh lam trắng','#48748a/#f4f4e5',23.0,0,8,1,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c02','Cam trắng','Cam trắng','#ee9417/#f4f4e5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c03','Xanh ngọc trắng','Xanh ngọc trắng','#48a4bc/#f4f4e5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c04','Vàng trắng','Vàng trắng','#e6db17/#f4f4e5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c05','Đen trắng','Đen trắng','#1d3442/#f4f4e5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-verified-c06','Xanh lục trắng','Xanh lục trắng','#298c8e/#f4f4e5',23.0,0,8,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-01.png','DIBAO Creer Nile - Xanh lam trắng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c01' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-02.png','DIBAO Creer Nile - Cam trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c02' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-03.png','DIBAO Creer Nile - Xanh ngọc trắng',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c03' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-04.png','DIBAO Creer Nile - Vàng trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c04' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-05.png','DIBAO Creer Nile - Đen trắng',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c05' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-creer-nile/source-06.png','DIBAO Creer Nile - Xanh lục trắng',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-verified-c06' WHERE p.slug='kinetic-creer-nile';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo Cross','kinetic-gogo-cross','DIBAO Gogo Cross là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',23700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROSS','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,12.0,50.0,90.0,1450,1450,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.730mm x 730mm x 1.040mm' FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-verified-c01','Đen','Đen','#252b31',23.0,0,10,1,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-verified-c02','Đỏ','Đỏ','#a82f40',23.0,0,10,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-verified-c03','Xanh ngọc','Xanh ngọc','#85a9b0',23.0,0,10,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-verified-c04','Trắng','Trắng','#e4ebee',23.0,0,9,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-verified-c05','Xanh đậm','Xanh đậm','#435a6d',23.0,0,9,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-01.png','DIBAO Gogo Cross - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c01' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-02.png','DIBAO Gogo Cross - Đỏ',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c02' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-03.png','DIBAO Gogo Cross - Đỏ',21,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c02' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-04.png','DIBAO Gogo Cross - Xanh ngọc',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c03' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-05.png','DIBAO Gogo Cross - Trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c04' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross/source-06.png','DIBAO Gogo Cross - Xanh đậm',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-verified-c05' WHERE p.slug='kinetic-gogo-cross';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo Cross G','kinetic-gogo-cross-g','DIBAO Gogo Cross G là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',22600000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROSS-G','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,50.0,90.0,1350,1350,130.0,97.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.720mm x 735mm x 1.040mm' FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c01','Cam','Cam','#d87722',22.0,0,8,1,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c02','Đen','Đen','#29343b',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c03','Đỏ','Đỏ','#af3347',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c04','Trắng','Trắng','#e1e6e4',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c05','Xanh ngọc','Xanh ngọc','#85a7a9',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-verified-c06','Xanh đậm','Xanh đậm','#43596a',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-01.png','DIBAO Gogo Cross G - Cam',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c01' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-02.png','DIBAO Gogo Cross G - Đen',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c02' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-03.png','DIBAO Gogo Cross G - Đỏ',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c03' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-04.png','DIBAO Gogo Cross G - Trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c04' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-05.png','DIBAO Gogo Cross G - Xanh ngọc',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c05' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-cross-g/source-06.png','DIBAO Gogo Cross G - Xanh đậm',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-verified-c06' WHERE p.slug='kinetic-gogo-cross-g';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo Crown','kinetic-gogo-crown','DIBAO Gogo Crown là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROWN','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,54.0,60.0,1600,1600,130.0,97.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.725mm x 740mm x 1.040mm' FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c01','Xanh cốm','Xanh cốm','#9aab58',22.0,0,8,1,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c02','Xám trắng','Xám trắng','#879394/#d5c7ae',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c03','Xám nâu','Xám nâu','#a39887',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c04','Đỏ tươi','Đỏ tươi','#cf283e',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c05','Trắng ngọc hồng','Trắng ngọc hồng','#ecebd3/#b83c70',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-verified-c06','Đen','Đen','#252c29',22.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-01.png','DIBAO Gogo Crown - Xanh cốm',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c01' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-02.png','DIBAO Gogo Crown - Xám trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c02' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-03.png','DIBAO Gogo Crown - Xám nâu',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c03' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-04.png','DIBAO Gogo Crown - Đỏ tươi',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c04' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-05.png','DIBAO Gogo Crown - Trắng ngọc hồng',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c05' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-crown/source-06.png','DIBAO Gogo Crown - Đen',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-verified-c06' WHERE p.slug='kinetic-gogo-crown';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo Moon','kinetic-gogo-moon','DIBAO Gogo Moon là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',21500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-MOON','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,13.0,50.0,80.0,1500,1500,150.0,107.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.800mm x 965mm x 1.080mm' FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-verified-c01','Xanh nhạt','Xanh nhạt','#a2c9c6/#eee9d3',22.0,0,16,1,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-verified-c02','Trắng','Trắng','#e1eeee',22.0,0,16,0,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-verified-c03','Xanh ngọc','Xanh ngọc','#5fa99d/#eee9d3',22.0,0,16,0,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-moon/source-01.png','DIBAO Gogo Moon - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-verified-c01' WHERE p.slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-moon/source-02.png','DIBAO Gogo Moon - Trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-verified-c02' WHERE p.slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-moon/source-03.png','DIBAO Gogo Moon - Xanh ngọc',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-verified-c03' WHERE p.slug='kinetic-gogo-moon';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Keva','kinetic-keva','DIBAO Keva là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'KEVA','DIBAO','E_BIKE','LEAD_ACID',22.0,60,12.0,45.0,70.0,1400,1400,75.0,83.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.670mm x 665mm x 1.040mm' FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-verified-c01','Đen','Đen','#29373e',22.0,0,10,1,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-verified-c02','Trắng','Trắng','#e4ebdf',22.0,0,10,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-verified-c03','Kem','Kem','#dfd6a7',22.0,0,10,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-verified-c04','Xanh nhạt','Xanh nhạt','#a1beb2',22.0,0,9,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-verified-c05','Vàng xanh','Vàng xanh','#f2c323/#30bbb9',22.0,0,9,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-01.png','DIBAO Keva - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c01' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-05.png','DIBAO Keva - Đen',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c01' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-02.png','DIBAO Keva - Trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c02' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-03.png','DIBAO Keva - Kem',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c03' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-04.png','DIBAO Keva - Xanh nhạt',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c04' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-keva/source-06.png','DIBAO Keva - Vàng xanh',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-verified-c05' WHERE p.slug='kinetic-keva';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Ls007','kinetic-ls007','DIBAO Ls007 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',23300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'LS007','DIBAO','E_SCOOTER','LEAD_ACID',22.0,72,14.0,54.0,60.0,2800,2800,130.0,NULL,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.920mm x 700mm x 1.110mm' FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-verified-c01','Trắng','Trắng','#dce6e4',22.0,0,10,1,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-verified-c02','Xám xanh','Xám xanh','#849a9e',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-verified-c03','Đen','Đen','#222a29',22.0,0,10,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-verified-c04','Ghi bóng','Ghi bóng','#93a4a8',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-verified-c05','Xanh lam','Xanh lam','#227ba8',22.0,0,9,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ls007/source-01.png','DIBAO Ls007 - Trắng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-verified-c01' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ls007/source-02.png','DIBAO Ls007 - Xám xanh',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-verified-c02' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ls007/source-03.png','DIBAO Ls007 - Đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-verified-c03' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ls007/source-04.png','DIBAO Ls007 - Ghi bóng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-verified-c04' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-ls007/source-05.png','DIBAO Ls007 - Xanh lam',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-verified-c05' WHERE p.slug='kinetic-ls007';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO M One','kinetic-m-one','DIBAO M One là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'M-ONE','DIBAO','E_SCOOTER','LEAD_ACID',20.0,48,8.0,40.0,110.0,980,980,130.0,83.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.630mm x 720mm x 1.250mm' FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-m-one-verified-c01','Trắng đen','Trắng đen','#dbe6e0/#1d2924',20.0,0,48,1,1 FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-m-one/source-01.webp','DIBAO M One - Trắng đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-m-one-verified-c01' WHERE p.slug='kinetic-m-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Mxs','kinetic-mxs','DIBAO Mxs là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',20000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'MXS','DIBAO','E_BIKE','LEAD_ACID',23.0,60,12.0,45.0,70.0,1400,1400,75.0,90.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.700mm x 700mm x 1.055mm' FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-verified-c01','Xám hồng','Xám hồng','#b1a6a6/#24272b',23.0,0,10,1,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-verified-c02','Xanh cam','Xanh cam','#abc0a9/#eb7222',23.0,0,10,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-verified-c03','Đen cam','Đen cam','#24272b/#eb7222',23.0,0,10,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-verified-c04','Xanh lam cam','Xanh lam cam','#8399ac/#eb7222',23.0,0,9,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-verified-c05','Trắng cam','Trắng cam','#e3e5d3/#eb7222',23.0,0,9,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-mxs/source-01.png','DIBAO Mxs - Xám hồng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-verified-c01' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-mxs/source-02.png','DIBAO Mxs - Xanh cam',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-verified-c02' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-mxs/source-03.png','DIBAO Mxs - Đen cam',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-verified-c03' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-mxs/source-04.png','DIBAO Mxs - Xanh lam cam',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-verified-c04' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-mxs/source-05.png','DIBAO Mxs - Trắng cam',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-verified-c05' WHERE p.slug='kinetic-mxs';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy Dio','kinetic-pansy-dio','DIBAO Pansy Dio là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-DIO','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,55.0,60.0,1450,1450,130.0,97.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.720mm x 710mm x 1.120mm' FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-verified-c01','Đen','Đen','#253b3a',22.0,0,10,1,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-verified-c02','Đỏ','Đỏ','#b51d30',22.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-verified-c03','Xám xanh','Xám xanh','#7c9096',22.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-verified-c04','Tím','Tím','#77617f',22.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-verified-c05','Xanh lục','Xanh lục','#428b77',22.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio/source-01.webp','DIBAO Pansy Dio - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-verified-c01' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio/source-02.jpg','DIBAO Pansy Dio - Đỏ',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-verified-c02' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio/source-03.jpg','DIBAO Pansy Dio - Xám xanh',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-verified-c03' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio/source-04.jpg','DIBAO Pansy Dio - Tím',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-verified-c04' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio/source-05.jpg','DIBAO Pansy Dio - Xanh lục',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-verified-c05' WHERE p.slug='kinetic-pansy-dio';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy Dio 2025','kinetic-pansy-dio-2025','DIBAO Pansy Dio 2025 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',22200000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-DIO-2025','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,50.0,60.0,1350,1350,130.0,97.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.740mm x 710mm x 1.120mm' FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c01','Đen','Đen','#323c42',22.0,0,8,1,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c02','Xanh cốm nhạt','Xanh cốm nhạt','#c1cfb6',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c03','Xám bạc','Xám bạc','#a6b6bc',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c04','Kem','Kem','#d8cdb1',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c05','Đỏ','Đỏ','#c22431',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-verified-c06','Xanh ô liu','Xanh ô liu','#8b9d49',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-01.png','DIBAO Pansy Dio 2025 - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c01' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-02.png','DIBAO Pansy Dio 2025 - Xanh cốm nhạt',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c02' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-03.png','DIBAO Pansy Dio 2025 - Xám bạc',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c03' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-04.png','DIBAO Pansy Dio 2025 - Kem',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c04' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-05.png','DIBAO Pansy Dio 2025 - Đỏ',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c05' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-dio-2025/source-06.png','DIBAO Pansy Dio 2025 - Xanh ô liu',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-verified-c06' WHERE p.slug='kinetic-pansy-dio-2025';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy S2 Càng Nhôm','kinetic-pansy-s2-cang-nhom','DIBAO Pansy S2 Càng Nhôm là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',24400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S2-CANG-NHOM','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,50.0,100.0,1450,1450,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.750mm x 710mm x 1.110mm' FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s2-cang-nhom-verified-c01','Xám xanh','Xám xanh','#8cabb4',22.0,0,48,1,1 FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s2-cang-nhom/source-01.png','DIBAO Pansy S2 Càng Nhôm - Xám xanh',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s2-cang-nhom-verified-c01' WHERE p.slug='kinetic-pansy-s2-cang-nhom';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy S3','kinetic-pansy-s3','DIBAO Pansy S3 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S3','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,13.0,50.0,90.0,1350,1350,150.0,98.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.760mm x 710mm x 1.140mm' FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-verified-c01','Xanh nhạt','Xanh nhạt','#a3c9c7',22.0,0,10,1,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-verified-c02','Xanh lam','Xanh lam','#254c8e',22.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-verified-c03','Xám xanh','Xám xanh','#829ca8',22.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-verified-c04','Đen','Đen','#232f30',22.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-verified-c05','Đỏ','Đỏ','#c42e43',22.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-01.png','DIBAO Pansy S3 - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c01' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-04.png','DIBAO Pansy S3 - Xanh nhạt',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c01' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-02.png','DIBAO Pansy S3 - Xanh lam',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c02' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-03.png','DIBAO Pansy S3 - Xám xanh',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c03' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-05.png','DIBAO Pansy S3 - Đen',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c04' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s3/source-06.png','DIBAO Pansy S3 - Đỏ',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-verified-c05' WHERE p.slug='kinetic-pansy-s3';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy S4','kinetic-pansy-s4','DIBAO Pansy S4 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S4','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,13.0,50.0,90.0,1350,1350,150.0,98.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.770mm x 710mm x 1.140mm' FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c01','Xanh nhạt','Xanh nhạt','#98beb3',22.0,0,8,1,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c02','Xanh lam','Xanh lam','#2b4d91',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c03','Xám','Xám','#8b9da0',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c04','Đen','Đen','#272c2c',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c05','Xám nhũ','Xám nhũ','#969e91',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-verified-c06','Xanh cốm','Xanh cốm','#cad8b9',22.0,0,8,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-01.png','DIBAO Pansy S4 - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c01' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-02.png','DIBAO Pansy S4 - Xanh lam',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c02' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-03.png','DIBAO Pansy S4 - Xám',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c03' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-04.png','DIBAO Pansy S4 - Đen',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c04' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-05.png','DIBAO Pansy S4 - Xám nhũ',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c05' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-s4/source-06.png','DIBAO Pansy S4 - Xanh cốm',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-verified-c06' WHERE p.slug='kinetic-pansy-s4';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Pansy Sq2','kinetic-pansy-sq2','DIBAO Pansy Sq2 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',23300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-SQ2','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,13.0,50.0,90.0,1450,1450,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.750mm x 710mm x 1.110mm' FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-verified-c01','Xanh lam','Xanh lam','#274b92',23.0,0,10,1,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-verified-c02','Xanh ngọc','Xanh ngọc','#3e9b9d',23.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-verified-c03','Xám xanh','Xám xanh','#738c9e',23.0,0,10,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-verified-c04','Xanh nhạt','Xanh nhạt','#a3c9d6',23.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-verified-c05','Đen','Đen','#20282c',23.0,0,9,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-01.png','DIBAO Pansy Sq2 - Xanh lam',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c01' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-02.png','DIBAO Pansy Sq2 - Xanh ngọc',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c02' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-03.png','DIBAO Pansy Sq2 - Xám xanh',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c03' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-04.png','DIBAO Pansy Sq2 - Xanh nhạt',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c04' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-06.png','DIBAO Pansy Sq2 - Xanh nhạt',61,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c04' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-pansy-sq2/source-05.png','DIBAO Pansy Sq2 - Đen',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-verified-c05' WHERE p.slug='kinetic-pansy-sq2';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'DIBAO Rosa','kinetic-rosa','DIBAO Rosa là mẫu xe đạp điện có bàn đạp của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'ROSA','DIBAO','E_BIKE','LEAD_ACID',23.0,60,12.0,45.0,80.0,1400,1400,75.0,90.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.740mm x 720mm x 1.060mm' FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-verified-c01','Kem đỏ','Kem đỏ','#e6ddc7/#ac253c',23.0,0,10,1,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-verified-c02','Xanh xám','Xanh xám','#9caf9d/#586c66',23.0,0,10,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-verified-c03','Hồng tím','Hồng tím','#b9a1bc/#272b31',23.0,0,10,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-verified-c04','Trắng','Trắng','#dcd5d4',23.0,0,9,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-verified-c05','Xanh ô liu','Xanh ô liu','#a2af73/#e3ddbc',23.0,0,9,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-rosa/source-01.png','DIBAO Rosa - Kem đỏ',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-verified-c01' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-rosa/source-02.png','DIBAO Rosa - Xanh xám',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-verified-c02' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-rosa/source-03.jpg','DIBAO Rosa - Hồng tím',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-verified-c03' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-rosa/source-04.png','DIBAO Rosa - Trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-verified-c04' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-rosa/source-05.png','DIBAO Rosa - Xanh ô liu',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-verified-c05' WHERE p.slug='kinetic-rosa';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla Chic','kinetic-tesla-chic','DIBAO Tesla Chic là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',22600000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-CHIC','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,13.0,50.0,80.0,1500,1500,150.0,98.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.775mm x 715mm x 1.120mm' FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c01','Xanh ngọc','Xanh ngọc','#69a793',23.0,0,8,1,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c02','Đen','Đen','#1e2426',23.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c03','Xám xanh','Xám xanh','#70848d',23.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c04','Trắng','Trắng','#ececee',23.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c05','Bạc','Bạc','#adb8c1',23.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-verified-c06','Xanh nhạt','Xanh nhạt','#bdd3c6',23.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-01.png','DIBAO Tesla Chic - Xanh ngọc',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c01' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-02.png','DIBAO Tesla Chic - Đen',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c02' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-03.png','DIBAO Tesla Chic - Xám xanh',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c03' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-04.png','DIBAO Tesla Chic - Trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c04' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-05.png','DIBAO Tesla Chic - Bạc',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c05' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic/source-06.png','DIBAO Tesla Chic - Xanh nhạt',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-verified-c06' WHERE p.slug='kinetic-tesla-chic';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla Chic Q','kinetic-tesla-chic-q','DIBAO Tesla Chic Q là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',21500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-CHIC-Q','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,13.0,50.0,80.0,1500,1500,150.0,102.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.780mm x 705mm x 1.110mm' FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c01','Xanh nhạt','Xanh nhạt','#b5d9ec',22.0,0,8,1,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c02','Trắng','Trắng','#e6ece3',22.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c03','Xanh ngọc','Xanh ngọc','#78c9c8',22.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c04','Xanh cốm','Xanh cốm','#bccf9f',22.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c05','Vàng','Vàng','#e5c527',22.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-verified-c06','Cam','Cam','#e4711d',22.0,0,8,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-01.png','DIBAO Tesla Chic Q - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c01' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-02.png','DIBAO Tesla Chic Q - Trắng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c02' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-03.png','DIBAO Tesla Chic Q - Xanh ngọc',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c03' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-04.png','DIBAO Tesla Chic Q - Xanh cốm',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c04' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-05.png','DIBAO Tesla Chic Q - Vàng',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c05' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-chic-q/source-06.png','DIBAO Tesla Chic Q - Cam',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-verified-c06' WHERE p.slug='kinetic-tesla-chic-q';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla Dio','kinetic-tesla-dio','DIBAO Tesla Dio là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-DIO','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,50.0,60.0,1450,1450,130.0,97.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.735mm x 720mm x 1.120mm' FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-verified-c01','Kem','Kem','#e9c99b',22.0,0,10,1,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-verified-c02','Vàng','Vàng','#e9cc17',22.0,0,10,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-verified-c03','Xanh lục','Xanh lục','#367964',22.0,0,10,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-verified-c04','Xám xanh','Xám xanh','#596d70',22.0,0,9,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-verified-c05','Đỏ','Đỏ','#a92335',22.0,0,9,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio/source-01.jpg','DIBAO Tesla Dio - Kem',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-verified-c01' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio/source-02.jpg','DIBAO Tesla Dio - Vàng',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-verified-c02' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio/source-03.jpg','DIBAO Tesla Dio - Xanh lục',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-verified-c03' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio/source-04.jpg','DIBAO Tesla Dio - Xám xanh',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-verified-c04' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio/source-05.jpg','DIBAO Tesla Dio - Đỏ',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-verified-c05' WHERE p.slug='kinetic-tesla-dio';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla Dio E','kinetic-tesla-dio-e','DIBAO Tesla Dio E là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',25900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-DIO-E','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,55.0,60.0,1350,1350,130.0,NULL,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,36,'1.750mm x 685mm x 1.120mm' FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-verified-c01','Đen bóng','Đen bóng','#23313a',22.0,0,10,1,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-verified-c02','Đen sần','Đen sần','#343f44',22.0,0,10,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-verified-c03','Trắng','Trắng','#dce6d5',22.0,0,10,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-verified-c04','Ghi bóng','Ghi bóng','#899faa',22.0,0,9,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-verified-c05','Xám xanh','Xám xanh','#667c7f',22.0,0,9,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-01.png','DIBAO Tesla Dio E - Đen bóng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c01' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-02.png','DIBAO Tesla Dio E - Đen sần',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c02' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-03.png','DIBAO Tesla Dio E - Đen sần',21,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c02' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-04.png','DIBAO Tesla Dio E - Trắng',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c03' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-05.png','DIBAO Tesla Dio E - Ghi bóng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c04' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-dio-e/source-06.png','DIBAO Tesla Dio E - Xám xanh',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-verified-c05' WHERE p.slug='kinetic-tesla-dio-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla E','kinetic-tesla-e','DIBAO Tesla E là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-E','DIBAO','E_SCOOTER','LEAD_ACID',22.3,12,12.0,45.0,70.0,1550,1550,75.0,94.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1740 × 700 × 1110 mm' FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-verified-c01','Đen','Đen','#303d3d',22.3,0,10,1,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-verified-c02','Xám nâu','Xám nâu','#716c5b',22.3,0,10,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-verified-c03','Đỏ','Đỏ','#bf1e38',22.3,0,10,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-verified-c04','Xám nhạt','Xám nhạt','#b3bdb0',22.3,0,9,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-verified-c05','Trắng kem','Trắng kem','#e1dfcb',22.3,0,9,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-01.png','DIBAO Tesla E - Đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c01' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-04.png','DIBAO Tesla E - Đen',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c01' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-02.png','DIBAO Tesla E - Xám nâu',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c02' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-03.png','DIBAO Tesla E - Đỏ',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c03' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-05.png','DIBAO Tesla E - Xám nhạt',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c04' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-e/source-06.png','DIBAO Tesla E - Trắng kem',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-verified-c05' WHERE p.slug='kinetic-tesla-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Tesla Sd','kinetic-tesla-sd','DIBAO Tesla Sd là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-SD','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,12.0,50.0,100.0,1450,1450,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.750mm x 700mm x 1.100mm' FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-verified-c01','Xanh đậm','Xanh đậm','#333d61',23.0,0,12,1,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-verified-c02','Xám xanh','Xám xanh','#657980',23.0,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-verified-c03','Đen','Đen','#202827',23.0,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-verified-c04','Trắng','Trắng','#e3edf1',23.0,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-01.png','DIBAO Tesla Sd - Xanh đậm',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c01' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-06.png','DIBAO Tesla Sd - Xanh đậm',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c01' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-02.png','DIBAO Tesla Sd - Xám xanh',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c02' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-03.png','DIBAO Tesla Sd - Đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c03' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-04.png','DIBAO Tesla Sd - Đen',41,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c03' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-tesla-sd/source-05.png','DIBAO Tesla Sd - Trắng',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-verified-c04' WHERE p.slug='kinetic-tesla-sd';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Xman Leo','kinetic-xman-leo','DIBAO Xman Leo là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'XMAN-LEO','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,12.0,55.0,90.0,1450,1450,150.0,95.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.770mm x 810mm x 1.080mm' FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-verified-c01','Xám xanh','Xám xanh','#86a5b5/#272c31',23.0,0,12,1,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-verified-c02','Xanh đậm','Xanh đậm','#3c5478/#272c31',23.0,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-verified-c03','Đỏ đen','Đỏ đen','#b8294a/#272c31',23.0,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-verified-c04','Xanh ngọc đen','Xanh ngọc đen','#8fc5c8/#272c31',23.0,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-01.png','DIBAO Xman Leo - Xám xanh',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c01' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-02.png','DIBAO Xman Leo - Xám xanh',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c01' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-03.png','DIBAO Xman Leo - Xanh đậm',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c02' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-04.png','DIBAO Xman Leo - Xanh đậm',21,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c02' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-05.png','DIBAO Xman Leo - Đỏ đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c03' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-xman-leo/source-06.png','DIBAO Xman Leo - Xanh ngọc đen',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-verified-c04' WHERE p.slug='kinetic-xman-leo';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Butterfly','kinetic-butterfly','DIBAO Butterfly là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',23300000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'BUTTERFLY','DIBAO','E_SCOOTER','LEAD_ACID',21.6,60,12.0,50.0,80.0,1500,1500,150.0,92.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.800mm x 700mm x 1.060mm' FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-verified-c01','Trắng','Trắng','#ede9e8',21.6,0,16,1,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-verified-c02','Xanh lam','Xanh lam','#4ca9d2',21.6,0,16,0,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-verified-c03','Hồng nhạt','Hồng nhạt','#e3c6ce',21.6,0,16,0,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-butterfly/source-01.png','DIBAO Butterfly - Trắng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-verified-c01' WHERE p.slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-butterfly/source-02.jpg','DIBAO Butterfly - Xanh lam',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-verified-c02' WHERE p.slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-butterfly/source-03.png','DIBAO Butterfly - Hồng nhạt',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-verified-c03' WHERE p.slug='kinetic-butterfly';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo S4','kinetic-gogo-s4','DIBAO Gogo S4 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',22600000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-S4','DIBAO','E_SCOOTER','LEAD_ACID',22.0,60,12.0,50.0,90.0,1450,1450,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.700mm x 730mm x 1.050mm' FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-verified-c01','Vàng','Vàng','#e4cd2b',22.0,0,12,1,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-verified-c02','Đỏ','Đỏ','#c33142',22.0,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-verified-c03','Cam','Cam','#dc7b26',22.0,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-verified-c04','Đen','Đen','#293834',22.0,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-01.webp','DIBAO Gogo S4 - Vàng',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c01' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-04.png','DIBAO Gogo S4 - Vàng',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c01' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-02.png','DIBAO Gogo S4 - Đỏ',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c02' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-03.png','DIBAO Gogo S4 - Cam',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c03' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-05.png','DIBAO Gogo S4 - Đen',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c04' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s4/source-06.png','DIBAO Gogo S4 - Đen',61,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-verified-c04' WHERE p.slug='kinetic-gogo-s4';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Gogo S5','kinetic-gogo-s5','DIBAO Gogo S5 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-S5','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,14.0,50.0,70.0,1500,1500,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.730mm x 730mm x 1.030mm' FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c01','Xanh nhạt','Xanh nhạt','#a5c8c5',23.0,0,8,1,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c02','Xanh xám','Xanh xám','#728a76',23.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c03','Đen','Đen','#2c3832',23.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c04','Cam','Cam','#d18034',23.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c05','Xanh lam','Xanh lam','#589bc0',23.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-verified-c06','Xám xanh','Xám xanh','#a8c0cb',23.0,0,8,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-01.jpg','DIBAO Gogo S5 - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c01' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-02.jpg','DIBAO Gogo S5 - Xanh xám',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c02' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-03.jpg','DIBAO Gogo S5 - Đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c03' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-04.jpg','DIBAO Gogo S5 - Cam',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c04' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-05.png','DIBAO Gogo S5 - Xanh lam',80,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c05' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-gogo-s5/source-06.png','DIBAO Gogo S5 - Xám xanh',100,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-verified-c06' WHERE p.slug='kinetic-gogo-s5';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO Jeek One','kinetic-jeek-one','DIBAO Jeek One là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',21100000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'JEEK-ONE','DIBAO','E_SCOOTER','LEAD_ACID',21.6,60,12.0,50.0,90.0,1500,1500,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.760mm x 770mm x 1.100mm' FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-verified-c01','Xám đen','Xám đen','#667f81/#232b31',21.6,0,12,1,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-verified-c02','Đen','Đen','#273332',21.6,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-verified-c03','Cam đen','Cam đen','#db761d/#232b31',21.6,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-verified-c04','Xanh đen','Xanh đen','#394c8a/#232b31',21.6,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-01.png','DIBAO Jeek One - Xám đen',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c01' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-05.png','DIBAO Jeek One - Xám đen',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c01' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-02.png','DIBAO Jeek One - Đen',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c02' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-04.png','DIBAO Jeek One - Đen',21,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c02' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-03.png','DIBAO Jeek One - Cam đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c03' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-jeek-one/source-06.png','DIBAO Jeek One - Xanh đen',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-verified-c04' WHERE p.slug='kinetic-jeek-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'DIBAO R1','kinetic-r1','DIBAO R1 là mẫu xe sử dụng động cơ điện của DIBAO, phù hợp cho di chuyển hằng ngày. Chọn màu để xem đúng ảnh và tình trạng của phiên bản.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'R1','DIBAO','E_SCOOTER','LEAD_ACID',23.0,60,12.0,50.0,90.0,1500,1500,150.0,93.0,NULL,'DISC',NULL,NULL,NULL,NULL,NULL,NULL,24,'1.835mm x 720mm x 1.090mm' FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-verified-c01','Xanh nhạt','Xanh nhạt','#a6d2d1',23.0,0,12,1,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-verified-c02','Xám xanh','Xám xanh','#7e969e',23.0,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-verified-c03','Đen','Đen','#252e3a',23.0,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-verified-c04','Xanh đậm','Xanh đậm','#485f7e',23.0,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-01.png','DIBAO R1 - Xanh nhạt',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c01' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-02.png','DIBAO R1 - Xám xanh',20,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c02' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-06.png','DIBAO R1 - Xám xanh',21,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c02' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-03.png','DIBAO R1 - Đen',40,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c03' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-04.png','DIBAO R1 - Xanh đậm',60,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c04' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/catalog/kinetic-r1/source-05.png','DIBAO R1 - Xanh đậm',61,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-verified-c04' WHERE p.slug='kinetic-r1';
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 1 - Nguyen Thai Hoc','Quan 1','187 Nguyen Thai Hoc, Phuong Pham Ngu Lao, Quan 1, TP. Ho Chi Minh','028 3920 1234','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 3 - Cach Mang Thang 8','Quan 3','420 Cach Mang Thang 8, Phuong 11, Quan 3, TP. Ho Chi Minh','028 3931 5678','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 7 - Nguyen Thi Thap','Quan 7','1021 Nguyen Thi Thap, Phuong Tan Phu, Quan 7, TP. Ho Chi Minh','028 3776 8899','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Go Vap - Quang Trung','Go Vap','588 Quang Trung, Phuong 11, Go Vap, TP. Ho Chi Minh','028 3589 4567','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Thu Duc - Vo Van Ngan','Thu Duc','216 Vo Van Ngan, Phuong Binh Tho, Thu Duc, TP. Ho Chi Minh','028 3722 7788','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Tan Binh - Cong Hoa','Tan Binh','45 Cong Hoa, Phuong 4, Tan Binh, TP. Ho Chi Minh','028 3811 2233','08:00 - 21:00',1);

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('REGISTRATION_SERVICE','Phí dịch vụ làm hồ sơ đăng ký xe','Phí cửa hàng thu khi làm hộ thủ tục đăng ký xe. Không gồm lệ phí nộp Nhà nước.',500000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_SAME_CITY','Phí giao xe tận nhà cùng tỉnh','Giao xe đến địa chỉ trong tỉnh, thành phố có showroom.',200000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_OTHER_PROVINCE','Phí giao xe tận nhà khác tỉnh','Giao xe đến địa chỉ ở tỉnh, thành phố không có showroom.',800000,'VND/xe','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_CENTRAL','Tỷ lệ lệ phí trước bạ (thành phố lớn)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',5,'%','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_OTHER','Tỷ lệ lệ phí trước bạ (khu vực khác)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',2,'%','ACTIVE',NOW());
INSERT INTO promotions(code,campaign_name,discount_type,discount_value,max_discount_amount,usage_count,usage_limit,status,starts_at,ends_at) VALUES
('KINETIC20','Chào hè xanh','PERCENTAGE',20,25000,326,800,'LIVE','2026-06-01 00:00:00','2026-06-30 23:59:59'),
('STUDENT15','Ưu đãi sinh viên','FIXED_AMOUNT',15000,NULL,1020,2000,'LIVE','2026-05-15 00:00:00','2026-07-15 23:59:59'),
('WEEKEND','Ưu đãi cuối tuần','PERCENTAGE',10,NULL,0,500,'WAITING','2026-06-08 00:00:00','2026-06-30 23:59:59'),
('EBIKE100','Giảm 100 nghìn','FIXED_AMOUNT',100000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59'),
('EBIKE200','Giảm 200 nghìn','FIXED_AMOUNT',200000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59');
INSERT INTO admin_audit_logs(actor,action,target,ip_address) VALUES ('System','Khởi tạo dữ liệu mẫu','pricing rules và promotions','127.0.0.1');

SET FOREIGN_KEY_CHECKS=1;
