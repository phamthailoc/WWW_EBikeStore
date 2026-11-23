# Kế hoạch tính năng mới (V20 trở đi)

Lập ngày 14/11/2026, giả định mọi mục trong `KE_HOACH_HOAN_THIEN.md` (V1–V19, B1–B8, audit) đã xong. File này chỉ ghi các tính năng **thêm mới** để web gần với một cửa hàng xe điện thật hơn; các việc còn dở của kế hoạch cũ (V16 OTP qua SMS) vẫn theo dõi ở file cũ.

Mỗi mục ghi: vì sao nên làm, làm gì, dùng lại phần nào đã có, ước lượng, cách kiểm thử và điều cần chốt trước khi code. Ước lượng tính cho một người, gồm cả test Selenium.

## Tình hình hiện tại

| Việc | Ưu tiên | Ước lượng | Trạng thái | Nhánh / PR |
|---|---|---|---|---|
| V20. Sổ bảo hành điện tử và nhắc bảo dưỡng | P1 | 2–3 ngày | 🟡 Code xong (hoàn thiện 21/11), chưa kiểm thử Selenium: ngày bàn giao lưu cố định ở `orders.delivered_at`, số khung xe đã có yêu cầu bảo hành/mốc nhắc bị khóa (409), job nhắc theo lô mỗi xe một giao dịch, hàng đợi Manager phân trang, thông báo/email/lỗi VI-EN; chưa đính ảnh vì kho ảnh S3 là bucket công khai; sổ bảo hành `/customer/warranty`, tra cứu `/warranty`, hàng đợi `/manager/warranty`, job nhắc bảo dưỡng (mốc `P1M,P6M,…,P36M`); xe hết hạn vẫn được gửi yêu cầu, đánh dấu sửa có tính phí; chạy `update_warranty_book.sql` trước; đã viết `WarrantySeleniumTest`, chưa chạy | `feat/warranty-book` |
| V21. Chạy test tự động trên GitHub Actions (CI) | P1 | 1 ngày | 🟡 Đã viết workflow, **chưa chạy thử trên GitHub** (20/11): job Unit test + job Selenium (MariaDB trong Docker, tài khoản thử tạo bằng SQL, app nền, Selenium headless); mặc định chạy Selenium cho mọi PR vào `develop` hoặc `main`; bỏ qua `ChatbotMemorySeleniumTest` nếu chưa có secret `GEMINI_AI_KEY` | `feat/ci-github-actions` (`.github/workflows/test.yml`) |
| V22. Báo khi có hàng lại | P1 | 1 ngày | 🟡 Code xong (21/11); lớp DB đã chạy thật trên MariaDB 13 (22/11, xem mục V23), chưa chạy app và Selenium: bảng `stock_alerts`, `POST/GET/DELETE /api/v1/stock-alerts` (chỉ vai trò khách hàng, Manager/Admin nhận 403 và không thấy nút), thông báo (bấm được) + email VI/EN theo ngôn ngữ hồ sơ, gửi email nền có timeout 5 giây, mỗi lượt chỉ báo một lần dù nhiều người cùng cập nhật, quét 5 phút một lần cho màu bán lại; trang yêu thích hiện "Đã có hàng lại" khi còn hàng; đã viết `StockAlertSeleniumTest`, chưa chạy | `feat/back-in-stock-alerts` |
| V23. Đặt lịch lái thử | P2 | 1–2 ngày | 🟡 Code xong (21/11), chưa kiểm thử Selenium: bảng `test_ride_bookings`, nút "Đặt lịch lái thử" ở trang xe (khung giờ 1 giờ trong 14 ngày tới, đặt trước ≥ 2 giờ, 2 lượt/giờ/cửa hàng khóa theo dòng cửa hàng, mỗi khách ≤ 3 lịch chưa diễn ra, chỉ vai trò khách hàng, khách vãng lai được dẫn đi đăng nhập rồi quay lại), giờ mở cửa đọc từ chữ `showrooms.opening_hours` (dạng `08:00 - 21:00`, không đọc được thì 08:00 - 21:00; không thêm cột), trang khách `/customer/test-rides` (hủy trước giờ hẹn), lịch Manager `/manager/test-rides` (xác nhận, đã lái thử, khách không đến, hủy kèm lý do), thông báo + email VI/EN, job 30 phút nhắc trước 24 giờ và đóng yêu cầu quá giờ chưa xác nhận, xóa tài khoản hủy lịch đang mở và xóa số điện thoại/ghi chú; chạy `update_test_ride_bookings.sql` trước; đã viết `TestRideSeleniumTest` (4 test), chưa chạy | `feat/test-ride-booking` |
| V24. Trả góp (mô phỏng) | P2 | 2 ngày | 🟡 Code xong (22/11), chưa kiểm thử Selenium, chưa chạy app: công cụ tính ở trang xe (công khai, `GET /api/v1/installments/quote`) và phương thức "Trả góp" ở checkout (chỉ tài khoản khách hàng; khách vãng lai được dẫn đăng nhập, Manager/Admin nhận 403); trả trước 20–50%, kỳ hạn 6/9/12 tháng, lãi phẳng theo năm đọc từ `pricing_rules` (`INSTALLMENT_RATE_6/9/12`, INACTIVE thì ngừng kỳ hạn đó, mẫu 8/9/10%/năm); hồ sơ chỉ lưu 4 số cuối CCCD + thu nhập khai (xóa khi xóa tài khoản), khoản trả hàng tháng ≤ 60% thu nhập, tối đa 2 hồ sơ mở mỗi khách; Manager duyệt/từ chối kèm lý do ở `/manager/installments` (từ chối hủy đơn và trả xe về kho), khóa dòng chống duyệt hai lần, thông báo + email VI/EN; dòng thanh toán của đơn là khoản trả trước (`INSTALLMENT`), trả qua VNPay hoặc tại cửa hàng sau khi duyệt, chưa duyệt thì không xác nhận/thu/thanh toán được; hồ sơ chưa duyệt giữ hàng 72 giờ, đã duyệt chờ khoản trả trước 72 giờ rồi đơn tự hủy; chạy `update_installments.sql` trước; đã viết `InstallmentSeleniumTest`, chưa chạy | `feat/installment-simulation` |
| V25. Phụ kiện và gợi ý mua kèm | P2 | 2 ngày | ⬜ Chưa làm | – |
| V26. Phiếu nhập kho và lịch sử tồn kho | P2 | 1–2 ngày | ✅ Xong và đã kiểm thử (23/11; Opus review 2 vòng, chạy app thật, Selenium 40/40 các lớp liên quan tồn kho và đơn hàng): bảng `stock_receipts`, `stock_receipt_lines`, `stock_movements` (chạy `update_stock_receipts.sql` trước); mọi chỗ đổi tồn (đặt, hủy, hết hạn thanh toán, Manager sửa, phiếu nhập) khóa dòng rồi ghi một dòng sổ; trang `/manager/stock-receipts`, hộp lịch sử tồn kho trong trang xe, đối soát tổng sổ với tồn từng màu; VI/EN; `StockReceiptSeleniumTest` 3/3 | `feat/stock-receipts` |
| V27. Hóa đơn và phiếu bảo hành PDF | P2 | 1–2 ngày | 🟡 Code xong (23/11), chưa kiểm thử Selenium, chưa chạy app: thêm cột `orders.customer_language` (ngôn ngữ trang lúc đặt, để email cho khách vãng lai đúng ngôn ngữ; ddl-auto=validate nên **chạy `update_orders_customer_language.sql` trước**), còn PDF tạo khi tải, không lưu; `GET /api/v1/orders/{id}/receipt` (phiếu bán hàng, "tạm tính" khi chưa giao, đơn đã hủy trả 409) và `/warranty-card[?unitId=]` (một trang mỗi xe, chỉ khi đã giao và có số khung), chỉ chủ đơn hoặc nhân viên xem được (người lạ nhận 404, khách chưa đăng nhập bị 401, giới hạn 40 lượt/phút/địa chỉ); openhtmltopdf + font DejaVu Sans (có đủ dấu tiếng Việt, lấy từ jar `jasperreports-fonts`, không dùng Be Vietnam Pro vì không có sẵn trong Maven) + template Thymeleaf VI/EN, thông tin cửa hàng ở `app.store.*` (mã số thuế giả), có số tiền bằng chữ, ghi rõ không phải hóa đơn điện tử; nút "Tải hóa đơn/Tải phiếu bảo hành" ở chi tiết đơn (khách và Manager) và ở sổ bảo hành; email kèm PDF khi đơn chuyển "Đã giao" (cả đơn khách vãng lai, bỏ qua tài khoản đã xóa; gửi lại khi nhân viên thu tiền sau lúc bàn giao) và nút "Gửi lại email phiếu" cho Manager ở chi tiết đơn; đã viết `OrderDocumentsSeleniumTest`, chưa chạy | `feat/pdf-invoice` |
| V28. Xuất báo cáo Excel | P3 | 1 ngày | 🟡 Code xong (24/11), chưa kiểm thử Selenium, chưa chạy app, không đổi cơ sở dữ liệu: nút "Xuất Excel" ở `/manager/orders` (đúng bộ lọc tìm kiếm đang chọn) và `/manager/reports` (đúng gộp theo/từ ngày/đến ngày), `GET /api/v1/manager/orders/export` (cần quyền xem mọi đơn) và `GET /api/v1/manager/revenue-report/export` (cần thêm quyền xem báo cáo), tối đa 10 lượt/phút/địa chỉ; Apache POI 5.2.5, tệp `.xlsx` tạo khi tải, không lưu; báo cáo có 4 trang (Tổng quan kèm số đơn hủy và tỉ lệ hủy, Doanh thu theo kỳ, Xe bán chạy, Xe bán chậm), danh sách đơn có 20 cột, thêm trang "Bộ lọc"; tối đa 10.000 đơn một tệp (quá thì báo 400, hãy lọc bớt); tiền là số, chữ không bao giờ thành công thức; tiếng Việt hoặc tiếng Anh theo ngôn ngữ trang (hoặc `lang=`); mỗi lần xuất ghi nhật ký (không ghi chữ tìm kiếm); đã viết `ExcelExportSeleniumTest`, chưa chạy | `feat/excel-export` |
| V29. Hoàn tiền VNPay qua API | P3 | 1 ngày | ⬜ Chưa làm | – |
| V30. Tồn kho theo từng cửa hàng | P3 | 3 ngày | ⬜ Chưa làm | – |
| V31. Đăng nhập bằng Google | P3 | 1 ngày | ⬜ Chưa làm | – |
| V32. Đánh giá có ảnh, hỏi đáp dưới mỗi xe | P3 | 2 ngày | ⬜ Chưa làm | – |
| V33. Công cụ "Xe điện tiết kiệm bao nhiêu" | P3 | ½ ngày | 🟡 Code xong (23/11), chưa kiểm thử Selenium, chưa chạy app: mục "Xe điện tiết kiệm bao nhiêu?" ở trang xe (khách vãng lai và mọi vai trò có quyền `product:view`; vai trò không có quyền này, như OPERATOR, SUPPORT, MAINTENANCE trong dữ liệu mẫu, nhận 403 và hộp tự ẩn), `GET /api/v1/products/{slug}/savings?kmPerDay=&daysPerMonth=` (không cache, 404 xe lạ, 400 số ngoài khoảng); tiền sạc tính từ Ah x V của pin và quãng đường (thực tế 80% công bố, hiệu suất sạc 85%), so với xe máy xăng cùng phân khúc; 5 quy tắc giá của Admin (`SAVINGS_ELECTRICITY_PRICE`, `SAVINGS_PETROL_PRICE`, `SAVINGS_PETROL_USE_E_SCOOTER/E_BIKE/E_MOTORBIKE`; Admin không nhập được 0 hoặc quá lớn, quy tắc 0/INACTIVE dùng giá mặc định); xe thiếu thông số pin báo chưa tính được, pin/phụ kiện ẩn mục; chatbot trả lời câu hỏi chi phí bằng giá hiện hành và dẫn tới trang xe; chạy `update_savings_calculator.sql` trước; đã viết `SavingsCalculatorSeleniumTest`, chưa chạy | `feat/savings-calculator` |
| V34. Docker Compose (app + MariaDB) | P3 | ½ ngày | ⬜ Chưa làm | – |
| V35. SEO: sitemap, dữ liệu cấu trúc, Open Graph | P3 | ½ ngày | 🟡 Code xong (23/11), chưa kiểm thử Selenium, chưa chạy app: `/sitemap.xml`, `/robots.txt`, canonical, Open Graph và JSON-LD `Product` ở trang xe; trang riêng tư và bộ lọc gắn `noindex`. Toàn bộ unit test qua (24/11), `SeoSeleniumTest` viết xong nhưng chưa chạy | feat/seo |

Ký hiệu: P1 nên làm trước; P2 làm tiếp nếu còn thời gian; P3 làm thêm. ⬜ chưa làm, 🟡 đang làm hoặc đã code chưa kiểm thử, ✅ xong và đã kiểm thử.

**Thứ tự đề xuất:** V21 (CI, để mọi việc sau được test tự động) → V20 → V22 → V23 → V24 → V27 → V26 → V25 → còn lại tùy thời gian.

**Nếu chỉ còn thời gian cho 3 việc:** V20, V21, V22. V20 thể hiện nghiệp vụ riêng của cửa hàng xe và dùng lại V18; V21 giúp hơn 200 test có sẵn tự chạy mỗi lần mở PR; V22 nhỏ, nhanh, dễ demo.

---

## P1. Nên làm trước

### V20. Sổ bảo hành điện tử và nhắc bảo dưỡng — khoảng 2–3 ngày

**Vì sao:** sau khi giao xe, web hiện không còn gì cho khách. Cửa hàng xe thật luôn có phiếu bảo hành theo số khung và nhắc bảo dưỡng định kỳ.

**Làm gì:**
- **Phiếu bảo hành:** mỗi xe đã giao (một dòng `order_item_units`, V18) có một phiếu. Ngày bắt đầu = `shipments.delivered_at` (đơn giao tận nhà) hoặc lúc đơn chuyển "Đã nhận" (đơn nhận tại cửa hàng); thời hạn lấy từ `product_specifications.warranty_months`. Không cần bảng mới nếu tính được lúc đọc; nếu cần lưu ngày cố định thì thêm cột `warranty_starts_at`, `warranty_ends_at` vào `order_item_units`.
- **Khách:** trang `/customer/warranty` liệt kê xe của mình (tên xe, màu, số khung, còn bảo hành đến ngày nào); nút "Yêu cầu bảo hành" mở biểu mẫu (mô tả lỗi, ảnh nếu có S3).
- **Tra cứu công khai:** `/warranty?frame=...` nhập số khung, trả về tên xe và hạn bảo hành (không lộ tên, số điện thoại chủ xe).
- **Yêu cầu bảo hành:** bảng mới `warranty_claims` (unit_id, user_id, mô tả, trạng thái `NEW` → `RECEIVED` → `REPAIRING` → `DONE`/`REJECTED`, ghi chú nhân viên, thời gian). Manager xử lý ở `/manager/warranty`, giống trang Hỗ trợ hiện có.
- **Nhắc bảo dưỡng:** job chạy mỗi ngày (giống `UnpaidOrderExpiryJob`) gửi thông báo và email khi xe đến mốc bảo dưỡng. Mốc cấu hình `app.maintenance.intervals=P1M,P6M,P12M,P18M,P24M,P30M,P36M` (bảo dưỡng lần đầu sau 1 tháng, rồi 6 tháng một lần trong 3 năm); ghi lại đã nhắc để không gửi trùng.

**Dùng lại:** `order_item_units` (V18), `NotificationCreationService` + SSE, công tắc nhận thông báo (V4), trang Hỗ trợ của Manager làm mẫu cho trang yêu cầu bảo hành, `messages*.properties` + `js-en.json` cho tiếng Anh.

**Kiểm thử:** Selenium giao một đơn (`enterUnits`), khách thấy phiếu với đúng hạn; tra cứu bằng số khung; khách gửi yêu cầu, Manager đổi trạng thái, khách nhận thông báo. Unit test cho tính ngày hết hạn và mốc nhắc.

**Cần chốt:** mốc bảo dưỡng (1 tháng / 6 tháng?); xe hết hạn bảo hành có được gửi yêu cầu (sửa có tính phí) không.

**Giới hạn đã biết:** sổ bảo hành chỉ đọc `order_item_units`, nên một xe không có dòng số khung thì không có phiếu và khách không tự thêm được. Gồm (a) đơn đã giao trước V18 (lúc đó đơn được giao mà không cần số khung): nhân viên mở chi tiết đơn đã giao ở `/manager/orders` và nhập số khung (trạng thái "Đã giao" vẫn cho sửa số khung), ngày bàn giao lấy từ `orders.delivered_at` hoặc giờ giao của đơn; (b) sản phẩm loại pin và phụ kiện (`OrderUnits.requiresNumbers` trả `false`), dù pin thường có bảo hành riêng: chưa có phiếu cho chúng, chủ sản phẩm cần quyết định có làm không.

**Chờ xác nhận (tác giả code đã tự chọn, chưa có ý kiến chủ sản phẩm):** (1) bỏ ảnh đính kèm trong biểu mẫu yêu cầu bảo hành (bucket S3 đang công khai, nên chưa làm); (2) mốc bảo dưỡng `P1M,P6M,P12M,P18M,P24M,P30M,P36M`; (3) xe hết hạn vẫn được gửi yêu cầu, đánh dấu sửa có tính phí; (4) thời hạn bảo hành được ghim vào từng xe lúc ghi số khung, đổi `warranty_months` của sản phẩm sau đó không làm đổi xe đã bán; (5) ghi chú nhân viên (`staff_note`) được giữ lại khi khách xóa tài khoản; (6) yêu cầu bảo hành đang mở (`NEW`/`RECEIVED`/`REPAIRING`) của tài khoản đã xóa được giữ nguyên trong hàng đợi, không tự đóng; nhân viên không còn cách liên lạc (email đã thành `deleted_<id>`, không có thông báo), nên cần chốt: để mở cho tới khi nhân viên tự đóng, hay tự chuyển `REJECTED`/`DONE` lúc xóa tài khoản. Các mục này chưa tính là xong cho đến khi được xác nhận.

### V21. Chạy test tự động trên GitHub Actions (CI) — khoảng 1 ngày

**Vì sao:** hiện 200+ test chỉ chạy tay trên một máy, phải nhớ đặt mật khẩu DB, tắt rate limit, khởi động app. Một PR có thể làm hỏng chỗ khác mà không ai biết.

**Làm gì:**
- `.github/workflows/test.yml` chạy khi mở hoặc cập nhật PR vào `develop`/`main`.
- Bước 1: `mvn test` (unit test, không cần DB).
- Bước 2: dịch vụ MariaDB trong workflow, nạp `backend/database/setup_ebike_database.sql`, tạo tài khoản `selenium.manager` / `selenium.admin` bằng một script SQL riêng cho CI (mật khẩu lấy từ GitHub Secrets), khởi động app nền với `APP_RATE_LIMIT_ENABLED=false` và khóa VNPay giả, đợi cổng 8080, rồi chạy Selenium headless (Chrome có sẵn trên runner `ubuntu-latest`).
- Lưu ảnh chụp `target/selenium/*.png` và báo cáo surefire làm artifact khi test lỗi.
- Bỏ qua `ChatbotMemorySeleniumTest` nếu không có secret `GEMINI_AI_KEY`.

**Cần chốt:** chạy Selenium cho mọi PR (khoảng 12–15 phút) hay chỉ cho PR vào `main`; có lưu khóa Gemini vào GitHub Secrets không.

### V22. Báo khi có hàng lại — khoảng 1 ngày

**Vì sao:** màu đã hết hiện chỉ bị khóa nút, khách rời đi và không quay lại.

**Làm gì:**
- Ở trang chi tiết xe, màu hết hàng hiện nút "Báo tôi khi có hàng" (khách phải đăng nhập; khách vãng lai nhập email nếu muốn mở rộng sau).
- Bảng mới `stock_alerts` (user_id, variant_id, created_at, notified_at), duy nhất theo (user_id, variant_id).
- Khi Manager tăng tồn kho một màu từ 0 lên > 0 (`PATCH /manager/products/{id}/variants/{variantId}/stock`, V17), gửi thông báo + email cho mọi người đang chờ màu đó, đặt `notified_at`.
- Khách xem và hủy đăng ký trong trang hồ sơ hoặc trang yêu thích.

**Dùng lại:** tồn kho theo màu (V17), hệ thống thông báo, cache sản phẩm (nhớ xóa cache khi đổi tồn kho, đã có).

**Kiểm thử:** Selenium đặt một màu về 0, khách đăng ký báo, Manager nhập thêm, khách thấy thông báo; đăng ký lần hai không tạo bản trùng.

**Giới hạn đã biết (V22):**
- Báo hàng được gửi ngay sau khi giao dịch tồn kho commit, trong giao dịch riêng; nếu bước báo lỗi thì chỉ ghi log, việc nhập kho không bị hoàn tác.
- Thông báo trong ứng dụng và email là "tối đa một lần": dòng chờ được đánh dấu `notified_at` cùng lúc với việc tạo sự kiện. Nếu sau đó SMTP lỗi, hàng đợi email đầy (email bị bỏ, có log cảnh báo) hoặc ứng dụng sập giữa chừng, khách không được báo lại. Khi tắt ứng dụng bình thường, hàng đợi được chờ tối đa 30 giây.
- Báo hàng (thông báo trong ứng dụng và email) vẫn được gửi khi khách đã tắt thông báo trong hồ sơ, vì đây là yêu cầu chủ động của khách; công tắc đó chỉ áp dụng cho thông báo đơn hàng và thanh toán.
- Khách đăng ký đúng lúc Manager nhập hàng có thể không được báo ngay; việc đăng ký đọc lại tồn kho sau khi lưu (bằng câu truy vấn scalar, không dùng entity đã nạp sẵn do open-in-view) và báo luôn nếu màu đã có hàng; việc này chưa có test tích hợp với DB thật, còn job quét 5 phút một lần là lưới an toàn.
- Xe không có màu nào (`ManagerServiceImpl.updateProductStock` vẫn hỗ trợ loại xe này) bán hết thì không có nút "Báo tôi khi có hàng": bảng `stock_alerts` gắn với màu (`variant_id`), nên chỉ đăng ký báo được theo màu. Muốn hỗ trợ cần thêm cách đăng ký theo xe (`product_id`) và sửa luồng báo; chưa làm.
- Hai test Selenium V22/V17 đánh dấu `@Execution(SAME_THREAD)`/`@ResourceLock`; dự án không bật chạy song song của JUnit nên các dòng này chưa có tác dụng. Dòng chờ còn sót của khách `selenium*` từ lần chạy bị dừng giữa chừng có thể được báo khi test trả lại tồn kho; dọn bằng `DELETE FROM stock_alerts WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'selenium%')` trước khi chạy.
- V22 đổi hành vi toàn ứng dụng ở hai chỗ, không chỉ cho báo hàng: (1) `CacheConfiguration` bọc cache manager bằng `TransactionAwareCacheManagerProxy`, nên mọi lần xóa/ghi cache trong giao dịch (sản phẩm, đơn hàng, đánh giá...) dời tới sau khi giao dịch commit; (2) `AsyncConfiguration` bật `@EnableAsync` và khai báo lại `applicationTaskExecutor`/`taskExecutor` bằng `TaskExecutorBuilder` của Boot. `CacheAndAsyncConfigurationTest` kiểm tra việc nối bean và thời điểm xóa cache; chưa có test khởi động cả context với DB thật, nên khi chạy Selenium cần kiểm tra thủ công rằng trang sản phẩm, đơn hàng và đánh giá vẫn hiện dữ liệu mới sau khi sửa.
- Nhánh này chứa 2 commit của V21 (`43c498e` workflow CI `.github/workflows/test.yml`, `4a8f602` dòng V21 trong kế hoạch) vì được tách ra từ nhánh V21. Khi mở PR cho V22 phải nói rõ điều này và review/gộp `feat/ci-github-actions` trước, hoặc chấp nhận workflow CI đi cùng PR; rebase bỏ 2 commit này gây xung đột ở file kế hoạch nên chưa làm. Cả hai commit đã có trong `origin/develop`: PR vào `develop` không thêm gì từ chúng, còn PR vào nhánh khác thì vẫn kéo theo workflow CI.

---

## P2. Làm tiếp nếu còn thời gian

### V23. Đặt lịch lái thử — khoảng 1–2 ngày

**Làm gì:** khách chọn xe, cửa hàng (`showrooms`) và khung giờ trong 14 ngày tới; mỗi khung giờ giới hạn số lượt (ví dụ 2 lượt/giờ/cửa hàng). Bảng mới `test_ride_bookings` (user_id, product_id, showroom_id, slot_start, trạng thái `REQUESTED` → `CONFIRMED` → `DONE`/`CANCELLED`/`NO_SHOW`). Manager xem lịch theo ngày và xác nhận; khách nhận thông báo khi được xác nhận và được nhắc trước 1 ngày (job như V20).

**Cần chốt:** giờ mở cửa lấy từ `showrooms.opening_hours` (đang là chữ tự do) hay thêm cột giờ mở/đóng; khách vãng lai có được đặt không.

**Đã kiểm trên MariaDB 13.0.2 thật (22/11, DB tạm cổng 3399):** `setup_ebike_database.sql` chạy được; `update_test_ride_bookings.sql` chạy hai lần liên tiếp, cả trên bảng bản cũ (thêm cột `cancel_source`, đổi dữ liệu cũ); `ddl-auto=validate` khớp mọi entity; cột sinh `active_user_slot` nhận UNIQUE dù `user_id` là khóa ngoại `ON DELETE CASCADE` (xóa user vẫn xóa lịch); các câu JPQL (`findSchedule`, `countBySlot`, đếm backlog, hai câu update hàng loạt) chạy đúng; hai luồng cùng đặt chỗ cuối chỉ một luồng thắng; một khách đặt hai cửa hàng cùng giờ chỉ được một lịch và không deadlock; đặt lịch song song với xóa tài khoản không deadlock. Bộ kiểm thử là `TestRideDatabaseTest` (chỉ chạy khi có `-Dtestride.db.url=jdbc:mariadb://host:cổng/ebike_db`, thêm `-Dtestride.db.user` và `-Dtestride.db.password` nếu không phải root không mật khẩu; DB phải dựng từ `setup_ebike_database.sql`; mọi dòng nó thêm đều được xóa lại). Docker không chạy trên máy này nên chưa dùng Testcontainers.

**Chưa làm:** khởi động app thật và chạy `TestRideSeleniumTest` (với `APP_RATE_LIMIT_ENABLED=false`); trước khi gắn ✅ cần chạy hai việc này.

**Hạn chế đã biết của bản đã code:**
- Giờ mở cửa chỉ đọc khoảng giờ đầu tiên (`08:00 - 21:00`, `8h - 21h`, `8:00 AM - 9:00 PM`, `08:00 - 24:00`); một mệnh đề chỉ có ngày nghỉ trong tuần ("CN đóng cửa", "Sunday: closed", "T7 nghỉ", tách bằng dấu phẩy, chấm phẩy hoặc dòng mới) làm ngày đó không nhận lịch. Chưa hiểu nghỉ trưa, giờ khác nhau theo thứ, giờ qua đêm (`22:00 - 02:00` quay về 08:00 - 21:00) và ngày lễ (chữ "Đóng cửa"/"Tạm nghỉ"/"Closed" mà không có giờ thì không nhận lịch). Muốn đúng hơn thì thêm cột giờ mở/đóng và bảng ngày nghỉ.
- Nhân viên không nhận thông báo riêng: menu "Lái thử" của Manager hiện số yêu cầu chờ xác nhận (chỉ tính yêu cầu của cửa hàng và xe còn hoạt động), và trang `/manager/test-rides` hiện thêm số lịch đã quá giờ chưa đóng, số lịch khách đã hủy và số lịch thuộc cửa hàng hoặc xe không còn hoạt động (bốn số không đếm trùng một lịch). Danh sách Manager tối đa 200 dòng, của khách tối đa 100 dòng, chưa phân trang; khi đủ số dòng, trang ghi "Chỉ hiện {n} lịch đầu tiên…". Tài khoản chỉ có quyền xem (không có `ORDER_UPDATE_STATUS`) không thấy các nút xử lý.
- "14 ngày tới" nghĩa là ngày cuối được đặt là hôm nay cộng 14 ngày (số ngày đọc từ `APP_TEST_RIDE_DAYS_AHEAD`; hộp thoại đọc số ngày và "hôm nay" theo `APP_TIME_ZONE` từ server, không theo múi giờ trình duyệt). Mọi mốc thời gian của lịch (kể cả `created_at`, `updated_at`) tính theo `APP_TIME_ZONE`.
- Xóa tài khoản xóa số điện thoại, ghi chú của khách và ghi chú của cửa hàng ở mọi lịch của tài khoản đó (khác với phiếu bảo hành, nơi ghi chú cửa hàng được giữ).
- Quyết định: thông báo và email về lịch lái thử của chính khách (xác nhận, cửa hàng hủy, nhắc lịch, đóng quá hạn) **không** bị công tắc thông báo trong hồ sơ chặn, giống cảnh báo hết hàng của V22; công tắc chỉ còn chặn thông báo đơn hàng và thanh toán.
- Một yêu cầu chưa ai xác nhận chỉ có thể chuyển sang "đã xác nhận", "hủy" hoặc "đã lái thử" (từ 30 phút trước giờ hẹn, khi khách đến sớm như lịch đã xác nhận); không có "khách không đến" cho yêu cầu (cửa hàng chưa hẹn thì khách không thể vắng mặt). Đến giờ hẹn mà chưa ai xác nhận thì yêu cầu không còn xác nhận được và không còn tính vào số "chờ xác nhận" ở menu Manager, nhưng vẫn mở trong cả khung giờ để nhân viên bấm "Đã lái thử" cho khách đến sớm; hết khung giờ (1 giờ sau giờ hẹn) job đóng yêu cầu và báo khách (chậm nhất 30 phút sau đó). Trạng thái trong DB của yêu cầu bị đóng là `CANCELLED` kèm `cancel_source = EXPIRED`; `EXPIRED` còn là tên loại thông báo gửi cho khách. Quyết định: thông báo `EXPIRED` chỉ hiện trong ứng dụng, không gửi email, vì nó đến sau khi khung giờ đã qua (khách đã biết trên trang "Lịch lái thử" là yêu cầu còn chờ cửa hàng) nên email cho một buổi đã qua ít giá trị; nhân viên không được nhắc riêng trước khi yêu cầu hết hạn, họ thấy số "chờ xác nhận" ở menu. Như V20, khi nhân viên chỉ sửa ghi chú của lịch đã xác nhận (ghi chú mới, không phải xóa ghi chú) thì khách nhận thông báo trong ứng dụng (loại `NOTE_UPDATED`, không gửi email).
- Khách hủy lịch đã xác nhận không báo riêng cho nhân viên: cột `cancelled_by_customer_at` đánh dấu, và trang Manager hiện số lịch đã xác nhận bị khách hủy trong 24 giờ tới (lọc trạng thái Đã hủy để xem). Mọi lịch `CANCELLED` ghi thêm ai hủy ở cột `cancel_source` (`CUSTOMER`, `STORE`, `EXPIRED`, `ERASED`; trống ở lịch hủy trước khi có cột này): cột Trạng thái của hai danh sách ghi "Khách hủy" / "Khách hủy sau khi đã xác nhận" / "Cửa hàng hủy" / "Hết hạn, cửa hàng chưa xác nhận" / "Khách đã xóa tài khoản" (khách thấy "Bạn đã hủy"), có VI/EN. Lịch đã xác nhận bị hủy do xóa tài khoản cũng được đánh dấu `cancelled_by_customer_at`.
- Tắt một cửa hàng hoặc ngừng bán một xe không tự hủy lịch đang mở: trang Manager hiện số lịch chưa đóng thuộc cửa hàng đã tắt hoặc xe ngừng bán để nhân viên hủy kèm lý do; danh sách của khách không còn nút "Xem xe" cho xe ngừng bán.
- Xóa tài khoản hủy lịch đang mở với `cancel_source = ERASED` (không còn ghi câu chữ tiếng Việt vào ghi chú cửa hàng; mỗi trang tự dịch theo ngôn ngữ người đọc) để nhân viên biết vì sao lịch bị hủy. **Còn chờ chủ sản phẩm quyết định (chưa ai trả lời, code giữ nguyên):** có nên giữ ghi chú cửa hàng của lịch đã đóng (đã lái thử, không đến) khi xóa tài khoản không. Hiện đang xóa, còn phiếu bảo hành V20 thì giữ ghi chú cửa hàng, nên hai tính năng chưa thống nhất. Đề xuất: giữ như V20 (ghi chú cửa hàng là hồ sơ của cửa hàng), nhưng đã có lời dặn nhân viên không ghi tên hoặc số điện thoại khách vào đó; nếu chủ sản phẩm chọn giữ thì bỏ `staff_note = null` khỏi `clearPersonalDataOf` cho lịch đã đóng.
- Không xác nhận và không nhắc lịch cho cửa hàng đã tắt hoặc xe ngừng bán (nhân viên chỉ còn nút hủy kèm lý do); không hủy được lịch đã qua cả khung giờ (chỉ đánh dấu đã lái thử hoặc không đến), để khách không nhận "cửa hàng hủy lịch" cho buổi đã qua.
- Giới hạn đặt lịch: 60 lượt/10 phút theo địa chỉ IP (chỉ chống tràn, đủ rộng cho máy dùng chung ở cửa hàng) và 10 lượt đặt thành công/10 phút theo tài khoản (trong `TestRideService.book`).
- Chưa kiểm tra cửa hàng có xe để lái thử: tồn kho chưa theo cửa hàng (V30 chưa làm), nên khách có thể đặt lái thử một mẫu xe tại cửa hàng không có xe mẫu hoặc mẫu đã hết hàng; nhân viên chỉ có thể hủy kèm lý do. Danh sách Manager (200 dòng) và của khách (100 dòng) chưa phân trang, cần thêm khi lượng lịch tăng.

### V24. Trả góp (mô phỏng) — khoảng 2 ngày

**Vì sao:** mua xe điện trả góp rất phổ biến; hiện chỉ có VNPay và trả sau.

**Làm gì:**
- Công cụ tính ở trang chi tiết xe và checkout: chọn trả trước (20–50%) và số kỳ (6/9/12 tháng), hiện khoản trả hàng tháng và tổng tiền. Lãi suất theo kỳ do Admin đặt trong bảng giá (`pricing_rules`, thêm loại `INSTALLMENT_RATE`).
- Phương thức thanh toán mới "Trả góp": khách gửi hồ sơ (CCCD, thu nhập ước tính, chỉ lưu thông tin cần thiết); đơn ở trạng thái chờ duyệt; Manager duyệt hoặc từ chối kèm lý do. Duyệt xong khách trả khoản trả trước (VNPay hoặc tại cửa hàng), đơn đi tiếp như bình thường.
- Không kết nối công ty tài chính thật; ghi rõ trên giao diện là mô phỏng.

**Đã chốt (22/11):** không lưu ảnh CCCD, chỉ lưu 4 số cuối và mức thu nhập khai (xóa khi xóa tài khoản); lãi suất mẫu 8/9/10%/năm cho kỳ 6/9/12 tháng (`INSTALLMENT_RATE_6/9/12` trong bảng giá, Admin sửa, tối đa 60%/năm); thu nhập khai tối đa mặc định 1.000.000.000 (`APP_INSTALLMENT_MAX_INCOME`).

**Chưa làm:** khởi động app thật và chạy `InstallmentSeleniumTest` (với `APP_RATE_LIMIT_ENABLED=false`; bài test tự chọn xe có giá trong khoảng được trả góp thay vì bỏ qua); đã viết `InstallmentSecurityTest` (chạy cùng bộ test thường) và `InstallmentDatabaseTest` (chạy với `-Dinstallment.db.url=...`) nhưng chưa chạy bài sau trên MariaDB thật, nên các truy vấn JPQL mới (`findExpiryCandidates`, `countOpenOf`, `sumMonthlyPaymentOf`, `findOpenOf`, `cancelOpenOfOrder`, `clearPersonalDataOf`, đồ thị thực thể của hàng đợi) và thứ tự khóa dòng mới chỉ được biên dịch, chưa được chạy thật.

**Hạn chế đã biết của bản đã code:**
- Chỉ mô phỏng: không kết nối công ty tài chính, không theo dõi các khoản trả hàng tháng, không có hợp đồng. Giới hạn 60% thu nhập tính cộng dồn: khoản trả hàng tháng mới cộng với các hồ sơ PENDING/APPROVED khác của khách (trên đơn chưa hủy, kể cả đơn đã trả trước) không được vượt 60% thu nhập khai; đơn đã giao vẫn được tính vì kế hoạch trả góp vẫn chạy.
- Admin tắt/bật từng kỳ hạn ngay ở `/admin/pricing` (nút "Ngừng kỳ hạn"/"Bật kỳ hạn" trên dòng `INSTALLMENT_RATE_*`); sửa lãi suất mà bấm Hủy hoặc để trống thì không đổi gì. Các giới hạn `app.installment.*` bị kiểm tra lúc khởi động (sai thì app không chạy).
- Hạn: hồ sơ chưa duyệt quá hạn giữ hàng (`APP_ORDER_INSTALLMENT_REVIEW_HOLD`) thì không duyệt được nữa (409; vẫn từ chối được) và nút duyệt ẩn; khoản trả trước quá hạn thì nút trả/thu ẩn. Trang VNPay của khoản trả trước không mở khi còn dưới 2 phút của hạn giữ, và hết hạn đúng lúc hết hạn giữ (không quá 15 phút), để không có khoản trả đến sau khi job hủy đơn. Trang đơn và hàng đợi hiện hạn duyệt; danh sách đơn của khách đánh dấu đơn trả góp cần duyệt hoặc cần trả trước (`GET /api/v1/installments/mine`). Tài khoản có thêm vai trò nhân viên không gửi hồ sơ trả góp được.
- `GET /api/v1/installments/orders/{id}` của đơn người khác trả **204** (không phải 404), để không dò được mã đơn.
- Thứ tự khóa: mọi thao tác đổi trạng thái đơn (khách hủy, nhân viên hủy, từ chối hồ sơ, job hết hạn, "Xác nhận đã thu" tại cửa hàng, kết quả VNPay và việc tạo lần thanh toán VNPay) khóa **dòng đơn trước**, rồi mới tới dòng hồ sơ hoặc dòng thanh toán, nên một thao tác chạy cùng lúc với thao tác khác thấy kết quả của nó (409 khi đơn đã hủy) thay vì ghi đè. Các thao tác phải đọc mã đơn trước khi khóa (duyệt hồ sơ, xác nhận thu, kết quả VNPay, job hết hạn) chạy ở mức `READ_COMMITTED`, để dữ liệu đọc sau khi chờ khóa không cũ hơn khóa. Job hết hạn đọc ứng viên không khóa, khóa **mọi** đơn sẽ hủy trước (theo thứ tự mã đơn) rồi mới trả xe về kho, nên không bao giờ giữ dòng xe khi còn đang chờ một dòng đơn (không có vòng chờ nhau với khách hủy hay nhân viên từ chối). Xóa tài khoản chạy ở `READ_COMMITTED` để lần đếm hồ sơ mở sau khi khóa tài khoản thấy hồ sơ vừa gửi.
- Khoản trả trước thu tại cửa hàng: nhận cả khoản có lần trả thẻ lỗi (`FAILED`, lần lỗi được xóa); từ chối (409) trong 15 phút kể từ khi khách mở trang VNPay (mã giao dịch mang giờ tạo), để khách không trả hai lần. Nếu VNPay vẫn báo thành công cho khoản đã thu bằng cách khác, hệ thống không đổi trạng thái mà ghi `DUPLICATE VNPAY PAYMENT <mã giao dịch>` vào `provider_response` của dòng thanh toán và cảnh báo vào nhật ký; nhân viên hoàn khoản đó ngoài hệ thống (nút "Ghi nhận hoàn tiền" chỉ có cho đơn đã hủy).
- Quá hạn chờ khoản trả trước (72 giờ kể từ lúc duyệt) thì không thu và không thanh toán được nữa dù job hết hạn chưa chạy; dòng thanh toán `INSTALLMENT` mà không còn hồ sơ bị từ chối chứ không được cho qua.
- Giờ: hạn trả khoản trả trước (và giờ duyệt, giờ nộp trong hàng đợi) được gửi kèm múi giờ của cửa hàng (`APP_TIME_ZONE`, ghi "GMT+7" trong thông báo và email) dù máy chủ chạy UTC. Các mốc lưu trong DB vẫn là giờ của máy chủ như phần còn lại của đơn hàng.
- Ghi chú duyệt tối đa 1000 ký tự, ghi chú từ chối ngắn hơn độ dài tiền tố của lý do hủy (khoảng 974); trang đọc giới hạn từ `GET /api/v1/installments/config`. Cả `config` (60 lần/phút/IP) lẫn `quote` (90 lần/phút/IP) có chặn tràn.
- Từ chối hồ sơ đánh dấu đơn (`suppressStatusNotice`, không lưu) để bỏ thông báo "đơn đã hủy" trùng; chữ trong lý do hủy không quyết định gì. Bộ nhớ đệm trang xe đã được xóa sau khi giao dịch xong (`TransactionAwareCacheManagerProxy`), kể cả khi từ chối hồ sơ.
- Việc đọc danh sách xe của nhiều đơn dùng `@BatchSize(50)` ở `Order.items`; không còn cài đặt Hibernate toàn cục.
- Tiền hiển thị theo dạng `1.234.567 ₫` ở cả hai ngôn ngữ như phần còn lại của cửa hàng (`KineticCart.money`); chỉ email và thông báo tiếng Anh viết `1,234,567 VND`. Lãi suất hiện không có số 0 thừa (`8%`, `8.5%`); dòng thanh toán đã trả của đơn trả góp ghi "Đã trả trước", không phải "Đã thanh toán".
- **Còn chờ (có từ trước V24):** hoàn kho (`restoreStock`) và đặt hàng (`addItems`) đọc rồi ghi số tồn kho mà không khóa dòng sản phẩm, nên hai việc chạy cùng lúc có thể làm mất một lần cập nhật. Khóa theo mã đơn không sửa được chỗ này; cần `UPDATE ... SET stock = stock ± n` nguyên tử ở cả hai nơi (hợp với V26, phiếu nhập kho).

### V25. Phụ kiện và gợi ý mua kèm — khoảng 2 ngày

**Làm gì:** danh mục mới "Phụ kiện" (mũ bảo hiểm, sạc, khóa, áo mưa) dùng chung bảng `products` nhưng **không** cần số khung/số máy (V18 đã có cờ `requiresUnitNumbers`), không tính phí đăng ký, phí giao theo đơn chứ không theo xe. Giỏ hàng và trang chi tiết xe gợi ý "thường mua kèm" (cấu hình tay theo danh mục, chưa cần thuật toán).

**Cần chốt:** phí giao hàng khi đơn chỉ có phụ kiện; phụ kiện có được dùng mã giảm giá không.

### V26. Phiếu nhập kho và lịch sử tồn kho — khoảng 1–2 ngày

**Vì sao:** hiện Manager sửa thẳng số tồn, không biết vì sao tồn kho thay đổi, không đối soát được.

**Làm gì:** bảng mới `stock_movements` (variant_id, thay đổi +/-, lý do `RECEIPT`/`SALE`/`CANCEL_RESTORE`/`ADJUSTMENT`, order_id nếu có, người làm, ghi chú, thời gian). Mọi chỗ đổi tồn kho (đặt đơn, hủy đơn, Manager nhập) ghi một dòng. Trang `/manager/stock-receipts` tạo phiếu nhập (nhà cung cấp, nhiều dòng màu + số lượng). Trang chi tiết tồn kho của một xe hiện lịch sử.

**Kiểm thử:** đặt rồi hủy một đơn, lịch sử có hai dòng bù nhau; tổng các dòng khớp số tồn hiện tại.

### V27. Hóa đơn và phiếu bảo hành PDF — khoảng 1–2 ngày

**Làm gì:** nút "Tải hóa đơn" ở chi tiết đơn (khách và Manager), "Tải phiếu bảo hành" cho xe đã giao (sau V20). Tạo PDF từ template Thymeleaf bằng thư viện openhtmltopdf (nhúng font có dấu tiếng Việt, ví dụ Be Vietnam Pro). Gửi kèm PDF trong email khi đơn chuyển "Đã giao".

**Cần chốt:** thông tin cửa hàng in trên hóa đơn (tên, mã số thuế giả định); hóa đơn này chỉ là phiếu bán hàng, **không** phải hóa đơn điện tử theo luật.

**Quyết định sau rà soát:** phiếu tạm tính (xe chưa bàn giao) mang số `TT-<mã đơn>`, phiếu bán hàng chính thức mang số `PBH-<mã đơn>`, để hai bản khác nhau của cùng một đơn không trùng số. Đơn lưu ngôn ngữ của trang lúc đặt hàng (cột `orders.customer_language`, file `update_orders_customer_language.sql`) nên email và PDF gửi khách vãng lai theo ngôn ngữ đó (đơn cũ chưa có thì tiếng Việt); Manager chọn được ngôn ngữ phiếu khi tải. Email bàn giao có hàng đợi riêng, không tự gửi lại, lỗi chỉ ghi log kèm mã đơn; Manager có nút "Gửi lại email phiếu" ở trang đơn (cần quyền cập nhật đơn, tối đa 6 lần mỗi phút, báo ngay nếu máy chủ thư từ chối).

**Việc theo dõi (chưa làm):** thư viện PDF `com.openhtmltopdf` 1.0.10 không còn được bảo trì và không nhận bản vá bảo mật. Cần chuyển sang `io.github.openhtmltopdf` (đòi hỏi PDFBox 3, kéo theo kiểm tra lại `xmpbox`, phông DejaVu và các bài `PdfRendererTest`); ghi lý do hoãn trong comment của `backend/pom.xml`.

---

## P3. Làm thêm

### V28. Xuất báo cáo Excel — khoảng 1 ngày
Nút "Xuất Excel" ở trang Báo cáo doanh thu và Đơn hàng của Manager: doanh thu theo kỳ, xe bán chạy, tỉ lệ hủy, danh sách đơn theo bộ lọc đang chọn. Dùng Apache POI, tải về `.xlsx`.

### V29. Hoàn tiền VNPay qua API — khoảng 1 ngày
Hiện Manager ghi nhận hoàn tiền bằng tay. Gọi API hoàn tiền của VNPay (`vnp_Command=refund`) khi hủy đơn đã trả qua VNPay, lưu kết quả vào `payments`. Chỉ thử được trên sandbox với khóa thật; khi dùng khóa giả thì giữ cách ghi nhận tay.

### V30. Tồn kho theo từng cửa hàng — khoảng 3 ngày
Tách tồn kho mỗi màu theo `showrooms` (bảng mới `showroom_stock`). Khách chọn nhận tại cửa hàng chỉ thấy cửa hàng còn xe màu đó; Manager chuyển kho giữa các cửa hàng. Việc lớn, đụng nhiều chỗ (đặt đơn, hủy đơn, V17, V22, V26): chỉ làm khi nhóm muốn mô phỏng chuỗi cửa hàng.

### V31. Đăng nhập bằng Google — khoảng 1 ngày
Spring Security OAuth2 client; sau khi Google trả về email đã xác thực thì tìm tài khoản theo email hoặc tạo tài khoản khách mới, rồi cấp cookie JWT như đăng nhập thường. Cần Google Client ID (đặt qua biến môi trường). Tài khoản `DELETED` (V6) không được đăng nhập lại bằng Google.

### V32. Đánh giá có ảnh, hỏi đáp dưới mỗi xe — khoảng 2 ngày
Đánh giá cho đính kèm tối đa 3 ảnh (cần S3 như V7). Mục "Hỏi đáp" dưới mỗi xe: khách hỏi, Manager trả lời, câu hỏi chỉ hiện khi đã có trả lời. Chatbot có thể dùng các câu đã trả lời làm ngữ cảnh.

### V33. Công cụ "Xe điện tiết kiệm bao nhiêu" — khoảng ½ ngày
Ở trang chi tiết xe: nhập số km đi mỗi ngày, so tiền sạc (theo dung lượng pin, giá điện bậc trung bình) với tiền xăng của xe máy cùng phân khúc. Giá điện, giá xăng và mức tiêu hao do Admin đặt trong bảng giá. Chatbot dẫn tới công cụ này khi khách hỏi về chi phí.

### V34. Docker Compose — khoảng ½ ngày
`docker-compose.yml` gồm MariaDB (nạp sẵn file setup) và app (build bằng Maven trong Dockerfile nhiều bước). Nhóm và người chấm chạy cả dự án bằng `docker compose up`. Mật khẩu DB và khóa JWT đọc từ file `.env` (không commit).

### V35. SEO — khoảng ½ ngày
`/sitemap.xml` sinh từ danh sách xe; `robots.txt`; dữ liệu cấu trúc JSON-LD `Product` (giá, còn hàng, điểm đánh giá) ở trang chi tiết xe; thẻ Open Graph (ảnh, tên, giá) để link xe hiện đẹp khi chia sẻ.

Kiểm thử (Selenium, `SeoSeleniumTest`; khách chưa đăng nhập, không cookie):
- `/robots.txt` và `/sitemap.xml` mở không cần tài khoản; sitemap có trang chủ, `/products`, `/support`, `/warranty` và đúng các xe đang bán; robots chỉ chặn `/api/` và `/lang/`.
- Trang xe: một canonical dù vào bằng `/product`, `/bike` hay `/models`; Open Graph, giá và tồn kho trong JSON-LD khớp trang; không có đánh giá thì không có `aggregateRating`.
- Trang riêng tư (đăng nhập, giỏ hàng, thanh toán, khu nhân viên...) và bộ lọc/tìm kiếm có `noindex`; trang thường được index.
- Bản tiếng Anh: `og:locale` là `en_US`, mô tả tiếng Anh; xong thì trả lại tiếng Việt.
- Điện thoại 390 px: không tràn ngang.
- Xe đã tắt không có trong sitemap (cần tắt xe qua API quản lý rồi bật lại; chưa tự động hóa).

---

## Cách làm mỗi việc

Giữ cách làm của kế hoạch cũ:

1. Tạo nhánh mới từ `develop` (ví dụ `feat/warranty`); nếu đụng cùng file với nhánh chưa merge thì tách từ nhánh đó.
2. Thay đổi DB: viết `backend/database/update_<tên>.sql` **và** sửa `setup_ebike_database.sql` cho khớp; ghi vào README cần chạy file nào.
3. Chữ mới trên giao diện: thêm khóa vào `messages.properties` và `messages_en.properties`; chữ trong JS bọc `t('...')` và thêm dòng vào `i18n/js-en.json`. `StaffEnglishSeleniumTest` và `CustomerEnglishSeleniumTest` sẽ báo nếu sót.
4. Viết test Selenium cho luồng chính; chạy cả bộ test trước khi push (app chạy với `APP_RATE_LIMIT_ENABLED=false`).
5. Commit nhỏ, mỗi commit một ý; mở PR vào `develop` với mô tả tiếng Việt (Tóm tắt / Thay đổi / Kiểm thử / Lưu ý).
6. Cập nhật dòng tương ứng trong bảng "Tình hình hiện tại" ở đầu file này.
