# Database files

The application currently uses MariaDB. The canonical import file is `setup_ebike_database.sql` in this folder. Import it into HeidiSQL to create and seed `ebike_db`.

`generate_mariadb_setup.py` rebuilds the import file from the product JSON data and schema source. The PostgreSQL schema source is used only by this generator; the application does not load it at startup. Historical V1 migrations and old product SQL outputs are kept outside `backend` under `archive/legacy-project/backend/`.
