# Cart and delivery verification — 2026-11-02

## Result

- Maven `verify`: BUILD SUCCESS; 8 tests, 0 failures/errors. Includes order totals, explicit promo/service amounts, home delivery, pickup without a home address, required province/store, unsupported delivery method, and insufficient stock across cart lines.
- Installed Google Chrome via Playwright: 29 browser checks passed; 0 uncaught JavaScript errors.
- Desktop 1440px and mobile 390px layouts checked. Product, cart, checkout and receipt screenshots are under `target/ui-tests/`.
- Current application URL: http://localhost:8080. No database re-import is needed for this change.

## Stored development orders

These real orders remain in the development database, with notes prefixed `[UI CART]` and email `cart.qa@example.com`. They consume stock and are visible to the manager.

| ID | Order | Delivery | Total |
|---|---|---|---|
| 6 | ORD-20261102-453E257E | Home delivery | 17,900,000 VND |
| 7 | ORD-20261102-82105C13 | Home delivery | 17,900,000 VND |
| 8 | ORD-20261102-04B85934 | Store pickup, two product lines | 37,300,000 VND |

## Manual review

1. Open a product, select a color and quantity, then choose **Thêm vào giỏ hàng**.
2. Open **Giỏ hàng** in the header. Change quantities, remove a line, and reload to check browser persistence.
3. Add a second color/product. **Mua ngay** opens a separate checkout and preserves the current cart.
4. In checkout, choose **Giao hàng tại nhà** and provide province/address, or **Nhận tại cửa hàng** and search/select a showroom.
5. Check the total with a blank promo code (no automatic discount). `EBIKE100`/`EBIKE200` apply the existing 100,000/200,000 VND code discounts. An invalid code shows an error and disables placing an order.
6. The optional registration service adds 2,500,000 VND explicitly. New orders use payment on receipt.
7. Submit only when you want to create a real local order. The receipt shows delivery details; purchased cart lines are removed after success.

The Chrome preview uses a separate local browser profile with sample cart lines. The preview itself does not place orders. See `tests/ui/README.md` for commands.
