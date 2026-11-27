# Kế hoạch sửa lỗi QA — EBikeStore (Kinetic)

> Tiến độ thực tế, quyết định và cách tiếp tục: xem `QA/TIEN_DO_SUA_LOI.md`.

> **Cho agent thực hiện:** dùng superpowers:subagent-driven-development. Mỗi batch = 1 nhánh = 1 PR vào `develop`. **Sonnet viết code, Opus review chi tiết** trước khi push/merge. Bước dùng checkbox `- [ ]`.

**Mục tiêu:** sửa toàn bộ lỗi trong `QA/BAO_CAO_KIEM_THU.md` (1 Critical, 6 High, ~30 Medium, ~20 Low) để 75 test `bug_*` chuyển xanh, không làm đỏ test nào khác.

**Cách làm:** test `bug_*` có sẵn chính là bước "test đỏ" của TDD. Mỗi batch: Sonnet sửa code tới khi các `bug_*` của batch xanh → đổi tên `bug_` → `regression_` và bỏ `@Tag("bug")` (giữ nguyên assertion) → chạy toàn bộ unit → Opus review → Sonnet sửa theo review → Opus xác nhận → PR. Lỗi chưa có test (`M-10`, `M-11`, `M-19`…) thì Sonnet **viết test đỏ trước** trong `backend/src/test/java/com/ebike/qa/unit/...` rồi mới sửa.

**Tech stack:** Spring Boot 3 / Java 21, JPA + MariaDB, Thymeleaf + JS thuần (`static/js`, `static/css/taste.css`), JUnit 5 + Mockito, Selenium (Chrome headless).

**Đặc tả:** `QA/BAO_CAO_KIEM_THU.md` + `QA/findings.md` (có file:dòng cho từng lỗi). Chuẩn đối chiếu là code hiện tại + README.

## Ràng buộc chung

- Nhánh gốc: `origin/develop` @ `7dc142a` (đã có QA). **Không** dùng `main` (main là bản cũ 31/10). Brief mỗi agent bắt đầu bằng `git fetch && git switch -c <nhánh> origin/develop` và kiểm tra có thư mục `QA/`.
- Mỗi batch rebase lên `develop` mới nhất trước khi review (các batch đụng chung `AuthenticationServiceImpl`, `ManagerServiceImpl`).
- Một agent chạy tại một thời điểm (giới hạn usage, chung `~/.m2`).
- Maven: `/c/Users/lemin/.m2/wrapper/dists/apache-maven-3.10.0-bin/39q0qgmngdu329psje7bjgne79/apache-maven-3.10.0/bin/mvn`, chạy offline `-o` trong `backend/`.
- Không ghi mật khẩu DB, khoá Gemini, khoá VNPay vào file — chỉ qua biến môi trường.
- Thông báo lỗi trả về client bằng **tiếng Việt**, cùng giọng với thông báo hiện có. Lỗi validate → **400**, không bao giờ 500.
- Không sửa assertion của test `bug_*`. Nếu Sonnet thấy test sai → báo lại, Opus quyết định.
- Thay đổi schema: sửa thẳng `backend/database/setup_ebike_database.sql` (file SQL duy nhất, không CHECK constraint) + một file `update_*.sql` nhỏ cho DB đang chạy.
- Commit: một dòng tiêu đề ngắn, không trailer Co-Authored-By.

## Trọng tâm review (Opus kiểm ở mọi batch)

1. Sửa đúng gốc chứ không vá triệu chứng (vd. U-01: bộ đếm phải thật sự được lưu khi ném lỗi — kiểm `REQUIRES_NEW`/transaction chứ không chỉ mock).
2. Không phá luồng đúng đã ghi ở mục 5 báo cáo (IDOR, ma trận trạng thái, VNPay chữ ký/idempotent, rate limit login).
3. Dữ liệu cũ trong DB: validate mới không được làm hỏng việc đọc/sửa bản ghi cũ đang có dữ liệu "xấu" (email cũ không hợp lệ, tên null).
4. Giao diện: JS đổi theo API (mã lỗi, field mới), chạy được ở 375 px, sáng/tối.
5. Tiền: mọi đường cộng/trừ tiền (khuyến mãi, doanh thu, IPN, hoàn tiền) không âm, không đếm trùng.

---

## Quy trình mỗi batch (áp dụng cho B1…B10)

- [ ] **1. Opus (phiên chính) viết brief** từ bảng của batch: mã lỗi, file, test phải xanh, quyết định đã chốt.
- [ ] **2. Sonnet thực hiện** — `Agent(model: "sonnet", isolation: "worktree")`:
  - chạy test của batch, xác nhận đang đỏ: `mvn -o test -Dtest='<Lớp>#bug_*' -Dsurefire.failIfNoSpecifiedTests=false`
  - viết test đỏ cho lỗi chưa có test
  - sửa code → test xanh → đổi tên `regression_`, bỏ `@Tag("bug")`
  - chạy **toàn bộ unit** (cũ + QA): `mvn -o test -DexcludedGroups=selenium` — chỉ còn đỏ các `bug_*` của batch khác
  - commit, báo cáo: file đã sửa, lệnh + kết quả test, điểm còn nghi ngờ
- [ ] **3. Opus review** — `Agent(model: "opus")` trên `git diff origin/develop...HEAD`:
  - đọc từng hunk, đối chiếu với findings; kiểm 5 điểm "Trọng tâm review"
  - tự chạy lại test của batch + toàn bộ unit
  - tìm ca biên chưa test (null, chuỗi rỗng, dữ liệu cũ, gọi đồng thời) và yêu cầu thêm test nếu đáng
  - trả danh sách lỗi xếp theo mức độ, mỗi lỗi có file:dòng + kịch bản hỏng
- [ ] **4. Sonnet sửa theo review** (SendMessage cho cùng agent) → **Opus xác nhận lại** chỉ các điểm đã nêu. Lặp tới khi Opus không còn lỗi "important".
- [ ] **5. Selenium trên app thật (mọi batch):** Sonnet build jar từ nhánh, chạy app cổng 8088 với DB `ebike_qa` (mật khẩu DB qua env), hộp thư giả `QA/tools/smtp-sink.js` 2525/2526, khoá VNPay giả, `APP_RATE_LIMIT_ENABLED=false`; chạy toàn bộ `com.ebike.qa.selenium` + kiểm tay qua API các lỗi của batch (bản thứ hai cổng 8089 bật rate limit khi cần). Mong đợi: chỉ đỏ các `bug_*` Selenium của batch khác. Opus đối chiếu kết quả trước khi push/merge.
- [ ] **6. Push + mở PR vào `develop`**, cập nhật `QA/BAO_CAO_KIEM_THU.md` cột "Đã sửa ở PR #…".

---

## Batch B1 — Bảo mật OTP và đăng nhập (Critical) · `fix/qa-otp-auth`

| Lỗi | Sửa | File |
|---|---|---|
| U-01 | Bộ đếm sai mã được lưu dù ném lỗi: tách ghi bộ đếm sang bean/method `@Transactional(propagation = REQUIRES_NEW)` (hoặc lưu rồi trả kết quả, ném lỗi ngoài transaction). Khoá mã sau 3 lần sai. | `service/order/impl/OrderEmailVerificationServiceImpl.java:78-152` |
| U-01 | Thêm rule rate limit cho `POST /api/v1/orders/email-verification/verify` và `POST /api/v1/auth/register` (cùng kiểu rule login). | `security/filter/ServerRateLimitFilter.java` |
| U-02 | Kiểm `resendAvailableAt` khi gửi lại; trong 60 s → 429 kèm số giây còn lại. | như trên |
| U-07 | Đăng nhập: user không tồn tại và sai mật khẩu cùng trả **401** + cùng thông báo. | `service/auth/impl/AuthenticationServiceImpl.java` |
| C-01 | `VnPayController.resolveClientIp` dùng `getRemoteAddr()` (giống rate limiter). | `controller/.../VnPayController.java` |

Test phải xanh: `OrderEmailVerificationServiceImplTest#bug_wrongOtpAttemptsAreRolledBack…`, `#bug_resendCooldownIsNotEnforced…`, `AuthenticationServiceTest#bug_login_unknownUserMustBeIndistinguishable…`, Selenium `KnownDefectsTest#bug_loginAnswersDifferently…`. Test mới: rule rate limit cho verify/register trong `qa/unit/ops/ServerRateLimitFilterTest`; **test tích hợp** `@SpringBootTest`/`@DataJpaTest` chứng minh bộ đếm còn sau rollback (mock không chứng minh được).
Opus kiểm thêm: JS đăng nhập (`login`) không còn dựa vào 404; OTP đúng sau khi bị khoá vẫn bị từ chối.

## Batch B2 — CSS và banner (High, nhanh) · `fix/qa-css-banner`

| Lỗi | Sửa | File |
|---|---|---|
| M-02 | Chuyển 2 `@import` lên dòng đầu `taste.css`. | `static/css/taste.css:1-8` |
| M-01 | Khôi phục `static/images/Kinetic-Banner.jpg` từ `9c34f60^` (xem Quyết định D4). | `taste.css:98` |

Test: `KnownDefectsTest#bug_tasteCssImports…`, `#bug_homeHeroBackgroundImageIsMissing`. Opus kiểm: font/icon tải thật (Selenium + xem 375 px), icon Phosphor hiển thị trên 7 trang.

## Batch B3 — Lỗi liên quan tiền · `fix/qa-money`

| Lỗi | Sửa | File |
|---|---|---|
| U-06 | `createPromotion` chặn `maxDiscountAmount` ≤ 0 giống `updatePromotion` (0/null = không giới hạn theo cách update đang làm). | `service/admin/impl/AdminServiceImpl.java:142` vs `:170` |
| U-10, M-24 | Thuế trước bạ 0–100 %; phí giao có trần (D5); phí dịch vụ hồ sơ > 0. | `AdminServiceImpl` (pricing) |
| M-21 | IPN thành công cho đơn đã huỷ: xem D1. | `service/order/impl/VnPayServiceImpl.java` |
| U-03 | IPN thành công chỉ đổi trạng thái đơn khi đơn đang `PENDING`; các trạng thái sau giữ nguyên. | `VnPayServiceImpl.java:324` |
| U-04 | Đơn tổng 0 không tạo URL VNPay (400). | `VnPayServiceImpl.java:124` |
| M-25, U-11 | Doanh thu dashboard và `totalSpent` chỉ tính thanh toán PAID của đơn không huỷ, trừ khoản REFUNDED — dùng **cùng một hàm** với báo cáo doanh thu tháng. | `service/manager/impl/ManagerServiceImpl.java:617-640` |
| M-22 | Hoàn tiền bắt buộc ghi chú không rỗng. | `ManagerServiceImpl` (refund) |

Test: 5 `bug_` trong `AdminServicePricingPromotionsTest`, `VnPayServiceImplTest` (2), `ManagerDashboardAndReportTest`, `ManagerPaymentsAndCustomersTest#bug_customers_totalSpent…`. Test mới: M-21, M-22.
Opus kiểm: số dashboard = số báo cáo tháng trên cùng dữ liệu; IPN vẫn idempotent, vẫn trả đúng mã 97/04/01.

## Batch B4 — Validate dữ liệu đầu vào · `fix/qa-validation`

| Lỗi | Sửa | File |
|---|---|---|
| U-08 | Handler `DataIntegrityViolationException` → 400 thông báo chung (lưới an toàn; validate thật ở dưới). | `common/exception/ApiExceptionHandler.java` |
| M-12, M-13, U-09, M-23, U-15 | Một lớp validator dùng chung (`common/validation/AccountFieldRules`): email đúng định dạng ≤ 255; họ/tên bắt buộc, ≤ 100; username không khoảng trắng, không dạng email, ≤ 100; mật khẩu ≥ **8** ở server (khớp form); trim nhất quán (cả register và login **không** trim mật khẩu). Áp cho register, update profile, admin create. Admin create: email không có "@" → 400, username tự sinh không rỗng. | `AuthenticationServiceImpl`, `AdminServiceImpl` |
| M-12 | Form profile: `type="email"`, đổi email cần mật khẩu hiện tại (D6). | template profile + JS |
| U-15 | Regex email support bỏ dấu chấm cuối. | `SupportService` |
| M-10, M-17 | Tỉnh/thành phải thuộc danh sách tỉnh hiện có (quote, order, sổ địa chỉ). | service quote/order/address |
| M-11 | Tối đa 10 xe **mỗi sản phẩm cộng mọi màu**. | service quote/order + cart |

Test: 12 `bug_` trong `AuthenticationServiceTest`, 5 trong `AdminServiceAccountsTest`, `ApiExceptionHandlerTest`, `SupportServiceTest`, Selenium `#bug_profileEmailField…`. Test mới: M-10, M-11, M-17.
Opus kiểm: tài khoản cũ có email/tên "xấu" vẫn đăng nhập và sửa profile được (chỉ validate trường gửi lên); selenium/seed account vẫn hợp lệ với mật khẩu ≥ 8.

## Batch B5 — Giao nhận và đơn hàng · `fix/qa-fulfilment`

| Lỗi | Sửa | File |
|---|---|---|
| M-18, U-12 | Trạng thái giao nhận chỉ đi tới; khoá sau khi đơn DELIVERED; không cho giao nhận lùi khi đơn SHIPPED. RETURNED: xem D3. | `ManagerServiceImpl.java:446, 768-780` |
| M-19 | Khoá sửa số khung/số máy khi đơn DELIVERED. | `ManagerServiceImpl` (units) |
| M-20 | PAY_LATER: xem D2. | `ManagerServiceImpl` (status) |
| U-05 | `limit < 1` → dùng mặc định, luôn ≤ 2000. | `OrderServiceImpl.java:186` |

Test: 2 `bug_shipment_*` trong `ManagerFulfilmentAndStockTest`, `OrderServiceCustomerTest#bug_limitBelowOne…`. Test mới: M-19, M-20, D3.
Opus kiểm: nút "Lưu giao nhận" trong `workspace.js` khoá đúng; ma trận trạng thái cũ vẫn đúng.

## Batch B6 — Phân quyền và quyền riêng tư · `fix/qa-access-privacy`

| Lỗi | Sửa |
|---|---|
| U-14 | `user:view` không đọc được tài khoản có role ADMIN (trừ admin); ghi địa chỉ người khác cần quyền ghi, không chỉ `user:view`. Danh sách khách của manager loại tài khoản có role ADMIN. |
| U-13 | Tự xoá tài khoản: xoá/ẩn danh IP + user agent trong `AuthenticationLog` như Javadoc. |
| M-15 | Đơn người khác: `GET /orders/{id}` trả 404 giống `/receipt`. |

Test: 3 `bug_` trong `UserProfileServiceTest`, `ManagerPaymentsAndCustomersTest#bug_customers_accountWithAdmin…`, `AccountErasureTest`. Opus kiểm: mục "Phân quyền / IDOR" ở mục 5 báo cáo vẫn đạt.

## Batch B7 — Tìm kiếm, lọc, ảnh sản phẩm · `fix/qa-catalog`

| Lỗi | Sửa | File |
|---|---|---|
| U-16 | Tìm không dấu: thêm cột `search_text` (tên + mô tả đã bỏ dấu, `đ→d`, chữ thường) cập nhật ở `@PrePersist/@PreUpdate`, backfill lúc khởi động cho dòng null; so `LIKE` trên cột này với chuỗi truy vấn đã fold. Escape `%`, `_`, `\`. Lowercase với `Locale.ROOT`. | `ProductService.java:180-188`, entity `Product`, setup SQL |
| U-17 | Lọc giá dùng giá hiệu lực `coalesce(discountPrice, price)`. | `ProductService.java:172-178` |
| M-06 | min > max → 400 có thông báo. | `ProductService` |
| U-20 | Sắp xếp tên theo `Collator` tiếng Việt; bỏ mặc định brand "Yadea-style"; Gemini không retry 400/401/403. | `ProductService`, `ProductSpecification`, `GeminiChatClient` |
| U-18 | Kiểm magic bytes (JPEG/PNG/WebP), từ chối SVG; cắt tên file để key S3 ≤ 1024 byte. | `AdminProductImageService.java:264-270` |

Test: 7 `bug_` trong `ProductServiceTest`, 3 trong `AdminProductImageServiceTest`, `ProductEntitiesTest`, `GeminiChatClientTest`.
Opus kiểm: phân trang + sắp xếp vẫn đúng; ảnh hiện có trên S3 không bị ảnh hưởng; DB `ddl-auto=validate` khởi động được với setup SQL mới.

## Batch B8 — Giao diện, trang lỗi, accessibility · `fix/qa-ui`

| Lỗi | Sửa |
|---|---|
| M-03, M-04 | Route trang HTML (không phải `/api/**`) lỗi 400/404 → trang lỗi Thymeleaf có header/điều hướng; `/api/**` giữ JSON. |
| M-09, UI-03 | Mã khuyến mãi sai: giữ bảng giá, hiện lỗi cạnh ô mã, nút "Bỏ mã"; lỗi server hiện cạnh trường liên quan, `aria-describedby`. |
| S-01, S-02, S-03 | Cột quyền ở `/admin/roles` (`workspace.js:105,188`); bỏ khoá trùng map `names` (`workspace.js:5`); bỏ code chết hoặc bỏ `required` để thông báo riêng hiện. |
| M-14, M-08 | Nhãn tiếng Việt, enum hiển thị tiếng Việt; một hàm định dạng giá `vi-VN`; thông số bỏ `.00`. |
| UI-01, UI-02 | Vùng bấm ≥ 24 px (nút đổi giao diện, link header, "Xóa bộ lọc"); `aria-label` ô tìm kiếm; skip link `/checkout`; chữ ≥ 12 px trang chi tiết. |
| M-07 | Một fragment header dùng chung cho mọi trang storefront. |
| M-16 | Thay `confirm()/prompt()` bằng một dialog dùng chung (`<dialog>`) — 11 luồng (D7: có thể tách PR riêng). |

Test: Selenium `#bug_unknownProduct…`, `#bug_unknownVehicleType…`, `#bug_invalidPromoCode…`, `#bug_rolesPage…`; chạy lại `ThemeResponsiveTest`, `CheckoutGuestTest`, `AdminTest`. Selenium đang dùng `confirm()` (M-16) phải sửa cùng lúc.
Opus kiểm: dùng skill ui-ux-pro-max checklist; 375 px không cuộn ngang; sáng/tối.

## Batch B9 — Chatbot · `fix/qa-chatbot`

U-19, M-26: khớp FAQ theo từ nguyên (không theo chuỗi con), đọc mọi mức ngân sách ("dưới 200 triệu" = 200 tr), câu hỏi giá trống sau gợi ý là follow-up dùng xe vừa gợi ý + giá từ DB, câu hỏi showroom trả danh sách showroom, "quang duong" → intent quãng đường, lỗi Gemini runtime → fallback (không 500), phát hiện câu trả lời bị cắt, fallback PDF có dấu, định dạng tiền `vi-VN` cố định, prompt có đoạn chống chèn lệnh. File `service/chatbot/ChatbotService.java` (`:85-87`, `:778-794`).
Test: 13 `bug_` trong `ChatbotServiceTest`. Opus kiểm thêm 1 hội thoại thật với Gemini (khoá qua env).

## Batch B10 — Lỗi thấp, dữ liệu, tài liệu · `fix/qa-low-docs`

- NPE: `TestRideRules.canCustomerCancel(null)`, `WarrantyRules.isKnownStatus(null)` → false, `MaintenanceMilestones.describe(null)`; `StockHistoryService.java:324` `beforeId <= 0` là trang đầu.
- M-05: phân loại lại 8 xe danh mục "Xe đạp điện" (D8) trong setup SQL + `update_*.sql`; sửa mô tả "xe đạp điện có bàn đạp".
- Tài liệu: `ARCHITECTURE.md` (giỏ hàng = session server; rate limiter dùng `getRemoteAddr`), README/Javadoc bỏ hứa VI/EN.
- Ghi chú triển khai: rate limit trong bộ nhớ từng JVM (chỉ ghi tài liệu).

Test: `TestRideRulesLogicTest`, `WarrantyRulesTest` (2), `StockHistoryAndLedgerTest`.

## Batch B11 — Hồi quy toàn bộ (cuối)

- [ ] Gộp mọi PR vào `develop`; tạo lại DB `ebike_qa` từ setup SQL mới (bỏ dòng `CREATE DATABASE/USE`).
- [ ] Unit toàn bộ: mong đợi **0** test `bug_*` còn lại, 0 đỏ.
- [ ] Selenium 180+ trên app cổng 8088; sửa test chập chờn `StorefrontTest.product_savingsCalculatorMatchesTheApi` (chờ ổn định).
- [ ] Opus review toàn nhánh `develop` so với `7dc142a` (một lượt cuối).
- [ ] Cập nhật `QA/BAO_CAO_KIEM_THU.md`: bảng lỗi thêm cột trạng thái + PR.
- Ngoài phạm vi: SMTP thật, VNPay sandbox thật (chờ người dùng có khoá).

---

## Quyết định cần chốt (mặc định đề xuất in đậm)

| # | Câu hỏi | Đề xuất |
|---|---|---|
| D1 | IPN thành công đến muộn cho đơn đã huỷ (M-21) | **Trả "00" cho VNPay, đặt thanh toán `REFUND_PENDING`, ghi audit, đơn vẫn CANCELLED, hiện cho manager hoàn tiền** (tiền đã bị trừ, từ chối IPN không hoàn được tiền). |
| D2 | PAY_LATER giao khi chưa thu tiền (M-20) | **Chặn chuyển DELIVERED khi thanh toán chưa PAID** (manager bấm "xác nhận đã thu" trước). |
| D3 | Giao nhận RETURNED | **Chỉ cho RETURNED khi đơn chưa DELIVERED; khi RETURNED thì hoàn kho và đơn → CANCELLED** (lý do "giao không thành công"). |
| D4 | Banner trang chủ (M-01) | ~~Khôi phục ảnh cũ từ git `9c34f60^`.~~ **Đã đổi:** ảnh cũ có watermark "SnapEdit" và là xe hãng khác, đã bị gỡ có chủ đích → hero dùng `/images/catalog/kinetic-butterfly/source-03.png` (như `store.css`). |
| D5 | Trần phí giao hàng | **5.000.000 ₫/xe.** |
| D6 | Đổi email ở profile | **Cần nhập mật khẩu hiện tại.** |
| D7 | M-16 (thay 11 `confirm/prompt`) | **Làm trong B8 nhưng commit riêng; nếu dài thì tách PR.** |
| D8 | 8 xe "Xe đạp điện" thực chất là xe máy điện (M-05) | **Chuyển sang danh mục "Xe máy điện"** (không đổi thông số). |
| D9 | Push/PR | **Sau khi Opus duyệt: push nhánh + mở PR vào develop.** Cập nhật 27/11: người dùng cho phép tự merge khi Opus duyệt + Selenium/kiểm tay đạt + CI xong (Unit test chỉ đỏ đúng số `bug_*` như local, Selenium xanh). |

## Thứ tự và ước lượng

B1 → B2 → B3 → B4 → B5 → B6 → B7 → B9 → B10 → B8 → B11. (B8 để gần cuối vì đụng Selenium nhiều nhất và cần chạy app thật.) Mỗi batch ≈ 1 agent Sonnet + 1–2 lượt Opus.
