-- =====================================================
-- Seed data for manager revenue report demo
-- - Top sellers: first 2 active products with high quantity
-- - Slow sellers: 3rd/4th products with 1 sale each
-- - Zero-sale products: remaining active products (shown as "Chua ban trong ky")
--
-- Prerequisites:
--   1. PostgreSQL running (ebike_db)
--   2. Product catalog imported (backend/data-backup/.../products_db_ready_clean.s3.sql)
--
-- Run:
--   psql -U ebike_user -d ebike_db -v ON_ERROR_STOP=1 -f backend/sql-init/seed_manager_revenue_test_data.sql
-- Or:
--   powershell -File backend/sql-init/run_seed_revenue_test.ps1
-- =====================================================

BEGIN;

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
    'revenue.test',
    'revenue.test@example.com',
    '$2a$10$tRiY3ac6Bnx5EHBkjP5ek.ofn3urim7dE36HV4uY3MpPWPficRNoy',
    'Revenue',
    'Tester',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'revenue.test'
);

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'CUSTOMER'
WHERE u.username = 'revenue.test'
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE rev_test_products ON COMMIT DROP AS
SELECT
    p.id,
    p.name,
    COALESCE(p.discount_price, p.price) AS unit_price,
    ROW_NUMBER() OVER (ORDER BY p.id) AS rn
FROM ebike_product.products p
WHERE p.is_active = TRUE
ORDER BY p.id;

DO $$
DECLARE
    active_product_count INT;
    existing_seed_count INT;
    customer_id BIGINT;
    showroom_id BIGINT;
    product_1_id BIGINT;
    product_1_name TEXT;
    product_1_price NUMERIC(12, 2);
    product_2_id BIGINT;
    product_2_name TEXT;
    product_2_price NUMERIC(12, 2);
    product_3_id BIGINT;
    product_3_name TEXT;
    product_3_price NUMERIC(12, 2);
    product_4_id BIGINT;
    product_4_name TEXT;
    product_4_price NUMERIC(12, 2);
    order_id BIGINT;
    subtotal NUMERIC(12, 2);
BEGIN
    SELECT COUNT(*) INTO active_product_count FROM rev_test_products;
    SELECT COUNT(*) INTO existing_seed_count
    FROM ebike_order.orders
    WHERE order_number LIKE 'REV-TEST-%';

    IF active_product_count < 4 THEN
        RAISE EXCEPTION 'Can it nhat 4 san pham active. Hay import catalog truoc (products_db_ready_clean.s3.sql).';
    END IF;

    IF existing_seed_count > 0 THEN
        RAISE NOTICE 'Du lieu test doanh thu da ton tai (% don REV-TEST-*). Bo qua.', existing_seed_count;
        RETURN;
    END IF;

    SELECT u.id INTO customer_id
    FROM ebike_auth.users u
    WHERE u.username = 'revenue.test'
    LIMIT 1;

    SELECT s.id INTO showroom_id
    FROM ebike_order.showrooms s
    WHERE s.is_active = TRUE
    ORDER BY s.id
    LIMIT 1;

    SELECT id, name, unit_price INTO product_1_id, product_1_name, product_1_price
    FROM rev_test_products WHERE rn = 1;

    SELECT id, name, unit_price INTO product_2_id, product_2_name, product_2_price
    FROM rev_test_products WHERE rn = 2;

    SELECT id, name, unit_price INTO product_3_id, product_3_name, product_3_price
    FROM rev_test_products WHERE rn = 3;

    SELECT id, name, unit_price INTO product_4_id, product_4_name, product_4_price
    FROM rev_test_products WHERE rn = 4;

    -- Order 1: best seller (5 units)
    subtotal := product_1_price * 5;
    INSERT INTO ebike_order.orders (
        user_id, order_number, status, subtotal, shipping_fee, discount_amount,
        total_amount, registration_fee, customer_email, created_at, updated_at
    ) VALUES (
        customer_id, 'REV-TEST-001', 'DELIVERED', subtotal, 0, 0,
        subtotal, 0, 'revenue.test@example.com', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days'
    ) RETURNING id INTO order_id;

    INSERT INTO ebike_order.order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
    VALUES (order_id, product_1_id, product_1_name, product_1_price, 5, subtotal);

    INSERT INTO ebike_order.payments (order_id, payment_method, payment_status, transaction_reference, amount, paid_at)
    VALUES (order_id, 'VNPAY', 'PAID', 'REV-TEST-PAY-001', subtotal, CURRENT_TIMESTAMP - INTERVAL '2 days');

    INSERT INTO ebike_order.shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id, delivered_at)
    VALUES (order_id, 'DELIVERED', 'Revenue Tester', '0900000001', showroom_id, CURRENT_TIMESTAMP - INTERVAL '1 day');

    -- Order 2: best seller again (3 units)
    subtotal := product_1_price * 3;
    INSERT INTO ebike_order.orders (
        user_id, order_number, status, subtotal, shipping_fee, discount_amount,
        total_amount, registration_fee, customer_email, created_at, updated_at
    ) VALUES (
        customer_id, 'REV-TEST-002', 'DELIVERED', subtotal, 0, 0,
        subtotal, 0, 'revenue.test@example.com', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '6 days'
    ) RETURNING id INTO order_id;

    INSERT INTO ebike_order.order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
    VALUES (order_id, product_1_id, product_1_name, product_1_price, 3, subtotal);

    INSERT INTO ebike_order.payments (order_id, payment_method, payment_status, transaction_reference, amount, paid_at)
    VALUES (order_id, 'VNPAY', 'PAID', 'REV-TEST-PAY-002', subtotal, CURRENT_TIMESTAMP - INTERVAL '6 days');

    INSERT INTO ebike_order.shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id, delivered_at)
    VALUES (order_id, 'DELIVERED', 'Revenue Tester', '0900000001', showroom_id, CURRENT_TIMESTAMP - INTERVAL '5 days');

    -- Order 3: moderate seller (2 units)
    subtotal := product_2_price * 2;
    INSERT INTO ebike_order.orders (
        user_id, order_number, status, subtotal, shipping_fee, discount_amount,
        total_amount, registration_fee, customer_email, created_at, updated_at
    ) VALUES (
        customer_id, 'REV-TEST-003', 'DELIVERED', subtotal, 0, 0,
        subtotal, 0, 'revenue.test@example.com', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '9 days'
    ) RETURNING id INTO order_id;

    INSERT INTO ebike_order.order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
    VALUES (order_id, product_2_id, product_2_name, product_2_price, 2, subtotal);

    INSERT INTO ebike_order.payments (order_id, payment_method, payment_status, transaction_reference, amount, paid_at)
    VALUES (order_id, 'VNPAY', 'PAID', 'REV-TEST-PAY-003', subtotal, CURRENT_TIMESTAMP - INTERVAL '9 days');

    INSERT INTO ebike_order.shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id, delivered_at)
    VALUES (order_id, 'DELIVERED', 'Revenue Tester', '0900000001', showroom_id, CURRENT_TIMESTAMP - INTERVAL '8 days');

    -- Order 4: slow seller (1 unit)
    subtotal := product_3_price;
    INSERT INTO ebike_order.orders (
        user_id, order_number, status, subtotal, shipping_fee, discount_amount,
        total_amount, registration_fee, customer_email, created_at, updated_at
    ) VALUES (
        customer_id, 'REV-TEST-004', 'DELIVERED', subtotal, 0, 0,
        subtotal, 0, 'revenue.test@example.com', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '13 days'
    ) RETURNING id INTO order_id;

    INSERT INTO ebike_order.order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
    VALUES (order_id, product_3_id, product_3_name, product_3_price, 1, subtotal);

    INSERT INTO ebike_order.payments (order_id, payment_method, payment_status, transaction_reference, amount, paid_at)
    VALUES (order_id, 'VNPAY', 'PAID', 'REV-TEST-PAY-004', subtotal, CURRENT_TIMESTAMP - INTERVAL '13 days');

    INSERT INTO ebike_order.shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id, delivered_at)
    VALUES (order_id, 'DELIVERED', 'Revenue Tester', '0900000001', showroom_id, CURRENT_TIMESTAMP - INTERVAL '12 days');

    -- Order 5: another slow seller (1 unit)
    subtotal := product_4_price;
    INSERT INTO ebike_order.orders (
        user_id, order_number, status, subtotal, shipping_fee, discount_amount,
        total_amount, registration_fee, customer_email, created_at, updated_at
    ) VALUES (
        customer_id, 'REV-TEST-005', 'DELIVERED', subtotal, 0, 0,
        subtotal, 0, 'revenue.test@example.com', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '19 days'
    ) RETURNING id INTO order_id;

    INSERT INTO ebike_order.order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
    VALUES (order_id, product_4_id, product_4_name, product_4_price, 1, subtotal);

    INSERT INTO ebike_order.payments (order_id, payment_method, payment_status, transaction_reference, amount, paid_at)
    VALUES (order_id, 'VNPAY', 'PAID', 'REV-TEST-PAY-005', subtotal, CURRENT_TIMESTAMP - INTERVAL '19 days');

    INSERT INTO ebike_order.shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id, delivered_at)
    VALUES (order_id, 'DELIVERED', 'Revenue Tester', '0900000001', showroom_id, CURRENT_TIMESTAMP - INTERVAL '18 days');

    RAISE NOTICE 'Da seed 5 don REV-TEST-* de demo bao cao doanh thu.';
    RAISE NOTICE 'Ban chay: san pham #1 (8 don vi), #2 (2 don vi), #3/#4 (1 don vi), con lai 0 don vi.';
END $$;

COMMIT;
