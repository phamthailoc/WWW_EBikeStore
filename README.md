# KINETIC E-Bike Store

Ứng dụng hiện tại dùng Spring Boot, Spring MVC, Thymeleaf, Spring Security/JWT và MariaDB.

## Cấu trúc chính

- `backend/src/main/java/com/ebike/`: mã Java được tổ chức theo các lớp MVC; xem `backend/ARCHITECTURE.md`.
- `backend/src/main/resources/`: templates Thymeleaf, CSS/JavaScript, ảnh, tài liệu chatbot và cấu hình.
- `backend/database/`: SQL MariaDB dùng để nạp database, script sinh SQL và schema nguồn.
- `backend/database/catalog/`: dữ liệu xe điện, màu và nguồn ảnh đã kiểm tra. Ảnh hiển thị ở `backend/src/main/resources/static/images/catalog/`.
- `backend/database/setup_ebike_database.sql`: tạo và nạp dữ liệu mẫu cho schema MariaDB `ebike_db`.
- `packages/web/`: bản giao diện React cũ, giữ làm tham chiếu thiết kế/chức năng.
- `archive/`: các client, cấu hình triển khai và build cũ không dùng để chạy Spring Boot.

## Chạy ứng dụng

1. Khởi động MariaDB và nạp `backend/database/setup_ebike_database.sql` vào `ebike_db` nếu chưa có database.
2. Từ PowerShell, vào `backend` và chạy `mvn spring-boot:run` (Java 17+ và Maven cần có trong PATH).
3. Mở <http://localhost:8080>.

Mặc định ứng dụng kết nối MariaDB ở `localhost:3306`. Thông tin đăng nhập database **không** ghi trong mã: đặt qua biến môi trường (xem bảng dưới), đừng commit mật khẩu.

## Biến môi trường

| Nhóm | Biến | Ý nghĩa |
|---|---|---|
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Kết nối MariaDB `ebike_db` |
| Đăng nhập | `APP_JWT_SECRET` | Khóa ký JWT, bắt buộc đổi khi chạy thật (≥ 32 ký tự) |
| VNPay | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_IPN_URL` | Thanh toán VNPay; chưa đặt `TMN_CODE` và `HASH_SECRET` thì checkout ẩn lựa chọn VNPay |
| Chatbot | `GEMINI_AI_KEY`, `GEMINI_AI_ENABLED`, `GEMINI_AI_MODEL` (mặc định `gemini-3.1-flash-lite`), `GEMINI_AI_FALLBACK_MODELS` (mặc định `gemini-flash-lite-latest`, dùng khi model chính quá tải hoặc không còn cấp) | Trả lời bằng Gemini; không có khóa thì chatbot dùng câu trả lời có sẵn |
| Email | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `ORDER_EMAIL_OTP_FROM` | Gửi mã xác minh email (đặt hàng, liên kết đơn đã đặt) |
| Xác minh email | `ORDER_EMAIL_OTP_REQUIRED` | `true` thì đặt hàng phải xác minh email bằng mã (mặc định `false`) |
| Hủy đơn quá hạn | `APP_ORDER_VNPAY_EXPIRY`, `APP_ORDER_PAY_LATER_EXPIRY` | Thời hạn giữ xe cho đơn VNPay (mặc định 30 phút) và đơn trả sau (72 giờ) |
| Cache | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis cho cache sản phẩm |
| Ảnh | `APP_STORAGE_S3_BUCKET`, `APP_STORAGE_S3_REGION`, `APP_STORAGE_S3_PUBLIC_BASE_URL` | Lưu ảnh sản phẩm |

## Cập nhật database đang chạy

`setup_ebike_database.sql` chỉ dùng cho database mới. Khi cập nhật code, chạy các file `backend/database/update_*.sql` còn thiếu vào `ebike_db` (mỗi file chạy lại nhiều lần vẫn an toàn) **trước** khi khởi động lại ứng dụng, vì Hibernate chỉ kiểm tra schema (`ddl-auto=validate`):

- `update_pricing_rules.sql`: biểu phí của cửa hàng bán xe
- `update_orders_promotion.sql`: cột `orders.promotion_id` (mỗi khách dùng một mã một lần)
- `update_orders_pickup_deadline.sql`: cột `orders.pickup_deadline_at` (hạn giữ xe do cửa hàng đặt cho đơn "Chờ lấy")
- `update_vehicle_types.sql`: xe trên 25 km/h hoặc 250 W là xe máy điện
- `update_product_stock_totals.sql`: tồn kho sản phẩm bằng tổng tồn kho các màu
- `update_support_requests.sql`: bảng `support_requests` của trang Hỗ trợ
