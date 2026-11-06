# Reviewed electric vehicle catalog

`products.json` is the source of the MariaDB product seed. It contains 34 electric models, 164 visually reviewed color variants and 181 unmodified manufacturer photographs. The KINETIC storefront sells the actual DIBAO models shown in the photos; product URLs remain stable.

- `source-manifest.json`: manufacturer page/gallery URLs, technical specification tables, image dimensions and SHA-256 checksums.
- `review.json`: explicit photo-to-color assignments after visual review. A slash in `color_hex` represents a two-tone swatch. Gallery positions are never automatically mapped to a palette.
- Product prices and demo stock are store sample data. Manufacturer technical values replace the previous randomly inferred specification values. Unknown facts remain null.
- The old gasoline models are removed from fresh installs. On an existing database they and their old variants are deactivated, preserving foreign keys and order history.

Source: https://dibao.com.vn/xe-may-dien and the individual product URLs recorded in the manifest. Original supplier photos are stored locally under `src/main/resources/static/images/catalog/`. No recoloring or replacement of manufacturer marks is performed.

## Updating

1. `python backend/database/crawl_catalog.py` downloads galleries and writes `backend/target/catalog-crawl/manifest.json` for review. Copy the reviewed manifest to this folder.
2. Review the photographs and explicitly update `review.json`. Only assign colors visible in the actual photographs.
3. `python backend/database/materialize_catalog.py` builds `products.json`. It does not generate additional SQL by default.
4. `python backend/database/generate_mariadb_setup.py` regenerates the complete MariaDB setup SQL for a fresh database.

The existing database already contains the reviewed catalog; the applied incremental SQL was removed during cleanup. For a future catalog change, pass `--write-update` explicitly to generate a new incremental script for review. The fresh setup file recreates database tables and is intended for a fresh install, not normal application startup.

Previous crawled data, fabricated color generators and legacy images are stored outside `backend` at `archive/catalog-before-correction/`. Obsolete SQL snapshots were removed during cleanup.
