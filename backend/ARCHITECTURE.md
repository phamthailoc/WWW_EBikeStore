# Backend code layout

Backend Java is organized by MVC layer. Business areas remain as subpackages to keep related controllers, services, repositories, models, and DTOs easy to locate without nesting the whole project under feature modules.

```text
com.ebike
├── controller/       HTTP and Thymeleaf controllers, grouped by area
├── service/          application logic and service implementations
├── repository/       Spring Data repositories
├── model/entity/     JPA entities and enums
├── dto/              request and response types
├── config/           Spring configuration and typed properties
├── security/         JWT and request security filters
├── common/           cross-cutting constants and exceptions
├── event/            domain events
├── listener/         event and persistence listeners
└── util/             focused utilities
```

## Data and resources

- `database/setup_ebike_database.sql` is the MariaDB setup script used for `ebike_db`.
- `database/generate_mariadb_setup.py` regenerates the setup SQL from `database/schema-source/` and the product JSON catalog.
- `database/schema-source/` contains PostgreSQL schema history used only as input to that converter; it is not loaded at application startup.
- Earlier PostgreSQL migration history and generated product SQL outputs are kept in the top-level `archive/legacy-project/backend/`.
- `data-backup/` stores product JSON data, image sources, and conversion scripts.
- `images/` stores the storefront banner assets.
- `src/main/resources/templates/` contains Thymeleaf pages.
- `src/main/resources/static/` contains CSS, JavaScript, and browser images.
- `src/main/resources/docs/` and `faq-data/` are used by the chatbot knowledge service.

The archived `chatbot-model-scaffold` contains empty planning directories and metadata that had no runtime references.

## Cart and delivery

The browser cart is saved in `kinetic_cart` localStorage. Lines are keyed by product and color. Buy-now uses a separate session entry, so it does not replace the customer's cart. Server quotes and order creation always read current prices and stock from MariaDB.

For home delivery, `shipments.pickup_showroom_id` is null, `pickup_district` holds the selected province, and the delivery address is stored in `detailed_address`/`shipping_address`. For store pickup, the selected showroom supplies the address. The response exposes `deliveryMethod` and `province`; existing schema and historical orders remain compatible.

UI test instructions: `tests/ui/README.md`.
