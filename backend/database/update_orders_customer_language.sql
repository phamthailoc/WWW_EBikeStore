-- V27: the language of the page an order was placed from ("vi" or "en"). A guest has no profile, so the hand-over email and the PDFs follow this.
-- Older orders keep NULL and are answered in Vietnamese. Changes the table structure. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_orders_customer_language.sql

ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_language VARCHAR(5) NULL;
