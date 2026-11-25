-- Test-only catalogue rows for the Selenium tests that need a product the normal seed does not have.
-- Apply to a LOCAL test database only (it adds products to the shop window):
--   * a battery pack and an accessory in stock       -> OrderDocumentsSeleniumTest, SavingsCalculatorSeleniumTest (no savings box, no warranty card)
--   * an e-bike whose specification has no battery data -> SavingsCalculatorSeleniumTest (says it cannot be estimated)
-- None of them has colours (a variant is shown only with a picture, and an order must name a colour whenever one exists).
-- Safe to run twice: every row is keyed by its slug or sku.

INSERT INTO products (category_id, name, slug, description, price, stock_quantity, is_featured, is_active)
SELECT 1, 'Pin rời Kinetic 48V 22Ah', 'selenium-pin-roi-48v-22ah', 'Pin thay thế dùng để kiểm thử.', 6500000, 20, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE slug = 'selenium-pin-roi-48v-22ah');
INSERT INTO products (category_id, name, slug, description, price, stock_quantity, is_featured, is_active)
SELECT 1, 'Mũ bảo hiểm Kinetic', 'selenium-mu-bao-hiem', 'Phụ kiện dùng để kiểm thử.', 450000, 40, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE slug = 'selenium-mu-bao-hiem');
INSERT INTO products (category_id, name, slug, description, price, stock_quantity, is_featured, is_active)
SELECT 1, 'Xe thử chưa có thông số pin', 'selenium-xe-thieu-thong-so-pin', 'Xe dùng để kiểm thử phần tính tiết kiệm khi chưa đủ thông số.', 9900000, 10, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE slug = 'selenium-xe-thieu-thong-so-pin');

INSERT INTO product_specifications (product_id, brand, vehicle_type)
SELECT id, 'Kinetic', 'BATTERY' FROM products WHERE slug = 'selenium-pin-roi-48v-22ah'
  AND NOT EXISTS (SELECT 1 FROM product_specifications s WHERE s.product_id = products.id);
INSERT INTO product_specifications (product_id, brand, vehicle_type)
SELECT id, 'Kinetic', 'ACCESSORY' FROM products WHERE slug = 'selenium-mu-bao-hiem'
  AND NOT EXISTS (SELECT 1 FROM product_specifications s WHERE s.product_id = products.id);
INSERT INTO product_specifications (product_id, brand, vehicle_type)
SELECT id, 'Kinetic', 'E_BIKE' FROM products WHERE slug = 'selenium-xe-thieu-thong-so-pin'
  AND NOT EXISTS (SELECT 1 FROM product_specifications s WHERE s.product_id = products.id);
