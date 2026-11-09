"""Generate the portable single-schema MariaDB setup from the project's source schema and catalog."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
DB = BACKEND / "database/schema-source"
BASELINE = (DB / "migration/V2_0_0__baseline.sql").read_text(encoding="utf-8")
OUT = ["-- KINETIC E-Bike Store: MariaDB single-database setup\n-- Source: backend/database/schema-source and backend/database/catalog/products.json\n-- Run this file in HeidiSQL. Images are referenced by relative local paths.\n",
       "CREATE DATABASE IF NOT EXISTS ebike_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nUSE ebike_db;\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n"]

def flat_names(sql: str) -> str:
    return re.sub(r"\bebike_(?:auth|product|order|chatbot|user|admin)\.", "", sql, flags=re.I)

# The baseline is the authoritative consolidated source schema. Preserve every table and FK.
tables = re.findall(r"CREATE TABLE IF NOT EXISTS\s+([\w.]+)\s*\((.*?)\);", BASELINE, re.I | re.S)
for name, body in tables:
    body = flat_names(body)
    body = re.sub(r"\bBIGSERIAL\b", "BIGINT AUTO_INCREMENT", body, flags=re.I)
    body = re.sub(r"\bSERIAL\b", "INT AUTO_INCREMENT", body, flags=re.I)
    body = re.sub(r"\bBOOLEAN\b", "TINYINT(1)", body, flags=re.I)
    body = re.sub(r"\bTEXT\b", "LONGTEXT", body, flags=re.I)
    body = re.sub(r"\bTIMESTAMP\b", "DATETIME", body, flags=re.I)
    # MariaDB does not support PostgreSQL partial indexes. They are recreated as full indexes below.
    OUT.append(f"\nDROP TABLE IF EXISTS `{name.split('.')[-1]}`;\nCREATE TABLE `{name.split('.')[-1]}` ({body}) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n")

# Add indexes from the baseline, dropping PostgreSQL-only IF NOT EXISTS and partial predicates.
indexes = re.findall(r"CREATE (UNIQUE )?INDEX IF NOT EXISTS\s+(\w+)\s+ON\s+([\w.]+)\s*\(([^;]+?)\)\s*(?:WHERE\s+[^;]+)?;", BASELINE, re.I | re.S)
for unique, name, table, cols in indexes:
    cols = flat_names(cols).strip()
    cols = re.sub(r"\bs3_key\b", "s3_key(191)", cols)
    OUT.append(f"CREATE {'UNIQUE ' if unique else ''}INDEX `{name}` ON `{table.split('.')[-1]}` ({cols});\n")

# MariaDB migration additions. Baseline already contains the cancellation fields and notification
# table definitions from intermediate migrations, so only genuinely additive latest tables/columns
# are carried forward. PostgreSQL-only index predicates and duplicate ALTER statements are omitted.
created_tables = {name.split(".")[-1].lower() for name, _ in tables}
known_indexes = {idx[1].lower() for idx in indexes}
for migration in sorted((DB / "migration").glob("V2_0_[1-9]*.sql")):
    content = migration.read_text(encoding="utf-8")
    content = flat_names(content)
    for name, body in re.findall(r"CREATE TABLE IF NOT EXISTS\s+([\w.]+)\s*\((.*?)\);", content, re.I | re.S):
        table = name.split(".")[-1]
        if table.lower() in created_tables:
            continue
        body = re.sub(r"\bBIGSERIAL\b", "BIGINT AUTO_INCREMENT", body, flags=re.I)
        body = re.sub(r"\bSERIAL\b", "INT AUTO_INCREMENT", body, flags=re.I)
        body = re.sub(r"\bUUID\b", "UUID", body, flags=re.I)
        body = re.sub(r"\bBOOLEAN\b", "TINYINT(1)", body, flags=re.I)
        body = re.sub(r"\bTIMESTAMP\b", "DATETIME", body, flags=re.I)
        body = re.sub(r"\bTEXT\b", "LONGTEXT", body, flags=re.I)
        OUT.append(f"\nDROP TABLE IF EXISTS `{table}`;\nCREATE TABLE `{table}` ({body}) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n")
        created_tables.add(table.lower())
    # Only the two latest columns are absent from the consolidated baseline.
    if migration.name.startswith("V2_0_3"):
        OUT.append("ALTER TABLE orders ADD COLUMN include_registration_service TINYINT(1) NOT NULL DEFAULT 0;\n")
    if migration.name.startswith("V2_0_5"):
        OUT.append("ALTER TABLE orders DROP COLUMN customer_identity_number;\n")
    if migration.name.startswith("V2_0_7"):
        OUT.append("ALTER TABLE users ADD COLUMN account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';\n")
        OUT.append("ALTER TABLE orders ADD COLUMN promotion_id BIGINT NULL, ADD CONSTRAINT fk_orders_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE SET NULL;\n")
        OUT.append("ALTER TABLE orders ADD COLUMN pickup_deadline_at DATETIME NULL;\n")
        OUT.append("ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'system', ALTER COLUMN language SET DEFAULT 'vi';\n")
        OUT.append("""
DROP TABLE IF EXISTS `support_requests`;
CREATE TABLE `support_requests` (
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
CREATE INDEX `idx_support_requests_status` ON `support_requests` (status, created_at);
DROP TABLE IF EXISTS `order_item_units`;
CREATE TABLE `order_item_units` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    frame_number VARCHAR(30) NOT NULL,
    engine_number VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_units_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
    CONSTRAINT uq_order_item_units_frame UNIQUE (frame_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
""")
    for unique, index_name, table_name, cols in re.findall(r"CREATE (UNIQUE )?INDEX IF NOT EXISTS\s+(\w+)\s+ON\s+([\w.]+)\s*\(([^;]+?)\)\s*(?:WHERE\s+[^;]+)?;", content, re.I | re.S):
        table = table_name.split(".")[-1]
        if index_name.lower() in known_indexes or table.lower() not in created_tables:
            continue
        OUT.append(f"CREATE {'UNIQUE ' if unique else ''}INDEX `{index_name}` ON `{table}` ({flat_names(cols).strip()});\n")
        known_indexes.add(index_name.lower())

def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"

# Useful role/permission identities and demo staff from the source baseline.
OUT += ["\nINSERT INTO roles(name,description) VALUES ('CUSTOMER','Customer account'),('MANAGER','Store manager account'),('ADMIN','System administrator account'),('OPERATOR','Operations staff account'),('SUPPORT','Customer support staff account'),('MAINTENANCE','Maintenance staff account');\n",
        "INSERT INTO users(username,email,password_hash,first_name,last_name,is_active,is_verified,email_verified_at) VALUES\n"
        "('manager.user','manager.user@example.com','$2a$10$tRiY3ac6Bnx5EHBkjP5ek.ofn3urim7dE36HV4uY3MpPWPficRNoy','Store','Manager',1,1,NOW()),\n"
        "('admin.user','admin.user@example.com','$2a$10$jLEp29fpNpvnAQDGZppNJeZpILS2AQXQiJunqbwJhjI3cBpDaF5Ba','System','Admin',1,1,NOW());\n",
        "INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.name=IF(u.username='admin.user','ADMIN','MANAGER') WHERE u.username IN ('admin.user','manager.user');\n"]

permission_block = re.search(r"INSERT INTO ebike_auth\.permissions\s*\(code, name, module_name, action_name, description\)\s*VALUES([\s\S]*?)\s*ON CONFLICT", BASELINE, re.I)
if permission_block:
    permission_rows = re.findall(r"\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\)", permission_block.group(1))
    OUT.append("INSERT INTO permissions(code,name,module_name,action_name,description) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in permission_rows) + ";\n")
role_permissions = re.search(r"INSERT INTO ebike_auth\.role_permissions\s*\(role_id, permission_id\)([\s\S]*?)\s*ON CONFLICT DO NOTHING;", BASELINE, re.I)
if role_permissions:
    query = flat_names(role_permissions.group(1)).strip().rstrip(";")
    OUT.append("INSERT IGNORE INTO role_permissions(role_id,permission_id) " + query + ";\n")

seed = json.loads((BACKEND / "database/catalog/products.json").read_text(encoding="utf-8"))
categories = {}
for item in seed["products"]:
    c = item["category"]
    categories[c["slug"]] = c
OUT.append("\nINSERT INTO categories(name,slug,description,is_active) VALUES\n" + ",\n".join(
    "(" + ",".join(q(x) for x in (c["name"], c["slug"], c["description"], True)) + ")" for c in categories.values()) + ";\n")
for item in seed["products"]:
    p, c, spec = item["product"], item["category"], item.get("specification", {})
    product_values = (p["name"], p["slug"], p["description"], p.get("price"), p.get("discount_price"),
                      p.get("stock_quantity"), p.get("rating"), p.get("review_count"), p.get("is_featured"), p.get("is_active"))
    OUT.append("\nINSERT INTO products(category_id,name,slug,description,price,discount_price,stock_quantity,rating,review_count,is_featured,is_active) VALUES ((SELECT id FROM categories WHERE slug=" + q(c["slug"]) + ")," + ",".join(q(value) for value in product_values) + ");\n")
    OUT.append("INSERT INTO product_specifications(product_id," + ",".join(spec.keys()) + ") SELECT id," + ",".join(q(v) for v in spec.values()) + " FROM products WHERE slug=" + q(p["slug"]) + ";\n")
    for v in item.get("variants", []):
        OUT.append("INSERT INTO product_variants(product_id,sku,variant_name,color_name,color_hex,battery_capacity_ah,additional_price,stock_quantity,is_default,is_active) SELECT id," + ",".join(q(v.get(k)) for k in ("sku","variant_name","color_name","color_hex","battery_capacity_ah","additional_price","stock_quantity","is_default","is_active")) + " FROM products WHERE slug=" + q(p["slug"]) + ";\n")
    for im in item.get("images", []):
        # Static resources are copied from the reviewed local image folders into Spring Boot.
        rel = im["image_url"]
        OUT.append("INSERT INTO product_images(product_id,variant_id,image_url,alt_text,sort_order,is_primary,storage_provider,status) SELECT p.id,v.id," + ",".join(q(x) for x in (rel,im.get("alt_text"),im.get("sort_order"),im.get("is_primary"),"LOCAL","ACTIVE")) + " FROM products p LEFT JOIN product_variants v ON v.sku=" + q(im.get("variant_sku")) + " WHERE p.slug=" + q(p["slug"]) + ";\n")

for row in [
    ("KINETIC Quan 1 - Nguyen Thai Hoc","Quan 1","187 Nguyen Thai Hoc, Phuong Pham Ngu Lao, Quan 1, TP. Ho Chi Minh","028 3920 1234"),
    ("KINETIC Quan 3 - Cach Mang Thang 8","Quan 3","420 Cach Mang Thang 8, Phuong 11, Quan 3, TP. Ho Chi Minh","028 3931 5678"),
    ("KINETIC Quan 7 - Nguyen Thi Thap","Quan 7","1021 Nguyen Thi Thap, Phuong Tan Phu, Quan 7, TP. Ho Chi Minh","028 3776 8899"),
    ("KINETIC Go Vap - Quang Trung","Go Vap","588 Quang Trung, Phuong 11, Go Vap, TP. Ho Chi Minh","028 3589 4567"),
    ("KINETIC Thu Duc - Vo Van Ngan","Thu Duc","216 Vo Van Ngan, Phuong Binh Tho, Thu Duc, TP. Ho Chi Minh","028 3722 7788"),
    ("KINETIC Tan Binh - Cong Hoa","Tan Binh","45 Cong Hoa, Phuong 4, Tan Binh, TP. Ho Chi Minh","028 3811 2233")]:
    OUT.append("INSERT INTO showrooms(name,district,address,phone,opening_hours,is_active) VALUES (" + ",".join(q(v) for v in (*row,"08:00 - 21:00",True)) + ");\n")
OUT.append("""
INSERT INTO pricing_rules(code,name,description,amount,unit,status,effective_from) VALUES
('REGISTRATION_SERVICE','Phí hỗ trợ đăng ký biển số tại cửa hàng','Nhân viên hỗ trợ chủ xe tự kê khai trên VNeID khi nhận xe tại cửa hàng. Không gồm lệ phí nộp Nhà nước.',500000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_SAME_CITY','Phí giao xe tận nhà cùng tỉnh','Giao xe đến địa chỉ trong tỉnh, thành phố có showroom.',200000,'VND/xe','ACTIVE',NOW()),
('HOME_DELIVERY_OTHER_PROVINCE','Phí giao xe tận nhà khác tỉnh','Giao xe đến địa chỉ ở tỉnh, thành phố không có showroom.',800000,'VND/xe','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_CENTRAL','Tỷ lệ lệ phí trước bạ (thành phố lớn)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',5,'%','ACTIVE',NOW()),
('REGISTRATION_TAX_RATE_OTHER','Tỷ lệ lệ phí trước bạ (khu vực khác)','Chỉ hiển thị ước tính tham khảo, không cộng vào đơn.',2,'%','ACTIVE',NOW());
INSERT INTO promotions(code,campaign_name,discount_type,discount_value,max_discount_amount,usage_count,usage_limit,status,starts_at,ends_at) VALUES
('KINETIC20','Chào hè xanh','PERCENTAGE',20,25000,326,800,'LIVE','2026-06-01 00:00:00','2026-06-30 23:59:59'),
('STUDENT15','Ưu đãi sinh viên','FIXED_AMOUNT',15000,NULL,1020,2000,'LIVE','2026-05-15 00:00:00','2026-07-15 23:59:59'),
('WEEKEND','Ưu đãi cuối tuần','PERCENTAGE',10,NULL,0,500,'WAITING','2026-06-08 00:00:00','2026-06-30 23:59:59');
INSERT INTO admin_audit_logs(actor,action,target,ip_address) VALUES ('System','Khởi tạo dữ liệu mẫu','pricing rules và promotions','127.0.0.1');
""")
OUT.append("\nSET FOREIGN_KEY_CHECKS=1;\n")
(BACKEND / "database/setup_ebike_database.sql").write_text("".join(OUT), encoding="utf-8-sig")
print(f"generated {BACKEND / 'database/setup_ebike_database.sql'} with {len(tables)} baseline tables and {len(seed['products'])} products")
