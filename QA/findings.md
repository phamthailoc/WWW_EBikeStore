# QA findings log (working notes)

## Manual (Chrome) — app 6b09bdf on :8088, DB ebike_qa
- M-01 [High/UI] Home hero banner empty: taste.css:98 `url('/images/Kinetic-Banner.jpg')` → 404 (file deleted in 9c34f60). Largest block of the home page is a blank grey box.
- M-02 [High/UI, site-wide] taste.css line 1 has a rule (`.qty-edit…`) before the two `@import`s (lines 7-8) → per CSS spec both imports are dropped: Google fonts (Be Vietnam Pro, Bricolage Grotesque) and Phosphor icons never load. Browser: CSSImportRule count 0, no network request. Every `<i class="ph ...">` (home 7, products 3, detail 2, login 2, signup 1, flow 1, workspace 1) renders empty; home "Công nghệ" feature tiles are blank lime squares.
- M-03 [Medium/UX] HTML pages answer bad query params with raw JSON: /products?vehicleType=XYZ | batteryType=FOO | maxPrice=abc | page=abc | inStock=maybe → 400 `{"status":400,...}` application/json shown in the browser, no store layout.
- M-04 [Medium/UX] /product/<unknown-slug> (also /bike, /models) → raw JSON 404 `{"message":"Không tìm thấy sản phẩm."}` instead of an HTML 404 page with navigation.
- M-05 [Medium/Data] All 8 products in category "Xe đạp điện" (id 1) have vehicle_type E_MOTORBIKE, 35-45 km/h, 1000-1550 W. README rule: >25 km/h or >250 W is xe máy điện. Category label contradicts the spec; category filter "Xe đạp điện" lists motorbikes. (seed setup_ebike_database.sql)
- M-06 [Low/UX] minPrice > maxPrice (and minRangeKm > maxRangeKm) silently returns empty list, no hint. Search "xe dien" (no diacritics) finds nothing.
- M-07 [Low/UX] Header navigation differs between pages: home = Sản phẩm/Công nghệ/Liên hệ/Bảo hành + Đăng nhập/Tạo tài khoản; /products = Mẫu xe/Hỗ trợ/Bảo hành/Tư vấn/Đã lưu + Tài khoản.
- M-08 [Low/UI] Price format differs: list/related cards "17,700,000 ₫" (en) vs detail/cart/checkout "17.700.000 ₫" (vi). Specs show "35.00 km/h", "70.00 km". Product description "xe đạp điện có bàn đạp" for a 1000 W motorbike.
- M-09 [Medium/UX] Checkout: after applying an invalid promo code the whole price breakdown (Tạm tính, phí giao, giảm giá, tổng) disappears and "Đặt hàng" is disabled; no "bỏ mã" button — user must guess to empty the field.
- M-10 [Medium/Logic] Quote/order accept an unknown province ("Atlantis") for HOME_DELIVERY and charge 800k/bike (far rate) instead of 400.
- M-11 [Medium/Logic] "Max 10 per product" enforced per colour only: quote productId 1 with 3 colours × 10 = 30 bikes → 200. Contradicts error text "Số lượng mỗi sản phẩm không được vượt quá 10" and detail page hint "Tối đa 10 xe mỗi đơn".
- OK: server ignores client userId/shippingFee/discountAmount on POST /orders; promo codes case/whitespace-insensitive; expired/WAITING codes rejected; cart API validation solid; quantity input clamps 99→10, -3→1; confirmation email sent to guest.
- M-12 [High/Validation] PUT /api/v1/auth/profile: email "abc" saved (200) — no format check; profile form email input is type=text; blank first/last name wipes them to null; 300-char name → HTTP 500. Email change needs no password.
- M-13 [Medium] POST /api/v1/auth/register firstName 300 chars → HTTP 500 (unhandled DB length). Username with spaces "qa user x" accepted. Server min password 6, signup form minlength 8 (inconsistent). XSS-looking first name stored raw (check rendering in staff pages).
- M-14 [Low/i18n] Profile page labels "First Name"/"Last Name" in English; workspace header "Bảng điều khiển customer"; order detail "Thanh toán: … · PENDING" raw enum.
- M-15 [Low/Sec] GET /api/v1/orders/{id} of another (guest) order → 403 with explanatory text, while /receipt → 404: order ids enumerable.
- M-16 [UX] 11 flows use native confirm()/prompt() (cancel reason, stock qty, refund note, pricing, support note, OTP code for linking guest orders…). Freezes automation; prompt() for an OTP/cancel reason is poor UX.
- OK: login wrong pw message; email login case-insensitive; dup email/username case+space-insensitive; guest-order link via OTP works (1 order linked); IDOR checks on orders/receipts/addresses/users/admin/manager pass; change-password validations all good.
- M-17 [Low] Address book accepts city "Atlantis" (not in province list).
- M-18 [High/Logic] PATCH /api/v1/manager/orders/{id}/shipment on a DELIVERED order: DELIVERED→PENDING→RETURNED→DELIVERED all 200, order stays DELIVERED. Also SHIPPED→PENDING while order stays SHIPPED (UI "Lưu giao nhận" stays enabled after delivery). RETURNED neither restores stock nor changes the order.
- M-19 [Medium] PUT /api/v1/manager/orders/{id}/units changes frame/engine numbers after DELIVERED (warranty book identity).
- M-20 [Medium/Business] PAY_LATER order delivered while payment stays PENDING; nothing forces "xác nhận đã thu" before hand-over.
- OK: order status transition matrix enforced (DELIVERED→CANCELLED/PENDING 400, CONFIRMED offers only PROCESSING/CANCELLED); cannot ship before PROCESSING; frame/engine required & validated (5-30 chars); delivery email with PDF sent once; customer cancel restores product+variant stock and cancels payment; staff pages escape XSS (name, notes, support subject).
- M-21 [Medium/Payment] Late successful VNPay IPN for an order the customer already cancelled answers RspCode 00 "Confirm Success" and leaves order CANCELLED + payment PAID (stock already returned). Money is taken for a cancelled order; only a manual refund catches it.
- M-22 [Low] Manager refund accepted with an empty note (PATCH …/refund {note:""} → 200).
- OK (probe pay): PAY_LATER confirm → order CONFIRMED; double confirm 400; refund only for PAID of a cancelled order; refunded can't be confirmed; customer gets 403 on manager APIs; VNPay create refused for other user/guest/paid order; IPN bad signature 97, wrong amount 04, unknown txn 01, replay idempotent 00; return URL validates signature.
- M-23 [High/Validation] Admin POST /api/v1/admin/accounts accepts email "zzz" (201) — staff account with an invalid email (same gap as M-12).
- M-24 [Low] Pricing rule REGISTRATION_SERVICE accepts amount 0 (only negatives refused); doc message elsewhere says "phải lớn hơn 0". (restored to 500000 after test)
- OK (probe admin): manager → admin API 403; dup email 409; unknown role 400; min pw 6; locked login 403; admin cannot lock/demote/delete self; delete needs exact confirmEmail; customer with orders cannot be deleted (409); audit log records actions with IP; installment rate >60 refused; promo dup (case-insensitive) 409, >100%, ends<starts, negative refused; promo usage limit 1 enforced; same customer cannot reuse a code.
- M-25 [Medium/Report] Same day, two numbers: manager dashboard todayRevenue = 50,400,000 (= 3×16.8M: order #6 paid, #5 paid-then-REFUNDED, #7 CANCELLED+PAID by late IPN) while revenue report Nov = 16,800,000 (1 successful order). Dashboard counts refunded / cancelled-order payments as revenue.
- OK (probe stock): negative stock 400; out-of-stock colour cannot be ordered; product with colours forces per-colour stock; stock alert idempotent and customer-only; receipt validation + PN-000001; revenue from>to 400; orders xlsx export; customer search with order count.
- M-26 [Medium/UC-10] Chatbot follow-up with Gemini ON: chatId qa2 "Tư vấn xe dưới 20 triệu đi học" → suggestions; then "giá bao nhiêu?" (same chatId) → "trong tài liệu ... chưa có bảng giá chi tiết" — memory of the suggested bike not used, and it denies having prices that are in the DB. (Unit: U-19 bare follow-up not detected.)
- OK (probe svc): test ride slots/lead/14-day/opening-hours/duplicate slot/phone/staff-403 all enforced; installment income range, CCCD format, guest 401, ID masked ********1234, reject needs note, double decision 409; warranty claim on foreign unit 404; chatbot blank 400, prompt-injection refused; robots/sitemap/health OK; garbage and alg=none JWT → 401.

- OK (Chrome admin): /admin, /admin/accounts (XSS payload rendered as text), /admin/pricing, /admin/audit-logs (IP shown raw "0:0:0:0:0:0:0:1", cosmetic), /admin/catalog load with no errors; app forbids framing (clickjacking protected).

- OK (375px): document scrollWidth = 375 on /, /products, /product/kinetic-amy, /checkout, /login, /support (support table 719px wide inside its own scroller). Mobile home shows the empty hero box (M-01) as the first screen.

- UI-01 [Medium/a11y] Touch targets under 24 px (WCAG 2.2 AA) and far under 44 px mobile guidance: theme toggle 16x40 on every page, header "Giỏ hàng" 50x23, "Đăng nhập"/"Tài khoản" ~19 px tall, "Xem chi tiết" 22 px, "Xóa bộ lọc" 21 px (13 on /products).
- UI-02 [Low/a11y] Header search input (name=q) has only a placeholder, no label/aria-label (home, products). /checkout lacks the skip link other pages have. Product detail has 14 text nodes under 12 px.
- UI-03 [Medium/UX] Checkout server errors shown at the bottom of the summary card, far from the field (e.g. "Họ và tên không được chứa số" while the name field is at the top); no aria-describedby link (ui-ux-pro-max error-placement).
- UI summary: contrast passes 4.5:1 on audited pages, single h1, no heading skips, all images have alt, viewport allows zoom, prefers-reduced-motion handled, body 16 px. Biggest visual defects remain M-01 (empty hero) and M-02 (icons/fonts never load), plus inconsistent header nav (M-07) and native confirm/prompt dialogs (M-16).

## Unit-test agents (verified)
- U-order (c77e206 → cherry-picked; 635 tests, 7 bug tests):
  - U-01 [Critical/Security] OTP wrong-attempt counter rolled back (OrderEmailVerificationServiceImpl.java:78-152 save then throw in @Transactional). LIVE CONFIRMED: 5 wrong codes, then right code → 200 verified. Only per-IP send limit protects; verify endpoint not rate limited.
  - U-02 [Medium] Resend cooldown 60 s never enforced (resendAvailableAt only written). LIVE CONFIRMED: two sends in a row both 200, 2 mails.
  - U-03 [Medium] Successful IPN moves PROCESSING/READY_FOR_PICKUP/SHIPPED order back to CONFIRMED (VnPayServiceImpl.java:324).
  - U-04 [Low] Zero-total order gets VNPay URL with vnp_Amount=0 (VnPayServiceImpl.java:124).
  - U-05 [Low] GET /orders?limit=0/-1 returns all orders, 2000 cap bypassed (OrderServiceImpl.java:186).
- U-backoffice (af19352 → cherry-picked; 589 tests, 33 bug tests):
  - U-06 [High/Money] createPromotion stores negative/zero maxDiscountAmount (AdminServiceImpl.java:142 vs update :170). LIVE CONFIRMED: cap -50000 → quote discount -50,000, total 16,850,000 > subtotal 16,800,000.
  - U-07 [Medium/Security] Login account enumeration: unknown user 404 "Không tìm thấy người dùng." vs wrong password 401. LIVE CONFIRMED.
  - U-08 [Medium] No handler for DataIntegrityViolationException → 500 on over-long fields (register, profile, admin create) — root cause of M-12/M-13.
  - U-09 [Medium] Admin createAccount: no email format check (= M-23); email without "@" and no username → StringIndexOutOfBounds 500; "@shop.com" → blank username; username with spaces.
  - U-10 [Medium] Pricing: REGISTRATION_TAX_RATE >100 accepted; delivery fee no upper bound; REGISTRATION_SERVICE 0 (= M-24).
  - U-11 [Medium] Dashboard revenue counts paid money of cancelled orders (ManagerServiceImpl.java:617-624) — root cause of M-25; customers totalSpent too (:640, Low).
  - U-12 [Medium] Shipment regression after delivery (ManagerServiceImpl.java:446, 768-780) = M-18.
  - U-13 [Medium/Privacy] Self-service erasure keeps AuthenticationLog IP + user agent (AccountErasure), contrary to its Javadoc.
  - U-14 [Medium, defence-in-depth] user:view lets a manager read ADMIN accounts via GET /users/{id}; service would allow address writes for read-only holders (HTTP blocks today).
  - U-15 [Low] register trims password, login does not; server min 6 vs form 8; username may look like an email (only self-shadowing, Low); support email regex accepts trailing dot.
- U-catalog (8d3027d → cherry-picked; 398 tests, 25 bug tests):
  - U-16 [Medium] Search not accent-insensitive (ProductService.java:180-188). LIVE CONFIRMED: q=đạp → 8 cards, q=dap → 0. LIKE wildcards unescaped (Low): q=% → all, q=go_o → gogo.
  - U-17 [Medium] Price filter ignores discountPrice (ProductService.java:172-178) while cards/sort use it.
  - U-18 [Medium/Security] Product image upload trusts client Content-Type (AdminProductImageService.java:264-270): EXE as image/png, SVG with script accepted; 3000-char filename → S3 key > 1024 bytes (Low).
  - U-19 [Medium] Chatbot rule engine: FAQ keywords matched as single-word substrings ("Thời tiết hôm nay thế nào?" → charging FAQ); bare "giá bao nhiêu?" after a suggestion not a follow-up; budgets other than 20M ignored, "dưới 200 triệu" read as 20M; showroom list never in rule answers; "quang duong" → showroom intent; unexpected Gemini runtime error → 500 instead of fallback (ChatbotService.java:85-87); prompt has no injection guard (:778-794); PDF fallback text without diacritics; currency format depends on server locale.
  - U-20 [Low] Gemini client retries 400/401/403; name sort puts accented names after z; ProductSpecification.brand default "Yadea-style" (competitor name) leaks into filters/JSON-LD; min>max filters silently accepted (= M-06).
- U-ops (4c37f61 → cherry-picked; 287 tests, 3 bug tests): WarrantyRules.isKnownStatus(null) NPE, MaintenanceMilestones.describe(null) NPE, StockHistoryService.java:324 beforeId<=0 skips balances (all Low). Notes: ServerRateLimitFilter has NO rule for /email-verification/verify — combined with U-01 the OTP is brute-forceable (Critical). ARCHITECTURE.md says limiter reads X-Forwarded-For; code uses getRemoteAddr (doc stale). Intervals stored as P1Y instead of P12M (cosmetic).
- U-booking (fdcfe9e; 768 tests): TestRideRules.canCustomerCancel NPE on null status (Low); VI/EN messages promised in Javadoc/README but only Vietnamese exists (Doc).

## Code reading
- C-01 [Low/Sec] VnPayController.resolveClientIp trusts X-Forwarded-For / X-Real-IP (vnp_IpAddr spoofable); ARCHITECTURE.md says rate limiter reads XFF but code comment/memory says getRemoteAddr — doc drift.
- C-02 [Doc] ARCHITECTURE.md says "browser cart is saved in kinetic_cart localStorage" and later "cart kept in server-side HTTP session" — contradictory.

## Selenium suite (cherry-picked d3bb2d3, 180 tests, run 10:43 min: 171 pass, 8 bug fail, 1 flaky QA-script error since patched)
Bugs re-confirmed by UI: M-01 hero 404, M-02 taste.css @import, M-03/M-04 raw JSON, M-09 promo wipes breakdown, M-12 profile email not type=email, U-07 login enumeration.
New (verified in code):
- S-01 (Medium) /admin/roles never shows permissions: workspace.js:105 asks for the `permissions` column but workspace.js:188 filters `permissions` out of every table, so admins can't see what a role grants.
- S-02 (Low) workspace.js:5 `names` map has duplicate keys createdAt/action/target/role/title...; the later values win, so the audit log shows "Ngày tạo"/"Thao tác"/"Mục tiêu" instead of "Thời gian"/"Hành động"/"Đối tượng", and roles show "Vai trò"/"Tiêu đề" for role/title.
- S-03 (Low) checkout message "Vui lòng chọn cửa hàng nhận xe." is dead code: HTML5 `required` on the showroom radios blocks submit first.
Not automated: test-ride DONE/NO_SHOW (future slots), real VNPay payment.

## Rate limiter (live, second app copy on :8089 with APP_RATE_LIMIT_ENABLED=true; :8088 untouched)
- PASS: POST /api/v1/auth/login → 5 allowed, 6th-8th = 429 with Retry-After: 59.
- PASS: forged X-Forwarded-For does not reset the bucket (still 429) — filter uses getRemoteAddr only.
- PASS: limiter is per-instance config; :8088 (disabled) unaffected.
- Confirms U-01 gap: POST /orders/email-verification/verify has no rule (8 tries, no 429). /auth/register also has no rule (code review of ServerRateLimitFilter rules list).
- Note: limiter is in-memory per JVM → behind several instances each one has its own 5/min (Low, deployment note).
