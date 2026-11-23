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

## Receipts and warranty cards (V27)

- `service/document` makes the sales receipt and the warranty card as PDF when they are asked for. Nothing is stored: there is no copy to go stale and no personal data to erase with an account. For an erased account the documents print no email address (the name, phone and address of past orders stay, as sales records).
- Pages are Thymeleaf templates in `resources/documents/` (well-formed XHTML), turned into PDF by openhtmltopdf with DejaVu Sans embedded (the font comes from the `jasperreports-fonts` jar), so every Vietnamese letter shows.
- `GET /api/v1/orders/{id}/receipt` and `/warranty-card[?unitId=]` (customer of the order, or staff; a stranger gets 404). The words follow the page language, or `lang=vi|en`.
- When an order is saved as DELIVERED, `OrderNotificationJpaListener` publishes `OrderDeliveredEvent`; after the commit `OrderDeliveredEmailListener` emails both PDFs on a bounded pool of their own (`documentExecutor`; guest orders too, an erased account never). A full queue, a render failure or an SMTP failure is logged with the order and the email is not retried: staff with the permission to update an order can press "Gửi lại email phiếu" on the Manager order page (`POST /api/v1/orders/{id}/documents/email`, six a minute per address, answers at once whether the mail server took it), and the customer can still download both PDFs.
- The shop name, tax code (made up) and address come from `app.store.*`. The receipt says it is not an electronic invoice under tax law.
- The order stores the language of the page it was placed from (`orders.customer_language`, `update_orders_customer_language.sql`), so the hand-over email and PDFs of a guest order follow it; an older order has none and is answered in Vietnamese (the shop's base language). A signed-in customer gets the language saved in the profile. Staff can download the PDFs in either language from the Manager order page.
- Dates: "issued at" is read from the same clock as the dates stored on the order (the JVM zone), so the order, hand-over and issue times on one page always agree. Run the server in the shop's zone (`TZ=Asia/Ho_Chi_Minh`) if the printed times must match the wall clock of the shop.
- A provisional receipt (bike not handed over) is numbered `TT-<order number>`, the final one `PBH-<order number>`, so two different papers of one order never carry the same number.
- Text typed by customers goes into XHTML pages; characters that XML 1.0 does not allow (such as a vertical tab) are dropped first, otherwise the page would not parse and no PDF could be made.
