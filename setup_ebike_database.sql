-- KINETIC E-Bike Store: MariaDB single-database setup
-- Source: backend/src/main/resources/db/migration and data-backup/dataCawl/data
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
('Xe điện','xe-dien','Dòng xe điện đô thị với thiết kế gọn, vận hành êm và phù hợp di chuyển hằng ngày.',1),
('Xe đạp điện','xe-dap-dien','Dòng xe đạp điện trợ lực, có bàn đạp, linh hoạt cho học sinh sinh viên và di chuyển trong phố.',1);

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Sir 50CC','kinetic-sir-50cc','KINETIC Sir 50CC là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 800 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',17900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'SIR-50CC','KINETIC','E_SCOOTER','LEAD_ACID',24,60,7.0,50,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-sir-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-sir-50cc-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-sir-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-sir-50cc-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-sir-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-sir-50cc-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-sir-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-sir-50cc-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-sir-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-dibao-sir-50cc-1.jpg','KINETIC Sir 50CC - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-sir-50cc-pearl-white' WHERE p.slug='kinetic-sir-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-dibao-sir-50cc-2.jpg','KINETIC Sir 50CC - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-sir-50cc-matte-black' WHERE p.slug='kinetic-sir-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-dibao-sir-50cc-3.jpg','KINETIC Sir 50CC - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-sir-50cc-ocean-blue' WHERE p.slug='kinetic-sir-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-dibao-sir-50cc-4.jpg','KINETIC Sir 50CC - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-sir-50cc-cherry-red' WHERE p.slug='kinetic-sir-50cc';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Creer','kinetic-creer','KINETIC Creer là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 800 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',19400000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'CREER','KINETIC','E_SCOOTER','LEAD_ACID',30,60,7.0,50,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-creer';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-creer';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-creer';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-creer';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-creer';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-creer-1.jpg','KINETIC Creer - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-pearl-white' WHERE p.slug='kinetic-creer';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-creer-2.jpg','KINETIC Creer - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-matte-black' WHERE p.slug='kinetic-creer';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-creer-3.jpg','KINETIC Creer - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-ocean-blue' WHERE p.slug='kinetic-creer';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-creer-4.jpg','KINETIC Creer - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-cherry-red' WHERE p.slug='kinetic-creer';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gofast','kinetic-gofast','KINETIC Gofast là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',17900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOFAST','KINETIC','E_SCOOTER','LEAD_ACID',28,60,7.0,45,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gofast';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gofast-pearl-white','Pearl White','Pearl White','#F5F5F4',28,0,12,1,1 FROM products WHERE slug='kinetic-gofast';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gofast-matte-black','Matte Black','Matte Black','#18181B',28,0,12,0,1 FROM products WHERE slug='kinetic-gofast';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gofast-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',28,0,12,0,1 FROM products WHERE slug='kinetic-gofast';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gofast-cherry-red','Cherry Red','Cherry Red','#DC2626',28,0,12,0,1 FROM products WHERE slug='kinetic-gofast';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-gofast-1.jpg','KINETIC Gofast - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gofast-pearl-white' WHERE p.slug='kinetic-gofast';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-gofast-2.jpg','KINETIC Gofast - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gofast-matte-black' WHERE p.slug='kinetic-gofast';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-gofast-3.jpg','KINETIC Gofast - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gofast-ocean-blue' WHERE p.slug='kinetic-gofast';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-gofast-4.jpg','KINETIC Gofast - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gofast-cherry-red' WHERE p.slug='kinetic-gofast';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Xs1 Bản Nâng Cấp','kinetic-pansy-xs1-ban-nang-cap','KINETIC Pansy Xs1 Bản Nâng Cấp là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 1400 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 110 km sau mỗi lần sạc.',20300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-XS1-BAN-NANG-CAP','KINETIC','E_SCOOTER','LEAD_ACID',32,60,7.0,55,110,1400,1890,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-ban-nang-cap-pearl-white','Pearl White','Pearl White','#F5F5F4',32,0,12,1,1 FROM products WHERE slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-ban-nang-cap-matte-black','Matte Black','Matte Black','#18181B',32,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-ban-nang-cap-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',32,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-ban-nang-cap-cherry-red','Cherry Red','Cherry Red','#DC2626',32,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-1.jpg','KINETIC Pansy Xs1 Bản Nâng Cấp - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-ban-nang-cap-pearl-white' WHERE p.slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-2.jpg','KINETIC Pansy Xs1 Bản Nâng Cấp - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-ban-nang-cap-matte-black' WHERE p.slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-3.jpg','KINETIC Pansy Xs1 Bản Nâng Cấp - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-ban-nang-cap-ocean-blue' WHERE p.slug='kinetic-pansy-xs1-ban-nang-cap';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-4.jpg','KINETIC Pansy Xs1 Bản Nâng Cấp - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-ban-nang-cap-cherry-red' WHERE p.slug='kinetic-pansy-xs1-ban-nang-cap';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Xs','kinetic-pansy-xs','KINETIC Pansy Xs là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1400 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',23300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-XS','KINETIC','E_SCOOTER','LITHIUM_ION',30,60,7.0,50,80,1400,1890,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-xs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-pansy-xs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs-1.jpg','KINETIC Pansy Xs - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs-pearl-white' WHERE p.slug='kinetic-pansy-xs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs-2.jpg','KINETIC Pansy Xs - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs-matte-black' WHERE p.slug='kinetic-pansy-xs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs-3.jpg','KINETIC Pansy Xs - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs-ocean-blue' WHERE p.slug='kinetic-pansy-xs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs-4.jpg','KINETIC Pansy Xs - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs-cherry-red' WHERE p.slug='kinetic-pansy-xs';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Xs1','kinetic-pansy-xs1','KINETIC Pansy Xs1 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',22200000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-XS1','KINETIC','E_SCOOTER','LITHIUM_ION',30,60,7.0,50,80,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-xs1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-pansy-xs1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-xs1-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-xs1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs1-1.jpg','KINETIC Pansy Xs1 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-pearl-white' WHERE p.slug='kinetic-pansy-xs1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs1-2.jpg','KINETIC Pansy Xs1 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-matte-black' WHERE p.slug='kinetic-pansy-xs1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs1-3.jpg','KINETIC Pansy Xs1 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-ocean-blue' WHERE p.slug='kinetic-pansy-xs1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-tay-ga-dibao-pansy-xs1-4.jpg','KINETIC Pansy Xs1 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-xs1-cherry-red' WHERE p.slug='kinetic-pansy-xs1';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'KINETIC Amy','kinetic-amy','KINETIC Amy là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 650 W, tốc độ tối đa 35 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',17700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'AMY','KINETIC','E_BIKE','LITHIUM_ION',24,48,5.5,35,100,650,877,150,68,14,'DISC','CHAIN','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-amy-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-amy-1.jpg','KINETIC Amy - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-pearl-white' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-amy-2.jpg','KINETIC Amy - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-matte-black' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-amy-3.jpg','KINETIC Amy - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-ocean-blue' WHERE p.slug='kinetic-amy';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-amy-4.jpg','KINETIC Amy - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-amy-cherry-red' WHERE p.slug='kinetic-amy';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'KINETIC Ella','kinetic-ella','KINETIC Ella là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 450 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',19500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'ELLA','KINETIC','E_BIKE','LITHIUM_ION',28,48,5.5,45,80,450,607,150,68,14,'DISC','CHAIN','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-pearl-white','Pearl White','Pearl White','#F5F5F4',28,0,12,1,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-matte-black','Matte Black','Matte Black','#18181B',28,0,12,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',28,0,12,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ella-cherry-red','Cherry Red','Cherry Red','#DC2626',28,0,12,0,1 FROM products WHERE slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ella-1.jpg','KINETIC Ella - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-pearl-white' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ella-2.jpg','KINETIC Ella - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-matte-black' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ella-3.jpg','KINETIC Ella - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-ocean-blue' WHERE p.slug='kinetic-ella';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ella-4.jpg','KINETIC Ella - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ella-cherry-red' WHERE p.slug='kinetic-ella';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'KINETIC Queen','kinetic-queen','KINETIC Queen là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 650 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',16800000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'QUEEN','KINETIC','E_BIKE','LITHIUM_ION',22,48,5.5,45,100,650,877,150,68,14,'DISC','CHAIN','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-queen-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-queen-1.jpg','KINETIC Queen - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-pearl-white' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-queen-2.jpg','KINETIC Queen - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-matte-black' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-queen-3.jpg','KINETIC Queen - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-ocean-blue' WHERE p.slug='kinetic-queen';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-queen-4.jpg','KINETIC Queen - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-queen-cherry-red' WHERE p.slug='kinetic-queen';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'KINETIC Ryan','kinetic-ryan','KINETIC Ryan là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 650 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',17700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'RYAN','KINETIC','E_BIKE','LITHIUM_ION',24,48,5.5,45,100,650,877,150,68,14,'DISC','CHAIN','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ryan-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ryan-1.jpg','KINETIC Ryan - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-pearl-white' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ryan-2.jpg','KINETIC Ryan - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-matte-black' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ryan-3.jpg','KINETIC Ryan - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-ocean-blue' WHERE p.slug='kinetic-ryan';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-ryan-4.jpg','KINETIC Ryan - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ryan-cherry-red' WHERE p.slug='kinetic-ryan';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dap-dien'),'KINETIC S One','kinetic-s-one','KINETIC S One là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 650 W, tốc độ tối đa 35 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',15900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'S-ONE','KINETIC','E_BIKE','LITHIUM_ION',20,48,5.5,35,100,650,877,150,68,14,'DISC','CHAIN','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-pearl-white','Pearl White','Pearl White','#F5F5F4',20,0,12,1,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-matte-black','Matte Black','Matte Black','#18181B',20,0,12,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',20,0,12,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-s-one-cherry-red','Cherry Red','Cherry Red','#DC2626',20,0,12,0,1 FROM products WHERE slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-s-one-1.jpg','KINETIC S One - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-pearl-white' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-s-one-2.jpg','KINETIC S One - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-matte-black' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-s-one-3.jpg','KINETIC S One - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-ocean-blue' WHERE p.slug='kinetic-s-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-có-bàn-đạp-dibao-s-one-4.jpg','KINETIC S One - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-s-one-cherry-red' WHERE p.slug='kinetic-s-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Creer E','kinetic-creer-e','KINETIC Creer E là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',21500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'CREER-E','KINETIC','E_SCOOTER','LITHIUM_ION',28,60,7.0,45,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-pearl-white','Pearl White','Pearl White','#F5F5F4',28,0,12,1,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-matte-black','Matte Black','Matte Black','#18181B',28,0,12,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',28,0,12,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-e-cherry-red','Cherry Red','Cherry Red','#DC2626',28,0,12,0,1 FROM products WHERE slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-e-1.jpg','KINETIC Creer E - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-pearl-white' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-e-2.jpg','KINETIC Creer E - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-matte-black' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-e-3.jpg','KINETIC Creer E - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-ocean-blue' WHERE p.slug='kinetic-creer-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-e-4.jpg','KINETIC Creer E - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-e-cherry-red' WHERE p.slug='kinetic-creer-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Creer Nile','kinetic-creer-nile','KINETIC Creer Nile là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'CREER-NILE','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,80,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-creer-nile-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-nile-1.jpg','KINETIC Creer Nile - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-pearl-white' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-nile-2.jpg','KINETIC Creer Nile - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-matte-black' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-nile-3.jpg','KINETIC Creer Nile - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-ocean-blue' WHERE p.slug='kinetic-creer-nile';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-creer-nile-4.jpg','KINETIC Creer Nile - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-creer-nile-cherry-red' WHERE p.slug='kinetic-creer-nile';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo Cross','kinetic-gogo-cross','KINETIC Gogo Cross là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',23700000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROSS','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,100,1600,2160,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-1.jpg','KINETIC Gogo Cross - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-pearl-white' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-2.jpg','KINETIC Gogo Cross - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-matte-black' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-3.jpg','KINETIC Gogo Cross - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-ocean-blue' WHERE p.slug='kinetic-gogo-cross';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-4.jpg','KINETIC Gogo Cross - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-cherry-red' WHERE p.slug='kinetic-gogo-cross';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo Cross G','kinetic-gogo-cross-g','KINETIC Gogo Cross G là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',22600000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROSS-G','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,100,1600,2160,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-cross-g-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-g-1.jpg','KINETIC Gogo Cross G - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-pearl-white' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-g-2.jpg','KINETIC Gogo Cross G - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-matte-black' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-g-3.jpg','KINETIC Gogo Cross G - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-ocean-blue' WHERE p.slug='kinetic-gogo-cross-g';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-cross-g-4.jpg','KINETIC Gogo Cross G - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-cross-g-cherry-red' WHERE p.slug='kinetic-gogo-cross-g';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo Crown','kinetic-gogo-crown','KINETIC Gogo Crown là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1000 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-CROWN','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,100,1000,1350,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-crown-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-crown-1.jpg','KINETIC Gogo Crown - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-pearl-white' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-crown-2.jpg','KINETIC Gogo Crown - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-matte-black' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-crown-3.jpg','KINETIC Gogo Crown - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-ocean-blue' WHERE p.slug='kinetic-gogo-crown';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-crown-4.jpg','KINETIC Gogo Crown - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-crown-cherry-red' WHERE p.slug='kinetic-gogo-crown';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo Moon','kinetic-gogo-moon','KINETIC Gogo Moon là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',21500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-MOON','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-moon-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-moon-1.jpg','KINETIC Gogo Moon - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-pearl-white' WHERE p.slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-moon-2.jpg','KINETIC Gogo Moon - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-matte-black' WHERE p.slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-moon-3.jpg','KINETIC Gogo Moon - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-ocean-blue' WHERE p.slug='kinetic-gogo-moon';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-gogo-moon-4.jpg','KINETIC Gogo Moon - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-moon-cherry-red' WHERE p.slug='kinetic-gogo-moon';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Keva','kinetic-keva','KINETIC Keva là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'KEVA','KINETIC','E_SCOOTER','LITHIUM_ION',24,60,7.0,50,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-keva-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-keva-1.jpg','KINETIC Keva - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-pearl-white' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-keva-2.jpg','KINETIC Keva - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-matte-black' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-keva-3.jpg','KINETIC Keva - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-ocean-blue' WHERE p.slug='kinetic-keva';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-keva-4.jpg','KINETIC Keva - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-keva-cherry-red' WHERE p.slug='kinetic-keva';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Ls007','kinetic-ls007','KINETIC Ls007 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',23300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'LS007','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,80,1600,2160,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-ls007-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-ls007-1.jpg','KINETIC Ls007 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-pearl-white' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-ls007-2.jpg','KINETIC Ls007 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-matte-black' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-ls007-3.jpg','KINETIC Ls007 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-ocean-blue' WHERE p.slug='kinetic-ls007';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-ls007-4.jpg','KINETIC Ls007 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-ls007-cherry-red' WHERE p.slug='kinetic-ls007';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC M One','kinetic-m-one','KINETIC M One là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',25500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'M-ONE','KINETIC','E_SCOOTER','LITHIUM_ION',24,60,7.0,50,90,1600,2160,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-m-one-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-m-one-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-m-one-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-m-one-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-m-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-m-one-1.jpg','KINETIC M One - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-m-one-pearl-white' WHERE p.slug='kinetic-m-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-m-one-2.jpg','KINETIC M One - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-m-one-matte-black' WHERE p.slug='kinetic-m-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-m-one-3.jpg','KINETIC M One - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-m-one-ocean-blue' WHERE p.slug='kinetic-m-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-m-one-4.jpg','KINETIC M One - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-m-one-cherry-red' WHERE p.slug='kinetic-m-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Mxs','kinetic-mxs','KINETIC Mxs là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',20000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'MXS','KINETIC','E_SCOOTER','LITHIUM_ION',30,60,7.0,50,100,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-mxs-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-mxs-1.jpg','KINETIC Mxs - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-pearl-white' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-mxs-2.jpg','KINETIC Mxs - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-matte-black' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-mxs-3.jpg','KINETIC Mxs - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-ocean-blue' WHERE p.slug='kinetic-mxs';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-mxs-4.jpg','KINETIC Mxs - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-mxs-cherry-red' WHERE p.slug='kinetic-mxs';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Dio','kinetic-pansy-dio','KINETIC Pansy Dio là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-DIO','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,80,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-1.jpg','KINETIC Pansy Dio - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-pearl-white' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-2.jpg','KINETIC Pansy Dio - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-matte-black' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-3.jpg','KINETIC Pansy Dio - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-ocean-blue' WHERE p.slug='kinetic-pansy-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-4.jpg','KINETIC Pansy Dio - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-cherry-red' WHERE p.slug='kinetic-pansy-dio';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Dio 2025','kinetic-pansy-dio-2025','KINETIC Pansy Dio 2025 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',22200000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-DIO-2025','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,80,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-dio-2025-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-2025-1.jpg','KINETIC Pansy Dio 2025 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-pearl-white' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-2025-2.jpg','KINETIC Pansy Dio 2025 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-matte-black' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-2025-3.jpg','KINETIC Pansy Dio 2025 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-ocean-blue' WHERE p.slug='kinetic-pansy-dio-2025';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-dio-2025-4.jpg','KINETIC Pansy Dio 2025 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-dio-2025-cherry-red' WHERE p.slug='kinetic-pansy-dio-2025';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy S2 Càng Nhôm','kinetic-pansy-s2-cang-nhom','KINETIC Pansy S2 Càng Nhôm là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',24400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S2-CANG-NHOM','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,80,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s2-cang-nhom-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s2-cang-nhom-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s2-cang-nhom-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s2-cang-nhom-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s2-càng-nhôm-1.jpg','KINETIC Pansy S2 Càng Nhôm - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s2-cang-nhom-pearl-white' WHERE p.slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s2-càng-nhôm-2.jpg','KINETIC Pansy S2 Càng Nhôm - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s2-cang-nhom-matte-black' WHERE p.slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s2-càng-nhôm-3.jpg','KINETIC Pansy S2 Càng Nhôm - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s2-cang-nhom-ocean-blue' WHERE p.slug='kinetic-pansy-s2-cang-nhom';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s2-càng-nhôm-4.jpg','KINETIC Pansy S2 Càng Nhôm - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s2-cang-nhom-cherry-red' WHERE p.slug='kinetic-pansy-s2-cang-nhom';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy S3','kinetic-pansy-s3','KINETIC Pansy S3 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S3','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,100,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s3-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s3-1.jpg','KINETIC Pansy S3 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-pearl-white' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s3-2.jpg','KINETIC Pansy S3 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-matte-black' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s3-3.jpg','KINETIC Pansy S3 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-ocean-blue' WHERE p.slug='kinetic-pansy-s3';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s3-4.jpg','KINETIC Pansy S3 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s3-cherry-red' WHERE p.slug='kinetic-pansy-s3';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy S4','kinetic-pansy-s4','KINETIC Pansy S4 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1000 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-S4','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,100,1000,1350,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-s4-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s4-1.jpg','KINETIC Pansy S4 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-pearl-white' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s4-2.jpg','KINETIC Pansy S4 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-matte-black' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s4-3.jpg','KINETIC Pansy S4 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-ocean-blue' WHERE p.slug='kinetic-pansy-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-s4-4.jpg','KINETIC Pansy S4 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-s4-cherry-red' WHERE p.slug='kinetic-pansy-s4';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Pansy Sq2','kinetic-pansy-sq2','KINETIC Pansy Sq2 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1400 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',23300000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'PANSY-SQ2','KINETIC','E_SCOOTER','LITHIUM_ION',30,60,7.0,50,100,1400,1890,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-pansy-sq2-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-sq2-1.jpg','KINETIC Pansy Sq2 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-pearl-white' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-sq2-2.jpg','KINETIC Pansy Sq2 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-matte-black' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-sq2-3.jpg','KINETIC Pansy Sq2 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-ocean-blue' WHERE p.slug='kinetic-pansy-sq2';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-pansy-sq2-4.jpg','KINETIC Pansy Sq2 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-pansy-sq2-cherry-red' WHERE p.slug='kinetic-pansy-sq2';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Rosa','kinetic-rosa','KINETIC Rosa là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',25500000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'ROSA','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rosa-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rosa-1.jpg','KINETIC Rosa - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-pearl-white' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rosa-2.jpg','KINETIC Rosa - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-matte-black' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rosa-3.jpg','KINETIC Rosa - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-ocean-blue' WHERE p.slug='kinetic-rosa';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rosa-4.jpg','KINETIC Rosa - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rosa-cherry-red' WHERE p.slug='kinetic-rosa';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Rs 50CC','kinetic-rs-50cc','KINETIC Rs 50CC là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 800 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',17900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'RS-50CC','KINETIC','E_SCOOTER','LEAD_ACID',30,60,7.0,50,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-rs-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rs-50cc-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-rs-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rs-50cc-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-rs-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rs-50cc-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-rs-50cc';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-rs-50cc-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-rs-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rs-50cc-1.jpg','KINETIC Rs 50CC - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rs-50cc-pearl-white' WHERE p.slug='kinetic-rs-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rs-50cc-2.jpg','KINETIC Rs 50CC - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rs-50cc-matte-black' WHERE p.slug='kinetic-rs-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rs-50cc-3.jpg','KINETIC Rs 50CC - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rs-50cc-ocean-blue' WHERE p.slug='kinetic-rs-50cc';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-rs-50cc-4.jpg','KINETIC Rs 50CC - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-rs-50cc-cherry-red' WHERE p.slug='kinetic-rs-50cc';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla Chic','kinetic-tesla-chic','KINETIC Tesla Chic là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1400 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',22600000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-CHIC','KINETIC','E_SCOOTER','LITHIUM_ION',24,60,7.0,50,90,1400,1890,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-1.jpg','KINETIC Tesla Chic - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-pearl-white' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-2.jpg','KINETIC Tesla Chic - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-matte-black' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-3.jpg','KINETIC Tesla Chic - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-ocean-blue' WHERE p.slug='kinetic-tesla-chic';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-4.jpg','KINETIC Tesla Chic - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-cherry-red' WHERE p.slug='kinetic-tesla-chic';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla Chic Q','kinetic-tesla-chic-q','KINETIC Tesla Chic Q là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1200 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',21500000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-CHIC-Q','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,100,1200,1620,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-chic-q-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-q-1.jpg','KINETIC Tesla Chic Q - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-pearl-white' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-q-2.jpg','KINETIC Tesla Chic Q - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-matte-black' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-q-3.jpg','KINETIC Tesla Chic Q - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-ocean-blue' WHERE p.slug='kinetic-tesla-chic-q';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-chic-q-4.jpg','KINETIC Tesla Chic Q - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-chic-q-cherry-red' WHERE p.slug='kinetic-tesla-chic-q';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla Dio','kinetic-tesla-dio','KINETIC Tesla Dio là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',25900000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-DIO','KINETIC','E_SCOOTER','LITHIUM_ION',22,60,7.0,45,100,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-pearl-white','Pearl White','Pearl White','#F5F5F4',22,0,12,1,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-matte-black','Matte Black','Matte Black','#18181B',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-cherry-red','Cherry Red','Cherry Red','#DC2626',22,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-1.jpg','KINETIC Tesla Dio - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-pearl-white' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-2.jpg','KINETIC Tesla Dio - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-matte-black' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-3.jpg','KINETIC Tesla Dio - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-ocean-blue' WHERE p.slug='kinetic-tesla-dio';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-4.jpg','KINETIC Tesla Dio - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-cherry-red' WHERE p.slug='kinetic-tesla-dio';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla Dio E','kinetic-tesla-dio-e','KINETIC Tesla Dio E là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',25900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-DIO-E','KINETIC','E_SCOOTER','LITHIUM_ION',30,60,7.0,50,80,1600,2160,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-dio-e-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-e-1.jpg','KINETIC Tesla Dio E - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-pearl-white' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-e-2.jpg','KINETIC Tesla Dio E - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-matte-black' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-e-3.jpg','KINETIC Tesla Dio E - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-ocean-blue' WHERE p.slug='kinetic-tesla-dio-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-dio-e-4.jpg','KINETIC Tesla Dio E - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-dio-e-cherry-red' WHERE p.slug='kinetic-tesla-dio-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla E','kinetic-tesla-e','KINETIC Tesla E là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1400 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-E','KINETIC','E_SCOOTER','LITHIUM_ION',28,60,7.0,45,90,1400,1890,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-pearl-white','Pearl White','Pearl White','#F5F5F4',28,0,12,1,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-matte-black','Matte Black','Matte Black','#18181B',28,0,12,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',28,0,12,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-e-cherry-red','Cherry Red','Cherry Red','#DC2626',28,0,12,0,1 FROM products WHERE slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-e-1.jpg','KINETIC Tesla E - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-pearl-white' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-e-2.jpg','KINETIC Tesla E - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-matte-black' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-e-3.jpg','KINETIC Tesla E - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-ocean-blue' WHERE p.slug='kinetic-tesla-e';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-e-4.jpg','KINETIC Tesla E - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-e-cherry-red' WHERE p.slug='kinetic-tesla-e';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla Sd','kinetic-tesla-sd','KINETIC Tesla Sd là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 110 km sau mỗi lần sạc.',20400000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-SD','KINETIC','E_SCOOTER','LITHIUM_ION',24,60,7.0,50,110,1600,2160,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-sd-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-sd-1.jpg','KINETIC Tesla Sd - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-pearl-white' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-sd-2.jpg','KINETIC Tesla Sd - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-matte-black' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-sd-3.jpg','KINETIC Tesla Sd - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-ocean-blue' WHERE p.slug='kinetic-tesla-sd';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-tesla-sd-4.jpg','KINETIC Tesla Sd - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-sd-cherry-red' WHERE p.slug='kinetic-tesla-sd';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Xman Leo','kinetic-xman-leo','KINETIC Xman Leo là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1600 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 110 km sau mỗi lần sạc.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'XMAN-LEO','KINETIC','E_SCOOTER','LITHIUM_ION',24,60,7.0,50,110,1600,2160,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-pearl-white','Pearl White','Pearl White','#F5F5F4',24,0,12,1,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-matte-black','Matte Black','Matte Black','#18181B',24,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',24,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-xman-leo-cherry-red','Cherry Red','Cherry Red','#DC2626',24,0,12,0,1 FROM products WHERE slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-xman-leo-1.jpg','KINETIC Xman Leo - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-pearl-white' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-xman-leo-2.jpg','KINETIC Xman Leo - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-matte-black' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-xman-leo-3.jpg','KINETIC Xman Leo - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-ocean-blue' WHERE p.slug='kinetic-xman-leo';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-máy-điện-dibao-xman-leo-4.jpg','KINETIC Xman Leo - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-xman-leo-cherry-red' WHERE p.slug='kinetic-xman-leo';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Tesla X','kinetic-tesla-x','KINETIC Tesla X là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng ắc quy chì kín khí, công suất 800 W, tốc độ tối đa 50 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',19400000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'TESLA-X','KINETIC','E_SCOOTER','LEAD_ACID',30,60,7.0,50,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-tesla-x';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-x-pearl-white','Pearl White','Pearl White','#F5F5F4',30,0,12,1,1 FROM products WHERE slug='kinetic-tesla-x';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-x-matte-black','Matte Black','Matte Black','#18181B',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-x';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-x-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-x';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-tesla-x-cherry-red','Cherry Red','Cherry Red','#DC2626',30,0,12,0,1 FROM products WHERE slug='kinetic-tesla-x';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-tay-ga-50cc-dibao-tesla-x-1.jpg','KINETIC Tesla X - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-x-pearl-white' WHERE p.slug='kinetic-tesla-x';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-tay-ga-50cc-dibao-tesla-x-2.jpg','KINETIC Tesla X - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-x-matte-black' WHERE p.slug='kinetic-tesla-x';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-tay-ga-50cc-dibao-tesla-x-3.jpg','KINETIC Tesla X - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-x-ocean-blue' WHERE p.slug='kinetic-tesla-x';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-tay-ga-50cc-dibao-tesla-x-4.jpg','KINETIC Tesla X - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-tesla-x-cherry-red' WHERE p.slug='kinetic-tesla-x';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Butterfly','kinetic-butterfly','KINETIC Butterfly là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',23300000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'BUTTERFLY','KINETIC','E_SCOOTER','LITHIUM_ION',32,60,7.0,55,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-pearl-white','Pearl White','Pearl White','#F5F5F4',32,0,12,1,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-matte-black','Matte Black','Matte Black','#18181B',32,0,12,0,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',32,0,12,0,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-butterfly-cherry-red','Cherry Red','Cherry Red','#DC2626',32,0,12,0,1 FROM products WHERE slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-butterfly-1.jpg','KINETIC Butterfly - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-pearl-white' WHERE p.slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-butterfly-2.jpg','KINETIC Butterfly - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-matte-black' WHERE p.slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-butterfly-3.jpg','KINETIC Butterfly - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-ocean-blue' WHERE p.slug='kinetic-butterfly';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-butterfly-4.jpg','KINETIC Butterfly - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-butterfly-cherry-red' WHERE p.slug='kinetic-butterfly';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo S4','kinetic-gogo-s4','KINETIC Gogo S4 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 45 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',22600000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-S4','KINETIC','E_SCOOTER','LITHIUM_ION',28,60,7.0,45,90,800,1080,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-pearl-white','Pearl White','Pearl White','#F5F5F4',28,0,12,1,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-matte-black','Matte Black','Matte Black','#18181B',28,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',28,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s4-cherry-red','Cherry Red','Cherry Red','#DC2626',28,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s4-1.jpg','KINETIC Gogo S4 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-pearl-white' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s4-2.jpg','KINETIC Gogo S4 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-matte-black' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s4-3.jpg','KINETIC Gogo S4 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-ocean-blue' WHERE p.slug='kinetic-gogo-s4';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s4-4.jpg','KINETIC Gogo S4 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s4-cherry-red' WHERE p.slug='kinetic-gogo-s4';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Gogo S5','kinetic-gogo-s5','KINETIC Gogo S5 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1000 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 90 km sau mỗi lần sạc.',27000000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'GOGO-S5','KINETIC','E_SCOOTER','LITHIUM_ION',32,60,7.0,55,90,1000,1350,180,92,10,'DRUM','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-pearl-white','Pearl White','Pearl White','#F5F5F4',32,0,12,1,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-matte-black','Matte Black','Matte Black','#18181B',32,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',32,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-gogo-s5-cherry-red','Cherry Red','Cherry Red','#DC2626',32,0,12,0,1 FROM products WHERE slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s5-1.jpg','KINETIC Gogo S5 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-pearl-white' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s5-2.jpg','KINETIC Gogo S5 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-matte-black' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s5-3.jpg','KINETIC Gogo S5 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-ocean-blue' WHERE p.slug='kinetic-gogo-s5';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-gogo-s5-4.jpg','KINETIC Gogo S5 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-gogo-s5-cherry-red' WHERE p.slug='kinetic-gogo-s5';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC Jeek One','kinetic-jeek-one','KINETIC Jeek One là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 800 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 80 km sau mỗi lần sạc.',21100000,NULL,48,0,0,1,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'JEEK-ONE','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,80,800,1080,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-jeek-one-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-jeek-one-1.jpg','KINETIC Jeek One - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-pearl-white' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-jeek-one-2.jpg','KINETIC Jeek One - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-matte-black' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-jeek-one-3.jpg','KINETIC Jeek One - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-ocean-blue' WHERE p.slug='kinetic-jeek-one';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-jeek-one-4.jpg','KINETIC Jeek One - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-jeek-one-cherry-red' WHERE p.slug='kinetic-jeek-one';

INSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug='xe-dien'),'KINETIC R1','kinetic-r1','KINETIC R1 là mẫu xe điện dành cho di chuyển hằng ngày, vận hành êm ái và dễ tiếp cận. Xe sử dụng pin lithium-ion, công suất 1400 W, tốc độ tối đa 55 km/h và quãng đường di chuyển tối đa 100 km sau mỗi lần sạc.',18900000,NULL,48,0,0,0,1);
INSERT INTO product_specifications(product_id,model_code,brand,vehicle_type,battery_type,battery_capacity_ah,battery_voltage_v,charging_time_hours,max_speed_kmh,max_range_km,motor_power_watts,peak_motor_power_watts,max_load_kg,product_weight_kg,wheel_size_inch,brake_type,drive_type,water_resistance_rating,frame_material,suspension_type,display_type,smart_features,warranty_months,dimensions_mm) SELECT id,'R1','KINETIC','E_SCOOTER','LITHIUM_ION',26,60,7.0,55,100,1400,1890,180,92,10,'DISC','HUB_MOTOR','IPX5','Alloy Steel','Front Telescopic','LED Digital Display','Digital dashboard, anti-theft lock and USB charging port.',24,'1850 x 700 x 1120' FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-pearl-white','Pearl White','Pearl White','#F5F5F4',26,0,12,1,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-matte-black','Matte Black','Matte Black','#18181B',26,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-ocean-blue','Ocean Blue','Ocean Blue','#2563EB',26,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id,'kinetic-r1-cherry-red','Cherry Red','Cherry Red','#DC2626',26,0,12,0,1 FROM products WHERE slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-r1-1.jpg','KINETIC R1 - Pearl White',0,1,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-pearl-white' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-r1-2.jpg','KINETIC R1 - Matte Black',1,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-matte-black' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-r1-3.jpg','KINETIC R1 - Ocean Blue',2,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-ocean-blue' WHERE p.slug='kinetic-r1';
INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id,'/images/products/xe-điện-dibao-r1-4.jpg','KINETIC R1 - Cherry Red',3,0,'LOCAL','ACTIVE' FROM products p LEFT JOIN product_variants v ON v.sku='kinetic-r1-cherry-red' WHERE p.slug='kinetic-r1';
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 1 - Nguyen Thai Hoc','Quan 1','187 Nguyen Thai Hoc, Phuong Pham Ngu Lao, Quan 1, TP. Ho Chi Minh','028 3920 1234','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 3 - Cach Mang Thang 8','Quan 3','420 Cach Mang Thang 8, Phuong 11, Quan 3, TP. Ho Chi Minh','028 3931 5678','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Quan 7 - Nguyen Thi Thap','Quan 7','1021 Nguyen Thi Thap, Phuong Tan Phu, Quan 7, TP. Ho Chi Minh','028 3776 8899','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Go Vap - Quang Trung','Go Vap','588 Quang Trung, Phuong 11, Go Vap, TP. Ho Chi Minh','028 3589 4567','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Thu Duc - Vo Van Ngan','Thu Duc','216 Vo Van Ngan, Phuong Binh Tho, Thu Duc, TP. Ho Chi Minh','028 3722 7788','08:00 - 21:00',1);
INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES ('KINETIC Tan Binh - Cong Hoa','Tan Binh','45 Cong Hoa, Phuong 4, Tan Binh, TP. Ho Chi Minh','028 3811 2233','08:00 - 21:00',1);

INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('UNLOCK_FEE','Phí đăng ký dịch vụ','Phí dịch vụ một lần khi tạo đơn hàng.',5000,'VND/lần','ACTIVE',NOW()),
('MINUTE_RATE','Phí giao xe','Phí xử lý và giao xe tại cửa hàng.',1800,'VND/phút','ACTIVE',NOW()),
('HOUR_RATE','Gói bảo hành mở rộng','Gói bảo hành mở rộng theo thời hạn.',45000,'VND/giờ','SCHEDULED',DATE_ADD(NOW(), INTERVAL 5 DAY)),
('WRONG_ZONE_FINE','Phí giao hàng ngoài khu vực','Áp dụng khi giao hàng ngoài vùng hỗ trợ.',30000,'VND/lần','ACTIVE',NOW()),
('OVERDUE_FINE','Phí giao trễ','Phí phát sinh khi lịch giao xe bị trễ.',12000,'VND/lần','ACTIVE',NOW());
INSERT INTO promotions(code,campaign_name,discount_type,discount_value,max_discount_amount,usage_count,usage_limit,status,starts_at,ends_at) VALUES
('KINETIC20','Chào hè xanh','PERCENTAGE',20,25000,326,800,'LIVE','2026-06-01 00:00:00','2026-06-30 23:59:59'),
('STUDENT15','Ưu đãi sinh viên','FIXED_AMOUNT',15000,NULL,1020,2000,'LIVE','2026-05-15 00:00:00','2026-07-15 23:59:59'),
('WEEKEND','Ưu đãi cuối tuần','PERCENTAGE',10,NULL,0,500,'WAITING','2026-06-08 00:00:00','2026-06-30 23:59:59'),
('EBIKE100','Giảm 100 nghìn','FIXED_AMOUNT',100000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59'),
('EBIKE200','Giảm 200 nghìn','FIXED_AMOUNT',200000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59');
INSERT INTO admin_audit_logs(actor,action,target,ip_address) VALUES ('System','Khởi tạo dữ liệu mẫu','pricing rules và promotions','127.0.0.1');

SET FOREIGN_KEY_CHECKS=1;
