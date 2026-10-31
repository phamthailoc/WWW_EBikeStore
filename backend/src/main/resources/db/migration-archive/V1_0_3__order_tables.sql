-- =====================================================
-- Order Module Tables
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_order;
CREATE SCHEMA IF NOT EXISTS ebike_auth;
CREATE SCHEMA IF NOT EXISTS ebike_product;

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

CREATE INDEX idx_orders_user ON ebike_order.orders(user_id);
CREATE INDEX idx_orders_status ON ebike_order.orders(status);

CREATE TABLE IF NOT EXISTS ebike_order.order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES ebike_order.orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id),
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
