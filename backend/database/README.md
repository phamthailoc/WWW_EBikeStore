# Database files

The application currently uses MariaDB. The canonical import file is `setup_ebike_database.sql` in this folder. Import it into HeidiSQL to create and seed `ebike_db`.

`setup_ebike_database.sql` is edited by hand and is the only definition of a fresh database. A schema or seed change goes in two places: this file, and a new `update_*.sql` (safe to run more than once) for databases that already exist. The reviewed electric catalog and image/color provenance are documented in `catalog/README.md`.

The application connects directly to the running MariaDB database `ebike_db`; it does not import SQL files at startup (Hibernate only validates the schema, `ddl-auto=validate`). The setup script recreates tables and seeds a fresh installation; do not reimport it into the existing database to start the application.

The PostgreSQL migration history and the Python scripts that used to generate the setup file from it were removed: they could not be run on the team's machines, so the generated file and its sources had drifted into three copies of the same schema. They remain in git history.
