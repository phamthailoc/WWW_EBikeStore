# KINETIC E-Bike Store

Ứng dụng hiện tại dùng Spring Boot, Spring MVC, Thymeleaf, Spring Security/JWT và MariaDB.

## Cấu trúc chính

- `backend/src/main/java/com/ebike/`: mã Java được tổ chức theo các lớp MVC; xem `backend/ARCHITECTURE.md`.
- `backend/src/main/resources/`: templates Thymeleaf, CSS/JavaScript, ảnh, tài liệu chatbot và cấu hình.
- `backend/database/`: SQL MariaDB dùng để tạo database mới (`setup_ebike_database.sql`) và cập nhật database đang chạy (`update_*.sql`).
- `backend/database/catalog/`: hồ sơ nguồn gốc của danh mục xe điện, màu và ảnh đã kiểm tra. Ảnh hiển thị ở `backend/src/main/resources/static/images/catalog/`.
- `backend/database/setup_ebike_database.sql`: tạo và nạp dữ liệu mẫu cho schema MariaDB `ebike_db`.

Giao diện React cũ (`packages/web`), các client và cấu hình triển khai cũ (`archive/`) đã được xóa khỏi nhánh làm việc; khi cần vẫn xem lại được trong lịch sử git.

## Chạy ứng dụng

1. Khởi động MariaDB và nạp `backend/database/setup_ebike_database.sql` vào `ebike_db` nếu chưa có database.
2. Từ PowerShell, vào `backend` và chạy `mvn spring-boot:run` (Java 17+ và Maven cần có trong PATH).
3. Mở <http://localhost:8080>.

Mặc định ứng dụng kết nối MariaDB ở `localhost:3306`. Thông tin đăng nhập database **không** ghi trong mã: đặt qua biến môi trường (xem bảng dưới), đừng commit mật khẩu.

## Biến môi trường

| Nhóm | Biến | Ý nghĩa |
|---|---|---|
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Kết nối MariaDB `ebike_db`. Profile `prod` bắt buộc đặt mật khẩu; profile dev mặc định `123` |
| Đăng nhập | `APP_JWT_SECRET` | Khóa ký JWT (≥ 32 ký tự). Chạy thật (profile `prod`) bắt buộc đặt, không đặt thì app không khởi động; profile dev tự sinh khóa ngẫu nhiên mỗi lần chạy (đăng nhập bị mất khi khởi động lại, đặt biến này để giữ phiên). **Server thật phải đặt `SPRING_PROFILES_ACTIVE=prod`**, mặc định là dev (bật log SQL, không dành cho chạy thật) |
| VNPay | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_IPN_URL` | Thanh toán VNPay; chưa đặt `TMN_CODE` và `HASH_SECRET` thì checkout ẩn lựa chọn VNPay |
| Chatbot | `GEMINI_AI_KEY`, `GEMINI_AI_ENABLED`, `GEMINI_AI_MODEL` (mặc định `gemini-3.1-flash-lite`), `GEMINI_AI_FALLBACK_MODELS` (mặc định `gemini-flash-lite-latest`, dùng khi model chính quá tải hoặc không còn cấp) | Trả lời bằng Gemini; không có khóa thì chatbot dùng câu trả lời có sẵn |
| Email | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `ORDER_EMAIL_OTP_FROM` | Gửi mã xác minh email (đặt hàng, liên kết đơn đã đặt) |
| Xác minh email | `ORDER_EMAIL_OTP_REQUIRED` | `true` thì đặt hàng phải xác minh email bằng mã (mặc định `false`) |
| Hủy đơn quá hạn | `APP_ORDER_VNPAY_EXPIRY`, `APP_ORDER_PAY_LATER_EXPIRY` | Thời hạn giữ xe cho đơn VNPay (mặc định 30 phút) và đơn trả sau (72 giờ) |
| Giới hạn tần suất | `APP_RATE_LIMIT_ENABLED` | Mặc định `true`: mỗi địa chỉ IP được 5 lần đăng nhập/phút, 3 lần gửi mã email/5 phút, 20 câu hỏi chatbot/phút, 10 lần tạo thanh toán VNPay/phút, 20 lần tra cứu bảo hành công khai/phút (V20). Đặt `false` khi chạy kiểm thử tự động (Selenium) |
| Báo khi có hàng lại | `APP_PUBLIC_BASE_URL`, `STOCK_ALERT_MAIL_ENABLED`, `STOCK_ALERT_FROM`, `STOCK_ALERT_SWEEP_INTERVAL`, `STOCK_ALERT_SWEEP_INITIAL_DELAY` | `APP_PUBLIC_BASE_URL` là địa chỉ công khai của cửa hàng (ví dụ `https://shop.example.com`), **phải đặt khi triển khai**: không đặt thì link trong email báo hàng trỏ tới `http://localhost:8080`. `STOCK_ALERT_MAIL_ENABLED` (mặc định `true`) bật/tắt email báo hàng; `STOCK_ALERT_FROM` là người gửi (mặc định lấy `ORDER_EMAIL_OTP_FROM`); `STOCK_ALERT_SWEEP_INTERVAL` (mặc định `PT5M`) và `STOCK_ALERT_SWEEP_INITIAL_DELAY` (mặc định `PT2M`) là chu kỳ quét và độ trễ lần quét đầu |
| Nhắc bảo dưỡng | `APP_MAINTENANCE_INTERVALS`, `APP_MAINTENANCE_CRON`, `APP_MAINTENANCE_BATCH_SIZE`, `APP_MAINTENANCE_MAX_PER_RUN`, `APP_MAINTENANCE_MAIL_ENABLED`, `MAINTENANCE_MAIL_FROM` | Mốc nhắc bảo dưỡng tính từ ngày giao xe (mặc định `P1M,P6M,P12M,P18M,P24M,P30M,P36M`: lần đầu sau 1 tháng, rồi 6 tháng một lần trong 3 năm) và giờ chạy job nhắc hằng ngày (mặc định `0 0 8 * * *`); job đọc xe theo lô (`APP_MAINTENANCE_BATCH_SIZE`, mặc định 100) và lưu từng xe trong một giao dịch riêng. Mỗi lần chạy chỉ nhắc tối đa `APP_MAINTENANCE_MAX_PER_RUN` xe (mặc định 200, dưới hàng đợi email 500, vì email vượt hàng đợi bị bỏ và không gửi lại): lần chạy đầu sau khi triển khai, mỗi xe đã giao trong 36 tháng gần đây sẽ nhận nhắc ngay, số vượt giới hạn được nhắc dần ở các lần chạy hằng ngày kế tiếp. Email nhắc có công tắc và địa chỉ gửi riêng (`APP_MAINTENANCE_MAIL_ENABLED`, `MAINTENANCE_MAIL_FROM`; mặc định lấy theo `ORDER_EMAIL_OTP_MAIL_ENABLED` và `ORDER_EMAIL_OTP_FROM`), gửi nền sau khi lưu và kèm liên kết tới sổ bảo hành (dùng `APP_PUBLIC_BASE_URL`) |
| Lái thử | `APP_TEST_RIDE_DAYS_AHEAD`, `APP_TEST_RIDE_CAPACITY`, `APP_TEST_RIDE_LEAD_MINUTES`, `APP_TEST_RIDE_MAX_OPEN`, `APP_TEST_RIDE_REMINDER_HOURS`, `APP_TEST_RIDE_CRON`, `APP_TEST_RIDE_MAX_PER_RUN`, `APP_TEST_RIDE_MAIL_ENABLED`, `TEST_RIDE_MAIL_FROM` | Khách (chỉ tài khoản khách hàng) đặt lịch lái thử một xe tại một cửa hàng, mỗi lượt 1 giờ trong giờ mở cửa ghi ở `showrooms.opening_hours` (chỉ đọc khoảng giờ đầu tiên dạng `08:00 - 21:00`; chữ chỉ có "Đóng cửa"/"Tạm nghỉ"/"Closed" thì không nhận lịch; không đọc được thì dùng 08:00 - 21:00; chưa hiểu giờ nghỉ trưa, giờ khác nhau theo thứ, giờ qua đêm, ngày nghỉ và ngày lễ). Mặc định đặt trước tối thiểu 2 giờ và tối đa 14 ngày (ngày cuối được đặt là hôm nay cộng 14 ngày; hộp thoại đọc số ngày từ cấu hình), mỗi giờ mỗi cửa hàng 2 lượt, mỗi khách tối đa 3 lịch chưa diễn ra. Job chạy 30 phút một lần: nhắc khách trước 24 giờ và đóng yêu cầu quá giờ mà chưa được xác nhận. |
| Ảnh | `APP_STORAGE_S3_BUCKET`, `APP_STORAGE_S3_REGION`, `APP_STORAGE_S3_PUBLIC_BASE_URL` | Lưu ảnh sản phẩm |

## Cập nhật database đang chạy

`setup_ebike_database.sql` chỉ dùng cho database mới. Khi cập nhật code, chạy các file `backend/database/update_*.sql` còn thiếu vào `ebike_db` (mỗi file chạy lại nhiều lần vẫn an toàn) **trước** khi khởi động lại ứng dụng, vì Hibernate chỉ kiểm tra schema (`ddl-auto=validate`):

- `update_pricing_rules.sql`: biểu phí của cửa hàng bán xe
- `update_orders_promotion.sql`: cột `orders.promotion_id` (mỗi khách dùng một mã một lần)
- `update_orders_pickup_deadline.sql`: cột `orders.pickup_deadline_at` (hạn giữ xe do cửa hàng đặt cho đơn "Chờ lấy")
- `update_order_item_units.sql`: bảng `order_item_units` (số khung, số máy từng xe)
- `update_stock_alerts.sql`: bảng `stock_alerts` (đăng ký báo khi có hàng lại, V22)
- `update_warranty_book.sql` (chạy **sau** `update_order_item_units.sql`, vì có khóa ngoại tới `order_item_units`): bảng `warranty_claims`, `maintenance_reminders`, cột `orders.delivered_at`, `order_item_units.warranty_months` và cột sinh `warranty_claims.open_unit_id` (sổ bảo hành điện tử, V20)
- `update_test_ride_bookings.sql`: bảng `test_ride_bookings` (đặt lịch lái thử, V23); chạy được sau `update_stock_alerts.sql` hoặc độc lập, cần bảng `showrooms`, `products`, `users` đã có
- `update_user_preferences_defaults.sql`: giao diện mặc định theo thiết bị (`system`), ngôn ngữ mặc định `vi`
- `update_verified_electric_catalog.sql`: chuyển danh mục cũ (có xe xăng 50cc, ảnh `/images/products/`) sang danh mục xe điện, ảnh ở `/images/catalog/`. Chạy **một lần**, trước `update_vehicle_types.sql` và `update_product_stock_totals.sql`
- `update_vehicle_types.sql`: xe trên 25 km/h hoặc 250 W là xe máy điện
- `update_product_stock_totals.sql`: tồn kho sản phẩm bằng tổng tồn kho các màu
- `update_support_requests.sql`: bảng `support_requests` của trang Hỗ trợ
