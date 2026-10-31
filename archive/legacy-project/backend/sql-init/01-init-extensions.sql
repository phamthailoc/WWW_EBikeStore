-- =====================================================
-- Initialize PostgreSQL Extensions
-- =====================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";
CREATE EXTENSION IF NOT EXISTS "hstore";

-- =====================================================
-- Initialize Schemas
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_auth;
CREATE SCHEMA IF NOT EXISTS ebike_product;
CREATE SCHEMA IF NOT EXISTS ebike_order;
CREATE SCHEMA IF NOT EXISTS ebike_chatbot;
CREATE SCHEMA IF NOT EXISTS ebike_user;

-- =====================================================
-- Set Search Path
-- =====================================================

ALTER USER ebike_user SET search_path TO public, ebike_auth, ebike_product, ebike_order, ebike_chatbot, ebike_user;