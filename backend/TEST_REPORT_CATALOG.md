# Catalog / color review — 2026-11-02

The previous dataset generated a fixed palette and assigned photographs by index, so view angles and unrelated colors were incorrectly treated as variants. Gasoline models also received inferred electric specifications. That data/generator and its images are now archived at `archive/catalog-before-correction/`.

## Current catalog

- 34 real DIBAO electric models: 26 electric scooters and 8 pedal-equipped electric bicycles.
- 164 visually reviewed color/finish choices, linked to 181 original manufacturer photos. Only confirmed photo choices are exposed. Two models have one confirmed color.
- All photo source URLs, image dimensions, SHA-256 checksums and the source technical specification tables are recorded in `database/catalog/source-manifest.json`. Explicit color assignments are in `database/catalog/review.json`.
- The KINETIC store brand is retained. Manufacturer/model names now match the DIBAO photos. Existing store prices and product stock totals are preserved.
- Old active color names/images were retired. Historical variant IDs and 7 existing orders are preserved. The 8 gasoline products are inactive in the live DB and completely absent from the fresh-install seed.

Removed from sale: Sir 50CC, Creer 50CC, Gofast 50CC, Pansy XS1 upgraded, Pansy XS, Pansy XS1, RS 50CC and Tesla X 50CC.

## Verification

- `mvn clean verify`: 12 tests passed, 0 failures/errors.
- Chrome catalog test: all 34 product pages, 164 color selections and 181 image checksums passed; no uncaught JavaScript errors. Each selected gallery is restricted to its selected color. The selected variant and photo remain aligned in the cart.
- Desktop and 390px mobile layout checked, including two-tone swatches. No horizontal overflow.
- Fresh setup SQL imported successfully into an isolated verification schema: 34 products, 164 variants, 181 photos, 0 broken image/variant foreign-key links, 0 gasoline models. The temporary verification schema was removed afterwards.
- Live database updated with `database/updates/20261102_verified_electric_catalog.sql`; no account/order tables were reset. A pre-update snapshot is retained locally at `archive/local-database-backups/` and is ignored by Git.

Screenshots and machine-readable browser report are at `target/catalog-verification/`. Reproduce with `npm run test:catalog` from `tests/ui/` after starting the application. Use `npm run preview:colors` to open Chrome for manual review.

Source catalog: https://dibao.com.vn/xe-may-dien. The official gasoline categories are https://dibao.com.vn/xe-so-50-cc and https://dibao.com.vn/xe-tay-ga-50cc. Specific product/gallery URLs are retained in the manifest.
