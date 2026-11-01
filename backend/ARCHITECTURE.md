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

- `../setup_ebike_database.sql` (project root) is the MariaDB setup script used for `ebike_db`.
- `database/legacy-postgresql/` stores unused PostgreSQL/Flyway migrations and schema notes outside the runtime classpath.
- `data-backup/` stores imported product data, image sources, and conversion scripts.
- `images/` stores the storefront banner assets.
- `src/main/resources/templates/` contains Thymeleaf pages.
- `src/main/resources/static/` contains CSS, JavaScript, and browser images.
- `src/main/resources/docs/` and `faq-data/` are used by the chatbot knowledge service.

The archived `chatbot-model-scaffold` contains empty planning directories and metadata that had no runtime references.
