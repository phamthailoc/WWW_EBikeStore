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

Mặc định MariaDB dùng `localhost:3306`, user `root`, mật khẩu `123`. Có thể đổi bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
