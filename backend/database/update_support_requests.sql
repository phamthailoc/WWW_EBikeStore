-- Task 14: support requests sent from the support page. Adds a table; changes no existing table.
-- Safe to run more than once. Apply with: mysql -u root -p ebike_db < update_support_requests.sql

CREATE TABLE IF NOT EXISTS `support_requests` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    customer_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    subject VARCHAR(150) NOT NULL,
    message LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    staff_note LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_support_requests_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS `idx_support_requests_status` ON `support_requests` (status, created_at);
