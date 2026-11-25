-- The assignment brief forbids database-level validation (CHECK constraints, functions, stored procedures):
-- every rule below is enforced by the application (enums, request validation and the rules classes).
-- Run this once on a database created by an earlier version of setup_ebike_database.sql. Safe to run twice.
-- The two checks written inline on a column are part of the column definition, so the column is redefined without them.
ALTER TABLE `reviews` MODIFY COLUMN `rating` INT NOT NULL;
ALTER TABLE `product_images` DROP CONSTRAINT IF EXISTS `chk_product_images_storage_provider`;
ALTER TABLE `product_images` DROP CONSTRAINT IF EXISTS `chk_product_images_status`;
ALTER TABLE `user_addresses` MODIFY COLUMN `address_type` VARCHAR(20) NULL;
ALTER TABLE `pricing_rules` DROP CONSTRAINT IF EXISTS `chk_pricing_rules_status`;
ALTER TABLE `promotions` DROP CONSTRAINT IF EXISTS `chk_promotions_status`;
ALTER TABLE `promotions` DROP CONSTRAINT IF EXISTS `chk_promotions_discount_type`;
ALTER TABLE `support_requests` DROP CONSTRAINT IF EXISTS `chk_support_requests_status`;
ALTER TABLE `stock_receipt_lines` DROP CONSTRAINT IF EXISTS `chk_stock_receipt_lines_quantity`;
ALTER TABLE `stock_movements` DROP CONSTRAINT IF EXISTS `chk_stock_movements_reason`;
ALTER TABLE `stock_movements` DROP CONSTRAINT IF EXISTS `chk_stock_movements_change`;
ALTER TABLE `warranty_claims` DROP CONSTRAINT IF EXISTS `chk_warranty_claims_status`;
ALTER TABLE `test_ride_bookings` DROP CONSTRAINT IF EXISTS `chk_test_ride_status`;
ALTER TABLE `test_ride_bookings` DROP CONSTRAINT IF EXISTS `chk_test_ride_cancel_source`;
ALTER TABLE `installment_applications` DROP CONSTRAINT IF EXISTS `chk_installment_status`;
ALTER TABLE `installment_applications` DROP CONSTRAINT IF EXISTS `chk_installment_percent`;
ALTER TABLE `installment_applications` DROP CONSTRAINT IF EXISTS `chk_installment_term`;
