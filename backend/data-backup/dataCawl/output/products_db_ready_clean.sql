-- =====================================================
-- Clean product seed generated from local images
-- Rules:
--   - Ignore image variants ending with index 0
--   - Models containing 'co-ban-dap' are mapped to xe-dap-dien / E_BIKE
--   - Other xe may images are mapped to xe-dien / E_SCOOTER
-- =====================================================

\encoding UTF8
SET client_encoding TO 'UTF8';

BEGIN;

INSERT INTO ebike_product.categories (name, slug, description, is_active)
VALUES ('Xe điện', 'xe-dien', 'Dòng xe điện đô thị với thiết kế gọn, vận hành êm và phù hợp di chuyển hằng ngày.', TRUE)
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  is_active = TRUE;

INSERT INTO ebike_product.categories (name, slug, description, is_active)
VALUES ('Xe đạp điện', 'xe-dap-dien', 'Dòng xe đạp điện trợ lực, có bàn đạp, linh hoạt cho học sinh sinh viên và di chuyển trong phố.', TRUE)
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  is_active = TRUE;

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Sir 50CC',
    'kinetic-sir-50cc',
    'KINETIC Sir 50CC la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 800W, toc do toi da 50 km/h va tam hoat dong den 90 km.',
    17900000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'SIR-50CC', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  24, 60, 7.0,
  50, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-sir-50cc'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-sir-50cc-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-sir-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-sir-50cc-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-sir-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-sir-50cc-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-sir-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-sir-50cc-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-sir-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-1.jpg', 'KINETIC Sir 50CC - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-sir-50cc' AND v.sku = 'kinetic-sir-50cc-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-2.jpg', 'KINETIC Sir 50CC - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-sir-50cc' AND v.sku = 'kinetic-sir-50cc-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-3.jpg', 'KINETIC Sir 50CC - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-sir-50cc' AND v.sku = 'kinetic-sir-50cc-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-4.jpg', 'KINETIC Sir 50CC - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-sir-50cc' AND v.sku = 'kinetic-sir-50cc-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-dibao-sir-50cc-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Creer',
    'kinetic-creer',
    'KINETIC Creer la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 800W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    19400000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'CREER', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  30, 60, 7.0,
  50, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-1.jpg', 'KINETIC Creer - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer' AND v.sku = 'kinetic-creer-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-2.jpg', 'KINETIC Creer - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer' AND v.sku = 'kinetic-creer-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-3.jpg', 'KINETIC Creer - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer' AND v.sku = 'kinetic-creer-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-4.jpg', 'KINETIC Creer - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer' AND v.sku = 'kinetic-creer-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-creer-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gofast',
    'kinetic-gofast',
    'KINETIC Gofast la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 90 km.',
    17900000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOFAST', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  28, 60, 7.0,
  45, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gofast'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gofast-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 28, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gofast'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gofast-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gofast'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gofast-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gofast'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gofast-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gofast'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-1.jpg', 'KINETIC Gofast - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gofast' AND v.sku = 'kinetic-gofast-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-2.jpg', 'KINETIC Gofast - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gofast' AND v.sku = 'kinetic-gofast-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-3.jpg', 'KINETIC Gofast - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gofast' AND v.sku = 'kinetic-gofast-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-4.jpg', 'KINETIC Gofast - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gofast' AND v.sku = 'kinetic-gofast-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-gofast-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Xs1 Bản Nâng Cấp',
    'kinetic-pansy-xs1-ban-nang-cap',
    'KINETIC Pansy Xs1 Bản Nâng Cấp la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 1400W, toc do toi da 55 km/h va tam hoat dong den 110 km.',
    20300000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-XS1-BAN-NANG-CAP', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  32, 60, 7.0,
  55, 110, 1400, 1890,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-ban-nang-cap-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 32, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-ban-nang-cap-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-ban-nang-cap-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-ban-nang-cap-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-1.jpg', 'KINETIC Pansy Xs1 Bản Nâng Cấp - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND v.sku = 'kinetic-pansy-xs1-ban-nang-cap-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-2.jpg', 'KINETIC Pansy Xs1 Bản Nâng Cấp - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND v.sku = 'kinetic-pansy-xs1-ban-nang-cap-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-3.jpg', 'KINETIC Pansy Xs1 Bản Nâng Cấp - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND v.sku = 'kinetic-pansy-xs1-ban-nang-cap-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-4.jpg', 'KINETIC Pansy Xs1 Bản Nâng Cấp - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND v.sku = 'kinetic-pansy-xs1-ban-nang-cap-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-50cc-dibao-pansy-xs1-bản-nâng-cấp-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Xs',
    'kinetic-pansy-xs',
    'KINETIC Pansy Xs la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1400W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    23300000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-XS', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  30, 60, 7.0,
  50, 80, 1400, 1890,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-1.jpg', 'KINETIC Pansy Xs - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs' AND v.sku = 'kinetic-pansy-xs-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-2.jpg', 'KINETIC Pansy Xs - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs' AND v.sku = 'kinetic-pansy-xs-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-3.jpg', 'KINETIC Pansy Xs - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs' AND v.sku = 'kinetic-pansy-xs-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-4.jpg', 'KINETIC Pansy Xs - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs' AND v.sku = 'kinetic-pansy-xs-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Xs1',
    'kinetic-pansy-xs1',
    'KINETIC Pansy Xs1 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    22200000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-XS1', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  30, 60, 7.0,
  50, 80, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-xs1-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-xs1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-1.jpg', 'KINETIC Pansy Xs1 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1' AND v.sku = 'kinetic-pansy-xs1-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-2.jpg', 'KINETIC Pansy Xs1 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1' AND v.sku = 'kinetic-pansy-xs1-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-3.jpg', 'KINETIC Pansy Xs1 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1' AND v.sku = 'kinetic-pansy-xs1-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-4.jpg', 'KINETIC Pansy Xs1 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-xs1' AND v.sku = 'kinetic-pansy-xs1-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-tay-ga-dibao-pansy-xs1-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dap-dien'),
    'KINETIC Amy',
    'kinetic-amy',
    'KINETIC Amy la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. Xe su dung dong co 650W, van toc toi da 35 km/h va quang duong di duoc toi da 100 km cho moi lan sac.',
    17700000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'AMY', 'KINETIC', 'E_BIKE', 'LITHIUM_ION',
  24, 48, 5.5,
  35, 100, 650, 877,
  150, 68, 14,
  'DISC', 'CHAIN', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-amy'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-amy-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-amy'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-amy-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-amy'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-amy-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-amy'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-amy-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-amy'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-1.jpg', 'KINETIC Amy - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-amy' AND v.sku = 'kinetic-amy-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-2.jpg', 'KINETIC Amy - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-amy' AND v.sku = 'kinetic-amy-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-3.jpg', 'KINETIC Amy - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-amy' AND v.sku = 'kinetic-amy-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-4.jpg', 'KINETIC Amy - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-amy' AND v.sku = 'kinetic-amy-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-amy-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dap-dien'),
    'KINETIC Ella',
    'kinetic-ella',
    'KINETIC Ella la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. Xe su dung dong co 450W, van toc toi da 45 km/h va quang duong di duoc toi da 80 km cho moi lan sac.',
    19500000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'ELLA', 'KINETIC', 'E_BIKE', 'LITHIUM_ION',
  28, 48, 5.5,
  45, 80, 450, 607,
  150, 68, 14,
  'DISC', 'CHAIN', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ella'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ella-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 28, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ella'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ella-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ella'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ella-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ella'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ella-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ella'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-1.jpg', 'KINETIC Ella - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ella' AND v.sku = 'kinetic-ella-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-2.jpg', 'KINETIC Ella - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ella' AND v.sku = 'kinetic-ella-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-3.jpg', 'KINETIC Ella - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ella' AND v.sku = 'kinetic-ella-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-4.jpg', 'KINETIC Ella - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ella' AND v.sku = 'kinetic-ella-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ella-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dap-dien'),
    'KINETIC Queen',
    'kinetic-queen',
    'KINETIC Queen la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. Xe su dung dong co 650W, van toc toi da 45 km/h va quang duong di duoc toi da 100 km cho moi lan sac.',
    16800000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'QUEEN', 'KINETIC', 'E_BIKE', 'LITHIUM_ION',
  22, 48, 5.5,
  45, 100, 650, 877,
  150, 68, 14,
  'DISC', 'CHAIN', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-queen'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-queen-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-queen'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-queen-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-queen'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-queen-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-queen'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-queen-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-queen'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-1.jpg', 'KINETIC Queen - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-queen' AND v.sku = 'kinetic-queen-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-2.jpg', 'KINETIC Queen - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-queen' AND v.sku = 'kinetic-queen-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-3.jpg', 'KINETIC Queen - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-queen' AND v.sku = 'kinetic-queen-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-4.jpg', 'KINETIC Queen - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-queen' AND v.sku = 'kinetic-queen-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-queen-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dap-dien'),
    'KINETIC Ryan',
    'kinetic-ryan',
    'KINETIC Ryan la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. Xe su dung dong co 650W, van toc toi da 45 km/h va quang duong di duoc toi da 100 km cho moi lan sac.',
    17700000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'RYAN', 'KINETIC', 'E_BIKE', 'LITHIUM_ION',
  24, 48, 5.5,
  45, 100, 650, 877,
  150, 68, 14,
  'DISC', 'CHAIN', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ryan'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ryan-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ryan'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ryan-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ryan'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ryan-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ryan'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ryan-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ryan'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-1.jpg', 'KINETIC Ryan - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ryan' AND v.sku = 'kinetic-ryan-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-2.jpg', 'KINETIC Ryan - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ryan' AND v.sku = 'kinetic-ryan-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-3.jpg', 'KINETIC Ryan - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ryan' AND v.sku = 'kinetic-ryan-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-4.jpg', 'KINETIC Ryan - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ryan' AND v.sku = 'kinetic-ryan-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-ryan-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dap-dien'),
    'KINETIC S One',
    'kinetic-s-one',
    'KINETIC S One la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. Xe su dung dong co 650W, van toc toi da 35 km/h va quang duong di duoc toi da 100 km cho moi lan sac.',
    15900000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'S-ONE', 'KINETIC', 'E_BIKE', 'LITHIUM_ION',
  20, 48, 5.5,
  35, 100, 650, 877,
  150, 68, 14,
  'DISC', 'CHAIN', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-s-one'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-s-one-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 20, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-s-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-s-one-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 20, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-s-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-s-one-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 20, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-s-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-s-one-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 20, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-s-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-1.jpg', 'KINETIC S One - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-s-one' AND v.sku = 'kinetic-s-one-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-2.jpg', 'KINETIC S One - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-s-one' AND v.sku = 'kinetic-s-one-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-3.jpg', 'KINETIC S One - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-s-one' AND v.sku = 'kinetic-s-one-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-4.jpg', 'KINETIC S One - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-s-one' AND v.sku = 'kinetic-s-one-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-có-bàn-đạp-dibao-s-one-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Creer E',
    'kinetic-creer-e',
    'KINETIC Creer E la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 90 km.',
    21500000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'CREER-E', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  28, 60, 7.0,
  45, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-e'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-e-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 28, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-e-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-e-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-e-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-1.jpg', 'KINETIC Creer E - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-e' AND v.sku = 'kinetic-creer-e-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-2.jpg', 'KINETIC Creer E - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-e' AND v.sku = 'kinetic-creer-e-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-3.jpg', 'KINETIC Creer E - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-e' AND v.sku = 'kinetic-creer-e-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-4.jpg', 'KINETIC Creer E - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-e' AND v.sku = 'kinetic-creer-e-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-e-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Creer Nile',
    'kinetic-creer-nile',
    'KINETIC Creer Nile la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 45 km/h va tam hoat dong den 80 km.',
    27000000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'CREER-NILE', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 80, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-nile'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-nile-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-nile'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-nile-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-nile'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-nile-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-nile'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-creer-nile-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-creer-nile'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-1.jpg', 'KINETIC Creer Nile - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-nile' AND v.sku = 'kinetic-creer-nile-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-2.jpg', 'KINETIC Creer Nile - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-nile' AND v.sku = 'kinetic-creer-nile-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-3.jpg', 'KINETIC Creer Nile - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-nile' AND v.sku = 'kinetic-creer-nile-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-4.jpg', 'KINETIC Creer Nile - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-creer-nile' AND v.sku = 'kinetic-creer-nile-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-creer-nile-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo Cross',
    'kinetic-gogo-cross',
    'KINETIC Gogo Cross la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 45 km/h va tam hoat dong den 100 km.',
    23700000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-CROSS', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 100, 1600, 2160,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-1.jpg', 'KINETIC Gogo Cross - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross' AND v.sku = 'kinetic-gogo-cross-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-2.jpg', 'KINETIC Gogo Cross - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross' AND v.sku = 'kinetic-gogo-cross-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-3.jpg', 'KINETIC Gogo Cross - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross' AND v.sku = 'kinetic-gogo-cross-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-4.jpg', 'KINETIC Gogo Cross - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross' AND v.sku = 'kinetic-gogo-cross-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo Cross G',
    'kinetic-gogo-cross-g',
    'KINETIC Gogo Cross G la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 55 km/h va tam hoat dong den 100 km.',
    22600000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-CROSS-G', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 100, 1600, 2160,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross-g'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-g-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross-g'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-g-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross-g'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-g-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross-g'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-cross-g-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-cross-g'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-1.jpg', 'KINETIC Gogo Cross G - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross-g' AND v.sku = 'kinetic-gogo-cross-g-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-2.jpg', 'KINETIC Gogo Cross G - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross-g' AND v.sku = 'kinetic-gogo-cross-g-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-3.jpg', 'KINETIC Gogo Cross G - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross-g' AND v.sku = 'kinetic-gogo-cross-g-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-4.jpg', 'KINETIC Gogo Cross G - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-cross-g' AND v.sku = 'kinetic-gogo-cross-g-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-cross-g-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo Crown',
    'kinetic-gogo-crown',
    'KINETIC Gogo Crown la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1000W, toc do toi da 55 km/h va tam hoat dong den 100 km.',
    20400000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-CROWN', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 100, 1000, 1350,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-crown'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-crown-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-crown'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-crown-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-crown'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-crown-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-crown'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-crown-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-crown'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-1.jpg', 'KINETIC Gogo Crown - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-crown' AND v.sku = 'kinetic-gogo-crown-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-2.jpg', 'KINETIC Gogo Crown - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-crown' AND v.sku = 'kinetic-gogo-crown-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-3.jpg', 'KINETIC Gogo Crown - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-crown' AND v.sku = 'kinetic-gogo-crown-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-4.jpg', 'KINETIC Gogo Crown - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-crown' AND v.sku = 'kinetic-gogo-crown-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-crown-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo Moon',
    'kinetic-gogo-moon',
    'KINETIC Gogo Moon la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 55 km/h va tam hoat dong den 80 km.',
    21500000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-MOON', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-moon'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-moon-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-moon'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-moon-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-moon'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-moon-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-moon'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-moon-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-moon'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-1.jpg', 'KINETIC Gogo Moon - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-moon' AND v.sku = 'kinetic-gogo-moon-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-2.jpg', 'KINETIC Gogo Moon - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-moon' AND v.sku = 'kinetic-gogo-moon-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-3.jpg', 'KINETIC Gogo Moon - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-moon' AND v.sku = 'kinetic-gogo-moon-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-4.jpg', 'KINETIC Gogo Moon - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-moon' AND v.sku = 'kinetic-gogo-moon-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-gogo-moon-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Keva',
    'kinetic-keva',
    'KINETIC Keva la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 50 km/h va tam hoat dong den 90 km.',
    25500000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'KEVA', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  24, 60, 7.0,
  50, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-keva'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-keva-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-keva'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-keva-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-keva'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-keva-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-keva'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-keva-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-keva'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-1.jpg', 'KINETIC Keva - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-keva' AND v.sku = 'kinetic-keva-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-2.jpg', 'KINETIC Keva - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-keva' AND v.sku = 'kinetic-keva-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-3.jpg', 'KINETIC Keva - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-keva' AND v.sku = 'kinetic-keva-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-4.jpg', 'KINETIC Keva - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-keva' AND v.sku = 'kinetic-keva-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-keva-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Ls007',
    'kinetic-ls007',
    'KINETIC Ls007 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 45 km/h va tam hoat dong den 80 km.',
    23300000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'LS007', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 80, 1600, 2160,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ls007'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ls007-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ls007'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ls007-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ls007'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ls007-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ls007'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-ls007-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-ls007'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-1.jpg', 'KINETIC Ls007 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ls007' AND v.sku = 'kinetic-ls007-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-2.jpg', 'KINETIC Ls007 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ls007' AND v.sku = 'kinetic-ls007-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-3.jpg', 'KINETIC Ls007 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ls007' AND v.sku = 'kinetic-ls007-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-4.jpg', 'KINETIC Ls007 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-ls007' AND v.sku = 'kinetic-ls007-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-ls007-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC M One',
    'kinetic-m-one',
    'KINETIC M One la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 50 km/h va tam hoat dong den 90 km.',
    25500000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'M-ONE', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  24, 60, 7.0,
  50, 90, 1600, 2160,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-m-one'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-m-one-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-m-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-m-one-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-m-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-m-one-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-m-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-m-one-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-m-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-1.jpg', 'KINETIC M One - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-m-one' AND v.sku = 'kinetic-m-one-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-2.jpg', 'KINETIC M One - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-m-one' AND v.sku = 'kinetic-m-one-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-3.jpg', 'KINETIC M One - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-m-one' AND v.sku = 'kinetic-m-one-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-4.jpg', 'KINETIC M One - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-m-one' AND v.sku = 'kinetic-m-one-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-m-one-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Mxs',
    'kinetic-mxs',
    'KINETIC Mxs la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 50 km/h va tam hoat dong den 100 km.',
    20000000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'MXS', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  30, 60, 7.0,
  50, 100, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-mxs'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-mxs-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-mxs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-mxs-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-mxs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-mxs-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-mxs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-mxs-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-mxs'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-1.jpg', 'KINETIC Mxs - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-mxs' AND v.sku = 'kinetic-mxs-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-2.jpg', 'KINETIC Mxs - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-mxs' AND v.sku = 'kinetic-mxs-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-3.jpg', 'KINETIC Mxs - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-mxs' AND v.sku = 'kinetic-mxs-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-4.jpg', 'KINETIC Mxs - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-mxs' AND v.sku = 'kinetic-mxs-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-mxs-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Dio',
    'kinetic-pansy-dio',
    'KINETIC Pansy Dio la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 55 km/h va tam hoat dong den 80 km.',
    18900000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-DIO', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 80, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-1.jpg', 'KINETIC Pansy Dio - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio' AND v.sku = 'kinetic-pansy-dio-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2.jpg', 'KINETIC Pansy Dio - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio' AND v.sku = 'kinetic-pansy-dio-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-3.jpg', 'KINETIC Pansy Dio - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio' AND v.sku = 'kinetic-pansy-dio-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-4.jpg', 'KINETIC Pansy Dio - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio' AND v.sku = 'kinetic-pansy-dio-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Dio 2025',
    'kinetic-pansy-dio-2025',
    'KINETIC Pansy Dio 2025 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 45 km/h va tam hoat dong den 80 km.',
    22200000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-DIO-2025', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 80, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio-2025'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-2025-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio-2025'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-2025-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio-2025'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-2025-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio-2025'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-dio-2025-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-dio-2025'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-1.jpg', 'KINETIC Pansy Dio 2025 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio-2025' AND v.sku = 'kinetic-pansy-dio-2025-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-2.jpg', 'KINETIC Pansy Dio 2025 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio-2025' AND v.sku = 'kinetic-pansy-dio-2025-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-3.jpg', 'KINETIC Pansy Dio 2025 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio-2025' AND v.sku = 'kinetic-pansy-dio-2025-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-4.jpg', 'KINETIC Pansy Dio 2025 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-dio-2025' AND v.sku = 'kinetic-pansy-dio-2025-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-dio-2025-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy S2 Càng Nhôm',
    'kinetic-pansy-s2-cang-nhom',
    'KINETIC Pansy S2 Càng Nhôm la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 55 km/h va tam hoat dong den 80 km.',
    24400000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-S2-CANG-NHOM', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 80, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s2-cang-nhom-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s2-cang-nhom-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s2-cang-nhom-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s2-cang-nhom-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-1.jpg', 'KINETIC Pansy S2 Càng Nhôm - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom' AND v.sku = 'kinetic-pansy-s2-cang-nhom-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-2.jpg', 'KINETIC Pansy S2 Càng Nhôm - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom' AND v.sku = 'kinetic-pansy-s2-cang-nhom-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-3.jpg', 'KINETIC Pansy S2 Càng Nhôm - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom' AND v.sku = 'kinetic-pansy-s2-cang-nhom-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-4.jpg', 'KINETIC Pansy S2 Càng Nhôm - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s2-cang-nhom' AND v.sku = 'kinetic-pansy-s2-cang-nhom-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s2-càng-nhôm-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy S3',
    'kinetic-pansy-s3',
    'KINETIC Pansy S3 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 100 km.',
    25500000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-S3', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 100, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s3'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s3-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s3'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s3-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s3'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s3-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s3'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s3-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s3'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-1.jpg', 'KINETIC Pansy S3 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s3' AND v.sku = 'kinetic-pansy-s3-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-2.jpg', 'KINETIC Pansy S3 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s3' AND v.sku = 'kinetic-pansy-s3-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-3.jpg', 'KINETIC Pansy S3 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s3' AND v.sku = 'kinetic-pansy-s3-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-4.jpg', 'KINETIC Pansy S3 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s3' AND v.sku = 'kinetic-pansy-s3-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s3-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy S4',
    'kinetic-pansy-s4',
    'KINETIC Pansy S4 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1000W, toc do toi da 45 km/h va tam hoat dong den 100 km.',
    18900000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-S4', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 100, 1000, 1350,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s4'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s4-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s4-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s4-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-s4-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-1.jpg', 'KINETIC Pansy S4 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s4' AND v.sku = 'kinetic-pansy-s4-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-2.jpg', 'KINETIC Pansy S4 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s4' AND v.sku = 'kinetic-pansy-s4-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-3.jpg', 'KINETIC Pansy S4 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s4' AND v.sku = 'kinetic-pansy-s4-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-4.jpg', 'KINETIC Pansy S4 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-s4' AND v.sku = 'kinetic-pansy-s4-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-s4-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Pansy Sq2',
    'kinetic-pansy-sq2',
    'KINETIC Pansy Sq2 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1400W, toc do toi da 50 km/h va tam hoat dong den 100 km.',
    23300000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'PANSY-SQ2', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  30, 60, 7.0,
  50, 100, 1400, 1890,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-sq2'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-sq2-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-sq2'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-sq2-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-sq2'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-sq2-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-sq2'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-pansy-sq2-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-pansy-sq2'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-1.jpg', 'KINETIC Pansy Sq2 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-sq2' AND v.sku = 'kinetic-pansy-sq2-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-2.jpg', 'KINETIC Pansy Sq2 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-sq2' AND v.sku = 'kinetic-pansy-sq2-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-3.jpg', 'KINETIC Pansy Sq2 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-sq2' AND v.sku = 'kinetic-pansy-sq2-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-4.jpg', 'KINETIC Pansy Sq2 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-pansy-sq2' AND v.sku = 'kinetic-pansy-sq2-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-pansy-sq2-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Rosa',
    'kinetic-rosa',
    'KINETIC Rosa la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 80 km.',
    25500000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'ROSA', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rosa'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rosa-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rosa'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rosa-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rosa'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rosa-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rosa'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rosa-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rosa'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-1.jpg', 'KINETIC Rosa - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rosa' AND v.sku = 'kinetic-rosa-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-2.jpg', 'KINETIC Rosa - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rosa' AND v.sku = 'kinetic-rosa-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-3.jpg', 'KINETIC Rosa - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rosa' AND v.sku = 'kinetic-rosa-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-4.jpg', 'KINETIC Rosa - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rosa' AND v.sku = 'kinetic-rosa-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rosa-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Rs 50CC',
    'kinetic-rs-50cc',
    'KINETIC Rs 50CC la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 800W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    17900000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'RS-50CC', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  30, 60, 7.0,
  50, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rs-50cc'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rs-50cc-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rs-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rs-50cc-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rs-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rs-50cc-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rs-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-rs-50cc-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-rs-50cc'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-1.jpg', 'KINETIC Rs 50CC - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rs-50cc' AND v.sku = 'kinetic-rs-50cc-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-2.jpg', 'KINETIC Rs 50CC - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rs-50cc' AND v.sku = 'kinetic-rs-50cc-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-3.jpg', 'KINETIC Rs 50CC - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rs-50cc' AND v.sku = 'kinetic-rs-50cc-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-4.jpg', 'KINETIC Rs 50CC - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-rs-50cc' AND v.sku = 'kinetic-rs-50cc-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-rs-50cc-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla Chic',
    'kinetic-tesla-chic',
    'KINETIC Tesla Chic la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1400W, toc do toi da 50 km/h va tam hoat dong den 90 km.',
    22600000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-CHIC', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  24, 60, 7.0,
  50, 90, 1400, 1890,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-1.jpg', 'KINETIC Tesla Chic - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic' AND v.sku = 'kinetic-tesla-chic-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-2.jpg', 'KINETIC Tesla Chic - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic' AND v.sku = 'kinetic-tesla-chic-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-3.jpg', 'KINETIC Tesla Chic - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic' AND v.sku = 'kinetic-tesla-chic-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-4.jpg', 'KINETIC Tesla Chic - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic' AND v.sku = 'kinetic-tesla-chic-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla Chic Q',
    'kinetic-tesla-chic-q',
    'KINETIC Tesla Chic Q la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1200W, toc do toi da 45 km/h va tam hoat dong den 100 km.',
    21500000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-CHIC-Q', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 100, 1200, 1620,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic-q'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-q-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic-q'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-q-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic-q'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-q-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic-q'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-chic-q-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-chic-q'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-1.jpg', 'KINETIC Tesla Chic Q - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic-q' AND v.sku = 'kinetic-tesla-chic-q-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-2.jpg', 'KINETIC Tesla Chic Q - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic-q' AND v.sku = 'kinetic-tesla-chic-q-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-3.jpg', 'KINETIC Tesla Chic Q - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic-q' AND v.sku = 'kinetic-tesla-chic-q-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-4.jpg', 'KINETIC Tesla Chic Q - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-chic-q' AND v.sku = 'kinetic-tesla-chic-q-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-chic-q-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla Dio',
    'kinetic-tesla-dio',
    'KINETIC Tesla Dio la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 100 km.',
    25900000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-DIO', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  22, 60, 7.0,
  45, 100, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 22, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 22, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-1.jpg', 'KINETIC Tesla Dio - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio' AND v.sku = 'kinetic-tesla-dio-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-2.jpg', 'KINETIC Tesla Dio - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio' AND v.sku = 'kinetic-tesla-dio-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-3.jpg', 'KINETIC Tesla Dio - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio' AND v.sku = 'kinetic-tesla-dio-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-4.jpg', 'KINETIC Tesla Dio - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio' AND v.sku = 'kinetic-tesla-dio-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla Dio E',
    'kinetic-tesla-dio-e',
    'KINETIC Tesla Dio E la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    25900000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-DIO-E', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  30, 60, 7.0,
  50, 80, 1600, 2160,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio-e'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-e-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-e-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-e-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-dio-e-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-dio-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-1.jpg', 'KINETIC Tesla Dio E - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio-e' AND v.sku = 'kinetic-tesla-dio-e-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-2.jpg', 'KINETIC Tesla Dio E - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio-e' AND v.sku = 'kinetic-tesla-dio-e-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-3.jpg', 'KINETIC Tesla Dio E - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio-e' AND v.sku = 'kinetic-tesla-dio-e-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-4.jpg', 'KINETIC Tesla Dio E - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-dio-e' AND v.sku = 'kinetic-tesla-dio-e-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-dio-e-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla E',
    'kinetic-tesla-e',
    'KINETIC Tesla E la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1400W, toc do toi da 45 km/h va tam hoat dong den 90 km.',
    20400000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-E', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  28, 60, 7.0,
  45, 90, 1400, 1890,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-e'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-e-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 28, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-e-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-e-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-e-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-e'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-1.jpg', 'KINETIC Tesla E - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-e' AND v.sku = 'kinetic-tesla-e-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-2.jpg', 'KINETIC Tesla E - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-e' AND v.sku = 'kinetic-tesla-e-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-3.jpg', 'KINETIC Tesla E - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-e' AND v.sku = 'kinetic-tesla-e-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-4.jpg', 'KINETIC Tesla E - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-e' AND v.sku = 'kinetic-tesla-e-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-e-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla Sd',
    'kinetic-tesla-sd',
    'KINETIC Tesla Sd la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 50 km/h va tam hoat dong den 110 km.',
    20400000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-SD', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  24, 60, 7.0,
  50, 110, 1600, 2160,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-sd'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-sd-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-sd'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-sd-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-sd'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-sd-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-sd'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-sd-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-sd'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-1.jpg', 'KINETIC Tesla Sd - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-sd' AND v.sku = 'kinetic-tesla-sd-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-2.jpg', 'KINETIC Tesla Sd - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-sd' AND v.sku = 'kinetic-tesla-sd-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-3.jpg', 'KINETIC Tesla Sd - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-sd' AND v.sku = 'kinetic-tesla-sd-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-4.jpg', 'KINETIC Tesla Sd - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-sd' AND v.sku = 'kinetic-tesla-sd-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-tesla-sd-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Xman Leo',
    'kinetic-xman-leo',
    'KINETIC Xman Leo la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1600W, toc do toi da 50 km/h va tam hoat dong den 110 km.',
    27000000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'XMAN-LEO', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  24, 60, 7.0,
  50, 110, 1600, 2160,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-xman-leo'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-xman-leo-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 24, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-xman-leo'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-xman-leo-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-xman-leo'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-xman-leo-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-xman-leo'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-xman-leo-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 24, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-xman-leo'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-1.jpg', 'KINETIC Xman Leo - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-xman-leo' AND v.sku = 'kinetic-xman-leo-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-2.jpg', 'KINETIC Xman Leo - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-xman-leo' AND v.sku = 'kinetic-xman-leo-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-3.jpg', 'KINETIC Xman Leo - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-xman-leo' AND v.sku = 'kinetic-xman-leo-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-4.jpg', 'KINETIC Xman Leo - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-xman-leo' AND v.sku = 'kinetic-xman-leo-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-máy-điện-dibao-xman-leo-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Tesla X',
    'kinetic-tesla-x',
    'KINETIC Tesla X la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LEAD_ACID, cong suat 800W, toc do toi da 50 km/h va tam hoat dong den 80 km.',
    19400000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'TESLA-X', 'KINETIC', 'E_SCOOTER', 'LEAD_ACID',
  30, 60, 7.0,
  50, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-x'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-x-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 30, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-x'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-x-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-x'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-x-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-x'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-tesla-x-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 30, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-tesla-x'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-1.jpg', 'KINETIC Tesla X - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-x' AND v.sku = 'kinetic-tesla-x-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-2.jpg', 'KINETIC Tesla X - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-x' AND v.sku = 'kinetic-tesla-x-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-3.jpg', 'KINETIC Tesla X - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-x' AND v.sku = 'kinetic-tesla-x-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-4.jpg', 'KINETIC Tesla X - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-tesla-x' AND v.sku = 'kinetic-tesla-x-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-tay-ga-50cc-dibao-tesla-x-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Butterfly',
    'kinetic-butterfly',
    'KINETIC Butterfly la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 55 km/h va tam hoat dong den 90 km.',
    23300000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'BUTTERFLY', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  32, 60, 7.0,
  55, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-butterfly'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-butterfly-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 32, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-butterfly'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-butterfly-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-butterfly'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-butterfly-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-butterfly'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-butterfly-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-butterfly'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-1.jpg', 'KINETIC Butterfly - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-butterfly' AND v.sku = 'kinetic-butterfly-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-2.jpg', 'KINETIC Butterfly - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-butterfly' AND v.sku = 'kinetic-butterfly-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-3.jpg', 'KINETIC Butterfly - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-butterfly' AND v.sku = 'kinetic-butterfly-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-4.jpg', 'KINETIC Butterfly - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-butterfly' AND v.sku = 'kinetic-butterfly-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-butterfly-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo S4',
    'kinetic-gogo-s4',
    'KINETIC Gogo S4 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 45 km/h va tam hoat dong den 90 km.',
    22600000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-S4', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  28, 60, 7.0,
  45, 90, 800, 1080,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s4'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s4-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 28, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s4-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s4-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s4-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 28, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s4'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-1.jpg', 'KINETIC Gogo S4 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s4' AND v.sku = 'kinetic-gogo-s4-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-2.jpg', 'KINETIC Gogo S4 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s4' AND v.sku = 'kinetic-gogo-s4-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-3.jpg', 'KINETIC Gogo S4 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s4' AND v.sku = 'kinetic-gogo-s4-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-4.jpg', 'KINETIC Gogo S4 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s4' AND v.sku = 'kinetic-gogo-s4-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s4-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Gogo S5',
    'kinetic-gogo-s5',
    'KINETIC Gogo S5 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1000W, toc do toi da 55 km/h va tam hoat dong den 90 km.',
    27000000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'GOGO-S5', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  32, 60, 7.0,
  55, 90, 1000, 1350,
  180, 92, 10,
  'DRUM', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s5'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s5-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 32, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s5'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s5-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s5'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s5-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s5'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-gogo-s5-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 32, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-gogo-s5'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-1.jpg', 'KINETIC Gogo S5 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s5' AND v.sku = 'kinetic-gogo-s5-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-2.jpg', 'KINETIC Gogo S5 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s5' AND v.sku = 'kinetic-gogo-s5-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-3.jpg', 'KINETIC Gogo S5 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s5' AND v.sku = 'kinetic-gogo-s5-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-4.jpg', 'KINETIC Gogo S5 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-gogo-s5' AND v.sku = 'kinetic-gogo-s5-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-gogo-s5-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC Jeek One',
    'kinetic-jeek-one',
    'KINETIC Jeek One la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 800W, toc do toi da 55 km/h va tam hoat dong den 80 km.',
    21100000,
    NULL,
    48,
    0,
    0,
    TRUE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'JEEK-ONE', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 80, 800, 1080,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-jeek-one'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-jeek-one-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-jeek-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-jeek-one-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-jeek-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-jeek-one-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-jeek-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-jeek-one-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-jeek-one'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-1.jpg', 'KINETIC Jeek One - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-jeek-one' AND v.sku = 'kinetic-jeek-one-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-2.jpg', 'KINETIC Jeek One - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-jeek-one' AND v.sku = 'kinetic-jeek-one-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-3.jpg', 'KINETIC Jeek One - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-jeek-one' AND v.sku = 'kinetic-jeek-one-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-4.jpg', 'KINETIC Jeek One - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-jeek-one' AND v.sku = 'kinetic-jeek-one-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-jeek-one-4.jpg'
  );

INSERT INTO ebike_product.products
  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)
VALUES
  (
    (SELECT id FROM ebike_product.categories WHERE slug = 'xe-dien'),
    'KINETIC R1',
    'kinetic-r1',
    'KINETIC R1 la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. Xe dung pin LITHIUM_ION, cong suat 1400W, toc do toi da 55 km/h va tam hoat dong den 100 km.',
    18900000,
    NULL,
    48,
    0,
    0,
    FALSE,
    TRUE
  )
ON CONFLICT (slug) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  discount_price = EXCLUDED.discount_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_featured = EXCLUDED.is_featured,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_specifications
  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,
   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,
   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,
   smart_features, warranty_months, dimensions_mm)
SELECT
  p.id, 'R1', 'KINETIC', 'E_SCOOTER', 'LITHIUM_ION',
  26, 60, 7.0,
  55, 100, 1400, 1890,
  180, 92, 10,
  'DISC', 'HUB_MOTOR', 'IPX5',
  'Alloy Steel', 'Front Telescopic', 'LED Digital Display',
  'Digital dashboard, anti-theft lock and USB charging port.', 24, '1850 x 700 x 1120'
FROM ebike_product.products p
WHERE p.slug = 'kinetic-r1'
ON CONFLICT (product_id) DO UPDATE SET
  model_code = EXCLUDED.model_code,
  brand = EXCLUDED.brand,
  vehicle_type = EXCLUDED.vehicle_type,
  battery_type = EXCLUDED.battery_type,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  battery_voltage_v = EXCLUDED.battery_voltage_v,
  charging_time_hours = EXCLUDED.charging_time_hours,
  max_speed_kmh = EXCLUDED.max_speed_kmh,
  max_range_km = EXCLUDED.max_range_km,
  motor_power_watts = EXCLUDED.motor_power_watts,
  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,
  max_load_kg = EXCLUDED.max_load_kg,
  product_weight_kg = EXCLUDED.product_weight_kg,
  wheel_size_inch = EXCLUDED.wheel_size_inch,
  brake_type = EXCLUDED.brake_type,
  drive_type = EXCLUDED.drive_type,
  water_resistance_rating = EXCLUDED.water_resistance_rating,
  frame_material = EXCLUDED.frame_material,
  suspension_type = EXCLUDED.suspension_type,
  display_type = EXCLUDED.display_type,
  smart_features = EXCLUDED.smart_features,
  warranty_months = EXCLUDED.warranty_months,
  dimensions_mm = EXCLUDED.dimensions_mm;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-r1-pearl-white', 'Pearl White', 'Pearl White',
  '#F5F5F4', 26, 0, 12,
  TRUE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-r1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-r1-matte-black', 'Matte Black', 'Matte Black',
  '#18181B', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-r1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-r1-ocean-blue', 'Ocean Blue', 'Ocean Blue',
  '#2563EB', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-r1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_variants
  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)
SELECT
  p.id, 'kinetic-r1-cherry-red', 'Cherry Red', 'Cherry Red',
  '#DC2626', 26, 0, 12,
  FALSE, TRUE
FROM ebike_product.products p
WHERE p.slug = 'kinetic-r1'
ON CONFLICT (sku) DO UPDATE SET
  variant_name = EXCLUDED.variant_name,
  color_name = EXCLUDED.color_name,
  color_hex = EXCLUDED.color_hex,
  battery_capacity_ah = EXCLUDED.battery_capacity_ah,
  additional_price = EXCLUDED.additional_price,
  stock_quantity = EXCLUDED.stock_quantity,
  is_default = EXCLUDED.is_default,
  is_active = EXCLUDED.is_active;

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-r1-1.jpg', 'KINETIC R1 - Pearl White', 0, TRUE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-r1' AND v.sku = 'kinetic-r1-pearl-white'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-r1-1.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-r1-2.jpg', 'KINETIC R1 - Matte Black', 1, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-r1' AND v.sku = 'kinetic-r1-matte-black'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-r1-2.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-r1-3.jpg', 'KINETIC R1 - Ocean Blue', 2, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-r1' AND v.sku = 'kinetic-r1-ocean-blue'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-r1-3.jpg'
  );

INSERT INTO ebike_product.product_images
  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)
SELECT
  p.id, v.id, 'backend/dataCawl/data/images/xe-điện-dibao-r1-4.jpg', 'KINETIC R1 - Cherry Red', 3, FALSE
FROM ebike_product.products p
JOIN ebike_product.product_variants v ON v.product_id = p.id
WHERE p.slug = 'kinetic-r1' AND v.sku = 'kinetic-r1-cherry-red'
  AND NOT EXISTS (
    SELECT 1 FROM ebike_product.product_images pi
    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = 'backend/dataCawl/data/images/xe-điện-dibao-r1-4.jpg'
  );

COMMIT;
