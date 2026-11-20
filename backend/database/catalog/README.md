# Reviewed electric vehicle catalog

`products.json` records the catalog that was loaded into the MariaDB product seed in `../setup_ebike_database.sql`. It contains 34 electric models, 164 visually reviewed color variants and 181 unmodified manufacturer photographs. The KINETIC storefront sells the actual DIBAO models shown in the photos; product URLs remain stable.

- `source-manifest.json`: manufacturer page/gallery URLs, technical specification tables, image dimensions and SHA-256 checksums.
- `review.json`: explicit photo-to-color assignments after visual review. A slash in `color_hex` represents a two-tone swatch. Gallery positions are never automatically mapped to a palette.
- Product prices and demo stock are store sample data. Manufacturer technical values replace the previous randomly inferred specification values. Unknown facts remain null.
- The old gasoline models are removed from fresh installs. On an existing database they and their old variants are deactivated, preserving foreign keys and order history.

Source: https://dibao.com.vn/xe-may-dien and the individual product URLs recorded in the manifest. Original supplier photos are stored locally under `src/main/resources/static/images/catalog/`. No recoloring or replacement of manufacturer marks is performed.

## Updating

These JSON files are a record, not an input: nothing reads them at build time or at startup. To change the catalog, edit the product rows in `../setup_ebike_database.sql`, add an `update_*.sql` for existing databases, put the photographs under `src/main/resources/static/images/catalog/`, and update these files so the provenance stays true. Only assign colors visible in the actual photographs.

The crawler and generator scripts and the earlier crawled data were removed; they remain in git history.
