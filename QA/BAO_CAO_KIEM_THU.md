# BÁO CÁO KIỂM THỬ — EBikeStore (Kinetic)

Ngày: 26/11/2026 · Phiên bản kiểm thử: `origin/develop` @ `6b09bdf` · Nhánh QA: `claude/determined-moser-d00a1b`
Nguyên tắc: **chỉ kiểm thử, không sửa code sản phẩm**. Chuẩn đối chiếu là **code hiện tại** (file Word đặc tả chưa hoàn thành).

## 1. Phạm vi

| Hạng mục | Nội dung |
|---|---|
| Unit test mới | `backend/src/test/java/com/ebike/qa/unit/` — 5 nhóm: booking, order, backoffice, catalog, ops |
| Selenium mới | `backend/src/test/java/com/ebike/qa/selenium/` — 15 lớp, page objects trong `pages/` |
| Chạy tay | Chrome (Claude in Chrome) + trình duyệt tích hợp (màn 375 px), API probe `QA/tools/probe.mjs` |
| Đánh giá UI | Checklist ui-ux-pro-max (tương phản, vùng bấm, nhãn, heading, form, responsive) |
| Không dùng | Bộ test cũ có sẵn trong dự án (không đọc lại, không chạy, không sửa) |

## 2. Môi trường

- App: jar build từ `6b09bdf`, Java 21, cổng 8088, DB riêng `ebike_qa` (MariaDB, tạo từ script setup đã bỏ dòng `CREATE DATABASE/USE`).
- Rate limit tắt cho Selenium; kiểm tra rate limit trên bản thứ hai cổng 8089 (bật).
- SMTP: **hộp thư giả** chạy cục bộ 127.0.0.1:2525 (xem thư qua :2526/mails).
- VNPay: **khoá giả** `QATEST01`; IPN/return được mô phỏng bằng cách tự ký HMAC-SHA512.
- Gemini: khoá thật của người dùng (chỉ qua biến môi trường).
- Lệnh chạy:
  - Unit: `mvn -o test -Dtest='com.ebike.qa.unit.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false`
  - Selenium: `mvn -o test -Dtest='com.ebike.qa.selenium.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dqa.baseUrl=http://localhost:8088` (thêm `-Dgroups=bug` để chỉ chạy test lỗi)

## 3. Kết quả chạy cuối

| Bộ test | Số test | Xanh | Đỏ do lỗi sản phẩm (`bug_*`) | Đỏ khác | Thời gian |
|---|---|---|---|---|---|
| Unit (booking, order, backoffice, catalog, ops) | 2.677 | 2.608 | 69 (65 failure + 4 error, từ 67 phương thức `bug_`, một số có tham số) | 0 | 1:11 phút |
| Selenium (15 lớp) | 180 | 171 | 8 (`KnownDefectsTest`) | 1* | 10:35 phút |
| **Tổng** | **2.857** | **2.779** | **77** | **1*** | |

\* `StorefrontTest.product_savingsCalculatorMatchesTheApi` lỗi `StaleElementReference` trong lượt chạy toàn bộ; chạy lại riêng thì **đạt**. Đây là test chập chờn do script chờ phần tử (lỗi phía QA, không phải lỗi sản phẩm), cần thêm chờ ổn định.

Không có test thường nào đỏ: mọi test đỏ đều là test lỗi có chủ đích.

Commit theo nhóm: `fdcfe9e` booking (768), `213737b` order (635), `e82bf9b` backoffice (589), `0232d0a` catalog (398), `02b7d87` ops (287), `d3bb2d3` Selenium (180).

Quy ước: mỗi lỗi sản phẩm được giữ lại dưới dạng test **cố ý đỏ**, tên `bug_*`, gắn `@Tag("bug")`. Test đỏ = lỗi còn tồn tại; khi lỗi được sửa test sẽ xanh.

## 4. Lỗi theo mức độ

Mã: **M** = chạy tay, **U** = unit test, **S** = Selenium, **UI** = đánh giá giao diện, **C** = đọc code. "Live" = đã tái hiện trên app đang chạy.

### 4.1 Nghiêm trọng (Critical)

| Mã | Lỗi | Tái hiện |
|---|---|---|
| U-01 | **OTP xác minh email có thể dò.** Bộ đếm sai mã bị rollback vì service `save` rồi `throw` trong cùng `@Transactional` (`OrderEmailVerificationServiceImpl.java:78-152`), nên giới hạn 3 lần không bao giờ có hiệu lực; endpoint `POST /api/v1/orders/email-verification/verify` cũng không có rule rate limit. | Live: gửi OTP, nhập sai 5 lần, nhập đúng → 200 verified. Thử 8 lần verify trên bản bật rate limit → không có 429. |

### 4.2 Cao (High)

| Mã | Lỗi | Tái hiện |
|---|---|---|
| M-02 | `taste.css` có rule ở dòng 1 trước hai `@import` (dòng 7-8) → trình duyệt bỏ cả hai: font Google và icon Phosphor **không tải trên toàn site**; mọi `<i class="ph …">` trống. | Mở `/`, DevTools: 0 `CSSImportRule`, không có request font/icon. (Selenium `KnownDefectsTest`) |
| M-01 | Banner hero trang chủ `/images/Kinetic-Banner.jpg` 404 → khối lớn nhất trang chủ là ô xám trống (cả desktop và mobile). | `GET /images/Kinetic-Banner.jpg` → 404. |
| M-12 | `PUT /api/v1/auth/profile` lưu email "abc", tên rỗng (xoá thành null); tên 300 ký tự → 500; ô email trên form là `type=text`; đổi email không cần mật khẩu. | Live qua API và `/customer/profile`. |
| M-23 / U-09 | Admin tạo tài khoản với email "zzz" → 201; email không có "@" và không có username → 500 (StringIndexOutOfBounds); "@shop.com" → username rỗng. | `POST /api/v1/admin/accounts`. |
| M-18 / U-12 | Đơn đã DELIVERED vẫn đổi được giao nhận DELIVERED→PENDING→RETURNED; SHIPPED→PENDING trong khi đơn vẫn SHIPPED; RETURNED không hoàn kho, không đổi trạng thái đơn. | `PATCH /api/v1/manager/orders/{id}/shipment`. |
| U-06 | Tạo khuyến mãi với `maxDiscountAmount` âm → khuyến mãi thành **phụ phí** (`AdminServiceImpl.java:142`, update ở :170 thì có chặn). | Live: cap -50.000 → giảm -50.000, tổng 16.850.000 > tạm tính 16.800.000. |

### 4.3 Trung bình (Medium)

| Mã | Lỗi |
|---|---|
| M-03 | Trang HTML trả JSON thô khi tham số lọc sai: `/products?vehicleType=XYZ`, `maxPrice=abc`, `page=abc`, `inStock=maybe` → 400 JSON. |
| M-04 | `/product/<slug không tồn tại>` (và `/bike`, `/models`) → JSON 404 thay vì trang 404 có điều hướng. |
| M-05 | Cả 8 sản phẩm danh mục "Xe đạp điện" là `E_MOTORBIKE` (35-45 km/h, 1000-1550 W) — trái quy tắc README (>25 km/h hoặc >250 W là xe máy điện). |
| M-09 | Checkout: nhập mã khuyến mãi sai (vd. `KINETIC20`) → toàn bộ bảng giá biến mất, nút "Đặt hàng" bị khoá, không có nút bỏ mã. |
| M-10 | Báo giá/đặt hàng chấp nhận tỉnh không tồn tại ("Atlantis") và tính phí xa 800k/xe. |
| M-11 | Giới hạn "tối đa 10 xe mỗi sản phẩm" chỉ áp theo từng màu: 3 màu × 10 = 30 xe vẫn 200. |
| M-13 | Đăng ký: tên 300 ký tự → 500; username có khoảng trắng được chấp nhận; server yêu cầu mật khẩu ≥ 6, form yêu cầu ≥ 8. |
| M-19 | Sửa số khung/số máy sau khi đã giao (`PUT …/units`) — ảnh hưởng sổ bảo hành. |
| M-20 | Đơn trả sau (PAY_LATER) giao được khi thanh toán vẫn PENDING. |
| M-21 | IPN VNPay thành công đến muộn cho đơn khách đã huỷ → trả "00 Confirm Success", đơn CANCELLED + thanh toán PAID (đã hoàn kho). Tiền bị thu cho đơn đã huỷ. |
| M-25 / U-11 | Doanh thu hôm nay trên dashboard 50.400.000 trong khi báo cáo doanh thu tháng 16.800.000: dashboard cộng cả khoản đã hoàn tiền và đơn đã huỷ (`ManagerServiceImpl.java:617-624`). |
| M-26 / U-19 | Chatbot: hỏi tiếp "giá bao nhiêu?" không nhớ xe vừa gợi ý và nói "chưa có bảng giá" (có Gemini). Rule engine: khớp từ khoá FAQ theo chuỗi con, ngân sách khác 20 triệu bị bỏ qua, "dưới 200 triệu" hiểu thành 20 triệu, lỗi Gemini runtime → 500. |
| U-02 | Thời gian chờ gửi lại OTP 60 s không được kiểm tra. Live: gửi 2 lần liên tiếp đều 200, 2 thư. |
| U-03 | IPN thành công kéo đơn PROCESSING/READY_FOR_PICKUP/SHIPPED về CONFIRMED (`VnPayServiceImpl.java:324`). |
| U-07 | Lộ tài khoản tồn tại khi đăng nhập: user không tồn tại 404, sai mật khẩu 401 (live, cả Selenium). |
| U-08 | Không có handler cho `DataIntegrityViolationException` → 500 với trường quá dài (gốc của M-12, M-13). |
| U-10 | Bảng giá: thuế trước bạ > 100% được chấp nhận; phí giao không có trần. |
| U-13 | Tự xoá tài khoản vẫn giữ IP và user agent trong nhật ký đăng nhập, trái Javadoc. |
| U-14 | Quyền `user:view` cho manager đọc tài khoản ADMIN qua `GET /users/{id}`. |
| U-16 | Tìm kiếm không bỏ dấu: `q=đạp` → 8 xe, `q=dap` → 0 (live). Ký tự `%`/`_` không được escape. |
| U-17 | Lọc theo giá bỏ qua giá ưu đãi trong khi thẻ sản phẩm và sắp xếp dùng giá ưu đãi. |
| U-18 | Upload ảnh sản phẩm tin `Content-Type` của client: file EXE khai `image/png`, SVG chứa script đều được nhận. |
| UI-01 | Vùng bấm < 24 px (WCAG 2.2 AA): nút đổi giao diện 16×40 trên mọi trang, link header cao 19-23 px, "Xóa bộ lọc" 13 px. |
| UI-03 | Lỗi server ở checkout hiện cuối thẻ tóm tắt, xa ô nhập (vd. lỗi họ tên), không có `aria-describedby`. |
| S-01 | `/admin/roles` không bao giờ hiện cột quyền: `workspace.js:105` yêu cầu cột `permissions` nhưng `workspace.js:188` lọc bỏ nó. |

### 4.4 Thấp (Low)

- M-06: min > max (giá, quãng đường) trả danh sách rỗng không báo gì.
- M-07: menu header khác nhau giữa trang chủ và `/products`.
- M-08: định dạng giá lẫn "17,700,000 ₫" và "17.700.000 ₫"; thông số "35.00 km/h"; mô tả "xe đạp điện có bàn đạp" cho xe 1000 W.
- M-14: nhãn tiếng Anh "First Name/Last Name", "Bảng điều khiển customer", enum thô "PENDING".
- M-15: `GET /orders/{id}` đơn người khác → 403 kèm giải thích, `/receipt` → 404 (đoán được id đơn).
- M-16: 11 luồng dùng `confirm()`/`prompt()` gốc (lý do huỷ, OTP liên kết đơn…).
- M-17: sổ địa chỉ nhận tỉnh "Atlantis".
- M-22: hoàn tiền với ghi chú rỗng được chấp nhận.
- M-24: phí dịch vụ hồ sơ = 0 được chấp nhận.
- U-04: đơn tổng 0 vẫn tạo URL VNPay `vnp_Amount=0`.
- U-05: `GET /orders?limit=0` hoặc âm trả tất cả, vượt trần 2000.
- U-15: đăng ký trim mật khẩu, đăng nhập không trim; regex email hỗ trợ chấp nhận dấu chấm cuối.
- U-20: client Gemini thử lại cả lỗi 400/401/403; sắp xếp tên đặt chữ có dấu sau "z"; brand mặc định "Yadea-style" lộ ra JSON-LD.
- NPE với trạng thái null: `TestRideRules.canCustomerCancel`, `WarrantyRules.isKnownStatus`, `MaintenanceMilestones.describe`; `StockHistoryService.java:324` `beforeId<=0` bỏ qua số dư.
- UI-02: ô tìm kiếm header chỉ có placeholder; `/checkout` thiếu skip link; trang chi tiết có 14 đoạn chữ < 12 px.
- S-02: map `names` trong `workspace.js` trùng khoá nên nhật ký hiện "Ngày tạo/Thao tác/Mục tiêu" thay vì "Thời gian/Hành động/Đối tượng".
- S-03: thông báo "Vui lòng chọn cửa hàng nhận xe." không bao giờ hiện (HTML5 `required` chặn trước).
- C-01: `VnPayController.resolveClientIp` tin `X-Forwarded-For` (giả được `vnp_IpAddr`).
- Rate limit lưu trong bộ nhớ từng JVM: chạy nhiều instance thì mỗi instance có 5 lần/phút riêng.

### 4.5 Tài liệu lệch code

- `ARCHITECTURE.md` nói giỏ hàng ở `localStorage` và ở đoạn khác nói ở session server (code: session server).
- `ARCHITECTURE.md` nói rate limiter đọc `X-Forwarded-For`; code dùng `getRemoteAddr` (đã kiểm chứng live: XFF không lách được).
- Javadoc/README hứa thông báo VI/EN, code chỉ có tiếng Việt.
- File Word đặc tả chưa hoàn thành; có chỗ mô tả CORS và số bảng khác thực tế.

## 5. Những gì đạt

- **Phân quyền / IDOR:** đơn hàng, hoá đơn, địa chỉ, user, API admin/manager đều chặn đúng; customer gọi API manager → 403; JWT rác và `alg=none` → 401.
- **Đơn hàng:** ma trận chuyển trạng thái đúng; không ship trước PROCESSING; số khung/máy bắt buộc và kiểm tra 5-30 ký tự; huỷ đơn hoàn kho sản phẩm + màu và huỷ thanh toán; server bỏ qua `userId/shippingFee/discountAmount` client gửi lên.
- **VNPay:** chữ ký sai → 97, sai số tiền → 04, giao dịch lạ → 01, gửi lại IPN idempotent; return URL kiểm chữ ký; không tạo URL cho đơn người khác/đã trả.
- **Khuyến mãi:** không phân biệt hoa thường/khoảng trắng; mã hết hạn, chưa bắt đầu, vượt lượt bị từ chối; một khách không dùng lại mã.
- **Admin:** không tự khoá/hạ quyền/xoá chính mình; xoá cần đúng email xác nhận; khách có đơn không xoá được; nhật ký kiểm toán ghi IP.
- **Lái thử / trả góp / bảo hành:** khung giờ, 14 ngày, giờ mở cửa, trùng slot; thu nhập, CCCD, che số CCCD; từ chối cần ghi chú; bảo hành xe người khác → 404.
- **Bảo mật giao diện:** trang staff escape XSS; app chặn nhúng iframe; rate limit đăng nhập 5/phút với `Retry-After`, XFF không lách được.
- **Giao diện:** tương phản ≥ 4.5:1, một h1, không nhảy cấp heading, ảnh đều có alt, viewport cho phép zoom, hỗ trợ `prefers-reduced-motion`, chữ thân 16 px; 375 px không cuộn ngang trên các trang chính; Selenium chế độ sáng/tối và responsive đạt.
- **Giỏ hàng:** validate tốt; số lượng kẹp 99→10, -3→1.

## 6. Giới hạn của đợt kiểm thử

- **Email chưa kiểm với SMTP thật**: chỉ kiểm nội dung thư và việc gửi qua hộp thư giả cục bộ; chưa kiểm xác thực SMTP, TLS, spam, hiển thị trên hộp thư thật.
- **VNPay chưa kiểm với sandbox thật**: khoá giả nên trang VNPay báo "website không tồn tại"; IPN/return được mô phỏng bằng tự ký. Cần chạy lại 1 giao dịch sandbox thật (return + IPN, đơn CONFIRMED/PAID) khi có TMN code và hash secret.
- Selenium không tự động hoá lái thử DONE/NO_SHOW (khung giờ ở tương lai).
- Selenium chỉ chạy Chrome headless; Playwright MCP không kết nối được nên không thử Firefox/WebKit.
- Dữ liệu `ebike_qa` bị tiêu tồn kho qua các lần chạy; Selenium chọn xe còn nhiều hàng nhất (`Shop.buySlug()`).

## 7. Đề xuất ưu tiên sửa

1. U-01 (OTP) — tách lưu bộ đếm ra transaction riêng (`REQUIRES_NEW`) hoặc lưu rồi trả lỗi không ném exception; thêm rule rate limit cho verify và register.
2. M-02, M-01 — chuyển `@import` lên đầu `taste.css`, khôi phục ảnh banner.
3. U-06, M-21, M-25 — các lỗi liên quan tiền.
4. M-12, M-23, U-08 — validate email/độ dài và handler 400 thay vì 500.
5. M-18, M-19, M-20, U-03 — khoá trạng thái sau khi giao.
