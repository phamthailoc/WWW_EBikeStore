#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import hashlib
import json
import os
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote


ROOT = Path(__file__).resolve().parent
IMAGES_DIR = ROOT / "data" / "images"
JSON_SOURCE = ROOT / "data" / "products_variants.json"
JSON_OUTPUT = ROOT / "data" / "products_db_ready_clean.json"
SQL_OUTPUT = ROOT / "output" / "products_db_ready_clean.sql"
IMAGE_BASE_URL = os.getenv("PRODUCT_IMAGE_BASE_URL", "").strip().rstrip("/")
LOCAL_IMAGE_PREFIX = "backend/dataCawl/data/images"


COLOR_PALETTE = [
    ("Pearl White", "#F5F5F4"),
    ("Matte Black", "#18181B"),
    ("Ocean Blue", "#2563EB"),
    ("Cherry Red", "#DC2626"),
    ("Mint Green", "#10B981"),
    ("Graphite Gray", "#4B5563"),
    ("Champagne Gold", "#D4A373"),
]


CATEGORY_MAP = {
    "xe-dien": {
        "name": "Xe điện",
        "slug": "xe-dien",
        "description": "Dòng xe điện đô thị với thiết kế gọn, vận hành êm và phù hợp di chuyển hằng ngày.",
        "vehicle_type": "E_SCOOTER",
    },
    "xe-dap-dien": {
        "name": "Xe đạp điện",
        "slug": "xe-dap-dien",
        "description": "Dòng xe đạp điện trợ lực, có bàn đạp, linh hoạt cho học sinh sinh viên và di chuyển trong phố.",
        "vehicle_type": "E_BIKE",
    },
}


@dataclass(frozen=True)
class ImageEntry:
    index: int
    filename: str


def normalize_ascii(text: str) -> str:
    text = text.replace("đ", "d").replace("Đ", "D")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(char for char in text if not unicodedata.combining(char))
    return text


def slugify(text: str) -> str:
    text = normalize_ascii(text).lower()
    text = re.sub(r"[^a-z0-9\s-]", "", text)
    text = re.sub(r"[-\s]+", "-", text)
    return text.strip("-")


def title_case_token(token: str) -> str:
    if token.upper() == "50CC":
        return "50CC"
    if re.fullmatch(r"[a-z]+\d+", token, re.IGNORECASE):
        prefix = re.match(r"[a-z]+", token, re.IGNORECASE).group(0)
        suffix = token[len(prefix):]
        return prefix.capitalize() + suffix.upper()
    if token.isdigit():
        return token
    return token.capitalize()


def choose_category(group_key_ascii: str) -> str:
    if "co-ban-dap" in group_key_ascii:
        return "xe-dap-dien"
    return "xe-dien"


def extract_model_name(group_key_ascii: str) -> str:
    tokens = group_key_ascii.split("-")
    if "dibao" in tokens:
        dibao_index = tokens.index("dibao")
        model_tokens = tokens[dibao_index + 1 :]
    else:
        model_tokens = tokens[-3:]

    compact = " ".join(title_case_token(token) for token in model_tokens if token)
    compact = compact.replace("Cang Nhom", "Càng Nhôm")
    compact = compact.replace("Ban Nang Cap", "Bản Nâng Cấp")
    return compact.strip() or "Urban"


def score_from_key(key: str) -> int:
    digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
    return int(digest[:8], 16)


def infer_price(category_slug: str, key: str) -> int:
    score = score_from_key(key)
    if category_slug == "xe-dap-dien":
        return 15_900_000 + (score % 5) * 900_000

    base = 18_900_000 + (score % 7) * 1_100_000
    lowered = key.lower()
    if "50cc" in lowered:
        base = 17_900_000 + (score % 5) * 800_000
    if any(keyword in lowered for keyword in ("tesla", "xman", "creer", "gogo")):
        base += 1_500_000
    return base


def infer_specification(category_slug: str, key: str, model_name: str) -> dict:
    score = score_from_key(key)
    is_bike = category_slug == "xe-dap-dien"
    has_50cc = "50cc" in key.lower()

    battery_type = "LITHIUM_ION" if is_bike or not has_50cc else "LEAD_ACID"
    battery_capacity = 20 + (score % 5) * 2 if is_bike else 22 + (score % 6) * 2
    voltage = 48 if is_bike else 60
    charging_time = 5.5 if is_bike else 7.0
    max_speed = 35 + (score % 3) * 5 if is_bike else 45 + (score % 3) * 5
    max_range = 70 + (score % 4) * 10 if is_bike else 80 + (score % 4) * 10
    motor_power = 350 + (score % 4) * 100 if is_bike else 800 + (score % 5) * 200
    brake_type = "DISC" if (score % 2 == 0 or is_bike) else "DRUM"
    drive_type = "CHAIN" if is_bike else "HUB_MOTOR"

    return {
        "model_code": slugify(model_name).upper().replace("-", "-"),
        "brand": "KINETIC",
        "vehicle_type": CATEGORY_MAP[category_slug]["vehicle_type"],
        "battery_type": battery_type,
        "battery_capacity_ah": battery_capacity,
        "battery_voltage_v": voltage,
        "charging_time_hours": charging_time,
        "max_speed_kmh": max_speed,
        "max_range_km": max_range,
        "motor_power_watts": motor_power,
        "peak_motor_power_watts": int(motor_power * 1.35),
        "max_load_kg": 150 if is_bike else 180,
        "product_weight_kg": 68 if is_bike else 92,
        "wheel_size_inch": 14 if is_bike else 10,
        "brake_type": brake_type,
        "drive_type": drive_type,
        "water_resistance_rating": "IPX5",
        "frame_material": "Alloy Steel",
        "suspension_type": "Front Telescopic",
        "display_type": "LED Digital Display",
        "smart_features": "Digital dashboard, anti-theft lock and USB charging port.",
        "warranty_months": 24,
        "dimensions_mm": "1850 x 700 x 1120",
    }


def build_description(category_slug: str, model_name: str, specification: dict) -> str:
    if category_slug == "xe-dap-dien":
        return (
            f"KINETIC {model_name} la mau xe dap dien co ban dap, gon nhe va de dieu khien trong do thi. "
            f"Xe su dung dong co {specification['motor_power_watts']}W, van toc toi da {specification['max_speed_kmh']} km/h "
            f"va quang duong di duoc toi da {specification['max_range_km']} km cho moi lan sac."
        )

    return (
        f"KINETIC {model_name} la dong xe dien thiet ke cho di chuyen hang ngay, van hanh em va de tiep can. "
        f"Xe dung pin {specification['battery_type']}, cong suat {specification['motor_power_watts']}W, "
        f"toc do toi da {specification['max_speed_kmh']} km/h va tam hoat dong den {specification['max_range_km']} km."
    )


def load_image_groups() -> dict[str, list[ImageEntry]]:
    grouped: dict[str, dict[int, list[str]]] = {}

    for path in IMAGES_DIR.glob("*.jpg"):
        stem = path.stem
        stem = re.sub(r"-\d{10,}$", "", stem)
        match = re.match(r"(.+)-(\d+)(?: copy)?$", stem)
        if not match:
            continue

        group_key = match.group(1)
        image_index = int(match.group(2))
        if image_index == 0:
            continue

        grouped.setdefault(group_key, {}).setdefault(image_index, []).append(path.name)

    resolved: dict[str, list[ImageEntry]] = {}
    for group_key, image_map in grouped.items():
        chosen_images: list[ImageEntry] = []
        for image_index in sorted(image_map):
            candidates = sorted(
                image_map[image_index],
                key=lambda name: (
                    " copy" in name,
                    bool(re.search(r"-\d{10,}\.jpg$", name)),
                    len(name),
                    name,
                ),
            )
            chosen_images.append(ImageEntry(index=image_index, filename=candidates[0]))
        resolved[group_key] = chosen_images

    return resolved


def escape_sql(value: str) -> str:
    return value.replace("'", "''")


def build_image_url(filename: str) -> str:
    if not IMAGE_BASE_URL:
        return f"{LOCAL_IMAGE_PREFIX}/{filename}"

    return f"{IMAGE_BASE_URL}/{quote(filename)}"


def build_db_ready_payload() -> list[dict]:
    image_groups = load_image_groups()
    payload: list[dict] = []

    for group_key in sorted(image_groups):
        group_key_ascii = slugify(group_key)
        category_slug = choose_category(group_key_ascii)
        model_name = extract_model_name(group_key_ascii)
        product_name = f"KINETIC {model_name}"
        product_slug = slugify(product_name)
        specification = infer_specification(category_slug, group_key_ascii, model_name)
        price = infer_price(category_slug, group_key_ascii)
        images = image_groups[group_key]

        variants = []
        image_rows = []
        for position, image_entry in enumerate(images):
            color_name, color_hex = COLOR_PALETTE[(image_entry.index - 1) % len(COLOR_PALETTE)]
            sku = f"{product_slug}-{slugify(color_name)}"
            variants.append(
                {
                    "sku": sku,
                    "variant_name": color_name,
                    "color_name": color_name,
                    "color_hex": color_hex,
                    "battery_capacity_ah": specification["battery_capacity_ah"],
                    "additional_price": 0,
                    "stock_quantity": 12,
                    "is_default": position == 0,
                    "is_active": True,
                }
            )
            image_rows.append(
                {
                    "variant_sku": sku,
                    "image_url": build_image_url(image_entry.filename),
                    "alt_text": f"{product_name} - {color_name}",
                    "sort_order": position,
                    "is_primary": position == 0,
                }
            )

        payload.append(
            {
                "source_group": group_key,
                "category": CATEGORY_MAP[category_slug],
                "product": {
                    "name": product_name,
                    "slug": product_slug,
                    "description": build_description(category_slug, model_name, specification),
                    "price": price,
                    "discount_price": None,
                    "stock_quantity": len(variants) * 12,
                    "rating": 0,
                    "review_count": 0,
                    "is_featured": score_from_key(group_key_ascii) % 5 == 0,
                    "is_active": True,
                },
                "specification": specification,
                "variants": variants,
                "images": image_rows,
            }
        )

    return payload


def write_json(payload: list[dict]) -> None:
    JSON_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    data = {
        "metadata": {
            "source": "local image folder",
            "rules": {
                "ignore_icon_images": "skip all images ending with index 0",
                "map_pedal_models_to": "xe-dap-dien",
                "map_motorbike_models_to": "xe-dien",
            },
            "total_products": len(payload),
        },
        "products": payload,
    }
    JSON_OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_sql(payload: list[dict]) -> None:
    SQL_OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    sql_lines = [
        "-- =====================================================",
        "-- Clean product seed generated from local images",
        "-- Rules:",
        "--   - Ignore image variants ending with index 0",
        "--   - Models containing 'co-ban-dap' are mapped to xe-dap-dien / E_BIKE",
        "--   - Other xe may images are mapped to xe-dien / E_SCOOTER",
        "-- =====================================================",
        "",
        "\\encoding UTF8",
        "SET client_encoding TO 'UTF8';",
        "",
        "BEGIN;",
        "",
    ]

    used_categories = {item["category"]["slug"]: item["category"] for item in payload}
    for category in used_categories.values():
        sql_lines.extend(
            [
                "INSERT INTO ebike_product.categories (name, slug, description, is_active)",
                f"VALUES ('{escape_sql(category['name'])}', '{escape_sql(category['slug'])}', '{escape_sql(category['description'])}', TRUE)",
                "ON CONFLICT (slug) DO UPDATE SET",
                "  name = EXCLUDED.name,",
                "  description = EXCLUDED.description,",
                "  is_active = TRUE;",
                "",
            ]
        )

    for item in payload:
        product = item["product"]
        category = item["category"]
        specification = item["specification"]

        discount_price = "NULL" if product["discount_price"] is None else str(product["discount_price"])
        sql_lines.extend(
            [
                "INSERT INTO ebike_product.products",
                "  (category_id, name, slug, description, price, discount_price, stock_quantity, rating, review_count, is_featured, is_active)",
                "VALUES",
                "  (",
                f"    (SELECT id FROM ebike_product.categories WHERE slug = '{escape_sql(category['slug'])}'),",
                f"    '{escape_sql(product['name'])}',",
                f"    '{escape_sql(product['slug'])}',",
                f"    '{escape_sql(product['description'])}',",
                f"    {product['price']},",
                f"    {discount_price},",
                f"    {product['stock_quantity']},",
                f"    {product['rating']},",
                f"    {product['review_count']},",
                f"    {'TRUE' if product['is_featured'] else 'FALSE'},",
                f"    {'TRUE' if product['is_active'] else 'FALSE'}",
                "  )",
                "ON CONFLICT (slug) DO UPDATE SET",
                "  category_id = EXCLUDED.category_id,",
                "  name = EXCLUDED.name,",
                "  description = EXCLUDED.description,",
                "  price = EXCLUDED.price,",
                "  discount_price = EXCLUDED.discount_price,",
                "  stock_quantity = EXCLUDED.stock_quantity,",
                "  is_featured = EXCLUDED.is_featured,",
                "  is_active = EXCLUDED.is_active;",
                "",
                "INSERT INTO ebike_product.product_specifications",
                "  (product_id, model_code, brand, vehicle_type, battery_type, battery_capacity_ah, battery_voltage_v, charging_time_hours,",
                "   max_speed_kmh, max_range_km, motor_power_watts, peak_motor_power_watts, max_load_kg, product_weight_kg,",
                "   wheel_size_inch, brake_type, drive_type, water_resistance_rating, frame_material, suspension_type, display_type,",
                "   smart_features, warranty_months, dimensions_mm)",
                "SELECT",
                f"  p.id, '{escape_sql(specification['model_code'])}', '{escape_sql(specification['brand'])}', '{specification['vehicle_type']}', '{specification['battery_type']}',",
                f"  {specification['battery_capacity_ah']}, {specification['battery_voltage_v']}, {specification['charging_time_hours']},",
                f"  {specification['max_speed_kmh']}, {specification['max_range_km']}, {specification['motor_power_watts']}, {specification['peak_motor_power_watts']},",
                f"  {specification['max_load_kg']}, {specification['product_weight_kg']}, {specification['wheel_size_inch']},",
                f"  '{specification['brake_type']}', '{specification['drive_type']}', '{escape_sql(specification['water_resistance_rating'])}',",
                f"  '{escape_sql(specification['frame_material'])}', '{escape_sql(specification['suspension_type'])}', '{escape_sql(specification['display_type'])}',",
                f"  '{escape_sql(specification['smart_features'])}', {specification['warranty_months']}, '{escape_sql(specification['dimensions_mm'])}'",
                "FROM ebike_product.products p",
                f"WHERE p.slug = '{escape_sql(product['slug'])}'",
                "ON CONFLICT (product_id) DO UPDATE SET",
                "  model_code = EXCLUDED.model_code,",
                "  brand = EXCLUDED.brand,",
                "  vehicle_type = EXCLUDED.vehicle_type,",
                "  battery_type = EXCLUDED.battery_type,",
                "  battery_capacity_ah = EXCLUDED.battery_capacity_ah,",
                "  battery_voltage_v = EXCLUDED.battery_voltage_v,",
                "  charging_time_hours = EXCLUDED.charging_time_hours,",
                "  max_speed_kmh = EXCLUDED.max_speed_kmh,",
                "  max_range_km = EXCLUDED.max_range_km,",
                "  motor_power_watts = EXCLUDED.motor_power_watts,",
                "  peak_motor_power_watts = EXCLUDED.peak_motor_power_watts,",
                "  max_load_kg = EXCLUDED.max_load_kg,",
                "  product_weight_kg = EXCLUDED.product_weight_kg,",
                "  wheel_size_inch = EXCLUDED.wheel_size_inch,",
                "  brake_type = EXCLUDED.brake_type,",
                "  drive_type = EXCLUDED.drive_type,",
                "  water_resistance_rating = EXCLUDED.water_resistance_rating,",
                "  frame_material = EXCLUDED.frame_material,",
                "  suspension_type = EXCLUDED.suspension_type,",
                "  display_type = EXCLUDED.display_type,",
                "  smart_features = EXCLUDED.smart_features,",
                "  warranty_months = EXCLUDED.warranty_months,",
                "  dimensions_mm = EXCLUDED.dimensions_mm;",
                "",
            ]
        )

        for variant in item["variants"]:
            sql_lines.extend(
                [
                    "INSERT INTO ebike_product.product_variants",
                    "  (product_id, sku, variant_name, color_name, color_hex, battery_capacity_ah, additional_price, stock_quantity, is_default, is_active)",
                    "SELECT",
                    f"  p.id, '{escape_sql(variant['sku'])}', '{escape_sql(variant['variant_name'])}', '{escape_sql(variant['color_name'])}',",
                    f"  '{variant['color_hex']}', {variant['battery_capacity_ah']}, {variant['additional_price']}, {variant['stock_quantity']},",
                    f"  {'TRUE' if variant['is_default'] else 'FALSE'}, {'TRUE' if variant['is_active'] else 'FALSE'}",
                    "FROM ebike_product.products p",
                    f"WHERE p.slug = '{escape_sql(product['slug'])}'",
                    "ON CONFLICT (sku) DO UPDATE SET",
                    "  variant_name = EXCLUDED.variant_name,",
                    "  color_name = EXCLUDED.color_name,",
                    "  color_hex = EXCLUDED.color_hex,",
                    "  battery_capacity_ah = EXCLUDED.battery_capacity_ah,",
                    "  additional_price = EXCLUDED.additional_price,",
                    "  stock_quantity = EXCLUDED.stock_quantity,",
                    "  is_default = EXCLUDED.is_default,",
                    "  is_active = EXCLUDED.is_active;",
                    "",
                ]
            )

        for image in item["images"]:
            sql_lines.extend(
                [
                    "INSERT INTO ebike_product.product_images",
                    "  (product_id, variant_id, image_url, alt_text, sort_order, is_primary)",
                    "SELECT",
                    f"  p.id, v.id, '{escape_sql(image['image_url'])}', '{escape_sql(image['alt_text'])}', {image['sort_order']}, {'TRUE' if image['is_primary'] else 'FALSE'}",
                    "FROM ebike_product.products p",
                    "JOIN ebike_product.product_variants v ON v.product_id = p.id",
                    f"WHERE p.slug = '{escape_sql(product['slug'])}' AND v.sku = '{escape_sql(image['variant_sku'])}'",
                    "  AND NOT EXISTS (",
                    "    SELECT 1 FROM ebike_product.product_images pi",
                    "    WHERE pi.product_id = p.id AND pi.variant_id = v.id AND pi.image_url = '{0}'".format(escape_sql(image["image_url"])),
                    "  );",
                    "",
                ]
            )

    sql_lines.extend(["COMMIT;", ""])
    SQL_OUTPUT.write_text("\n".join(sql_lines), encoding="utf-8")


def main() -> None:
    payload = build_db_ready_payload()
    write_json(payload)
    write_sql(payload)
    print(f"Generated {len(payload)} products")
    print(f"JSON: {JSON_OUTPUT}")
    print(f"SQL : {SQL_OUTPUT}")


if __name__ == "__main__":
    main()
