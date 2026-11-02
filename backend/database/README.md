# Database files

The application currently uses MariaDB. The canonical import file is `setup_ebike_database.sql` in this folder. Import it into HeidiSQL to create and seed `ebike_db`.

`generate_mariadb_setup.py` rebuilds the import file from `catalog/products.json` and `schema-source/`. The reviewed electric catalog and image/color provenance are documented in `catalog/README.md`. The PostgreSQL schema source is used only by the generator, not at startup.

For an existing database, `updates/20261102_verified_electric_catalog.sql` applies the electric-only catalog correction while preserving orders, accounts and current store stock. The full setup script is for fresh installations.
