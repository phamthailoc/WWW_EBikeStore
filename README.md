# KINETIC E-Bike Store

Ứng dụng hiện tại dùng Spring Boot, Spring MVC, Thymeleaf, Spring Security/JWT và MariaDB.

## Thư mục chính

- `backend/`: mã nguồn Spring Boot, templates Thymeleaf, tài nguyên tĩnh, ảnh và dữ liệu ứng dụng.
- `setup_ebike_database.sql`: schema `ebike_db` và dữ liệu mẫu để import bằng HeidiSQL.
- `packages/web/`: giao diện React được giữ làm bản tham chiếu giao diện/chức năng.
- `archive/`: client, cấu hình triển khai và build output cũ không dùng khi chạy ứng dụng.

## Chạy ứng dụng có sẵn

1. Khởi động MariaDB và đảm bảo đã nạp `setup_ebike_database.sql` vào `ebike_db`.
2. Cài Java 17 trở lên.
3. Mở PowerShell tại thư mục `backend` rồi chạy:

   ```powershell
   java -jar target/ebike-backend-1.0.0.jar
   ```

4. Mở <http://localhost:8080>.

## Chạy từ mã nguồn

Cài Maven 3.8+ rồi chạy trong thư mục `backend`:

```powershell
mvn spring-boot:run
```

Mặc định MariaDB dùng `localhost:3306`, user `root`, mật khẩu `123`. Có thể đổi bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

Thành phần cũ được mô tả trong `archive/legacy-project/README.md` và `archive/legacy-deployment/README.md`.

