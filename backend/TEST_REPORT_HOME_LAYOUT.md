# Kiểm tra bố cục trang chủ — 02/11/2026

## Thay đổi

- Bổ sung `static/css/home.css` cho phần công nghệ và đăng ký nhận tin trước đây thiếu CSS: căn lề theo bộ sưu tập, giới hạn độ rộng nội dung, chia cột và điều chỉnh khoảng cách.
- Công nghệ hiển thị ba cột trên máy tính, một cột trên điện thoại. Liên kết “Công nghệ” mở đúng phần này.
- Ô email và nút đăng ký có bố cục riêng, không còn kiểu hiển thị mặc định của trình duyệt. Vùng thông báo có `role="status"`.
- Ảnh xe trên trang chủ dùng `object-fit: contain` để không cắt thân xe; tên và giá xuống dòng khi cần.
- Thanh điều hướng có thể xuống hàng trên màn hình nhỏ, giữ các liên kết và ô tìm kiếm trong khung nhìn.
- Sửa cú pháp `.@media` thành `@media` trong CSS bảng điều khiển để quy tắc cho màn hình nhỏ được trình duyệt áp dụng.
- Tăng phiên bản URL CSS trên các template dùng chung để trình duyệt tải bản mới.

## Kiểm tra

Chạy Chrome với Playwright bằng:

```powershell
cd backend/tests/ui
npm run test:layout
```

Kết quả: **91 kiểm tra đạt**.

- Trang chủ ở 1920, 1440, 1024, 768, 390 và 360px: HTTP 200, không tràn ngang; số cột, căn lề, độ rộng ô email, liên kết điều hướng và chế độ hiển thị ảnh đúng.
- Các trang `/products`, `/product/kinetic-keva`, `/login`, `/signup`, `/cart`, `/favorites`, `/compare` ở 1440, 390 và 360px: HTTP 200 và không tràn ngang.
- Không có lỗi JavaScript hoặc tài nguyên nội bộ trả HTTP lỗi trong các lượt kiểm tra.
- Kiểm tra trực quan ảnh chụp phần công nghệ và đăng ký nhận tin ở máy tính và điện thoại.
- Maven `package -DskipTests` thành công; chạy lại ứng dụng bằng JAR đã đóng gói và kiểm tra lại bố cục trên bản phục vụ tại cổng 8080.
- Đây là kiểm tra bố cục và tải trang; không tạo đơn hàng, không thay đổi dữ liệu nghiệp vụ và không đánh giá lại toàn bộ nghiệp vụ quản trị.

Ảnh chụp và báo cáo JSON được tạo tại `backend/target/home-layout/` (không đưa vào Git).
