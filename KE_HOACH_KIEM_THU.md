# Kế hoạch kiểm thử tất cả luồng, tất cả vai trò

Mục tiêu: đi qua mọi luồng nghiệp vụ của KINETIC E-Bike Store bằng từng vai trò, kể cả những gì vai trò đó **không** được làm, rồi ghi kết quả và sửa lỗi tìm thấy. Chạy trên máy local (app + MariaDB `ebike_db`), dữ liệu thử, không đụng database thật.

## 1. Vai trò và tài khoản thử

| Vai trò | Có trong hệ thống | Tài khoản dùng để thử |
|---|---|---|
| Khách vãng lai (chưa đăng nhập) | không cần tài khoản | trình duyệt ẩn danh |
| CUSTOMER (khách hàng) | 16 quyền | tạo mới mỗi lượt (`tay…`), thêm một khách thứ hai để thử "xe/đơn của người khác" |
| MANAGER (quản lý) | 26 quyền | `selenium.manager` |
| ADMIN (quản trị) | 39 quyền | `selenium.admin` |
| Vai trò chỉ xem (Manager chỉ có quyền xem, không có quyền cập nhật) | Admin tạo ở trang Vai trò | tạo `selenium.viewonly` với `order:view-all` nhưng không có `order:update-status` |
| OPERATOR, SUPPORT, MAINTENANCE | có vai trò nhưng 0 quyền | tạo một tài khoản mỗi vai trò; kiểm tra chỉ vào được phần được phép (nếu có) và bị chặn ở mọi nơi khác |
| Tài khoản bị khóa, tài khoản đã xóa | trạng thái LOCKED, DELETED | tạo từ khách thử rồi Admin khóa / khách tự xóa |

## 2. Ma trận luồng × vai trò (v = làm được, x = phải bị chặn, – = không áp dụng)

| Luồng | Vãng lai | Khách | Chỉ xem | Manager | Admin |
|---|---|---|---|---|---|
| Xem danh mục, chi tiết xe, so sánh, tìm kiếm | v | v | v | v | v |
| Công cụ tiết kiệm trên trang xe | v | v | v | v | v |
| Chatbot | v | v | v | v | v |
| Gửi yêu cầu hỗ trợ | v | v | – | – | – |
| Tra cứu bảo hành công khai (theo số khung) | v | v | v | v | v |
| Đặt hàng (trả sau, VNPay, nhận tại cửa hàng, giao tận nhà, mã khuyến mãi, dịch vụ biển số) | v (trừ trả góp) | v | x | x | x |
| Mua trả góp (hồ sơ, theo dõi, trả trước) | x | v | x | x | x |
| Hủy đơn, yêu cầu hủy | – | v | x | v (duyệt) | v |
| Yêu thích, đánh giá, báo hàng lại | x | v | x | x | x |
| Đặt, hủy lịch lái thử | x | v | x | x | x |
| Sổ bảo hành, gửi yêu cầu bảo hành, tải phiếu | x | v | x | x | x |
| Hồ sơ cá nhân: địa chỉ, giao diện, thông báo, đổi mật khẩu, xóa tài khoản | x | v | v (của mình) | v | v |
| Đơn hàng (xem tất cả, đổi trạng thái, giao nhận, số khung, hủy) | x | x | xem | v | v |
| Thanh toán (xác nhận thu, hoàn tiền) | x | x | xem | v | v |
| Hàng đợi hỗ trợ, bảo hành, lái thử, trả góp (xem, xử lý) | x | x | xem, không nút xử lý | v | v |
| Thu nhập khai và CCCD trong hồ sơ trả góp | x | của mình | ẩn | thấy | thấy |
| Kho: điều chỉnh tồn, lịch sử tồn | x | x | xem lịch sử | v | v |
| Phiếu nhập kho | x | x | x | v | v |
| Báo cáo doanh thu, xuất Excel | x | x | x | v | v |
| Sản phẩm: tạo, sửa, bật/tắt, màu, ảnh | x | x | x | v | v |
| Khuyến mãi, quy tắc giá, kỳ hạn trả góp, quy tắc tiết kiệm | x | x | x | x | v |
| Tài khoản, vai trò và quyền, nhật ký, người dùng | x | x | x | x | v |

## 3. Luồng xuyên vai trò (kịch bản từ đầu đến cuối)

Mỗi kịch bản ghi: bước, kết quả mong đợi, kết quả thực tế, lỗi (nếu có).

1. **Mua trả sau, nhận tại cửa hàng:** khách đặt, Manager xác nhận thu, đổi trạng thái đến "Đã nhận" (nhập số khung), phiếu bán hàng PDF, khách thấy đơn và thông báo.
2. **Mua trả sau, giao tận nhà, có mã khuyến mãi và dịch vụ biển số:** phí giao, phí đăng ký, giảm giá hiện đúng trên giỏ, đơn, phiếu.
3. **Mua qua VNPay:** tạo link, quay về trang kết quả (thành công, thất bại, hủy), IPN, thanh toán lại, hủy đơn khi quá hạn.
4. **Mua trả góp:** đặt, Manager duyệt hoặc từ chối (kèm lý do), trả trước tại cửa hàng hoặc VNPay, quá hạn duyệt thì đơn tự hủy, quá hạn trả trước thì tự hủy; khách tự xóa tài khoản khi còn hồ sơ mở thì bị từ chối.
5. **Hủy đơn:** khách hủy đơn đang chờ; yêu cầu hủy khi đơn đã đang xử lý; Manager duyệt hoặc từ chối; kho được hoàn lại; đơn đã giao không hủy được.
6. **Khách vãng lai đặt hàng rồi đăng ký:** liên kết đơn bằng email xác minh; không thấy đơn của người khác.
7. **Báo khi có hàng lại:** khách đăng ký màu hết hàng, Manager nhập kho, khách nhận thông báo và email.
8. **Lái thử:** đặt khung giờ, trùng giờ, ngoài giờ mở cửa, ngày nghỉ, Manager xác nhận, đánh dấu đã lái/không đến, hủy kèm lý do, nhắc lịch, hủy từ phía khách.
9. **Bảo hành:** tra cứu công khai theo số khung, sổ bảo hành, gửi yêu cầu (trong và ngoài hạn), Manager tiếp nhận, sửa, hoàn tất, từ chối (phải có lý do mới), thông báo cho khách, nhắc bảo dưỡng.
10. **Kho:** Manager lập phiếu nhập kho, tồn tăng, lịch sử tồn ghi dòng mới; điều chỉnh tay có ghi chú; đặt hàng làm tồn giảm; hủy đơn hoàn tồn.
11. **Báo cáo và Excel:** doanh thu theo ngày/tuần/tháng, đơn hủy, xuất Excel báo cáo và danh sách đơn (đủ cột, số là số, công thức không chạy).
12. **Admin:** đổi giá quy tắc (điện, xăng, tiêu hao, phí giao, thuế, lãi suất) và xem đáp án thay đổi ở trang xe, checkout; tạo khuyến mãi; tạo tài khoản và vai trò; khóa tài khoản và xem nhật ký ghi lại.
13. **Xóa tài khoản và tài khoản bị khóa:** đơn, bảo hành, lái thử giữ nguyên bản ghi cửa hàng, thông tin cá nhân được ẩn danh; tài khoản bị khóa không nhận nhắc bảo dưỡng và thông báo bảo hành.
14. **Việc chạy ngầm:** hủy đơn quá hạn, nhắc bảo dưỡng, nhắc lịch lái thử, đóng yêu cầu lái thử hết hạn, quét báo hàng lại (rút ngắn thời hạn để chạy thử).

## 4. Kiểm thử phân quyền (kịch bản âm tính)

Với từng vai trò trong bảng 1, đi qua cả hai tầng:

- **Trang:** mở trực tiếp mọi đường dẫn `/customer/**`, `/manager/**`, `/admin/**` (kể cả `/admin/warranty`, `/admin/test-rides`, `/admin/installments`, `/admin/stock-receipts`); kết quả mong đợi: được vào, chuyển hướng đăng nhập, hoặc trang "không có quyền".
- **API:** gọi từng điểm cuối `/api/v1/**` với token của vai trò đó: mong đợi 200, 401 hoặc 403; không có điểm cuối nào trả dữ liệu của người khác (đơn, hồ sơ trả góp, bảo hành, lịch lái thử của khách khác).
- **Nút:** ở vai trò chỉ xem, các nút xử lý (Duyệt, Từ chối, Tiếp nhận, Xác nhận thu...) không hiện.

## 5. Kiểm thử không chức năng

- **Điện thoại 375px, máy tính bảng 768px:** mọi trang chính, không tràn ngang, nút bấm được, hộp thoại vừa màn hình.
- **Sáng và tối:** mọi trang, chữ đọc được, không vùng trắng chói.
- **Trình duyệt:** Chrome (có sẵn), thêm Edge và Firefox cho các luồng đặt hàng và thanh toán.
- **Dữ liệu lớn:** nạp vài nghìn đơn, tài khoản, hồ sơ giả vào DB local, đo thời gian mở các danh sách và nút "Xem thêm".
- **Đồng thời:** hai khách mua cùng chiếc xe cuối, hai Manager duyệt cùng một hồ sơ, hủy đơn đúng lúc job quá hạn chạy.
- **Giới hạn tần suất bật** (`APP_RATE_LIMIT_ENABLED=true`): đăng nhập sai nhiều lần, gửi mã, hỏi chatbot; thông báo dễ hiểu, không khóa nhầm người dùng chung IP.
- **Bảo mật cơ bản:** gửi `<script>` vào mọi ô văn bản (ghi chú, mô tả sự cố, tên), mở lại trang xem có bị chạy không; ID đơn người khác; token hết hạn.

## 6. Dịch vụ ngoài (cần bạn cấp)

| Dịch vụ | Cần | Kiểm gì |
|---|---|---|
| Email | tài khoản SMTP thử (hoặc MailHog/Mailpit chạy local) | email xác nhận, trả góp, lái thử, báo hàng, bảo dưỡng, giao xe kèm hai PDF |
| VNPay | `TMN_CODE` và `HASH_SECRET` của sandbox | thanh toán thành công, thất bại, người dùng hủy, IPN |
| Gemini | khóa API (đặt bằng biến môi trường, không ghi vào file) | chatbot có và không có AI, khi AI lỗi thì dùng câu trả lời nội bộ |

Nếu chưa cấp, các mục trên đánh dấu "chưa kiểm" thay vì bỏ qua âm thầm.

## 7. Cách thực hiện và ghi nhận

1. **Chuẩn bị (0,5 ngày):** tạo các tài khoản thử trong bảng 1, đưa DB local về dữ liệu mẫu, bật app với `APP_RATE_LIMIT_ENABLED=false` cho phần chức năng và bật lại cho phần giới hạn tần suất.
2. **Tự động hóa những gì lặp lại (1 ngày):** viết thêm Selenium cho các khoảng trống: vai trò chỉ xem, OPERATOR/SUPPORT/MAINTENANCE, tài khoản bị khóa, ma trận phân quyền API (một test duyệt bảng 2 và 4), việc chạy ngầm.
3. **Duyệt tay theo kịch bản (1,5 ngày):** đi bảng 3 từng vai trò, ghi kết quả vào bảng trong `KIEM_THU_KET_QUA.md` (bước, mong đợi, thực tế, đạt/lỗi, ảnh chụp hoặc dòng log khi lỗi).
4. **Không chức năng (1 ngày):** bảng 5.
5. **Sửa và chạy lại (1 ngày):** mỗi lỗi một dòng trong `MINOR_ISSUES.md` (cách tái hiện, mức độ), sửa, thêm test, chạy lại toàn bộ.

Tiêu chí hoàn thành: mọi dòng của bảng 2 và 3 có kết quả (đạt, đã sửa, hoặc ghi rõ "chưa kiểm vì thiếu dịch vụ ngoài"); không còn lỗi mức nghiêm trọng hay lỗi phân quyền; unit và Selenium xanh trên CI.
