# Cart and checkout checks

Start the Spring Boot application with MariaDB first. Run from this folder:

```powershell
npm ci --ignore-scripts
npm test
node preview.cjs
```

The browser checks use installed Google Chrome. Set `CHROME_PATH` if needed, and optionally `BASE_URL` (default `http://localhost:8080`).

The smoke test submits real local orders with email `cart.qa@example.com` and notes starting `[UI CART]`. These are visible to the manager and consume stock like normal orders. Use a development database with stocked products/showrooms.

Screenshots and the JSON report are written under `backend/target/ui-tests/`. `preview.cjs` opens a separate Chrome profile with a sample cart and does not submit an order. Close that Chrome window to stop the preview script. Add `--headless` to only capture screenshots.

Covered flows: selected color/quantity, cart merge/remove/reload/stock limits, independent buy-now, home address validation, store search/pickup, promo validation, explicit service fee, real order submission and cart cleanup, desktop/mobile layout, and browser JavaScript errors.

Run `npm run test:catalog` to verify the reviewed electric-only catalog, every color/gallery/photo checksum, disabled gasoline URLs, and the selected photo in the cart. This check does not create orders. `npm run preview:colors` opens Chrome on the two-tone Keva and Gogo Cross galleries.
