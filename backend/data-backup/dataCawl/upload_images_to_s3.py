#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote


ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parent.parent
DEFAULT_JSON_INPUT = ROOT / "data" / "products_db_ready_clean.json"
DEFAULT_JSON_OUTPUT = ROOT / "data" / "products_db_ready_clean.s3.json"
DEFAULT_SQL_INPUT = ROOT / "output" / "products_db_ready_clean.sql"
DEFAULT_SQL_OUTPUT = ROOT / "output" / "products_db_ready_clean.s3.sql"
DEFAULT_IMAGES_DIR = ROOT / "data" / "images"
LOCAL_IMAGE_PREFIX = "backend/dataCawl/data/images/"
DOTENV_CANDIDATE_PATHS = [
    ROOT.parent / ".env",
    ROOT / ".env",
]


@dataclass(frozen=True)
class ImageUploadResult:
    source_url: str
    public_url: str


@dataclass(frozen=True)
class ImageReference:
    source_url: str
    target_filename: str


def load_dotenv() -> None:
    for dotenv_path in DOTENV_CANDIDATE_PATHS:
        if not dotenv_path.exists() or not dotenv_path.is_file():
            continue

        for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue

            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip()
            if not key or key in os.environ:
                continue

            if len(value) >= 2 and (
                (value.startswith('"') and value.endswith('"'))
                or (value.startswith("'") and value.endswith("'"))
            ):
                value = value[1:-1]

            os.environ[key] = value
        return


def normalize_ascii(text: str) -> str:
    text = text.replace("đ", "d").replace("Đ", "D")
    text = unicodedata.normalize("NFKD", text)
    return "".join(char for char in text if not unicodedata.combining(char))


def slugify_text(text: str) -> str:
    normalized = normalize_ascii(text).lower()
    normalized = re.sub(r"[^a-z0-9]+", "-", normalized).strip("-")
    return normalized or "image"


def slugify_filename(filename: str) -> str:
    path = Path(filename)
    stem = slugify_text(path.stem)
    return f"{stem}{path.suffix.lower()}"


def resolve_public_base_url(bucket: str, region: str) -> str:
    configured = (
        os.getenv("APP_STORAGE_S3_PUBLIC_BASE_URL", "").strip()
        or os.getenv("AWS_S3_PUBLIC_BASE_URL", "").strip()
    ).rstrip("/")
    if configured:
        return configured

    return f"https://{bucket}.s3.{region}.amazonaws.com"


def build_s3_key(prefix: str, filename: str) -> str:
    safe_name = slugify_filename(filename)
    prefix = prefix.strip("/")
    return f"{prefix}/{safe_name}" if prefix else safe_name


def resolve_local_image_path(raw_url: str, images_dir: Path) -> Path:
    if raw_url.startswith("http://") or raw_url.startswith("https://"):
        raise ValueError(f"Image is already remote: {raw_url}")

    if raw_url.startswith(LOCAL_IMAGE_PREFIX):
        return REPO_ROOT / raw_url.replace("/", os.sep)

    candidate = images_dir / Path(raw_url).name
    if candidate.exists():
        return candidate

    direct = REPO_ROOT / raw_url.replace("/", os.sep)
    if direct.exists():
        return direct

    raise FileNotFoundError(f"Cannot resolve local image path for '{raw_url}'")


def build_target_filename(product_record: dict[str, Any], image_record: dict[str, Any]) -> str:
    category_slug = str(product_record.get("category", {}).get("slug", "")).strip()
    product_slug = str(product_record.get("product", {}).get("slug", "")).strip()
    if not product_slug:
        product_slug = slugify_text(str(product_record.get("product", {}).get("name", "")).strip())

    variant_lookup = {
        str(variant.get("sku", "")).strip(): variant
        for variant in product_record.get("variants", [])
    }
    variant_sku = str(image_record.get("variant_sku", "")).strip()
    variant = variant_lookup.get(variant_sku, {})
    color_name = str(variant.get("color_name") or variant.get("variant_name") or "default").strip()
    color_slug = slugify_text(color_name)

    vehicle_prefix = "DD" if category_slug == "xe-dap-dien" else "MD"
    return f"{vehicle_prefix}-{product_slug}-{color_slug}.jpg"


def collect_image_references(payload: dict[str, Any]) -> list[ImageReference]:
    seen: set[str] = set()
    image_references: list[ImageReference] = []

    for product_record in payload.get("products", []):
        for image_record in product_record.get("images", []):
            image_url = str(image_record.get("image_url", "")).strip()
            if not image_url or image_url in seen:
                continue
            seen.add(image_url)
            image_references.append(
                ImageReference(
                    source_url=image_url,
                    target_filename=build_target_filename(product_record, image_record),
                )
            )

    return image_references


def upload_images(
    image_references: list[ImageReference],
    images_dir: Path,
    bucket: str,
    region: str,
    prefix: str,
) -> dict[str, ImageUploadResult]:
    try:
        import boto3
    except ImportError as exc:  # pragma: no cover
        raise SystemExit(
            "Missing dependency 'boto3'. Install it with: pip install boto3"
        ) from exc

    client_kwargs = {
        "region_name": region,
    }

    endpoint_url = os.getenv("AWS_S3_ENDPOINT_URL", "").strip()
    if endpoint_url:
        client_kwargs["endpoint_url"] = endpoint_url

    access_key = os.getenv("AWS_ACCESS_KEY_ID", "").strip()
    secret_key = os.getenv("AWS_SECRET_ACCESS_KEY", "").strip()
    session_token = os.getenv("AWS_SESSION_TOKEN", "").strip()

    if access_key and secret_key:
        client_kwargs["aws_access_key_id"] = access_key
        client_kwargs["aws_secret_access_key"] = secret_key
    if session_token:
        client_kwargs["aws_session_token"] = session_token

    s3_client = boto3.client("s3", **client_kwargs)
    public_base_url = resolve_public_base_url(bucket, region)
    acl = os.getenv("AWS_S3_OBJECT_ACL", "").strip()

    uploaded: dict[str, ImageUploadResult] = {}
    used_keys: set[str] = set()

    for image_reference in image_references:
        local_path = resolve_local_image_path(image_reference.source_url, images_dir)
        key = build_s3_key(prefix, image_reference.target_filename)

        if key in used_keys:
            digest = hashlib.sha1(str(local_path).encode("utf-8")).hexdigest()[:10]
            target_path = Path(image_reference.target_filename)
            key = build_s3_key(prefix, f"{target_path.stem}-{digest}{target_path.suffix}")
        used_keys.add(key)

        extra_args: dict[str, Any] = {
            "ContentType": mimetypes.guess_type(local_path.name)[0] or "application/octet-stream",
        }
        if acl:
            extra_args["ACL"] = acl

        s3_client.upload_file(str(local_path), bucket, key, ExtraArgs=extra_args)
        public_url = f"{public_base_url}/{quote(key, safe='/')}"
        uploaded[image_reference.source_url] = ImageUploadResult(
            source_url=image_reference.source_url,
            public_url=public_url,
        )

    return uploaded


def rewrite_payload(payload: dict[str, Any], uploads: dict[str, ImageUploadResult]) -> dict[str, Any]:
    rewritten = json.loads(json.dumps(payload))

    metadata = rewritten.setdefault("metadata", {})
    metadata["image_storage"] = {
        "provider": "aws-s3",
        "total_uploaded_images": len(uploads),
    }

    for product in rewritten.get("products", []):
        for image in product.get("images", []):
            source_url = str(image.get("image_url", "")).strip()
            if source_url in uploads:
                image["image_url"] = uploads[source_url].public_url

    return rewritten


def rewrite_sql(sql_text: str, uploads: dict[str, ImageUploadResult]) -> str:
    updated = sql_text
    for item in uploads.values():
        updated = updated.replace(item.source_url, item.public_url)
    return updated


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Upload local product images to S3 and rewrite JSON/SQL outputs with S3 URLs."
    )
    parser.add_argument("--json-input", type=Path, default=DEFAULT_JSON_INPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument("--sql-input", type=Path, default=DEFAULT_SQL_INPUT)
    parser.add_argument("--sql-output", type=Path, default=DEFAULT_SQL_OUTPUT)
    parser.add_argument("--images-dir", type=Path, default=DEFAULT_IMAGES_DIR)
    parser.add_argument(
        "--bucket",
        default=os.getenv("APP_STORAGE_S3_BUCKET", "").strip() or os.getenv("AWS_S3_BUCKET", "").strip(),
    )
    parser.add_argument(
        "--region",
        default=(
            os.getenv("APP_STORAGE_S3_REGION", "").strip()
            or os.getenv("AWS_REGION", "").strip()
            or os.getenv("AWS_DEFAULT_REGION", "").strip()
        ),
    )
    parser.add_argument(
        "--prefix",
        default=os.getenv("APP_STORAGE_S3_PRODUCT_IMAGE_PREFIX", "").strip()
        or os.getenv("AWS_S3_PREFIX", "products").strip(),
    )
    return parser.parse_args()


def main() -> None:
    load_dotenv()
    args = parse_args()

    if not args.bucket:
        raise SystemExit("Missing S3 bucket. Set AWS_S3_BUCKET or pass --bucket.")
    if not args.region:
        raise SystemExit("Missing AWS region. Set AWS_REGION or pass --region.")

    payload = json.loads(args.json_input.read_text(encoding="utf-8"))
    image_references = collect_image_references(payload)
    uploads = upload_images(
        image_references=image_references,
        images_dir=args.images_dir,
        bucket=args.bucket,
        region=args.region,
        prefix=args.prefix,
    )

    rewritten_payload = rewrite_payload(payload, uploads)
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(rewritten_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    if args.sql_input.exists():
        sql_text = args.sql_input.read_text(encoding="utf-8")
        args.sql_output.parent.mkdir(parents=True, exist_ok=True)
        args.sql_output.write_text(rewrite_sql(sql_text, uploads), encoding="utf-8")

    print(f"Uploaded {len(uploads)} images to s3://{args.bucket}/{args.prefix.strip('/')}")
    print(f"JSON: {args.json_output}")
    if args.sql_input.exists():
        print(f"SQL : {args.sql_output}")


if __name__ == "__main__":
    main()
