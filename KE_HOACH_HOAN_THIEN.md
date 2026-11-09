# Kế hoạch hoàn thiện các phần còn thiếu

Rà soát ngày 06/11/2026 trên nhánh `MinhTien` (đã merge `develop`, commit `79f6baf`).

## Tình hình hiện tại (cập nhật 06/11/2026)

| Việc | Trạng thái | Nhánh / PR |
|---|---|---|
| V1. VNPay ở checkout (UC-07, UC-12) | ✅ Xong, **đã merge vào `develop`** (07/11) | `feat/vnpay-checkout`, [PR #1](https://github.com/phamthailoc/WWW_EBikeStore/pull/1) vào `develop` |
| Test Selenium cho VNPay | ✅ 5/5 pass | Trong PR #1: `backend/src/test/java/com/ebike/selenium/VnPayCheckoutSeleniumTest.java` |
| V2. Hủy đơn đúng Word (UC-14) | ✅ Xong, đã review và test (8/8 Selenium pass), **[PR #3](https://github.com/phamthailoc/WWW_EBikeStore/pull/3) đã merge vào `develop`** (07/11) | `feat/cancel-order` (4 commit, tách từ `feat/vnpay-checkout`) |
| V3. Chi tiết đơn đầy đủ (UC-13, UC-16) | ✅ Xong và đã kiểm thử: 18 unit test pass; Selenium 20/20 pass (chạy 2 lần: VNPay 5, hủy đơn 8, chi tiết đơn 7). **gộp vào [PR #5](https://github.com/phamthailoc/WWW_EBikeStore/pull/5)** (07/11; PR #4 đã đóng) | `feat/order-detail` (4 commit, tách từ `feat/cancel-order`) |
| B1–B3. Sửa nghiệp vụ P0 | ✅ Xong (06/11): B3 chặn xác nhận thu sai; B1 liên kết đơn vãng lai qua OTP; B2 tự hủy đơn quá hạn và xử lý VNPay báo muộn. Unit test pass; Selenium B1, B3 pass | `fix/business-rules` (tách từ `feat/order-detail`, **chưa push**) |
| B4. Phí từ bảng giá | ✅ **Xong** (07/11): `PricingService` + báo giá đọc `REGISTRATION_SERVICE`, **tính theo số xe**; nhãn ở checkout và chatbot đọc phí từ bảng giá (bỏ hằng số 2.500.000); dữ liệu mẫu `pricing_rules` đã thay, `update_pricing_rules.sql` đã chạy vào `ebike_db`; Admin nhập phí ≥ 0. Sửa thêm lỗi app không khởi động (`UnpaidOrderExpiryJob` của B2 thiếu `@Autowired`). Test: 64 test, 0 lỗi, Selenium đầy đủ (tài khoản test `selenium.admin`) | `fix/business-rules` |
| B6. Phí giao xe | ✅ **Xong** (07/11, người dùng tự làm, commit `e90756e`): nhận tại cửa hàng 0đ; giao tận nhà tính theo xe, cùng tỉnh có showroom dùng `HOME_DELIVERY_SAME_CITY`, khác tỉnh dùng `HOME_DELIVERY_OTHER_PROVINCE`; tên tỉnh so khớp bỏ dấu và tiền tố. Test: 72 test, 0 lỗi, có `DeliveryFeeSeleniumTest` | `fix/business-rules` |
| B5. Đăng ký xe là dịch vụ | ✅ **Xong** (07/11, commit `3519303`): nhãn "Nhờ cửa hàng làm hộ thủ tục đăng ký xe"; báo giá trả thêm `registrationServiceAvailable` và ước tính lệ phí trước bạ (thấp–cao, chỉ để tham khảo, không vào tổng); Admin đặt `REGISTRATION_SERVICE` INACTIVE thì ẩn lựa chọn và backend từ chối đơn có chọn; ghi chú hướng dẫn tự đăng ký khi không chọn. Test: 77 test, 0 lỗi, có `RegistrationServiceSeleniumTest`; đã thử tay qua API: Admin đặt INACTIVE thì báo giá trả `registrationServiceAvailable=false` và chọn dịch vụ bị từ chối 400 | `fix/business-rules` |
| B5b. Chỉ hỗ trợ đăng ký khi nhận tại cửa hàng | ✅ **Xong** (07/11, commit `7997cee`, người dùng chốt): từ 2025 chủ xe tự kê khai đăng ký trên VNeID mức 2 (chính chủ), nên cửa hàng chỉ **hỗ trợ tại chỗ** khi khách nhận xe tại cửa hàng. Giao tận nhà: không có lựa chọn, ghi chú hướng dẫn tự đăng ký vẫn hiện; đặt đơn giao tận nhà kèm dịch vụ bị từ chối 400. Báo giá thêm `registrationRequired`. Đổi nhãn "Hỗ trợ đăng ký biển số tại cửa hàng" và tên quy tắc phí trong DB. Test: 93 test, 0 lỗi | `fix/business-rules` |
| B7. Mỗi khách dùng một mã một lần | ✅ **Xong** (Selenium pass 07/11; đã áp `update_orders_promotion.sql` vào `ebike_db` sau khi sửa cú pháp khóa ngoại cho MariaDB, commit `30a2027`) (07/11, commit `707faaf`): cột mới `orders.promotion_id` (FK tới `promotions`); đặt đơn kèm mã thì từ chối nếu khách (theo tài khoản, hoặc theo email nếu vãng lai) đã có đơn **không bị hủy** dùng mã đó; hủy đơn (khách, Manager, tự hủy quá hạn) giảm `usage_count`. 83 test, 0 lỗi. **Phải chạy `backend/database/update_orders_promotion.sql` vào `ebike_db` TRƯỚC khi khởi động lại app** (`ddl-auto=validate` sẽ báo thiếu cột). Đơn cũ không có `promotion_id` nên không bị tính. **Còn lại:** chạy `PromotionOncePerCustomerSeleniumTest` (đã viết, **chưa chạy**; cần `-Dselenium.adminUser/-adminPassword`) | `fix/business-rules` |
| B8. Phân loại xe đúng luật | ✅ **Xong** (Selenium pass 07/11; đã áp `update_vehicle_types.sql`: 5 xe chuyển sang `E_MOTORBIKE`) (07/11, commit `06a492d`): quy tắc `VehicleRegistration` (xe trên 25 km/h hoặc 250 W là xe máy, phải đăng ký; `E_MOTORBIKE` luôn phải; pin và phụ kiện thì không). Dịch vụ làm hồ sơ chỉ hiện khi đơn có xe thuộc diện phải đăng ký, chỉ tính phí cho số xe đó, đơn không có xe nào như vậy mà đòi dịch vụ thì bị từ chối 400; trang chi tiết xe ghi chú "cần đăng ký và gắn biển số". 8 mẫu `E_BIKE` (Amy, Ella, Queen, Ryan, S-One, Keva, MXS, Rosa: 35–45 km/h, 1.000 W trở lên) đổi sang `E_MOTORBIKE` trong `products.json`, file setup, `materialize_catalog.py`. **Lưu ý:** số liệu thực tế khác ghi chú ban đầu (35 km/h, 650 W); cả 35 mẫu đều vượt ngưỡng, nên mọi xe trong catalog đều thuộc diện đăng ký. Danh mục (category) "Xe đạp điện" của 8 mẫu này **giữ nguyên** vì là cách DIBAO gọi (xe có bàn đạp); đổi tên danh mục là quyết định của nhóm. 91 test, 0 lỗi. **Phải chạy `backend/database/update_vehicle_types.sql` vào `ebike_db`**. **Còn lại:** chạy Selenium (`RegistrationServiceSeleniumTest` có thêm 1 test, chưa chạy) | `fix/business-rules` |
| V4. Hồ sơ cá nhân (UC-08, Task 10, 13) | ✅ **Giao diện sáng/tối đã code (09/11, nhánh `feat/ready-for-pickup`, chưa chạy thử trên app đã khởi động lại; đã chụp ảnh trang chủ, danh mục, chi tiết xe, trang Manager ở chế độ tối):** ~130 mã màu trong 5 file CSS chuyển sang biến `--c-xxxxxx` trong `static/css/theme.css` (bộ tối sinh bằng cách đảo độ sáng, chỉnh từng biến ở file này); `static/js/theme.js` chạy trong `<head>` mọi trang, nút ◐/☀/☾ ở thanh đầu trang đổi vòng Theo thiết bị → Sáng → Tối, ô chọn trong trang hồ sơ; khách đăng nhập lưu vào `user_preferences.theme` (server vẽ sẵn `data-theme` qua `ThemeModelAdvice` nên không nháy sáng), khách vãng lai lưu `localStorage`; mặc định theo hệ điều hành. Mặc định `theme=system`, `language=vi` (entity, file setup; DB đang chạy: **chạy `backend/database/update_user_preferences_defaults.sql`**, nó cũng đổi các dòng `light`/`en` cũ). Ô chọn ngôn ngữ chưa hiện (chờ V19). Chưa soát hết màu viết cứng trong JS (`product.js`, `stock.js`). Phần còn lại ✅ **xong, đã kiểm chứng trên giao diện** (Selenium `ProfileSeleniumTest` 4/4, 07/11 16:00; sửa kèm 2 lỗi có sẵn: thiếu hàm `GET /users/{id}/preferences` từ lần tách MVC — commit `f615e06`, và thiếu luật `DELETE /api/v1/users/**` nên khách không xóa được địa chỉ — commit `f13f1c8`) (07/11, commit `3168ac6` backend + `7305342` giao diện): trang `/customer/profile` có đổi mật khẩu, sổ địa chỉ (thêm/sửa/xóa/đặt mặc định, tối đa 10, địa chỉ ≥ 10 ký tự, tỉnh lấy từ `vietnam-provinces.json`), công tắc nhận thông báo; checkout hiện ô "Địa chỉ đã lưu" cho khách đã đăng nhập, tự chọn địa chỉ mặc định và báo giá lại phí giao. 102 test, 0 lỗi. Đổi mật khẩu từ chối khi trùng mật khẩu cũ. **Quyết định:** tùy chọn `theme` và `language` **không đưa lên giao diện** vì không nơi nào dùng (web chỉ có một giao diện, tiếng Việt); chỉ giữ `notificationsEnabled`, và nay `NotificationCreationService` thật sự bỏ qua khách đã tắt. Độ dài mật khẩu tối thiểu giữ 6 (khớp trang đăng ký); đổi lên 8 là quyết định của nhóm. **Còn lại:** khởi động lại app (template Thymeleaf bị cache nên khối hồ sơ mới chưa hiện) rồi chạy `ProfileSeleniumTest` (4 test, đã viết, chưa chạy được) | `feat/profile` (tách từ `fix/business-rules`) |
| V17. Tồn kho theo màu (Task 8) | ✅ **Xong, đã kiểm chứng** (`ColourStockSeleniumTest` pass 07/11) (07/11, commit `8381dc7`, nhánh `feat/profile`): API mới `PATCH /manager/products/{id}/variants/{variantId}/stock` đặt tồn kho một màu và tự tính lại tổng sản phẩm = tổng các màu **đang bán** (màu bị tắt không tính); `PATCH /manager/products/{id}/stock` với sản phẩm có màu bị từ chối 400 (vẫn dùng được cho sản phẩm không có màu); trang kho của Manager và Admin mở hộp thoại sửa từng màu (`static/js/stock.js`). 6 unit test mới (`ManagerStockTest`), 117 test, 0 lỗi. **Phải chạy `backend/database/update_product_stock_totals.sql`** một lần để căn lại tổng cho dữ liệu đã lệch. Chưa chạy: `ColourStockSeleniumTest` (cần khởi động lại app, mật khẩu DB) | `feat/profile` |
| V5. Lọc thanh toán, tìm khách hàng (UC-17, UC-18) | ✅ **Xong, đã kiểm chứng** (`ManagerFiltersSeleniumTest` 2/2 sau khi sửa test chờ đúng lúc bảng lọc xong, commit `40fa236`) (07/11, commit `c717437`): thanh toán lọc theo trạng thái, phương thức và tìm theo mã đơn/email/người nhận/mã giao dịch; khách hàng tìm theo tên/email/tài khoản; lọc phía máy chủ (API đã có sẵn), ô tìm gõ xong 0,3 giây mới gửi, nút "Xóa bộ lọc". `ManagerFiltersSeleniumTest` (2 test) đã viết, chưa chạy (cần khởi động lại app) | `feat/profile` |
| V6. Admin: tài khoản, vai trò, khuyến mãi, nhật ký (UC-21, 22, 24, 25) | ⚠️ **Lệch quyết định 07/11: vẫn xóa cứng tài khoản chưa có đơn; chưa có trạng thái `DELETED` và ẩn danh hóa hồ sơ.** Phần còn lại ✅ **xong, đã kiểm chứng** (`AdminConsoleSeleniumTest` 4/4 sau khi cho `wait` bỏ qua phần tử cũ khi bảng vẽ lại, commit `40fa236`) (07/11, commit `93e1200`, `7b454f7`, `a4f38ff`, nhánh `feat/profile`): **sửa lỗi:** Admin không tạo hay gán được vai trò MANAGER qua API (thiếu trong danh sách cho phép) và tài khoản tạo không mật khẩu lấy mật khẩu cố định `ChangeMe@123` (nay bắt buộc ≥ 6 ký tự). Bảo vệ: không tự xóa, tự vô hiệu hóa, tự đổi vai trò; không bỏ quản trị viên hoạt động cuối cùng; không xóa tài khoản đã có đơn (vô hiệu hóa thay thế). Khuyến mãi: giảm phần trăm ≤ 100%, ngày kết thúc sau ngày bắt đầu, nhập 0 ở "giảm tối đa" để bỏ trần. Giao diện: form tạo tài khoản, đổi vai trò, tạo/sửa mã khuyến mãi (đủ trường, ghi "0 = không giới hạn" và "mỗi khách một lần") thay cho chuỗi `prompt()`; trang mới `/admin/roles` và `/admin/audit-logs` (100 thao tác gần nhất) có link ở menu. Cả bộ: 137 test chạy, 0 lỗi (40 test Selenium bị bỏ qua vì không bật). `AdminConsoleSeleniumTest` (4 test) đã viết, chưa chạy. **Còn tồn tại:** xóa tài khoản chưa có đơn vẫn có thể để lại đánh giá, yêu thích, thông báo mồ côi (các bảng đó cũng không có khóa ngoại thật); cần chạy Selenium sau khi khởi động lại app | `feat/profile` |
| V7. Quản lý ảnh sản phẩm (UC-19, 23, Task 15) | 🟡 **Đã kiểm chứng một phần** (`ManagerConsoleSeleniumTest` 07/11: hộp thoại mở và liệt kê đúng ảnh; **chưa thử tải ảnh lên** vì máy này không có cấu hình S3) (07/11, `a2e4091`): hộp thoại "Quản lý ảnh" cho cả Manager và Admin: tải nhiều ảnh một lần (gán chung hoặc theo màu), đặt ảnh đại diện, đổi thứ tự bằng nút ← →, xóa có hỏi; thay cho nút tải 1 ảnh cũ. Backend và phân quyền đã đủ từ trước, không đổi | `feat/profile` |
| V8. Trang chi tiết xe: đánh giá, xe liên quan (UC-04, Task 1, 16) | ✅ **Xong, đã kiểm chứng** (`ProductReviewSeleniumTest` 07/11; sửa kèm lỗi có sẵn từ lần tách MVC: API đánh giá thiếu tiền tố `/api/v1` nên trang xe không gọi được — commit `a079c56`) (07/11, `2152c09`): lưới "Xe liên quan"; khối đánh giá có điểm trung bình, phân bố sao, danh sách, form 1–5 sao, sửa/xóa đánh giá của mình. **Chỉ khách đã nhận xe (đơn `DELIVERED` chứa sản phẩm) mới đánh giá được**, nhãn "Đã mua hàng"; API `GET /products/{slug}/reviews/eligibility` cho biết có hiện form hay hiện lý do; thông báo lỗi chuyển sang tiếng Việt. Đánh giá cũ của người chưa mua vẫn còn, không bị xóa | `feat/profile` |
| V9. Chatbot nhớ hội thoại (UC-10) | ✅ **Xong, đã kiểm chứng** (`ChatbotMemorySeleniumTest` 07/11; sửa kèm: câu "xe đầu tiên giá bao nhiêu?" trước đó không được nhận là câu nối tiếp — commit `20362c8`) (07/11, `a1652a4`): trình duyệt gửi `chatId` riêng cho mỗi tab (`sessionStorage`), nên câu hỏi nối tiếp "xe đầu tiên giá bao nhiêu?" đúng xe; trước đây mọi người dùng chung phiên "default". Xe gợi ý hiện thành thẻ có link. Server giới hạn số hội thoại nhớ (2.000) và độ dài `chatId` (64). Chatbot khi chưa có Gemini vẫn để sau theo mục 5 | `feat/profile` |
| V10. Lọc sản phẩm đủ tiêu chí (UC-03, Task 7) | ✅ **Xong, đã kiểm chứng** (`CatalogFilterSeleniumTest` 07/11) (07/11, `1660a54`): thêm lọc loại xe và quãng đường (từ–đến km); tên loại xe, loại pin hiển thị tiếng Việt; phân trang giữ đủ bộ lọc (trước đây rơi mất khi sang trang 2). Sắp xếp giá/tên/mới nhất đã có sẵn | `feat/profile` |
| V11. Thông báo (Task 4) | ✅ **Xong, đã kiểm chứng** (`CustomerAreaSeleniumTest` 07/11; sửa kèm lỗi nghiêm trọng: luồng thông báo giữ 1 kết nối DB tới 30 phút cho mỗi trang mở, khoảng 10 trang là app treo — commit `e52a58a`) (07/11, `9522a00`): nút "Đánh dấu tất cả đã đọc"; huy hiệu số chưa đọc ở header khu vực khách hàng, cập nhật trực tiếp qua luồng sự kiện của server | `feat/profile` |
| V12. Báo cáo doanh thu (Task 9) | ✅ **Xong, đã kiểm chứng** (`ManagerConsoleSeleniumTest` 07/11) (07/11, `d559934`): chọn gộp theo ngày/tháng/quý/năm và khoảng ngày; biểu đồ cột SVG; **doanh thu tính theo ngày thanh toán** (cùng cơ sở với trang tổng quan; trước đây theo ngày đặt đơn nên hai nơi ra hai số). Chưa có cột "đã hoàn" vì payment không lưu ngày hoàn tiền (cần thêm cột nếu nhóm muốn) | `feat/profile` |
| V13. Timeline đơn hàng (Task 3) | ✅ **Trạng thái "Chờ lấy" đã code và kiểm thử (09/11, nhánh `feat/ready-for-pickup`): `ReadyForPickupSeleniumTest` 4/4; cả bộ 152 test chỉ lỗi `ChatbotMemorySeleniumTest` vì app chạy không có Gemini (mục 5 "Để sau", không liên quan V13); `CancelOrderSeleniumTest` sửa theo nhãn "Đã hủy" mới ở cột trạng thái; đã chạy `update_orders_pickup_deadline.sql` vào `ebike_db` local.** thêm `READY_FOR_PICKUP`; đơn nhận tại cửa hàng đi Đang xử lý → Chờ lấy → Đã nhận (`DELIVERED`, ghi `deliveredAt`), không qua `SHIPPED`; giao tận nhà giữ chuỗi cũ; backend chặn sai hình thức (pickup không sang `SHIPPED`, giao tận nhà không sang `READY_FOR_PICKUP`) cả ở đổi trạng thái lẫn form giao nhận; Manager chỉ hủy được đơn "Chờ lấy" sau hạn giữ xe hạn giữ xe của từng đơn `orders.pickup_deadline_at` (người dùng chốt 09/11: phải báo khách và chờ một thời gian rồi mới hủy; mặc định `app.order.pickup-hold` = 7 ngày kể từ lúc báo, **người dùng chốt 7 ngày (09/11)**; Manager đổi hạn từng đơn khi khách hẹn lại lịch bằng `PATCH /manager/orders/{id}/pickup-deadline`, khách thấy hạn trong chi tiết đơn và nhận thông báo "Đổi lịch lấy xe" mỗi khi đổi hạn; khách vãng lai không có tài khoản thì không có thông báo), khách không tự hủy; cột mới `orders.pickup_deadline_at`, **phải chạy `backend/database/update_orders_pickup_deadline.sql` trước khi khởi động lại app**; timeline, dropdown, nhãn danh sách và thông báo biết trạng thái mới. Chưa tính vào báo cáo riêng. Phần timeline thường ✅ **đã kiểm chứng** (`CustomerAreaSeleniumTest` 07/11) (07/11, `289c698`): 5 mốc Chờ xử lý → Đã giao, đánh dấu mốc hiện tại, nhánh "Đã hủy" kèm lý do. Chỉ có 3 mốc có thời gian thật (ngày đặt, bàn giao vận chuyển, đã giao); các mốc khác không hiện giờ thay vì bịa | `feat/profile` |
| V14. Lịch sử thanh toán, tồn kho (Task 8, 12) | ✅ **Xong, đã kiểm chứng** (`CustomerAreaSeleniumTest`, `ManagerConsoleSeleniumTest` 07/11) (07/11, `edb479d`): khách lọc lịch sử thanh toán theo trạng thái (cột trạng thái trước đây trống do sai tên trường, đã sửa); bảng kho có cột "Tình trạng kho" (Hết hàng / Sắp hết ≤ 3 / Còn hàng) và bộ lọc; hộp thoại sửa tồn kho từng màu có nhãn từng màu. Bảng chỉ biết tổng nên một màu hết trong khi tổng còn thì chỉ thấy ở hộp thoại | `feat/profile` |
| V15. Trang hỗ trợ (Task 14) | ✅ **Xong, đã kiểm chứng** (`SupportPageSeleniumTest` 07/11; đã tạo bảng `support_requests` trong `ebike_db` bằng `update_support_requests.sql`) (07/11, `2bbf54e`): `/support` có câu hỏi thường gặp (lấy từ cùng `faq.csv` của chatbot qua `GET /api/v1/faq`, phí hiện hành đã điền sẵn), form gửi yêu cầu (kiểm tra họ tên, email, số điện thoại, tiêu đề, nội dung; tối đa 5 yêu cầu/giờ/email; khách đăng nhập được điền sẵn tên và email) và danh sách showroom. Manager có trang `/manager/support` để nhận xử lý, ghi chú, đánh dấu đã giải quyết hoặc mở lại. **Bảng mới `support_requests`**: đã thêm vào `setup_ebike_database.sql` và `generate_mariadb_setup.py`; **phải chạy `backend/database/update_support_requests.sql` vào `ebike_db` trước khi khởi động lại app** (`ddl-auto=validate`). Ngoài phạm vi Word: cần ghi bổ sung vào tài liệu. Chưa gửi email phản hồi cho khách; nhân viên trả lời bằng email riêng | `feat/profile` |
| V16. OTP email khi đặt hàng (UC-06, NFR-07) | ⚠️ **Lệch quyết định 07/11: đã làm OTP qua EMAIL, còn quyết định là OTP qua SMS, chỉ cho đơn trả sau, xác thực lại sau 30 ngày.** Bản email 🟡 **code xong, chưa thử được** (cần SMTP) (07/11, `36ff5bf`): khi `ORDER_EMAIL_OTP_REQUIRED=true`, checkout hiện khối xác minh email: gửi mã, nhập mã, đếm ngược gửi lại theo thời gian server trả về, đổi email thì phải xác minh lại, đơn gửi kèm `emailVerificationSessionId`. Tắt mặc định thì checkout như cũ. Cần SMTP để gửi mã thật | `feat/profile` |
| V17 | ✅ xem dòng V17 ở trên | – |
| V18, V19 | Chưa làm (quyết định 07/11: V18 số khung/số máy từng xe; V19 giao diện, FAQ, chatbot tiếng Anh) | – |

**PR #1 cũng chứa 3 commit của `MinhTien`** chưa có trên `develop`: mã khuyến mãi lấy từ bảng `promotions`, Manager hoàn tiền, hủy đơn cộng lại kho.

**Thứ tự mở PR:** PR #1 (V1, đã merge) → #3 V2 (`feat/cancel-order`, đã merge) → **#5 gộp V3, B1–B8, V4–V17 (`feat/profile` → `develop`)**, theo yêu cầu merge một lần ngày 07/11 (PR #4 đã đóng). PR #2 (`feat/profile` → `main`, gộp tất cả) đã đóng ngày 07/11 theo lựa chọn của người dùng. Mỗi PR chỉ mở sau khi PR trước đã merge, để không kéo theo commit của nhánh trước.

**Việc tiếp theo (cập nhật 07/11 tối):**
1. B1–B8 và V1–V17 đều đã có code. Từ V4 trở đi (nhánh `feat/profile`) **chưa được chạy thử trên giao diện thật** và không có test mới theo yêu cầu "chỉ viết code".
2. **Trước khi khởi động lại app, chạy các file SQL cập nhật** theo README (mục "Cập nhật database đang chạy"): đặc biệt `update_product_stock_totals.sql` và `update_support_requests.sql` (bảng mới, nếu thiếu app không khởi động được).
3. Khởi động lại app rồi thử tay: hồ sơ (đổi mật khẩu, sổ địa chỉ), kho theo màu, ảnh sản phẩm, lọc thanh toán, form Admin, đánh giá, chatbot, thông báo, báo cáo, timeline, trang hỗ trợ.
4. Mở PR: PR #1 → V2 → V3 → `fix/business-rules` → `feat/profile` (xếp chồng, mỗi PR sau khi PR trước được merge).
5. **Để sau (người dùng chốt 07/11): chatbot khi chưa có Gemini.** `ChatbotService.ask` ưu tiên trích PDF hơn câu trả lời FAQ (intent `faq` không nằm trong `shouldPreferRuleBase…`). **[Phần còn lại của ghi chú này đã bị mất khi Claude ghi đè mục này ngày 07/11 vì file chưa được git theo dõi; xin bổ sung lại nếu cần.]**
6. Còn lại ngoài code: sửa mô tả trong Word (UC-01, UC-11, UC-17, UC-24, UC-08, UC-04, UC-25, Task 14 hỗ trợ), điền cột "Kết quả thực tế" ở mục 5.3, dọn dữ liệu thử `selenium…` trong `ebike_db`.

**Lưu ý hành vi mới trên `fix/business-rules`:**
- Phí hồ sơ ở checkout đổi từ 2.500.000đ (hằng số cũ) thành **500.000đ** (mặc định của `PricingService`), cho tới khi thêm quy tắc `REGISTRATION_SERVICE` vào DB.
- Tác vụ tự hủy chạy mỗi 5 phút, bắt đầu 1 phút sau khi khởi động app. Ngày 06/11, DB local không có đơn PENDING nào.
- Bỏ tự gắn đơn khi đăng ký. Liên kết đơn cần SMTP để gửi OTP; máy này chưa cấu hình nên nút liên kết báo "Chưa gửi được mã xác minh".

**Đã xử lý ở V3 (phát hiện khi review V2):** trước đây Manager chọn `CANCELLED` ở dropdown trạng thái thì đơn bị hủy nhưng không cộng lại tồn kho, không hủy payment PENDING, không lưu lý do, và dropdown cho chuyển trạng thái tùy ý. Nay backend chỉ cho đi từng bước (PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED; CANCELLED và DELIVERED là trạng thái cuối). Manager hủy đơn đi qua cùng logic với khách: bắt buộc lý do, cộng lại kho, đóng payment đang chờ, xóa cache sản phẩm.

Quy tắc chọn nhánh chung khi PR trước chưa merge:
- Nếu PR #1 **đã merge**: `git switch develop && git pull`, rồi tạo nhánh mới từ `develop`.
- Nếu PR #1 **chưa merge**: tạo nhánh từ `feat/vnpay-checkout` (`git switch feat/vnpay-checkout && git switch -c feat/cancel-order`), để code mới nằm trên code VNPay và không xung đột. Đợi PR #1 merge xong mới mở PR tiếp theo.

**Chạy app và test trên máy này**
- MariaDB root **không dùng mật khẩu `123`** như trong `application-dev.properties`. Truyền mật khẩu thật qua biến môi trường `SPRING_DATASOURCE_PASSWORD`, không sửa file.
- Muốn hiện lựa chọn VNPay khi chưa có khóa thật: đặt `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` bằng giá trị giả bất kỳ.
- Chatbot dùng Gemini: đặt `GEMINI_AI_KEY` (không ghi vào file). Model mặc định `gemini-3.1-flash-lite`, đổi bằng `GEMINI_AI_MODEL`; model dự phòng `gemini-flash-lite-latest`, đổi bằng `GEMINI_AI_FALLBACK_MODELS` (nhiều model cách nhau dấu phẩy). Các model đánh số cũ như `gemini-2.5-flash`, `gemini-2.5-flash-lite` trả 404 với key mới. Từ máy này kết nối tới Google hay bị reset, khi đó chatbot rơi về trích PDF.
- Chạy test Selenium (cần app đang chạy): `mvn test -Dtest=VnPayCheckoutSeleniumTest -Dselenium.baseUrl=http://localhost:8080 -Dselenium.vnpayHashSecret=<giá trị VNPAY_HASH_SECRET>`.
- Mỗi lần chạy, mỗi lớp test Selenium tạo 1 tài khoản `selenium…` và vài đơn thử. Đơn nào hủy được sẽ tự hủy sau khi chạy. Tính đến 06/11 tối, `ebike_db` còn **22 tài khoản** `selenium…` (không tính `selenium.manager`), **18 đơn thử kẹt ở SHIPPED** đang giữ 18 xe của `kinetic-sir-50cc`, và vài đơn đã hủy có payment PAID.
- Test Selenium mới nên kế thừa `StorefrontSeleniumTest`. Lớp này có sẵn đăng nhập, đặt hàng, `click()` (cuộn tức thì rồi mới click, vì trang có `scroll-behavior:smooth`), `pageState()` (chụp màn hình vào `target/selenium` khi lỗi) và gọi REST. Mỗi lớp test con phải tự gắn `@Tag("selenium")` và `@EnabledIfSystemProperty`.
- Test cần quyền Manager: thêm `-Dselenium.managerUser=selenium.manager -Dselenium.managerPassword=SeleniumManager@123`. Tài khoản `selenium.manager` (vai trò MANAGER) được tạo trong `ebike_db` local chỉ để test, không có trong file SQL.
- Chạy toàn bộ test Selenium: `mvn test -Dtest='*SeleniumTest' -Dselenium.baseUrl=... -Dselenium.vnpayHashSecret=... -Dselenium.managerUser=... -Dselenium.managerPassword=...`.
- Chrome thỉnh thoảng tự ngắt kết nối (`NoSuchSessionException`). Đó là lỗi môi trường, chạy lại là qua.
- Các test cần đơn ở trạng thái SHIPPED để lại đơn đang giao, không hủy được, nên **mỗi lần chạy tiêu hao vĩnh viễn vài xe** của sản phẩm test. Hàm `openCheckoutWithOneBike` đã chọn màu còn nhiều xe nhất để không bị kẹt. Ngày 06/11, màu Pearl White của `kinetic-sir-50cc` đã về 0 vì lý do này.

**Dọn dẹp còn treo**
- Dữ liệu thử trong `ebike_db` (xem mục trên): 18 đơn SHIPPED và 22 tài khoản `selenium…`. Chưa dọn.
- Các nhánh `backup/*` đã xóa ngày 06/11.

Đối chiếu với:
- Tài liệu Word `TÀI LIỆU WEB BÁN XE_Lập trình WWW (Java).docx`: 25 use case, NFR-01 → NFR-19
- `PHAN_CONG_CONG_VIEC.md`: 16 task

## 1. Hiện trạng tóm tắt

- **Backend gần như đủ.** Hầu hết use case đã có REST API, phân quyền và test. Ba việc trong `HUONG_DAN_SUA_CODE.md` đã làm xong: mã khuyến mãi lấy từ bảng `promotions`, hủy đơn cộng lại tồn kho, Manager ghi nhận hoàn tiền. 12/12 test đang pass.
- **Phần thiếu chủ yếu nằm ở giao diện Thymeleaf.** Nhiều API đã có nhưng trang web chưa gọi tới, hoặc chỉ làm tạm bằng `prompt()`.
- **File `Đánh giá các task.md` (30/05/2026) đã lỗi thời.** File đó chấm trên giao diện React cũ (`packages/web`). Ứng dụng hiện chạy bằng Thymeleaf (`templates/store/*.html` + `static/js/*.js`), nên mọi đánh giá dưới đây làm lại trên code hiện tại.

Ký hiệu: ✅ đủ · 🟡 một phần · ❌ chưa có

## 2. Đối chiếu 25 use case trong Word

| UC | Tên | Backend | Giao diện | Còn thiếu |
|---|---|---|---|---|
| UC-01 | Đăng ký | ✅ | ✅ | – |
| UC-02 | Đăng nhập | ✅ | ✅ | – |
| UC-03 | Xem, tìm kiếm sản phẩm | ✅ | 🟡 | Form lọc thiếu loại xe và quãng đường (backend đã hỗ trợ `vehicleType`, `minRangeKm`, `maxRangeKm`) |
| UC-04 | Chi tiết sản phẩm | ✅ | 🟡 | Chưa hiển thị đánh giá và xe liên quan (controller đã tính sẵn `related`) |
| UC-05 | Chatbot | ✅ | ✅ | – |
| UC-06 | Đặt hàng nhanh | ✅ | 🟡 | Chưa có bước OTP email (backend có, cấu hình mặc định tắt) |
| UC-07 | Thanh toán (guest) | ✅ | ✅ | Đã làm ở V1 (PR #1, chờ merge) |
| UC-08 | Hồ sơ cá nhân | ✅ | 🟡 | Chỉ sửa được họ tên, email, SĐT. Chưa có giao diện đổi mật khẩu, sổ địa chỉ, tùy chọn |
| UC-09 | Yêu thích | ✅ | ✅ | – |
| UC-10 | Chatbot khi đã đăng nhập | ✅ | 🟡 | Giao diện không gửi `chatId`, nên chatbot không nhớ được câu hỏi nối tiếp |
| UC-11 | Báo giá, đặt hàng | ✅ | ✅ | Nhập mã khuyến mãi đã chạy |
| UC-12 | Thanh toán, theo dõi kết quả | ✅ | ✅ | Đã làm ở V1 (PR #1, chờ merge) |
| UC-13 | Lịch sử đơn | ✅ | ✅ | Đã làm ở V3 (`feat/order-detail`, chưa merge): chi tiết đơn có danh sách xe, bảng giá, trạng thái thanh toán, lý do hủy |
| UC-14 | Hủy đơn | ✅ | ✅ | Đã làm ở V2 (`feat/cancel-order`, đã push, chưa merge). Hủy từ phía Manager đã sửa ở V3 |
| UC-15 | Dashboard | ✅ | ✅ | – |
| UC-16 | Quản lý đơn | ✅ | ✅ | Đã làm ở V3 (chưa merge): form cập nhật giao nhận; dropdown chỉ hiện bước hợp lệ, backend cũng chặn bước sai |
| UC-17 | Quản lý thanh toán | ✅ | 🟡 | Chưa có bộ lọc trạng thái/phương thức (API đã nhận tham số); nút "Xác nhận đã thu" hiện ở mọi dòng, kể cả giao dịch VNPay hoặc đã PAID |
| UC-18 | Quản lý khách hàng | ✅ | 🟡 | Chưa có ô tìm theo tên/email (API đã nhận `search`) |
| UC-19 | Quản lý sản phẩm (Manager) | ✅ | 🟡 | Trang Manager không có nút quản lý ảnh. Trang Admin chỉ upload được 1 ảnh; chưa đặt ảnh đại diện, sắp thứ tự, xóa ảnh |
| UC-20 | Nội dung chatbot | ✅ | ✅ | Theo Word là sửa file rồi khởi động lại, không cần giao diện |
| UC-21 | Quản lý người dùng | ✅ | 🟡 | Chưa có nút xóa tài khoản (API `DELETE /admin/accounts/{id}` đã có); tạo tài khoản bằng chuỗi `prompt()` |
| UC-22 | Vai trò, phân quyền | ✅ | ❌ | Chưa có trang xem role và permission (API `GET /admin/roles` đã có) |
| UC-23 | Quản trị dữ liệu sản phẩm | ✅ | 🟡 | Như UC-19 |
| UC-24 | Giá, khuyến mãi | ✅ | 🟡 | Tạo mã khuyến mãi bằng `prompt()`, cố định 500 lượt và 30 ngày, không nhập được trần giảm hay ngày bắt đầu/kết thúc; sửa mã chỉ đổi được trạng thái |
| UC-25 | Báo cáo, nhật ký | ✅ | 🟡 | Dashboard chỉ hiện vài log gần nhất; chưa có trang nhật ký đầy đủ (API `GET /admin/audit-logs` đã có) |

## 3. Đối chiếu 16 task trong `PHAN_CONG_CONG_VIEC.md`

| Task | Chức năng | Trạng thái | Còn thiếu |
|---|---|---|---|
| 1 | Đánh giá, bình luận | 🟡 | API đủ; trang chi tiết xe chưa hiện danh sách và form đánh giá |
| 2 | Mã giảm giá | ✅ | – |
| 3 | Timeline giao hàng | ❌ | Chi tiết đơn chỉ có khối "Thông tin nhận hàng", chưa có timeline và ngày cập nhật |
| 4 | Thông báo | 🟡 | Có danh sách và nút đọc từng cái; thiếu "Đánh dấu tất cả đã đọc" và badge số chưa đọc trên header (API `read-all`, `unread-count`, `stream` đã có) |
| 5 | Yêu thích | ✅ | – |
| 6 | So sánh sản phẩm | ✅ | Đã có trang `/compare` |
| 7 | Lọc nâng cao | 🟡 | Như UC-03 |
| 8 | Tồn kho | 🟡 | Cập nhật được tồn kho; thiếu nhãn cảnh báo sắp hết/hết hàng, chưa sửa được tồn kho theo màu |
| 9 | Báo cáo doanh thu | 🟡 | Có bảng theo kỳ, top bán chạy, bán chậm; thiếu chọn kỳ và khoảng ngày (API đã nhận `period`, `from`, `to`) và biểu đồ |
| 10 | Sổ địa chỉ | 🟡 | API CRUD đủ; chưa có giao diện trong hồ sơ, checkout chưa chọn được địa chỉ đã lưu |
| 11 | Hủy đơn có lý do | ✅ | Đã làm ở V2 và V3 (chưa merge): khách và Manager đều hủy kèm lý do; Manager xem được lý do |
| 12 | Lịch sử thanh toán | 🟡 | Có trang; thiếu bộ lọc trạng thái |
| 13 | Tài khoản cá nhân | 🟡 | Như UC-08 (thiếu đổi mật khẩu) |
| 14 | Trang hỗ trợ | 🟡 | `/support` chỉ liệt kê showroom; chưa có FAQ, chưa có form gửi yêu cầu (cần bảng và API mới) |
| 15 | Quản lý ảnh | 🟡 | Như UC-19 |
| 16 | Sản phẩm liên quan | 🟡 | Backend có; trang chi tiết chưa render |

## 4. Danh sách việc cần làm

Ưu tiên: **P0** là use case trong Word bị thiếu hoặc sai, sẽ lộ khi demo. **P1** là use case có nhưng chưa đủ ý trong Word, hoặc task phân công còn dở. **P2** là phần làm thêm.

Đường dẫn viết tắt: `js/` = `backend/src/main/resources/static/js/`, `tpl/` = `backend/src/main/resources/templates/store/`, `…/` = `backend/src/main/java/com/ebike/`.

### P0. Phải làm trước

**V1. Thanh toán VNPay trên checkout (UC-07, UC-12)** — ✅ **ĐÃ XONG** (6 commit, PR #1). Phần dưới giữ lại để tham khảo.
- Thêm lựa chọn `VNPAY` vào ô "Phương thức thanh toán" trong `js/commerce.js`.
- Tạo đơn xong, nếu chọn VNPay thì gọi `POST /api/v1/payments/vnpay/create` (xem `…/dto/order/request/VnPayCreatePaymentRequest.java`) rồi chuyển trình duyệt sang `paymentUrl`.
- Trang `/payment/return` (`js/flow.js`): lấy nguyên query string, gọi `GET /api/v1/payments/vnpay/return?...`, hiển thị thành công hoặc thất bại kèm mã đơn.
- Đổi giá trị mặc định `vnpay.return-url` trong `application.properties` từ `http://localhost:5173/payment/return` thành `http://localhost:8080/payment/return`.
- Ở chi tiết đơn của khách: đơn VNPay chưa thanh toán thì có nút "Thanh toán lại".
- **Kiểm tra:** dùng thẻ test của VNPay sandbox, thanh toán xong thì đơn có payment PAID; bấm hủy trên trang VNPay thì hiện thất bại. Cần biến môi trường `VNPAY_TMN_CODE` và secret, không ghi vào code.

**V2. Hủy đơn đúng theo Word (UC-14)** — ✅ **ĐÃ XONG** (`feat/cancel-order`). Phần dưới giữ lại để tham khảo.
- `js/workspace.js` (`actionFor`): hiện nút cho cả `PROCESSING`; đổi nhãn thành "Hủy đơn"; báo "Đã hủy đơn hàng".
- Hiển thị `cancellationReason` trong chi tiết đơn, cho cả khách lẫn Manager.
- **Kiểm tra:** đơn PROCESSING hủy được; đơn SHIPPED không có nút; sau khi hủy, tồn kho tăng lại.

**V3. Trang chi tiết đơn đầy đủ (UC-13, UC-16)** — ✅ **ĐÃ XONG** (`feat/order-detail`). Phần dưới giữ lại để tham khảo.
- Hiện tại `renderTable` loại bỏ `items` và `shipment`, nên trang chi tiết thiếu thông tin. Viết riêng `renderOrderDetail` gồm:
  - danh sách xe (tên, màu, số lượng, đơn giá);
  - bảng giá (tạm tính, phí đăng ký, giảm giá, tổng);
  - trạng thái thanh toán, lý do hủy.
- Manager: thêm form cập nhật giao nhận gọi `PATCH /api/v1/manager/orders/{id}/shipment`. Dropdown trạng thái chỉ liệt kê các bước chuyển hợp lệ từ trạng thái hiện tại.

**V4. Hồ sơ cá nhân đủ chức năng (UC-08, Task 10, 13)** — khoảng 1,5 ngày
- Form đổi mật khẩu gọi `POST /api/v1/auth/change-password` (mật khẩu cũ, mật khẩu mới ≥ 6 ký tự, nhập lại).
- Sổ địa chỉ: liệt kê, thêm, sửa, xóa, đặt mặc định qua `/api/v1/users/{userId}/addresses`.
- Tùy chọn người dùng qua `/api/v1/users/{userId}/preferences`.
- Checkout: khách đã đăng nhập thì chọn được địa chỉ đã lưu, tự điền vào form giao tận nhà.

**V5. Quản lý thanh toán và khách hàng cho Manager (UC-17, UC-18)** — khoảng ½ ngày
- `/manager/payments`: thêm bộ lọc `paymentStatus` và `paymentMethod`, gửi kèm query lên API. Nút "Xác nhận đã thu" chỉ hiện khi `paymentMethod = PAY_LATER` và `paymentStatus = PENDING`.
- `/manager/customers`: thêm ô tìm kiếm, gửi `?search=`.

**V6. Admin: người dùng, vai trò, khuyến mãi, nhật ký (UC-21, 22, 24, 25)** — khoảng 2 ngày
- Thêm nút "Xóa tài khoản" (hỏi xác nhận, không cho tự xóa mình). Thay chuỗi `prompt()` bằng form tạo tài khoản.
- Trang mới `/admin/roles`: thêm `case` trong `…/controller/web/WorkspaceController.java` và link ở menu admin; hiển thị role kèm danh sách permission từ `GET /api/v1/admin/roles`.
- Form khuyến mãi đầy đủ: mã, tên, loại, giá trị, trần giảm, giới hạn lượt (ghi rõ `0` = không giới hạn), ngày bắt đầu/kết thúc, trạng thái. Dùng chung form cho tạo mới và sửa.
- Trang mới `/admin/audit-logs` dùng `GET /api/v1/admin/audit-logs`.

**V7. Quản lý ảnh sản phẩm (UC-19, UC-23, Task 15)** — khoảng 1,5 ngày
- Thêm nút "Quản lý ảnh" cho cả trang Manager (hiện chỉ có ở Admin; kiểm tra lại quyền của `AdminProductImageController` đã cho MANAGER chưa).
- Mở một khung quản lý ảnh: lưới ảnh hiện có (`GET /api/v1/admin/product-images/by-product/{id}`), upload nhiều ảnh (`<input multiple>`, gửi lần lượt, hiện tiến độ), đặt ảnh đại diện, đổi thứ tự, xóa ảnh có hỏi xác nhận.
- Xong thao tác nhớ xóa cache sản phẩm (kiểm tra service đã `@CacheEvict` chưa).

### P1. Làm tiếp

**V8. Trang chi tiết xe: đánh giá và xe liên quan (UC-04, Task 1, 16)** — khoảng 1 ngày
- `tpl/detail.html`: render `related` (đã có trong model) thành lưới 4 xe; ẩn khối khi danh sách rỗng.
- Khối đánh giá: điểm trung bình, danh sách (`GET /api/v1/products/{slug}/reviews`), form 1–5 sao cho khách đã đăng nhập, sửa và xóa đánh giá của chính mình.

**V9. Chatbot nhớ hội thoại (UC-10)** — khoảng ½ ngày
- `js/flow.js`: lưu `chatId` từ phản hồi đầu tiên (vào `sessionStorage`) và gửi lại ở mỗi câu hỏi sau. Hiện các xe được gợi ý (`recommendations`) thành thẻ có link.
- **Kiểm tra:** hỏi "gợi ý xe đi học", rồi hỏi "xe đầu tiên giá bao nhiêu?" thì chatbot trả lời đúng xe.

**V10. Lọc sản phẩm đủ tiêu chí (UC-03, Task 7)** — khoảng ½ ngày
- `tpl/products.html`: thêm `vehicleType`, `minRangeKm`, `maxRangeKm`; kiểm tra các tùy chọn `sort` có đủ giá, tên, mới nhất. Form dùng GET nên bộ lọc đã giữ trên URL.

**V11. Thông báo (Task 4)** — khoảng ½ ngày
- Nút "Đánh dấu tất cả đã đọc" (`PATCH /api/v1/notifications/read-all`).
- Badge số chưa đọc trên header khu vực khách hàng (`GET /api/v1/notifications/unread-count`; muốn realtime thì dùng `EventSource('/api/v1/notifications/stream')`).

**V12. Báo cáo doanh thu (Task 9)** — khoảng 1 ngày
- `/manager/reports`: chọn kỳ (ngày/tuần/tháng/quý/năm) và khoảng từ ngày–đến ngày, gửi `period`, `from`, `to`.
- Vẽ biểu đồ cột doanh thu theo kỳ bằng SVG hoặc canvas tự viết, không cần thêm thư viện.

**V13. Timeline giao hàng (Task 3)** — khoảng ½ ngày
- Trong chi tiết đơn (V3): các mốc Chờ xử lý → Đã xác nhận → Đang xử lý → Đang giao → Đã giao, đánh dấu mốc hiện tại, hiện `shippedAt`/`deliveredAt`/`updatedAt`. Đơn hủy thì hiện nhánh "Đã hủy".

**V14. Lịch sử thanh toán và tồn kho (Task 8, 12)** — khoảng ½ ngày
- `/customer/payments`: bộ lọc trạng thái.
- Bảng kho: nhãn "Hết hàng" (= 0) và "Sắp hết" (≤ 3), cho lọc theo nhãn.

### P2. Làm thêm nếu còn thời gian

**V15. Trang hỗ trợ khách hàng (Task 14)** — khoảng 1,5 ngày
- Hiện FAQ từ `faq.csv` (thêm endpoint đọc FAQ, hoặc render sẵn bằng Thymeleaf), giữ danh sách showroom.
- Form gửi yêu cầu hỗ trợ cần **bảng mới** `support_requests`, entity, API, và trang Manager xem danh sách. Lưu ý `ddl-auto=validate`: phải thêm bảng vào `backend/database/setup_ebike_database.sql` và chạy tay vào DB. Phần này ngoài phạm vi Word, nên làm thì ghi bổ sung vào tài liệu.

**V16. OTP email khi đặt hàng (UC-06, NFR-07)** — khoảng 1 ngày
- Backend có sẵn (`OrderEmailVerificationController`), mặc định tắt (`ORDER_EMAIL_OTP_REQUIRED=false`). Làm giao diện: gửi mã, nhập mã, đếm ngược 60 giây gửi lại, gửi kèm `emailVerificationSessionId` khi tạo đơn.

**V17. Tồn kho theo màu (Task 8)**
- Cho Manager sửa tồn kho từng biến thể màu. Cần thêm API vì hiện mới có `PATCH /manager/products/{id}/stock`.

### Dọn dẹp và tài liệu (làm song song)

**Xóa khi hoàn thành dự án (rà soát 07/11, người dùng chốt để cuối cùng mới xóa).** Đã kiểm tra không còn chỗ nào dùng; xóa xong chạy lại toàn bộ test, mọi file vẫn lấy lại được từ lịch sử git.
- [ ] Nhóm A — xóa không ảnh hưởng:
  - `EMAIL_OTP_FLOW.md` (tài liệu của dự án khác, "Zalo Lite"; các lớp `AuthService`, `EmailService` không có trong dự án này).
  - `archive/legacy-deployment/`, `archive/legacy-project/` (Docker/EC2/GitHub Actions và client React Native/Electron/PostgreSQL cũ).
  - `backend/tests/ui/` (script Playwright 02/11 đã lỗi thời: `checkout-smoke.cjs` còn đòi phí hồ sơ 2.500.000đ; Selenium đã thay).
  - `backend/src/main/java/com/ebike/dto/auth/response/ErrorResponse.java` (không dùng; lỗi trả về qua `ApiErrorResponse`).
  - 6 repository không ai gọi (bảng được đọc/ghi qua entity cha): `PermissionRepository`, `UserPermissionRepository`, `OrderItemRepository`, `ShipmentRepository`, `CategoryRepository`, `ProductSpecificationRepository`. Kế hoạch còn lại (V18, V19, các mục lệch) không cần tới; V18 sẽ có repository riêng cho bảng số khung/số máy.
  - Thư mục rỗng `backend/src/main/resources/templates/email/`.
- [ ] Nhóm B — cân nhắc trước khi xóa:
  - `archive/catalog-before-correction/` (220 MB): nguồn duy nhất của `materialize_catalog.py`; xóa thì không dựng lại được danh mục từ dữ liệu crawl (app vẫn chạy).
  - `packages/web/` (giao diện React cũ): xóa thì sửa các dòng nhắc tới nó trong `README.md`, `backend/ARCHITECTURE.md`.
  - `Đánh giá các task.md` (đã ghi là tài liệu cũ), `backend/TEST_REPORT_CART.md`, `TEST_REPORT_CATALOG.md`, `TEST_REPORT_HOME_LAYOUT.md` (biên bản 02/11).
  - `HUONG_DAN_SUA_CODE.md` (việc 1–3 đã xong; xác nhận việc 4 — rà Word — trước khi bỏ).

- [x] (07/11, `ee6241d`) Xóa mã chết: `/manager/orders/{id}/cancellation/approve` và `/reject`, trạng thái `CANCELLATION_REQUESTED`, phần liên quan trong `NotificationCreationService` (luồng hủy hiện không còn tạo trạng thái này).
- [ ] ~~Thêm test~~ (từ 07/11 người dùng bảo chỉ viết code, không viết test; còn thiếu test cho V4–V16 và các mục dưới đây): hoàn tiền (`ManagerServiceImpl.refundPayment`), hủy đơn cộng lại tồn kho, mã khuyến mãi hết lượt hoặc WAITING bị từ chối.
- [x] (07/11, `d1ea94a`) Đánh dấu `Đánh giá các task.md` là bản cũ (dựa trên React), hoặc cập nhật theo bảng ở mục 3.
- [x] (07/11, `d1ea94a`) README: bỏ dòng ghi mật khẩu MariaDB mặc định nếu nhóm không muốn công khai; thêm danh sách biến môi trường cần cho VNPay, Gemini, SMTP.
- [ ] Sau mỗi việc, điền cột "Kết quả thực tế" cho test case tương ứng ở mục 5.3 trong Word.

### Kết quả rà V4–V17 so với code và thực tế (07/11/2026)

Đã đối chiếu từng mục với code hiện tại. Phần lớn đúng như kế hoạch; các điểm dưới đây **thay đổi phạm vi** hoặc **cần chốt** trước khi làm.

- **V17 nên lên P1 và làm ngay sau V4.** Tồn kho sản phẩm (`products.stock_quantity`) và tồn kho từng màu (`product_variants.stock_quantity`) là hai con số độc lập, và đặt hàng đòi **cả hai** đủ. Nút "Sửa tồn kho" của Manager (`PATCH /manager/products/{id}/stock`) chỉ đổi số của sản phẩm, nên Manager nhập hàng về cũng không mở lại được màu đã hết (đã gặp: Pearl White của `kinetic-sir-50cc` về 0). Quy tắc đề xuất: sản phẩm có màu thì tồn kho sản phẩm = tổng tồn kho các màu; Manager sửa theo từng màu, backend tính lại tổng khi lưu. V14 (nhãn "Sắp hết/Hết hàng") làm sau V17 để nhãn theo từng màu.
- **V6, nút xóa tài khoản:** `deleteAccount` xóa cứng `users`, không chặn gì. Bảng `orders.user_id` không có khóa ngoại thật (MariaDB bỏ qua `REFERENCES` viết ngay trong định nghĩa cột), nên xóa khách đã có đơn để lại đơn mồ côi; Admin cũng tự xóa được chính mình hoặc Admin cuối cùng. Thực tế các hệ thống giữ tài khoản có giao dịch để đối soát. Đề xuất: nút chính là **"Vô hiệu hóa"** (đã có `account-status`); chỉ cho **xóa** khi tài khoản chưa có đơn; backend (không chỉ giao diện) chặn tự xóa và xóa Admin cuối cùng.
- **V6, form khuyến mãi:** ghi chú thêm "mỗi khách dùng một lần" (B7 đã áp dụng cho mọi mã).
- **V8, đánh giá:** hiện **bất kỳ ai đăng nhập** cũng đánh giá được, chưa kiểm tra đã mua. Thực tế (Shopee, Tiki, Thế Giới Di Động) chỉ khách đã nhận hàng mới được đánh giá và đánh giá có nhãn "Đã mua hàng". Đề xuất thêm điều kiện: có đơn `DELIVERED` chứa sản phẩm đó; hiện nhãn trên đánh giá. Thông báo lỗi của API đang bằng tiếng Anh ("You have already reviewed this product"), giao diện phải đổi sang tiếng Việt.
- **V12, báo cáo doanh thu:** số theo kỳ tính doanh thu theo **ngày tạo đơn**, còn các tổng khác (`sumPaidOrdersWithin`) tính theo **ngày thanh toán** (`paidAt`), nên cùng một kỳ có thể ra hai con số. Đơn trả sau được thu khi nhận xe sẽ nằm ở kỳ ngày đặt. Đề xuất thống nhất theo `paidAt`, và hoàn tiền (`REFUNDED`) hiện không được trừ ở kỳ nào: ghi chú trên báo cáo hoặc hiện riêng một cột "Đã hoàn".
- **V5:** nút "Xác nhận đã thu" đã ẩn đúng ở B3, bỏ khỏi phạm vi V5. Còn lại: bộ lọc trạng thái/phương thức, ô tìm theo mã đơn/email (API `search` đã có), ô tìm khách hàng.
- **V4:** đổi mật khẩu đã kiểm tra mật khẩu cũ nhưng chỉ đòi ≥ 6 ký tự; thực tế nên ≥ 8. Sổ địa chỉ ở checkout: tỉnh/thành đã lưu phải khớp danh sách `vietnam-provinces.json` (B6 dựa vào chuỗi tỉnh để tính phí giao), nên form địa chỉ trong hồ sơ dùng chung danh sách đó.
- **V7:** không cần đổi phân quyền; Manager đã có `product:create/update/delete` (đủ cho `/api/v1/admin/product-images`), và đã xóa cache sản phẩm khi sửa ảnh.
- **V16 (OTP khi đặt hàng):** B1 đã có luồng OTP ở giao diện (liên kết đơn vãng lai), dùng lại được, giảm công sức. Vẫn cần SMTP để chạy thật.
- **Không đổi:** V9, V10, V11, V13, V15. V9 và chatbot khi chưa có Gemini: xem mục "Để sau" ở trên.

#### Chốt với người dùng sau khi rà theo thực tế (07/11/2026)

- **V13 → đơn nhận tại cửa hàng có trạng thái riêng "Chờ lấy".** Thêm `READY_FOR_PICKUP` vào `OrderStatus`. Đơn nhận tại cửa hàng đi: Chờ xử lý → Đã xác nhận → Đang xử lý → **Chờ lấy** → **Đã nhận xe** (`DELIVERED`); không qua `SHIPPED`. Đơn giao tận nhà giữ nguyên chuỗi cũ. Khi khách tới lấy xe, Manager bấm **"Đã nhận"** để chuyển sang `DELIVERED` (ghi `deliveredAt`). Backend kiểm tra bước chuyển theo hình thức nhận hàng (pickup không được sang `SHIPPED`, giao tận nhà không được sang `READY_FOR_PICKUP`). Cột `orders.status` là `varchar(30)`, không có CHECK, nên **không đổi cấu trúc bảng**. Timeline, dropdown Manager, chi tiết đơn, thông báo và báo cáo phải biết trạng thái mới. Gửi thông báo cho khách khi đơn sang "Chờ lấy". Làm sớm, ngay sau V5 (gắn với B5b: nhận tại cửa hàng là lúc hỗ trợ đăng ký).
- **V4 → tùy chọn người dùng gồm: nhận thông báo, giao diện (sáng/tối), ngôn ngữ.** "Nhận thông báo" đã làm ở `3168ac6`. **Theme làm trong V4:** chuyển các mã màu viết cứng trong CSS (khoảng 180 chỗ) sang biến CSS, viết bộ màu tối; khách đã đăng nhập lưu ở `user_preferences.theme`, khách chưa đăng nhập lưu ở trình duyệt; mặc định theo cài đặt hệ điều hành. **Ngôn ngữ tách thành V19** (lớn, xem dưới); trong lúc chưa làm V19 thì không hiện ô chọn ngôn ngữ. Đổi mặc định `language` trong DB từ `en` thành `vi`.
- **V16 → OTP qua số điện thoại (SMS), chỉ cho đơn trả sau, và phải xác thực lại định kỳ (giống MoMo).** Đơn VNPay không cần OTP (thanh toán thành công đã chứng minh người đặt). Đơn trả sau: SĐT phải được xác thực trong vòng **30 ngày** gần nhất (cấu hình được, ví dụ `app.order.phone-verification-ttl=P30D`); quá hạn thì lần đặt trả sau tiếp theo phải nhập OTP lại. Áp dụng cho cả khách vãng lai (theo SĐT) lẫn khách đã đăng nhập. **Đổi cấu trúc bảng:** cần lưu SĐT nào đã xác thực lúc nào (bảng mới, ví dụ `phone_verifications(phone_number, verified_at)`), thêm vào file setup và viết file cập nhật cho DB đang chạy. Viết qua interface `SmsSender`: môi trường dev ghi mã ra log, production gắn nhà cung cấp thật (ở Việt Nam thường phải đăng ký brandname) qua biến môi trường. Gửi lại sau 60 giây, giới hạn số lần nhập sai. Khi có SMS thì liên kết đơn vãng lai (B1) cũng nên chuyển sang SMS.
- **V6 → tách hai trường hợp, không xóa cứng.** (1) **Khóa** (`account_status = 'INACTIVE'`, đã có): Admin chặn tài khoản gian lận/spam; email, username, SĐT vẫn bị giữ nên không đăng ký lại được; mở khóa được. (2) **Xóa theo yêu cầu của khách** (`account_status = 'DELETED'`, cột đã là `varchar(30)`, không đổi cấu trúc bảng): không đăng nhập được, ẩn khỏi danh sách mặc định, không khôi phục; **ẩn danh hóa hồ sơ** (họ tên, SĐT trên tài khoản; xóa sổ địa chỉ, yêu thích, tùy chọn) theo quyền xóa dữ liệu cá nhân; **đổi email/username thành `deleted_<id>_…`** để người đó đăng ký lại được; **giữ nguyên đơn, thông tin giao nhận và thanh toán** (tên, SĐT, địa chỉ in trên đơn) vì chứng từ bán hàng phải lưu theo Luật Kế toán. Backend chặn tự xóa/khóa mình và xóa/khóa Admin cuối cùng.
- **V18 (mới, đồng ý) → lưu số khung, số máy từng xe đã bán.** Khi bàn giao (giao xe hoặc khách nhận tại cửa hàng), Manager nhập số khung và số máy cho từng chiếc trong đơn; khách xem được trong chi tiết đơn. Cần cho hóa đơn, cho chủ xe tự đăng ký trên VNeID (B5b) và cho bảo hành. **Đổi cấu trúc bảng**: một đơn có thể có nhiều xe cùng mẫu (`order_items.quantity` > 1), nên cần bảng mới (ví dụ `order_item_units(order_item_id, frame_number, engine_number)`, số khung `UNIQUE`), thêm vào file setup và viết file `CREATE TABLE` cho DB đang chạy. Không cho bấm "Đã nhận"/"Đã giao" khi chưa nhập đủ số khung, số máy.
- **V19 (mới) → giao diện, FAQ và chatbot tiếng Anh.** Hiện không có `messages*.properties`, template không dùng `#{...}`, khoảng 6.300 ký tự tiếng Việt viết cứng trong template và JS. Làm: `messages_vi`/`messages_en`, tách chữ trong template và JS ra file dịch, `LocaleResolver` theo cookie (khách chưa đăng nhập) và theo `user_preferences.language` (đã đăng nhập), hiện ô chọn ngôn ngữ trong hồ sơ và header. **FAQ:** thêm `faq_en.csv` (12 câu, cùng placeholder phí). **Chatbot:** gửi kèm ngôn ngữ người dùng; khi có Gemini thì đổi câu "Hãy luôn trả lời bằng tiếng Việt" theo ngôn ngữ (Gemini tự dịch ngữ cảnh tiếng Việt); khi không có Gemini thì thêm từ khóa tiếng Anh cho 15 nhóm nhận diện câu hỏi và bản tiếng Anh cho các câu trả lời mẫu. **Giữ tiếng Việt:** tài liệu PDF và mô tả sản phẩm (dịch mô tả sản phẩm cần cột mới, tách thành mục riêng nếu làm). Khoảng 3–4 ngày, làm sau V18.
- **Giữ như rà soát trước:** V5, V7, V8 (chỉ khách đã nhận xe mới đánh giá), V9, V10, V11, V12 (doanh thu theo `paidAt`, tách tiền đã hoàn), V14, V17. Góp ý thêm (tùy chọn): V15 có ô loại yêu cầu Bảo hành / Bảo dưỡng / Khác và gắn được với đơn; V8 cho Manager ẩn đánh giá không phù hợp.

#### Đối chiếu việc đã làm với quyết định ở trên (07/11/2026 tối)

Phiên viết code V4–V17 trên `feat/profile` **chưa đọc** mục "Chốt với người dùng" ở trên khi làm, nên bốn việc lệch với quyết định. Chưa sửa; cần làm lại theo thứ tự ở dưới. Bộ nhớ làm việc đã ghi nhận cùng nội dung.

| Mục | Quyết định đã chốt | Đã làm trong code | Việc cần làm |
|---|---|---|---|
| V13 | Đơn nhận tại cửa hàng: Chờ xử lý → Đã xác nhận → Đang xử lý → **Chờ lấy** (`READY_FOR_PICKUP`) → **Đã nhận** (`DELIVERED`), không qua `SHIPPED`; Manager bấm "Đã nhận"; gửi thông báo khi sang "Chờ lấy" | Timeline 5 mốc cố định (Chờ xử lý → Đã giao), không biết hình thức nhận hàng | Thêm `READY_FOR_PICKUP` vào `OrderStatus`; `STATUS_TRANSITIONS` theo hình thức nhận (pickup không sang `SHIPPED`, giao tận nhà không sang `READY_FOR_PICKUP`); dropdown Manager, timeline (`renderTimeline`), nhãn `orderStatusText`, thông báo, báo cáo |
| V4 | Giao diện sáng/tối làm trong V4 (biến CSS, bộ màu tối, lưu `user_preferences.theme` cho khách đăng nhập, trình duyệt cho khách vãng lai, mặc định theo hệ điều hành); đổi mặc định `language` trong DB từ `en` sang `vi`; chưa hiện ô ngôn ngữ cho tới V19 | Chỉ có công tắc nhận thông báo; cố ý **không** làm theme và ngôn ngữ (lý do "không nơi nào dùng" đã lỗi thời) | Chuyển ~180 mã màu viết cứng trong CSS sang biến, viết bộ màu tối, ô chọn giao diện trong hồ sơ, đổi mặc định `language` |
| V6 | Không xóa cứng. (1) Khóa = `INACTIVE` (đã có). (2) Xóa theo yêu cầu khách = `DELETED`: không đăng nhập được, ẩn khỏi danh sách mặc định, ẩn danh hóa hồ sơ (họ tên, SĐT; xóa sổ địa chỉ, yêu thích, tùy chọn), đổi email/username thành `deleted_<id>_…`, **giữ nguyên đơn, giao nhận, thanh toán**. Chặn tự xóa/khóa mình và Admin cuối cùng | Nút "Xóa" vẫn xóa cứng khi tài khoản chưa có đơn (`userRepository.delete`); đã có chặn tự xóa/khóa mình, Admin cuối cùng và tài khoản đã có đơn | Thay `deleteAccount` bằng chuyển sang `DELETED` + ẩn danh hóa; lọc `DELETED` khỏi danh sách và đăng nhập; giữ các chặn đã có. Hiện còn nguy cơ xóa cứng làm mồ côi đánh giá, yêu thích, thông báo |
| V16 | OTP qua SMS, chỉ đơn trả sau, SĐT phải xác thực trong 30 ngày (`app.order.phone-verification-ttl`), bảng mới `phone_verifications`, interface `SmsSender` (dev ghi ra log), gửi lại sau 60 giây, giới hạn số lần sai; đơn VNPay không cần OTP | OTP qua email ở checkout khi `ORDER_EMAIL_OTP_REQUIRED=true` (mặc định tắt), áp dụng mọi phương thức thanh toán | Làm lại theo thiết kế SMS. Bản email có thể giữ (đang tắt) hoặc bỏ khi chuyển liên kết đơn B1 sang SMS |
| V18, V19 | Xem mục "Chốt" ở trên | Chưa làm | Làm sau V13, V4 theme, V6 |

**Những việc khớp quyết định:** V5, V7, V8 (chỉ khách đã nhận xe được đánh giá), V9, V10, V11, V12 (theo `paidAt`; **còn thiếu** phần "tách tiền đã hoàn" vì payment chưa lưu ngày hoàn), V14, V17.

**Thứ tự làm lại đề xuất:** V13 (trạng thái "Chờ lấy") → V4 (theme) → V6 (`DELETED` + ẩn danh hóa) → V18 → V19 → V16 (SMS, cần nhà cung cấp).

**Thứ tự đề xuất sau rà soát:** V4 (thêm theme) → V17 → V5 → V13 (trạng thái "Chờ lấy") → V18 → V6 → V7 → V8 → V9 → V10 → V11 → V12 → V14 → V19 (tiếng Anh), rồi V15, V16 (OTP SMS, cần nhà cung cấp).

### B. Sửa nghiệp vụ lệch thực tế (rà soát 06/11/2026)

Các lỗi dưới đây có trong code hiện tại, không phụ thuộc giao diện. B1–B3 là lỗi thật (lộ dữ liệu, mất hàng, sai doanh thu), nên **làm trước V4**.

**Nhánh:** `fix/business-rules`, tách từ `feat/order-detail`, vì B2 và B3 dùng lại hàm `cancelOrder` mà V3 vừa tách ra trong `OrderServiceImpl`. Mỗi mục là 1–3 commit ngắn. Mở PR sau PR của V3.

Thứ tự làm: B3 → B1 → B2 → B4 → B6 → B5 → B7 → B8 (việc nhỏ và lỗi nặng trước; B5, B6 dựa trên B4).

#### B1. Không cho chiếm đơn của người khác qua email (bảo mật, P0) — khoảng 1 ngày

**Lỗi:** `AuthenticationServiceImpl.register` gọi `linkGuestOrdersToUser` ngay khi đăng ký, trong khi email chưa xác minh (`setVerified(false)`). Ai biết email của khách đều xem được tên, SĐT, địa chỉ và hủy được đơn của họ.

**Sửa:**
1. Bỏ lời gọi `linkGuestOrdersToUser` trong `register`.
2. Thêm luồng "Liên kết đơn đã đặt" trong khu vực khách hàng:
   - `POST /api/v1/users/me/guest-orders/link` nhận `emailVerificationSessionId`;
   - dùng lại `OrderEmailVerificationService` (gửi OTP → nhập mã → `assertVerifiedForOrder`);
   - email trong phiên OTP phải trùng email tài khoản. Chỉ khi đó mới gắn các đơn `user_id IS NULL` có cùng email.
3. Máy chưa có SMTP thì nút liên kết báo "Chưa cấu hình gửi email" (OTP hiện trả 503). Không làm hỏng đăng ký.

**Đã chốt (06/11, theo hướng sát thực tế):** xác minh quyền sở hữu bằng **OTP gửi tới email** trước khi liên kết. Các trang bán hàng ở Việt Nam thường xác minh bằng OTP qua SMS tới số điện thoại; dự án dùng email vì cổng SMS nằm ngoài phạm vi. Khi chạy thật cần cấu hình SMTP.

**Test:**
- Unit: đăng ký không còn gắn đơn; liên kết thất bại khi OTP sai hoặc email khác; thành công thì chỉ gắn đơn đúng email.
- Selenium: khách vãng lai đặt đơn bằng email X, rồi người khác đăng ký bằng email X thì **không** thấy đơn đó.

**Tài liệu:** sửa mô tả UC-01 trong Word ("liên kết sau khi xác minh email").

#### B2. Tự hủy đơn quá hạn thanh toán, trả lại kho (P0) — khoảng 1 ngày

**Lỗi:** tồn kho bị trừ ngay khi đặt hàng, nhưng dự án không có tác vụ định kỳ nào (`@Scheduled`). Đơn VNPay bỏ dở (link hết hạn sau 15 phút) hoặc đơn trả sau mà khách không đến nhận thì giữ xe mãi mãi.

**Sửa:**
1. `@EnableScheduling` và lớp mới `UnpaidOrderExpiryJob` (ví dụ `service/order/impl/`), chạy mỗi 5 phút.
2. Hủy các đơn `PENDING` quá hạn bằng `cancelOrder(order, "Quá hạn thanh toán", null, "Expired")`, để kho được cộng lại và payment đang chờ được đóng:
   - đơn VNPay chưa có payment PAID, sau `app.order.vnpay-expiry` (mặc định **30 phút**);
   - đơn trả sau chưa được xác nhận, sau `app.order.pay-later-expiry` (mặc định **72 giờ**).
3. **Sửa kèm lỗi phát hiện thêm:** `VnPayServiceImpl.applyGatewayResult` đặt `order.setStatus(CONFIRMED)` vô điều kiện. Nếu VNPay báo thanh toán sau khi đơn đã bị tự hủy, đơn sẽ "sống lại" dù kho đã trả. Sửa thành: đơn đã CANCELLED thì giữ nguyên CANCELLED, payment ghi PAID để Manager hoàn tiền (UC-17).
4. Ghi log số đơn đã hủy mỗi lần chạy.

**Đã chốt (06/11, theo hướng sát thực tế):**
- Đơn thanh toán online (VNPay) chưa trả sau **30 phút** thì tự hủy. Link VNPay hết hạn sau 15 phút; các sàn thương mại điện tử thường giữ đơn chờ thanh toán khoảng 15–30 phút.
- Đơn trả sau chưa được nhân viên xác nhận sau **72 giờ** thì tự hủy. Thực tế, cửa hàng gọi điện xác nhận trong vòng 1 ngày và giữ xe vài ngày cho khách đến nhận.
- Đơn đã được xác nhận thì không tự hủy nữa.
- Cả hai thời hạn đặt trong cấu hình.

**Test:**
- Unit (đặt "giờ hiện tại" bằng `Clock`): đơn quá hạn bị hủy và cộng kho; đơn chưa quá hạn, đơn đã PAID, đơn đã CONFIRMED thì giữ nguyên; VNPay trả về sau khi hủy thì đơn vẫn CANCELLED.
- Không cần Selenium.

#### B3. Chặn "xác nhận đã thu" cho đơn đã hủy (P0) — khoảng 1 giờ

**Lỗi:** `ManagerServiceImpl.confirmPayLaterPayment` chỉ chặn payment PAID hoặc REFUNDED. Payment CANCELLED của đơn đã hủy vẫn bị đánh dấu PAID và được cộng vào doanh thu.

**Sửa:** chỉ cho xác nhận khi `paymentStatus = PENDING` và đơn chưa CANCELLED, ngược lại trả 400 kèm thông báo tiếng Việt. Ẩn nút "Xác nhận đã thu" trên giao diện trong các trường hợp đó (trùng V5, làm luôn ở đây).

**Test:** unit cho 3 trường hợp (PENDING thì được; CANCELLED thì bị chặn; đơn hủy thì bị chặn) và 1 test Selenium kiểm tra nút ẩn.

#### B4. Phí lấy từ bảng giá của Admin, thay dữ liệu mẫu (P1) — khoảng 1 ngày

**Lỗi:** báo giá dùng hằng số `REGISTRATION_FEE_AMOUNT = 2.500.000`, không đọc `pricing_rules`, nên UC-24 không có tác dụng. Dữ liệu mẫu `pricing_rules` là của app thuê xe theo phút (`UNLOCK_FEE`, `MINUTE_RATE`, `HOUR_RATE`, `WRONG_ZONE_FINE`, `OVERDUE_FINE`).

**Sửa:**
1. Thay dữ liệu mẫu trong `backend/database/setup_ebike_database.sql` bằng các mã phí bán xe: `REGISTRATION_SERVICE` (phí dịch vụ làm hồ sơ), `HOME_DELIVERY_SAME_CITY`, `HOME_DELIVERY_OTHER_PROVINCE` (dùng ở B6). Tùy chọn: `REGISTRATION_TAX_RATE_CENTRAL`, `REGISTRATION_TAX_RATE_OTHER`, chỉ để hiện **ước tính tham khảo** lệ phí Nhà nước ở B5, không cộng vào đơn.
2. Viết thêm file SQL cập nhật dữ liệu cho DB đang chạy. Chỉ thay dữ liệu, **không đổi cấu trúc bảng**.
3. `PricingService.activeAmount(code)` đọc quy tắc `ACTIVE` có `effective_from` gần nhất và đã đến hạn; `OrderServiceImpl` gọi hàm này thay cho hằng số.
4. Admin sửa phí thì báo giá đổi theo ngay.

**Test:** unit (đọc đúng quy tắc đang hiệu lực, bỏ qua `SCHEDULED`); Selenium: Admin đổi phí, rồi trang checkout hiện phí mới.

**Lưu ý:** `generate_mariadb_setup.py` sinh lại file setup từ `schema-source/`, nhưng máy này chưa cài Python. Sửa tay file setup, đồng thời sửa nguồn tương ứng để lần sinh sau không mất thay đổi.

#### B5. Đăng ký xe là thủ tục của chủ xe; cửa hàng chỉ bán dịch vụ hỗ trợ (P1, sau B4) — khoảng ½ ngày

**Thực tế (đã chốt 06/11):**
- Đăng ký xe và cấp biển số là thủ tục hành chính với cơ quan Công an, do **chủ xe** chịu trách nhiệm. Chủ xe có thể tự làm (kể cả trực tuyến), hoặc **nhờ người khác làm thay** khi cung cấp giấy tờ tùy thân.
- Đại lý thường nhận làm hộ như một dịch vụ.
- Lệ phí trước bạ (2% giá xe; lần đầu ở thành phố trực thuộc trung ương, thành phố thuộc tỉnh, thị xã tỉnh lỵ là 5%) và lệ phí cấp biển số (theo khu vực I, II, III) là **tiền nộp cho Nhà nước, không phải doanh thu của cửa hàng**.

**Lỗi hiện tại:** "phí đăng ký" 2.500.000 được cộng vào tổng đơn như một khoản bán hàng, không tách tiền dịch vụ của cửa hàng với lệ phí Nhà nước.

**Sửa:**
1. Đổi tên lựa chọn ở checkout thành **"Nhờ cửa hàng làm hộ thủ tục đăng ký xe"**. Đơn chỉ thu **phí dịch vụ** `REGISTRATION_SERVICE` do Admin chỉnh (B4). Khoản này là doanh thu của cửa hàng.
2. **Không cộng lệ phí trước bạ và lệ phí biển số vào tổng đơn.** Hiện dòng ghi chú: "Lệ phí trước bạ (2–5% giá xe) và lệ phí cấp biển số do chủ xe nộp cho Nhà nước khi đăng ký; cửa hàng có thể nộp hộ khi làm thủ tục." Có thể kèm số tiền **ước tính để tham khảo** (tỷ lệ lấy từ `pricing_rules`), nhưng không tính vào đơn.
3. Khách không chọn dịch vụ thì hiện hướng dẫn ngắn: cửa hàng giao hóa đơn và phiếu kiểm tra chất lượng xuất xưởng để khách tự đăng ký.
4. Chỉ hiện lựa chọn này với xe thuộc diện phải đăng ký (xem B8).
5. Sửa mô tả trong Word (UC-11, UC-24) cho đúng: "phí dịch vụ hỗ trợ đăng ký", không phải "phí đăng ký".
6. Chốt ý nghĩa khi Admin đặt `REGISTRATION_SERVICE` là INACTIVE: hiện tại báo giá quay về mặc định 500.000đ (chỉ nên dùng khi DB thiếu quy tắc). Đề xuất: INACTIVE = ẩn lựa chọn dịch vụ ở checkout.

**Test:**
- Unit: tổng đơn chỉ cộng phí dịch vụ; không chọn dịch vụ thì không cộng gì.
- Selenium: checkout hiện ghi chú lệ phí Nhà nước, không cộng vào tổng.

#### B6. Phí giao xe tận nhà (P1, sau B4) — khoảng ½ ngày

**Lỗi:** `setShippingFee(ZERO)` cho mọi đơn, kể cả giao liên tỉnh.

**Sửa:** nhận tại cửa hàng thì 0đ. Giao tận nhà: cùng tỉnh với một showroom đang hoạt động thì dùng `HOME_DELIVERY_SAME_CITY`, khác tỉnh thì dùng `HOME_DELIVERY_OTHER_PROVINCE`, nhân số xe. Báo giá và đơn hàng hiển thị dòng phí giao xe.

**Test:** unit cho 3 trường hợp; Selenium: đổi tỉnh thì tổng tiền đổi.

#### B7. Giới hạn mỗi khách dùng một mã một lần (P2) — khoảng 1 ngày

**Lỗi:** mã chỉ bị giới hạn theo tổng số lượt, một người dùng được nhiều lần.

**Sửa (cần đổi cấu trúc bảng):**
1. Thêm cột `orders.promotion_id`, có khóa ngoại tới `promotions`. Cập nhật file setup, viết file `ALTER TABLE` cho DB đang chạy, và thêm vào entity `Order`.
2. Đặt hàng kèm mã thì kiểm tra khách (theo `user_id`, hoặc theo email nếu là khách vãng lai) chưa có đơn **không bị hủy** nào dùng mã này.
3. Đơn bị hủy thì trả lại lượt: giảm `usage_count` của mã.

**Test:** unit (lần 2 bị từ chối; hủy đơn rồi dùng lại được).

#### B8. Phân loại xe đúng luật (P2) — khoảng ½ ngày

**Lỗi:** 5 mẫu `E_BIKE` có tốc độ 35 km/h, công suất tới 650 W, vượt ngưỡng xe đạp điện (25 km/h, 250 W), nên về pháp lý là xe máy điện.

**Sửa:**
1. Trong `backend/database/catalog/products.json` và file setup, đổi các mẫu vượt ngưỡng sang `E_MOTORBIKE`, hoặc giữ tên gọi thương mại nhưng hiện ghi chú "Cần đăng ký biển số".
2. Chỉ cho chọn dịch vụ làm biển số với xe thuộc diện phải đăng ký.

**Test:** unit cho quy tắc ngưỡng; kiểm tra lại bộ lọc theo loại xe.

#### Chung cho B1–B8

- Mỗi commit một dòng ngắn, không có dòng đồng tác giả.
- Sau mỗi mục: chạy `mvn test` và bộ Selenium (`-Dtest='*SeleniumTest'`).
- Cập nhật bảng use case ở mục 2 (UC-01, UC-11, UC-17, UC-24) và mô tả tương ứng trong Word.
- Phần **ngoài phạm vi**, chỉ ghi vào mục "hạn chế" của báo cáo: hóa đơn VAT hoặc hóa đơn điện tử, đặt cọc, trả góp, phiếu bảo hành theo số khung.

## 5. Lộ trình đề xuất

Ước lượng theo khoảng 3–4 giờ/ngày.

| Tuần | Việc | Kết quả |
|---|---|---|
| 1 | ~~V1~~ ✅, ~~V2~~ ✅, ~~V3~~ ✅ | Luồng mua đầy đủ: đặt hàng → VNPay → xem chi tiết → hủy (xong, chờ merge) |
| 1–2 | **B3, B1, B2** (P0), rồi B4, B6, B5 | Bịt lỗ hổng email, tự hủy đơn quá hạn, doanh thu đúng; phí lấy từ bảng giá |
| 2 | V4, V5, V6 | Hồ sơ khách hàng, Manager và Admin đủ use case trong Word |
| 3 | V7, V8, V9, V10 | Ảnh sản phẩm, trang chi tiết xe, chatbot, bộ lọc |
| 4 | V11 → V14, dọn dẹp, test | Hoàn tất 16 task (trừ Task 14), test và tài liệu khớp code |
| (thêm) | V15 → V17 | Phần mở rộng |

## 6. Cách làm mỗi việc

1. Tạo nhánh từ `MinhTien`, ví dụ `git checkout -b feat/vnpay-checkout`.
2. Sửa tối thiểu, giữ nguyên URL API đang có.
3. Build và test: `cd backend` rồi `mvn test`.
4. Chạy app, thử tay theo mục "Kiểm tra" của việc đó. Nếu có giao diện thì chạy thêm UI test trong `backend/tests/ui/`.
5. Commit với message ghi rõ UC/Task, ví dụ `UC-07: thêm thanh toán VNPay ở checkout`, rồi mở PR vào `develop`.
