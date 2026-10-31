# Migration Archive

This folder stores archived `V1_*` Flyway migrations after the migration squash to `V2_0_0__baseline.sql`.

Notes:

- `application.properties` now points Flyway to `classpath:db/migration`
- `V1_*` files are kept here for reference only
- the active baseline is `db/migration/V2_0_0__baseline.sql`
- product catalog seed still lives outside Flyway in `backend/data-backup/`
