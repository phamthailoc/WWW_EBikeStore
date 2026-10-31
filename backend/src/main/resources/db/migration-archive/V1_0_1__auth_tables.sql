-- =====================================================
-- Auth Module Tables
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_auth;

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
) WITH (fillfactor=70);

CREATE INDEX idx_users_email ON ebike_auth.users(email);
CREATE INDEX idx_users_username ON ebike_auth.users(username);
CREATE INDEX idx_users_active ON ebike_auth.users(is_active);

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

CREATE INDEX idx_permissions_code ON ebike_auth.permissions(code);
CREATE INDEX idx_permissions_module_action ON ebike_auth.permissions(module_name, action_name);

CREATE TABLE IF NOT EXISTS ebike_auth.authentication_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES ebike_auth.users(id),
    login_time TIMESTAMP,
    logout_time TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT
);
