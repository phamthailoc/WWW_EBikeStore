# Legacy project files

Các thành phần được giữ ở đây để tham khảo/lưu trữ; chúng không tham gia chạy ứng dụng Spring Boot hiện tại.

- `packages/`: các client React Native mobile, Electron desktop và thư viện TypeScript dùng chung của monorepo cũ.
- `root/`: `package.json` và lockfile của npm workspaces cũ.
- `backend/`: cấu hình khởi tạo extension/schema PostgreSQL cũ và output Maven `target` đã sinh. Mã nguồn backend không bị chuyển; Maven sẽ tạo lại `target` khi build.
- `../legacy-deployment/`: Docker, Compose, nginx, GitHub Actions và các script triển khai EC2 cũ.

`packages/web/` vẫn ở thư mục gốc làm bản tham chiếu giao diện/chức năng cho các trang Thymeleaf. `backend/src`, cấu hình MariaDB, templates, ảnh và dữ liệu SQL vẫn là phần hiện tại.
