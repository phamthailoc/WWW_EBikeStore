-- =====================================================
-- Product Module Tables
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_product;
CREATE SCHEMA IF NOT EXISTS ebike_auth;

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

CREATE INDEX idx_products_category ON ebike_product.products(category_id);
CREATE INDEX idx_products_slug ON ebike_product.products(slug);
CREATE INDEX idx_products_active ON ebike_product.products(is_active);

CREATE TABLE IF NOT EXISTS ebike_product.reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title VARCHAR(255),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, user_id)
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

CREATE INDEX idx_product_variants_product_id ON ebike_product.product_variants(product_id);
CREATE INDEX idx_product_variants_sku ON ebike_product.product_variants(sku);

CREATE TABLE IF NOT EXISTS ebike_product.product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES ebike_product.product_variants(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    alt_text VARCHAR(255),
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_product_images_product_id ON ebike_product.product_images(product_id);
