# Kết quả kiểm thử tất cả luồng, tất cả vai trò

Kế hoạch: [KE_HOACH_KIEM_THU.md](KE_HOACH_KIEM_THU.md). Môi trường: app và MariaDB trên máy local, Chrome, dữ liệu thử; email, VNPay và Gemini thật chưa kiểm (để sau).

Cập nhật lần cuối: 24/11/2026. Ký hiệu: ✅ đạt, 🔧 lỗi đã tìm thấy và sửa, 🟡 mới có Selenium hoặc unit test phủ, chưa đi tay, ⏳ chưa làm.

## 1. Vai trò và tài khoản thử

Hệ thống chỉ có sáu vai trò cố định (ADMIN, MANAGER, CUSTOMER, OPERATOR, SUPPORT, MAINTENANCE); chưa có chức năng tạo vai trò mới, nên vai trò "chỉ xem" trong kế hoạch được thay bằng việc thử ba vai trò không có quyền nào. Tài khoản thử đã tạo trên máy local: `tay…` (khách), `role.customer2`, `role.manager2`, `role.operator`, `role.support`, `role.maintenance`, cùng `selenium.manager`, `selenium.admin`.

## 2. Ma trận phân quyền (trang và API)

Đã chạy tay bằng script trên 8 tài khoản × 42 địa chỉ, rồi chuyển thành test tự động `RoleAccessMatrixSeleniumTest` (46 dòng × 7 vai trò, cộng thử ghi và thử đọc dữ liệu người khác).

| Kết quả | Chi tiết |
|---|---|
| ✅ Khách vãng lai | mọi trang chuyển hướng đăng nhập (302), mọi API trả 401 |
| ✅ Khách hàng | vào được `/customer/**` và API của mình; mọi trang và API `/manager` và `/admin` trả 403; không đọc được đơn, phiếu, hồ sơ trả góp của khách khác |
| ✅ Manager | vào được `/manager/**` và các trang `/admin/warranty`, `/admin/test-rides`, `/admin/installments`, `/admin/stock-receipts`; `/admin` và mọi API quản trị khác trả 403; không vào được Sổ bảo hành và Lịch lái thử của khách |
| ✅ Admin | vào được tất cả |
| 🔧 OPERATOR, SUPPORT, MAINTENANCE | **Lỗi phân quyền:** ba vai trò này không có quyền nào nhưng vào được toàn bộ trang `/admin` và gọi được mọi API quản trị: đọc được 875 tài khoản (email, vai trò), nhật ký hoạt động, bảng giá, và **sửa được giá và khuyến mãi** (PATCH quy tắc giá trả 200). Chỉ tạo tài khoản là bị chặn (403). Nguyên nhân: danh sách `ADMIN_CONSOLE_ROLES` trong `SecurityConfiguration` gồm cả ba vai trò. **Đã sửa:** chỉ ADMIN mở được bảng quản trị; test mới khẳng định ba vai trò này nhận 403 ở mọi nơi. |

Còn mở: `/api/v1/notifications` trả 200 cho cả ba vai trò không quyền (trả danh sách rỗng của chính họ, không lộ dữ liệu người khác), chấp nhận.

## 3. Luồng xuyên vai trò

| # | Luồng | Kết quả |
|---|---|---|
| 1 | Mua trả sau, nhận tại cửa hàng | 🟡 Selenium (`OrderDetail`, `CancelOrder`, `CustomerArea`...) |
| 2 | Giao tận nhà, mã khuyến mãi, dịch vụ biển số | 🟡 Selenium (`DeliveryFee`, `PromotionOncePerCustomer`, `AdminPricing`) |
| 3 | VNPay (thành công, thất bại, quá hạn) | 🟡 Selenium với khóa giả (`VnPayCheckout`); VNPay thật chưa kiểm |
| 4 | Mua trả góp | ✅ đi tay trọn vẹn: khách đặt (công cụ trả góp, CCCD, thu nhập), Manager duyệt kèm ghi chú, thu trả trước tại cửa hàng, chuyển trạng thái, nhập số khung, giao xe; thông báo của khách đầy đủ. Quá hạn duyệt và quá hạn trả trước: 🟡 unit test |
| 5 | Hủy đơn, yêu cầu hủy | 🟡 Selenium (`CancelOrder`) |
| 6 | Khách vãng lai đặt rồi liên kết đơn | 🟡 Selenium (`GuestOrderLink`) |
| 7 | Báo khi có hàng lại | ✅ đi tay: khách đăng ký màu hết hàng (đăng ký lại trả 200, không trùng), Manager nhập kho 3 xe, khách nhận thông báo "đã có hàng lại", hồ sơ ghi giờ đã báo |
| 8 | Lái thử | ✅ đi tay: xem khung giờ, đặt lịch, đặt trùng giờ 409, giờ đã qua 400, Manager xác nhận kèm ghi chú, hủy không ghi lý do 400, khách nhận thông báo |
| 9 | Bảo hành | ✅ đi tay: khách thấy xe trong Sổ bảo hành, gửi yêu cầu, Manager thấy đủ điện thoại và mã đơn, tiếp nhận kèm ghi chú, khách nhận thông báo; từ chối, sửa, hoàn tất: 🟡 Selenium (`Warranty`) |
| 10 | Kho: phiếu nhập, điều chỉnh, lịch sử | ✅ đi tay: phiếu nhập PN-000010, số lượng 0 bị từ chối, sổ kho khớp tồn ở mọi màu |
| 11 | Báo cáo và Excel | 🟡 Selenium (`ExcelExport`); Manager mở báo cáo bằng tay: ✅ |
| 12 | Admin: giá, khuyến mãi, tài khoản, nhật ký | ✅ duyệt tay các trang (🔧 sửa ngày giờ ISO thô, 🔧 trang Khách hàng thiếu cột); sửa giá và hiện ở trang xe: 🟡 Selenium (`AdminPricing`, `SavingsCalculator`) |
| 13 | Xóa tài khoản, tài khoản bị khóa | 🟡 unit và Selenium (`AccountErasure`...); bản ghi cửa hàng giữ, thông tin cá nhân ẩn danh |
| 14 | Việc chạy ngầm (hủy quá hạn, nhắc bảo dưỡng, nhắc lái thử, quét báo hàng) | ✅ chạy thử với thời hạn rút ngắn: đơn VNPay và đơn trả sau quá hạn tự hủy đúng lý do, tồn kho hoàn lại và sổ kho khớp; khách nhận thông báo hủy và nhắc lịch lái thử. Nhắc bảo dưỡng: job chạy, chưa có xe đến hạn nên không gửi (🟡 unit test) |

## 4. Lỗi tìm thấy trong lúc kiểm thử

| Mức | Lỗi | Trạng thái |
|---|---|---|
| Nghiêm trọng (phân quyền) | OPERATOR, SUPPORT, MAINTENANCE đọc được tài khoản, nhật ký, sửa được giá và khuyến mãi | 🔧 đã sửa, có test |
| Trung bình | Ngày giờ hiện chuỗi ISO thô ở bảng quản trị | 🔧 đã sửa |
| Trung bình | Trang "Khách hàng" của Manager thiếu cột tên, có nhãn tiếng Anh | 🔧 đã sửa |
| Thấp | Menu Admin đổi sang menu Manager khi mở Bảo hành, Lái thử, Trả góp, Nhập kho | 🔧 đã sửa (`/admin/...`) |
| Thấp | Trang xe mở ở màu mặc định đã hết hàng dù màu khác còn hàng | 🔧 đã sửa |
| Thấp | Nút "Tải hóa đơn" trong khi chú thích nói đó không phải hóa đơn | 🔧 đã đổi thành "Tải phiếu bán hàng" |
| Thấp | Thông báo tạo đơn trả góp nói "chờ xác nhận" thay vì "chờ cửa hàng duyệt hồ sơ"; thông báo "đã giao" cho đơn nhận tại cửa hàng | mở |

## 5. Không chức năng (đã chạy)

| Hạng mục | Kết quả |
|---|---|
| Chèn mã độc vào ô văn bản (yêu cầu hỗ trợ: tên, tiêu đề, nội dung; mô tả bảo hành) | ✅ lưu nguyên văn, hiện như chữ ở hàng đợi của Manager, không chạy mã, không tạo phần tử mới |
| Điện thoại 375px, chế độ tối, các trang Manager (tổng quan, chi tiết đơn, trả góp, bảo hành, lái thử, phiếu nhập kho, báo cáo) | ✅ không tràn ngang |
| Điện thoại 375px, chế độ tối, các trang công khai (chủ, danh sách xe, chi tiết xe, thanh toán, hỗ trợ, bảo hành) | ✅ không tràn ngang |
| Trình duyệt | chỉ Chrome (theo yêu cầu) |

## 6. Phần còn lại của kế hoạch

- Còn đi tay: luồng 3 hiện chỉ có Selenium với khóa giả.
- Không chức năng còn lại: máy tính bảng, dữ liệu lớn, truy cập đồng thời, giới hạn tần suất bật.
- Dịch vụ ngoài (email, VNPay, Gemini): để sau.
