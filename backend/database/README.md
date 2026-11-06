# Database files

The application currently uses MariaDB. The canonical import file is `setup_ebike_database.sql` in this folder. Import it into HeidiSQL to create and seed `ebike_db`.

`generate_mariadb_setup.py` rebuilds the import file from `catalog/products.json` and `schema-source/`. The reviewed electric catalog and image/color provenance are documented in `catalog/README.md`. The PostgreSQL schema source is used only by the generator, not at startup.

The application connects directly to the running MariaDB database `ebike_db`; it does not import SQL files at startup (`spring.flyway.enabled=false`, Hibernate schema validation only). The setup script recreates tables and seeds a fresh installation; do not reimport it into the existing database to start the application.

The eight SQL files under `schema-source/migration/` remain required inputs of `generate_mariadb_setup.py`. They are not separate setup scripts. Obsolete archived SQL, old local SQL snapshots and the already-applied catalog update have been removed.

`materialize_catalog.py` normally writes only the reviewed catalog JSON. If a future catalog change needs an incremental database update, generate it explicitly with `python backend/database/materialize_catalog.py --write-update`, then review it before applying it. No incremental SQL is needed to start the current application.
