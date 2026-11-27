# TIẾN ĐỘ SỬA LỖI QA — EBikeStore (Kinetic)

Cập nhật: 27/11/2026 (tạm dừng theo yêu cầu). Kế hoạch gốc: `QA/KE_HOACH_SUA_LOI.md`. Danh sách lỗi: `QA/BAO_CAO_KIEM_THU.md`, `QA/findings.md`.

## 1. Tóm tắt

| Batch | Nội dung | Trạng thái | PR / nhánh |
|---|---|---|---|
| B1 | OTP dò được (U-01), cooldown gửi lại (U-02), lộ tài khoản khi đăng nhập (U-07), IP VNPay giả được (C-01) | **Đã merge** | #42 → `9d82b81` |
| B1b | Gia cố OTP: cooldown + giới hạn theo người gửi, OTP lưu HMAC, giới hạn mỗi ngày, tài khoản bị khoá không lộ khi sai mật khẩu | **Đã merge** | #43 → `b8d59de` |
| B2 | `@import` CSS (M-02: font + icon toàn site), banner trang chủ (M-01) | **Đã merge** | #44 → `81009c0` |
| B3 | Lỗi liên quan tiền: U-06, U-10, M-24, M-21, U-03, U-04, M-25/U-11, M-22 | Opus **đã duyệt** (2 lượt); Selenium trên app thật **chưa chạy xong**; chưa push | nhánh local `fix/qa-money` |
| B4 | Validate đầu vào: U-08, M-12, M-13, U-09, M-23, U-15, M-10, M-17, M-11 | **Đang viết dở**, chưa chạy test, chưa review | nhánh local `fix/qa-validation` (commit WIP `20ec2e3`) |
| B5–B11 | Giao nhận, phân quyền, tìm kiếm/ảnh, chatbot, lỗi thấp/tài liệu, giao diện, hồi quy cuối | Chưa bắt đầu | — |

Test `bug_*` trên `develop` (sau B2): unit còn **64** tên đỏ (62 failure + 4 error), Selenium `KnownDefectsTest` còn **4** đỏ (2 trang JSON thô, mã khuyến mãi làm mất bảng giá, email profile, trang roles). Nhánh B3 làm xanh thêm 9 test unit (còn 55).

## 2. Cách làm việc (đã áp dụng cho mọi batch)

1. **Opus (phiên chính) viết brief** cho từng batch: mã lỗi, file:dòng, test phải xanh, quyết định đã chốt.
2. **Sonnet viết code** trong worktree riêng, nhánh tách từ `origin/develop` mới nhất (worktree của agent khởi tạo từ commit cũ nên brief luôn bắt đầu bằng `git fetch && git switch -c <nhánh> origin/develop`).
   - Test `bug_*` có sẵn là test đỏ của TDD: sửa tới khi xanh, **không đổi assertion**, rồi đổi tên `bug_` → `regression_` và bỏ `@Tag("bug")` ở phương thức.
   - Lỗi chưa có test → viết test đỏ trước.
   - Chạy toàn bộ `mvn -o test`; tập test đỏ phải đúng bằng tập `bug_*` của các batch khác (so tên với danh sách chuẩn).
3. **Opus review chi tiết** (agent riêng, chỉ đọc): đọc từng hunk, đối chiếu lỗi gốc, tìm ca biên, tự chạy lại toàn bộ test (trong worktree tạm nếu worktree tác giả đang bận). Trả lỗi xếp theo mức độ (important / nit).
4. **Sonnet sửa theo review → Opus kiểm lại** tới khi không còn lỗi "important".
5. **Selenium + kiểm tay trên app thật** (mọi batch, do người dùng yêu cầu 27/11): build jar từ nhánh, app cổng 8088, DB `ebike_qa`, hộp thư giả `QA/tools/smtp-sink.js` (2525/2526), khoá VNPay giả trong `qa-selenium.properties`, `APP_RATE_LIMIT_ENABLED=false`; bản thứ hai cổng 8089 bật rate limit khi cần. Chạy toàn bộ `com.ebike.qa.selenium` + kiểm từng lỗi của batch qua API/trình duyệt.
6. **Push + PR vào `develop`**. **Merge** khi: Opus duyệt, Selenium/kiểm tay đạt, CI xong với job *Unit test* chỉ đỏ đúng số `bug_*` như local và job *Selenium* xanh. (CI *Unit test* đỏ từ PR #41 vì các test `bug_*` cố ý đỏ — phải so số, không coi đỏ là ổn mặc định.)
7. Song song: trong lúc một batch chạy Selenium thì batch kế tiếp được viết code; tối đa **một agent viết code + một agent chạy app** cùng lúc (cổng dùng chung). Có vòng lặp 30 phút tự tiếp tục (đã tắt khi dừng).

Quy ước khác: commit một dòng ngắn, không trailer; thông báo lỗi tiếng Việt, lỗi validate trả 400 (không 500); mật khẩu DB, khoá Gemini, khoá VNPay chỉ truyền qua biến môi trường, không ghi vào file.

## 3. Quyết định đã chốt

| # | Nội dung | Quyết định |
|---|---|---|
| D1 | IPN VNPay thành công đến muộn cho đơn đã huỷ (M-21) | Trả "00", thanh toán PAID, đơn giữ CANCELLED, ghi nhật ký **sau commit**, manager thấy "Đã thanh toán, cần hoàn tiền"; khách thấy "Đã thanh toán — cửa hàng sẽ liên hệ hoàn tiền". |
| D2 | PAY_LATER giao khi chưa thu tiền (M-20) | Chặn DELIVERED khi chưa PAID (B5, chưa làm). |
| D3 | Giao nhận RETURNED | Chỉ khi chưa DELIVERED; hoàn kho, đơn → CANCELLED (B5, chưa làm). |
| D4 | Banner trang chủ (M-01) | Ban đầu: khôi phục ảnh cũ. **Đổi lại:** ảnh cũ có watermark và là xe hãng khác, từng bị gỡ có chủ đích (9c34f60) → dùng ảnh catalogue `kinetic-butterfly/source-03.png`. |
| D5 | Trần phí giao | 5.000.000 ₫/xe. |
| D6 | Đổi email ở profile | Cần mật khẩu hiện tại (B4). |
| D7 | Thay `confirm()/prompt()` (M-16) | Trong B8, commit riêng. |
| D8 | 8 xe "Xe đạp điện" là xe máy điện (M-05) | Chuyển danh mục (B10). |
| D9 | Push/merge | Tự merge khi đủ điều kiện ở mục 2.6. |
| B1b | 4 điểm gia cố sau B1 | Làm cả 4 (người dùng: "hoàn thành 4 điểm"). |
| B3-1 | Phí dịch vụ hồ sơ = 0 | Giữ quy tắc > 0 (dịch vụ là tuỳ chọn theo đơn, bảng giá có trạng thái ACTIVE). |
| B3-2 | Đơn tổng thanh toán = 0 | Từ chối khi tạo đơn và khi báo giá: 400 "Tổng thanh toán của đơn phải lớn hơn 0." (trả góp tối thiểu 20% nên không ảnh hưởng). |
| B3-3 | Dashboard bỏ qua thanh toán thiếu `paidAt` | Dùng chung cách tính với báo cáo (lấy ngày đặt đơn); không cần SQL (0 dòng thiếu). |

## 4. Chi tiết từng batch

### B1 — PR #42 (đã merge)
- U-01: bộ đếm sai OTP được lưu (`@Transactional(noRollbackFor = ResponseStatusException.class)` trên `verifyCode`), sai 3 lần khoá phiên; phiên đọc bằng `findLockedById` (`PESSIMISTIC_WRITE`) để gọi song song không vượt giới hạn và một mã không đặt được hai đơn. Rate limit mới: verify 10/5 phút, register 5/10 phút.
- U-02: gửi lại trong 60 s → 429 "Vui lòng đợi N giây…".
- U-07: user không tồn tại → 401 cùng body với sai mật khẩu (bcrypt giả cho thời gian tương đương).
- C-01: `VnPayController.resolveClientIp` dùng `getRemoteAddr()`.
- Kiểm: Opus 2 lượt; unit 3776 (chỉ `bug_*` đỏ); Selenium 180 (7 `bug_*` đỏ như dự kiến); kiểm tay OTP/cooldown/login/rate limit (8089)/IP VNPay đều đạt.

### B1b — PR #43 (đã merge)
- Cooldown và giới hạn theo (email, người gửi): cột mới `requester_key` = HMAC của IP (không lưu IP gốc). Giới hạn/ngày: 3 phiên bị khoá và 5 lần gửi cho mỗi người gửi, 50 thư mỗi email.
- OTP lưu `HMAC("otp:"+code)` với khoá `APP_ORDER_EMAIL_OTP_HMAC_SECRET`; **profile prod không khởi động nếu thiếu khoá**; dev tự tạo khoá ngẫu nhiên + cảnh báo.
- Đăng nhập: kiểm mật khẩu trước; bị khoá + sai mật khẩu → 401 như mọi trường hợp; đúng mật khẩu → 403 "Tài khoản đang bị khóa."; tài khoản đã xoá → 401.
- DB: `setup_ebike_database.sql` + `backend/database/update_otp_requester_key.sql` (đã chạy trên `ebike_qa`).
- **Khi deploy:** đặt `APP_ORDER_EMAIL_OTP_HMAC_SECRET` và chạy `update_otp_requester_key.sql`.
- Kiểm: Opus 3 lượt; Selenium 7 `bug_*` đỏ như dự kiến; kiểm tay (127.0.0.1 vs ::1, HMAC trong DB, giới hạn, login, prod thiếu khoá) đạt.

### B2 — PR #44 (đã merge)
- `taste.css`: hai `@import` (Google Fonts, Phosphor) lên đầu file. Hero dùng ảnh catalogue (xem D4). `KnownDefectsTest`: tag `bug` ở từng phương thức; `-Dgroups=bug` chọn đúng các lỗi còn mở. Test mới `StaticAssetsTest`.
- Kiểm: Opus 2 lượt (có thử đột biến); Selenium chỉ `bug_*` đỏ; font/icon/hero kiểm trên 4 trang và màn 375 px.

### B3 — nhánh local `fix/qa-money` (chưa push)
Commit (đã rebase lên `81009c0`): `52f26b1` pricing + promo, `26c6a12` VNPay/doanh thu/ghi chú hoàn tiền, `c4cbc42` audit sau commit + chặn tổng 0 + giữ dữ liệu VNPay khi hoàn tiền, `f4af892` thêm trạng thái đơn vào lịch sử thanh toán của khách.
- U-06 tạo/sửa khuyến mãi dùng chung `normalizeMaxDiscount`; số tiền giảm kẹp trong [0, tạm tính].
- U-10/M-24 thuế trước bạ 0–100%, phí giao ≤ 5.000.000, phí dịch vụ hồ sơ > 0.
- M-21/D1, U-03 (IPN chỉ đẩy PENDING → CONFIRMED), U-04 (số tiền 0 không tạo URL VNPay), M-25/U-11 (một công thức doanh thu `countsAsRevenue` cho dashboard, báo cáo, totalSpent), M-22 (hoàn tiền cần ghi chú).
- Opus duyệt ở `bc95113` (nay là `c4cbc42`); unit 55 `bug_*` đỏ (9 đã xanh), không test mới đỏ.
- **Còn lại:** (1) chạy lại toàn bộ unit sau rebase + commit `f4af892`; (2) hai điểm nhỏ Opus nêu: gọi `assertPayableTotal` trong `updateItemQuantity`, thêm test giao tận nhà có giảm giá lớn hơn tạm tính (tổng = phí giao); (3) Selenium + kiểm tay trên app thật (lần chạy bị dừng giữa chừng); (4) push, PR, CI, merge.
- Test cũ đã đổi theo quyết định: `adminCanSetFeeToZero` → `registrationServiceFeeOfZeroIsRejected`; các test giảm giá ≥ tạm tính giờ kỳ vọng bị từ chối do tổng 0; test dashboard thiếu `paidAt`.

### B4 — nhánh local `fix/qa-validation` (WIP)
- Commit `20ec2e3` chứa phần đang làm dở: `common/validation/AccountFieldRules.java`, `VietnamProvinces.java`, sửa `ApiExceptionHandler`, `AuthenticationServiceImpl`, `AdminServiceImpl`, `UserProfileServiceImpl`, `SessionCartService`, `OrderServiceImpl`, `SupportService`, JS profile/admin/workspace và test liên quan. **Chưa chạy test, chưa review.**
- Lưu ý: agent B4 khi chạy tiếp đã làm việc nhầm trong worktree `qa-project-planning-58d8ed` (nhánh đã được lưu lại như trên).
- Còn lại: kiểm phần đã làm theo brief B4 trong `KE_HOACH_SUA_LOI.md`, chạy test, rebase sau khi B3 merge (cùng đụng `OrderServiceImpl`, `AdminServiceImpl`), Opus review, Selenium.

## 5. Việc phát sinh chưa làm (ngoài kế hoạch)
- Gộp IPv6 theo /64 cho rate limiter và `requester_key` (một dải IPv6 hiện tạo vô số "người gửi").
- `VnPayServiceImpl.prepareExistingPayment`: mỗi lần thanh toán lại tạo mã giao dịch mới → khách trả ở tab VNPay cũ thì IPN trả "01" và tiền không được ghi nhận (lỗi có từ trước, nên làm batch riêng).
- Test Selenium chập chờn (StaleElementReference): `StorefrontTest.product_savingsCalculatorMatchesTheApi`, `AdminCatalogTest.categoryCreateEditDeleteWithConfirm`, `CustomerAreaTest.orderDetailShowsTimelineItemsAndPricing` — sửa cách chờ ở B11.
- Tự host Google Fonts và Phosphor (bớt phụ thuộc unpkg/Google, không gửi IP người xem).
- Đăng ký vẫn trả 409 khi trùng username/email (lộ tài khoản tồn tại) — ngoài phạm vi B1.
- Chưa kiểm với SMTP thật và VNPay sandbox thật (chờ khoá).

## 6. Cách tiếp tục
1. B3: vào nhánh `fix/qa-money`, làm 2 điểm nhỏ ở mục 4, chạy `mvn -o test` (tập đỏ = 55 tên `bug_*`), chạy Selenium + kiểm tay theo mục 2.5 (mong đợi chỉ 4 `bug_*` Selenium đỏ), push, PR, chờ CI, merge.
2. B4: rà commit WIP `20ec2e3`, hoàn thành theo brief, rebase lên `develop` sau B3, review, Selenium, PR.
3. Tiếp theo theo thứ tự: B5 → B6 → B7 → B9 → B10 → B8 → B11.
4. Lệnh: Maven `C:/Users/lemin/.m2/wrapper/dists/apache-maven-3.10.0-bin/39q0qgmngdu329psje7bjgne79/apache-maven-3.10.0/bin/mvn -o` trong `backend/`; Selenium `mvn -o test -Dtest='com.ebike.qa.selenium.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dqa.baseUrl=http://localhost:8088`.
