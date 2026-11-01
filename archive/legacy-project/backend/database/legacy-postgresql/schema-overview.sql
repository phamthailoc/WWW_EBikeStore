-- =====================================================
-- E-Bike Multiplatform Database Schema Overview
-- =====================================================
-- Current migration strategy:
--   - Active Flyway baseline: db/migration/V2_0_0__baseline.sql
--   - Archived migration history: db/migration-archive/V1_*.sql
--
-- Purpose:
--   Consolidated overview of the current database schema
--   derived from migrations V1_0_0 through V1_0_9.
--
-- Notes:
--   - This file is for reference/documentation.
--   - Flyway should continue using the versioned migration files.
--   - ebike_chatbot schema exists, but no tables are defined yet.

-- =====================================================
-- SCHEMAS
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_auth;
CREATE SCHEMA IF NOT EXISTS ebike_product;
CREATE SCHEMA IF NOT EXISTS ebike_order;
CREATE SCHEMA IF NOT EXISTS ebike_chatbot;
CREATE SCHEMA IF NOT EXISTS ebike_user;

-- =====================================================
-- AUTH MODULE
-- Source: V1_0_1__auth_tables.sql
-- =====================================================

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

-- =====================================================
-- PRODUCT MODULE
-- Source: V1_0_2__product_tables.sql
-- =====================================================

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

CREATE TABLE IF NOT EXISTS ebike_product.product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES ebike_product.product_variants(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    alt_text VARCHAR(255),
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE
);

-- =====================================================
-- ORDER MODULE
-- Source: V1_0_3__order_tables.sql
-- =====================================================

CREATE TABLE IF NOT EXISTS ebike_order.orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    shipping_fee DECIMAL(12, 2) DEFAULT 0,
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    total_amount DECIMAL(12, 2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS ebike_order.payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100),
    amount DECIMAL(12, 2) NOT NULL,
    paid_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ebike_order.shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT UNIQUE NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    shipment_status VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(255),
    phone_number VARCHAR(30),
    shipping_address TEXT,
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP
);

-- =====================================================
-- CHATBOT MODULE
-- Source: V1_0_4__chatbot_tables.sql
-- =====================================================
-- No tables defined yet.

-- =====================================================
-- USER MODULE
-- Source: V1_0_5__user_tables.sql
-- =====================================================

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
