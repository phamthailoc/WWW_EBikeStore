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
- `database/update_*.sql` bring an existing database up to date; every schema change goes into both the setup script and a new update script.
- `database/catalog/` records where the reviewed electric models, color/photo assignments and photographs came from.
- `src/main/resources/static/images/catalog/` stores original manufacturer photographs used by the storefront and color galleries.
- `src/main/resources/templates/` contains Thymeleaf pages.
- `src/main/resources/static/` contains CSS, JavaScript, and browser images.
- `src/main/resources/docs/` and `faq-data/` are used by the chatbot knowledge service.

## Cart and delivery

The browser cart is saved in `kinetic_cart` localStorage. Lines are keyed by product and color. Buy-now uses a separate session entry, so it does not replace the customer's cart. Server quotes and order creation always read current prices and stock from MariaDB.

For home delivery, `shipments.pickup_showroom_id` is null, `pickup_district` holds the selected province, and the delivery address is stored in `detailed_address`/`shipping_address`. For store pickup, the selected showroom supplies the address. The response exposes `deliveryMethod` and `province`; existing schema and historical orders remain compatible.

Browser tests are the Selenium classes under `src/test/java/com/ebike/selenium/`; they run only when `-Dselenium.baseUrl` is given.

## Product colors

Only active variants with active photos are exposed as selectable colors. The product API includes a separate `images` gallery for each variant. Selecting a different view keeps the chosen color, while switching colors replaces the gallery and updates the cart's variant/photo reference. Two-tone colors render a split swatch alongside the actual product thumbnail.

Catalog provenance: `database/catalog/README.md`.

## Caching and request limits

Product lists and product details are cached in the application's memory (`CacheNames`); every change to stock, photos, reviews or orders evicts both caches. The cache is per process, so the application is meant to run as a single instance.

`ServerRateLimitFilter` limits, per client IP, sign-in attempts, email-code requests, chatbot questions and VNPay payment creation. It reads the client address from `X-Forwarded-For`, so it is only as trustworthy as the proxy in front of the application.
