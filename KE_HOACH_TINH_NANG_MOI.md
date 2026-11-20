# Kế hoạch tính năng mới (V20 trở đi)

Lập ngày 14/11/2026, giả định mọi mục trong `KE_HOACH_HOAN_THIEN.md` (V1–V19, B1–B8, audit) đã xong. File này chỉ ghi các tính năng **thêm mới** để web gần với một cửa hàng xe điện thật hơn; các việc còn dở của kế hoạch cũ (V16 OTP qua SMS) vẫn theo dõi ở file cũ.

Mỗi mục ghi: vì sao nên làm, làm gì, dùng lại phần nào đã có, ước lượng, cách kiểm thử và điều cần chốt trước khi code. Ước lượng tính cho một người, gồm cả test Selenium.

## Tình hình hiện tại

| Việc | Ưu tiên | Ước lượng | Trạng thái | Nhánh / PR |
|---|---|---|---|---|
| V20. Sổ bảo hành điện tử và nhắc bảo dưỡng | P1 | 2–3 ngày | ⬜ Chưa làm | – |
| V21. Chạy test tự động trên GitHub Actions (CI) | P1 | 1 ngày | ⬜ Chưa làm | – |
| V22. Báo khi có hàng lại | P1 | 1 ngày | ⬜ Chưa làm | – |
| V23. Đặt lịch lái thử | P2 | 1–2 ngày | ⬜ Chưa làm | – |
| V24. Trả góp (mô phỏng) | P2 | 2 ngày | ⬜ Chưa làm | – |
| V25. Phụ kiện và gợi ý mua kèm | P2 | 2 ngày | ⬜ Chưa làm | – |
| V26. Phiếu nhập kho và lịch sử tồn kho | P2 | 1–2 ngày | ⬜ Chưa làm | – |
| V27. Hóa đơn và phiếu bảo hành PDF | P2 | 1–2 ngày | ⬜ Chưa làm | – |
| V28. Xuất báo cáo Excel | P3 | 1 ngày | ⬜ Chưa làm | – |
| V29. Hoàn tiền VNPay qua API | P3 | 1 ngày | ⬜ Chưa làm | – |
| V30. Tồn kho theo từng cửa hàng | P3 | 3 ngày | ⬜ Chưa làm | – |
| V31. Đăng nhập bằng Google | P3 | 1 ngày | ⬜ Chưa làm | – |
| V32. Đánh giá có ảnh, hỏi đáp dưới mỗi xe | P3 | 2 ngày | ⬜ Chưa làm | – |
| V33. Công cụ "Xe điện tiết kiệm bao nhiêu" | P3 | ½ ngày | ⬜ Chưa làm | – |
| V34. Docker Compose (app + MariaDB) | P3 | ½ ngày | ⬜ Chưa làm | – |
| V35. SEO: sitemap, dữ liệu cấu trúc, Open Graph | P3 | ½ ngày | ⬜ Chưa làm | – |

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
- **Nhắc bảo dưỡng:** job chạy mỗi ngày (giống `UnpaidOrderExpiryJob`) gửi thông báo và email khi xe đến mốc bảo dưỡng. Mốc cấu hình `app.maintenance.intervals=P1M,P6M,P12M` (bảo dưỡng lần đầu sau 1 tháng, rồi 6 tháng một lần); ghi lại đã nhắc để không gửi trùng.

**Dùng lại:** `order_item_units` (V18), `NotificationCreationService` + SSE, công tắc nhận thông báo (V4), trang Hỗ trợ của Manager làm mẫu cho trang yêu cầu bảo hành, `messages*.properties` + `js-en.json` cho tiếng Anh.

**Kiểm thử:** Selenium giao một đơn (`enterUnits`), khách thấy phiếu với đúng hạn; tra cứu bằng số khung; khách gửi yêu cầu, Manager đổi trạng thái, khách nhận thông báo. Unit test cho tính ngày hết hạn và mốc nhắc.

**Cần chốt:** mốc bảo dưỡng (1 tháng / 6 tháng?); xe hết hạn bảo hành có được gửi yêu cầu (sửa có tính phí) không.

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

---

## P2. Làm tiếp nếu còn thời gian

### V23. Đặt lịch lái thử — khoảng 1–2 ngày

**Làm gì:** khách chọn xe, cửa hàng (`showrooms`) và khung giờ trong 14 ngày tới; mỗi khung giờ giới hạn số lượt (ví dụ 2 lượt/giờ/cửa hàng). Bảng mới `test_ride_bookings` (user_id, product_id, showroom_id, slot_start, trạng thái `REQUESTED` → `CONFIRMED` → `DONE`/`CANCELLED`/`NO_SHOW`). Manager xem lịch theo ngày và xác nhận; khách nhận thông báo khi được xác nhận và được nhắc trước 1 ngày (job như V20).

**Cần chốt:** giờ mở cửa lấy từ `showrooms.opening_hours` (đang là chữ tự do) hay thêm cột giờ mở/đóng; khách vãng lai có được đặt không.

### V24. Trả góp (mô phỏng) — khoảng 2 ngày

**Vì sao:** mua xe điện trả góp rất phổ biến; hiện chỉ có VNPay và trả sau.

**Làm gì:**
- Công cụ tính ở trang chi tiết xe và checkout: chọn trả trước (20–50%) và số kỳ (6/9/12 tháng), hiện khoản trả hàng tháng và tổng tiền. Lãi suất theo kỳ do Admin đặt trong bảng giá (`pricing_rules`, thêm loại `INSTALLMENT_RATE`).
- Phương thức thanh toán mới "Trả góp": khách gửi hồ sơ (CCCD, thu nhập ước tính, chỉ lưu thông tin cần thiết); đơn ở trạng thái chờ duyệt; Manager duyệt hoặc từ chối kèm lý do. Duyệt xong khách trả khoản trả trước (VNPay hoặc tại cửa hàng), đơn đi tiếp như bình thường.
- Không kết nối công ty tài chính thật; ghi rõ trên giao diện là mô phỏng.

**Cần chốt:** có lưu ảnh CCCD không (khuyên **không** lưu, chỉ lưu số CCCD che bớt); lãi suất mẫu.

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

---

## Cách làm mỗi việc

Giữ cách làm của kế hoạch cũ:

1. Tạo nhánh mới từ `develop` (ví dụ `feat/warranty`); nếu đụng cùng file với nhánh chưa merge thì tách từ nhánh đó.
2. Thay đổi DB: viết `backend/database/update_<tên>.sql` **và** sửa `setup_ebike_database.sql` cho khớp; ghi vào README cần chạy file nào.
3. Chữ mới trên giao diện: thêm khóa vào `messages.properties` và `messages_en.properties`; chữ trong JS bọc `t('...')` và thêm dòng vào `i18n/js-en.json`. `StaffEnglishSeleniumTest` và `CustomerEnglishSeleniumTest` sẽ báo nếu sót.
4. Viết test Selenium cho luồng chính; chạy cả bộ test trước khi push (app chạy với `APP_RATE_LIMIT_ENABLED=false`).
5. Commit nhỏ, mỗi commit một ý; mở PR vào `develop` với mô tả tiếng Việt (Tóm tắt / Thay đổi / Kiểm thử / Lưu ý).
6. Cập nhật dòng tương ứng trong bảng "Tình hình hiện tại" ở đầu file này.
