-- V24: simulated instalment payment. Adds one table and three pricing rules; changes no existing table.
-- One row per instalment application, tied to the order it was made with (a customer applies while placing the order). The row keeps a snapshot of the plan
-- (down payment, term, rate, monthly payment) so a later change of the admin's rates never changes an application that was already made, and only the
-- last four digits of the ID number (the rest is masked before it is stored); the income and the masked number are erased with the account.
-- No real finance company is connected: the staff approve or reject, and the down payment is paid through VNPay or at the store.
-- Needs the tables orders and users (and pricing_rules for the rates). Safe to run more than once.
-- Apply with: mysql -u root -p --default-character-set=utf8mb4 ebike_db < update_installments.sql

CREATE TABLE IF NOT EXISTS `installment_applications` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    id_number_masked VARCHAR(20) NULL,
    monthly_income DECIMAL(14, 2) NULL,
    order_total DECIMAL(12, 2) NOT NULL,
    down_payment_percent INT NOT NULL,
    down_payment_amount DECIMAL(12, 2) NOT NULL,
    financed_amount DECIMAL(12, 2) NOT NULL,
    term_months INT NOT NULL,
    annual_rate_percent DECIMAL(5, 2) NOT NULL,
    monthly_payment DECIMAL(12, 2) NOT NULL,
    total_payable DECIMAL(14, 2) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(1000) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_installment_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_installment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_installment_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_installment_order UNIQUE (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX IF NOT EXISTS `idx_installment_status_created` ON `installment_applications` (status, created_at);
CREATE INDEX IF NOT EXISTS `idx_installment_user_status` ON `installment_applications` (user_id, status);

-- Rates an admin has already edited are kept (ON DUPLICATE KEY does not touch amount/status). Set a rule INACTIVE to stop offering that term.
INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('INSTALLMENT_RATE_6','Lãi suất trả góp 6 tháng (mô phỏng)','Lãi suất phẳng theo năm, tính trên số tiền còn lại sau khi trả trước. Đặt trạng thái INACTIVE để ngừng nhận kỳ hạn này.',8,'%/năm','ACTIVE',NOW()),
('INSTALLMENT_RATE_9','Lãi suất trả góp 9 tháng (mô phỏng)','Lãi suất phẳng theo năm, tính trên số tiền còn lại sau khi trả trước. Đặt trạng thái INACTIVE để ngừng nhận kỳ hạn này.',9,'%/năm','ACTIVE',NOW()),
('INSTALLMENT_RATE_12','Lãi suất trả góp 12 tháng (mô phỏng)','Lãi suất phẳng theo năm, tính trên số tiền còn lại sau khi trả trước. Đặt trạng thái INACTIVE để ngừng nhận kỳ hạn này.',10,'%/năm','ACTIVE',NOW())
ON DUPLICATE KEY UPDATE code = code;
