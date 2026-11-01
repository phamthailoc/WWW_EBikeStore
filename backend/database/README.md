# Database files

The application currently uses MariaDB. The canonical import file is `../../setup_ebike_database.sql` at the project root. Import it into HeidiSQL to create and seed `ebike_db`.

`legacy-postgresql/` preserves the previous PostgreSQL schema, Flyway migrations, and schema overview for reference. Flyway is disabled in the current configuration; these scripts are not loaded by the running application.
