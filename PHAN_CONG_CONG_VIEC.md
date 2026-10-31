# Phân chia công việc cho 4 thành viên

## Thông tin chung

- Dự án: E-bike Multiplatform
- Thành viên tạm gọi: A, B, C, D
- Cách chia: 4 task độc lập, các thành viên tự chọn 1 task
- Thời gian thực hiện: khoảng 1 tuần
- Deadline dự kiến: 23/05/2026
- Mục tiêu: bổ sung các chức năng mới có thể tích hợp vào hệ thống web/backend hiện tại

## Nguyên tắc chọn task

- Mỗi thành viên chọn 1 task chính và chịu trách nhiệm từ phân tích, code, test đến demo.
- Nếu task cần thay đổi database/API thì phải ghi rõ thay đổi trong phần ghi chú hoặc tài liệu ngắn.
- Ưu tiên hoàn thành luồng chính trước, sau đó mới làm thêm giao diện đẹp hoặc tính năng phụ.
- Khi hoàn thành cần có ảnh chụp màn hình hoặc mô tả ngắn cách test chức năng.

## Danh sách task

| Mã task | Chức năng | Mô tả ngắn | Kết quả cần bàn giao | Người chọn |
| --- | --- | --- | --- | --- |
| Task 1 | Đánh giá và bình luận sản phẩm | Cho phép khách hàng đánh giá sản phẩm bằng số sao và bình luận sau khi đã xem/mua sản phẩm. | API thêm/sửa/xóa/lấy đánh giá, hiển thị đánh giá trên trang chi tiết sản phẩm, validation dữ liệu đầu vào. | Chưa chọn |
| Task 2 | Mã giảm giá/khuyến mãi | Xây dựng chức năng nhập mã giảm giá khi thanh toán đơn hàng. | API kiểm tra mã giảm giá, áp dụng giảm giá vào tổng tiền, giao diện nhập mã tại trang checkout, xử lý mã hết hạn/sai điều kiện. | Chưa chọn |
| Task 3 | Theo dõi trạng thái giao hàng | Bổ sung màn hình cho khách hàng xem trạng thái vận chuyển của đơn hàng. | API lấy trạng thái shipment, timeline trạng thái đơn hàng, giao diện trong trang đơn hàng khách hàng, hiển thị ngày cập nhật gần nhất. | Chưa chọn |
| Task 4 | Thông báo người dùng | Tạo chức năng thông báo cho khách hàng khi đơn hàng đổi trạng thái hoặc thanh toán thành công/thất bại. | API danh sách thông báo, đánh dấu đã đọc, icon/số lượng thông báo chưa đọc trên giao diện, trang hoặc dropdown xem thông báo. | Chưa chọn |
| Task 5 | Danh sách yêu thích nâng cao | Cải thiện chức năng yêu thích để người dùng lưu xe quan tâm và xem lại dễ dàng. | Nút thêm/bỏ yêu thích ở danh sách và chi tiết sản phẩm, trang danh sách yêu thích, lưu trạng thái theo người dùng. | Chưa chọn |
| Task 6 | So sánh sản phẩm | Cho phép khách hàng chọn 2-3 xe để so sánh thông số, giá và đặc điểm nổi bật. | UI chọn sản phẩm để so sánh, bảng so sánh thông số, xử lý khi thiếu dữ liệu thông số. | Chưa chọn |
| Task 7 | Tìm kiếm và lọc sản phẩm nâng cao | Bổ sung bộ lọc theo giá, loại xe, hãng, pin, quãng đường hoặc trạng thái còn hàng. | API/query lọc sản phẩm, giao diện filter, sắp xếp theo giá/tên/mới nhất, giữ trạng thái filter trên URL nếu phù hợp. | Chưa chọn |
| Task 8 | Quản lý tồn kho sản phẩm | Hỗ trợ manager theo dõi số lượng tồn kho và cảnh báo sản phẩm sắp hết hàng. | Trường tồn kho cho sản phẩm/biến thể, giao diện cập nhật tồn kho, nhãn cảnh báo sắp hết hàng. | Chưa chọn |
| Task 9 | Báo cáo doanh thu cơ bản | Tạo màn hình thống kê doanh thu, số đơn hàng và sản phẩm bán chạy cho manager. | API thống kê, biểu đồ/bảng doanh thu theo ngày hoặc tháng, top sản phẩm bán chạy. | Chưa chọn |
| Task 10 | Quản lý địa chỉ giao hàng | Cho phép khách hàng lưu nhiều địa chỉ và chọn địa chỉ khi đặt hàng. | API CRUD địa chỉ, giao diện quản lý địa chỉ trong hồ sơ, chọn địa chỉ ở checkout. | Chưa chọn |
| Task 11 | Hủy đơn hàng có lý do | Cho phép khách hàng hủy đơn khi đơn chưa giao và nhập lý do hủy. | API hủy đơn, validate trạng thái được phép hủy, giao diện nhập lý do, lưu lịch sử hủy. | Chưa chọn |
| Task 12 | Lịch sử thanh toán | Bổ sung màn hình xem lịch sử thanh toán và trạng thái giao dịch của khách hàng. | API danh sách payment theo người dùng, giao diện lịch sử thanh toán, lọc theo trạng thái thành công/thất bại/chờ xử lý. | Chưa chọn |
| Task 13 | Quản lý tài khoản cá nhân | Cải thiện trang hồ sơ để người dùng cập nhật thông tin cá nhân và đổi mật khẩu. | Form cập nhật hồ sơ, form đổi mật khẩu, validation, thông báo thành công/thất bại. | Chưa chọn |
| Task 14 | Trang hỗ trợ khách hàng | Tạo khu vực hỗ trợ gồm câu hỏi thường gặp và form gửi yêu cầu hỗ trợ. | Trang FAQ, form liên hệ/hỗ trợ, API lưu yêu cầu hỗ trợ hoặc mock dữ liệu nếu chưa làm backend. | Chưa chọn |
| Task 15 | Quản lý hình ảnh sản phẩm | Cải thiện luồng thêm/xóa/sắp xếp ảnh sản phẩm cho manager | Giao diện upload nhiều ảnh, chọn ảnh đại diện, xóa ảnh, hiển thị trạng thái upload. | Chưa chọn |
| Task 16 | Gợi ý sản phẩm liên quan | Hiển thị các sản phẩm tương tự hoặc phù hợp với sản phẩm khách hàng đang xem. | API hoặc logic lấy sản phẩm liên quan, khu vực gợi ý trên trang chi tiết sản phẩm, xử lý khi không có sản phẩm phù hợp. | Chưa chọn |

## Chi tiết từng task

### Task 1: Đánh giá và bình luận sản phẩm

#### Phạm vi

- Khách hàng có thể gửi đánh giá gồm số sao từ 1 đến 5 và nội dung bình luận.
- Trang chi tiết sản phẩm hiển thị danh sách đánh giá.
- Có thể tính điểm đánh giá trung bình cho từng sản phẩm.

#### Tiêu chí hoàn thành

- Tạo được đánh giá mới.
- Hiển thị được danh sách đánh giá theo sản phẩm.
- Không cho nhập số sao ngoài khoảng 1-5.
- Giao diện không bị lỗi khi sản phẩm chưa có đánh giá.

### Task 2: Mã giảm giá/khuyến mãi

#### Phạm vi

- Người dùng nhập mã giảm giá ở bước checkout.
- Hệ thống kiểm tra mã còn hiệu lực, đúng điều kiện và tính lại tổng tiền.
- Có thể hỗ trợ giảm theo phần trăm hoặc số tiền cố định.

#### Tiêu chí hoàn thành

- Nhập mã hợp lệ thì tổng tiền được cập nhật.
- Nhập mã sai/hết hạn thì hiển thị lỗi rõ ràng.
- Không cho tổng tiền sau giảm nhỏ hơn 0.
- Dữ liệu giảm giá được lưu cùng đơn hàng hoặc ghi chú trong đơn hàng.

### Task 3: Theo dõi trạng thái giao hàng

#### Phạm vi

- Khách hàng xem trạng thái giao hàng trong trang chi tiết đơn hàng.
- Hiển thị dạng timeline: chờ xử lý, đang chuẩn bị, đang giao, đã giao, đã hủy.
- Manager/admin có thể cập nhật trạng thái giao hàng nếu phù hợp với hệ thống hiện tại.

#### Tiêu chí hoàn thành

- Lấy được trạng thái giao hàng từ backend.
- Trang đơn hàng hiển thị timeline dễ hiểu.
- Khi trạng thái thay đổi, giao diện phản ánh đúng trạng thái mới.
- Xử lý trường hợp đơn hàng chưa có thông tin giao hàng.

### Task 4: Thông báo người dùng

#### Phạm vi

- Người dùng nhận thông báo khi đơn hàng đổi trạng thái hoặc thanh toán thành công/thất bại.
- Có danh sách thông báo và trạng thái đã đọc/chưa đọc.
- Header hoặc layout hiển thị số thông báo chưa đọc.

#### Tiêu chí hoàn thành

- Lấy được danh sách thông báo theo người dùng.
- Đánh dấu một thông báo hoặc tất cả thông báo là đã đọc.
- Hiển thị số lượng thông báo chưa đọc.
- Giao diện xử lý tốt khi chưa có thông báo.

### Task 5: Danh sách yêu thích nâng cao

#### Phạm vi

- Người dùng có thể thêm hoặc bỏ sản phẩm khỏi danh sách yêu thích.
- Trang yêu thích hiển thị các sản phẩm đã lưu.
- Trạng thái yêu thích cần đồng bộ khi người dùng quay lại trang sản phẩm.

#### Tiêu chí hoàn thành

- Thêm/bỏ yêu thích hoạt động ổn định.
- Danh sách yêu thích hiển thị đúng theo người dùng.
- Không bị lỗi khi danh sách trống.
- Giao diện có trạng thái đang xử lý hoặc thông báo kết quả.

### Task 6: So sánh sản phẩm

#### Phạm vi

- Người dùng chọn nhiều sản phẩm để so sánh.
- Bảng so sánh hiển thị giá, loại xe, pin, thông số kỹ thuật và mô tả chính.
- Có thể xóa sản phẩm khỏi bảng so sánh.

#### Tiêu chí hoàn thành

- Chọn được ít nhất 2 sản phẩm để so sánh.
- Bảng so sánh dễ đọc trên desktop.
- Có xử lý khi sản phẩm thiếu thông số.
- Không làm ảnh hưởng trang danh sách sản phẩm hiện tại.

### Task 7: Tìm kiếm và lọc sản phẩm nâng cao

#### Phạm vi

- Lọc sản phẩm theo khoảng giá, danh mục, loại xe, hãng hoặc trạng thái còn hàng.
- Sắp xếp sản phẩm theo giá, tên hoặc mới nhất.
- Có thể kết hợp nhiều điều kiện lọc cùng lúc.

#### Tiêu chí hoàn thành

- Filter trả về kết quả đúng.
- Có trạng thái rỗng khi không tìm thấy sản phẩm.
- Giao diện filter dễ dùng, không làm vỡ layout.
- Query hoặc state filter rõ ràng, dễ mở rộng.

### Task 8: Quản lý tồn kho sản phẩm

#### Phạm vi

- Manager/admin xem và cập nhật số lượng tồn kho.
- Hệ thống hiển thị cảnh báo khi số lượng thấp.
- Khi đặt hàng thành công có thể trừ tồn kho nếu phù hợp với luồng hiện tại.

#### Tiêu chí hoàn thành

- Hiển thị số lượng tồn kho trong trang quản lý sản phẩm.
- Cập nhật tồn kho thành công.
- Có cảnh báo sản phẩm hết hàng hoặc sắp hết hàng.
- Không cho nhập số lượng âm.

### Task 9: Báo cáo doanh thu cơ bản

#### Phạm vi

- Manager xem doanh thu theo ngày/tháng/quý/năm/chọn ngày từ ngày nào đến ngày nào
- Hiển thị tổng đơn hàng, tổng doanh thu, số đơn thành công.
- Có danh sách sản phẩm bán chạy.

#### Tiêu chí hoàn thành

- Thống kê lấy được từ dữ liệu đơn hàng.
- Có biểu đồ dễ đọc.
- Có trạng thái loading/error.
- Số liệu không tính các đơn đã hủy nếu hệ thống có trạng thái hủy.

### Task 10: Quản lý địa chỉ giao hàng

#### Phạm vi

- Khách hàng có thể thêm, sửa, xóa địa chỉ.
- Checkout cho phép chọn một địa chỉ đã lưu.
- Có thể đánh dấu địa chỉ mặc định.

#### Tiêu chí hoàn thành

- CRUD địa chỉ hoạt động đúng.
- Checkout lấy được địa chỉ đã chọn.
- Validate số điện thoại, tên người nhận và địa chỉ.
- Xử lý tốt khi người dùng chưa có địa chỉ nào.

### Task 11: Hủy đơn hàng có lý do

#### Phạm vi

- Khách hàng được hủy đơn trực tiếp khi đơn chưa giao hoặc chưa hoàn tất (không cần manager duyệt).
- Khi hủy bắt buộc nhập lý do.
- Manager/admin xem được lý do hủy trên chi tiết đơn.

#### Tiêu chí hoàn thành

- Chỉ cho hủy ở trạng thái hợp lệ (ví dụ: chờ xác nhận, đã xác nhận, đang xử lý).
- Lưu được lý do hủy và cập nhật trạng thái đơn thành `CANCELLED` ngay lập tức.
- Giao diện cập nhật trạng thái đơn sau khi hủy.
- Có thông báo lỗi khi không thể hủy đơn hoặc thiếu lý do.

### Task 12: Lịch sử thanh toán

#### Phạm vi

- Khách hàng xem các lần thanh toán của mình.
- Hiển thị mã giao dịch, số tiền, phương thức, trạng thái và thời gian.
- Có bộ lọc theo trạng thái thanh toán.

#### Tiêu chí hoàn thành

- Danh sách thanh toán hiển thị đúng.
- Có trạng thái khi chưa có giao dịch.
- Lọc theo trạng thái hoạt động.
- Không hiển thị dữ liệu thanh toán của người dùng khác.

### Task 13: Quản lý tài khoản cá nhân

#### Phạm vi

- Người dùng cập nhật họ tên, số điện thoại, email hoặc thông tin cơ bản.
- Người dùng đổi mật khẩu.
- Có validate dữ liệu đầu vào.

#### Tiêu chí hoàn thành

- Cập nhật hồ sơ thành công.
- Đổi mật khẩu có kiểm tra mật khẩu cũ.
- Hiển thị thông báo lỗi/thành công rõ ràng.
- Không làm mất trạng thái đăng nhập sau khi cập nhật.

### Task 14: Trang hỗ trợ khách hàng

#### Phạm vi

- Tạo trang câu hỏi thường gặp.
- Cho phép khách hàng gửi yêu cầu hỗ trợ.
- Manager/admin có thể xem danh sách yêu cầu nếu làm kịp.

#### Tiêu chí hoàn thành

- Trang FAQ có nội dung phù hợp với mua xe/đặt hàng/thanh toán.
- Form hỗ trợ có validate.
- Gửi yêu cầu thành công hoặc lưu mock rõ ràng.
- Giao diện thân thiện, dễ đọc.

### Task 15: Quản lý hình ảnh sản phẩm

#### Phạm vi

- Manager/admin upload nhiều ảnh cho sản phẩm.
- Chọn ảnh đại diện.
- Xóa ảnh không cần dùng.

#### Tiêu chí hoàn thành

- Upload và hiển thị ảnh hoạt động.
- Chọn được ảnh chính.
- Xóa ảnh có xác nhận.
- Có xử lý lỗi khi upload thất bại.

### Task 16: Gợi ý sản phẩm liên quan

#### Phạm vi

- Trang chi tiết sản phẩm hiển thị danh sách sản phẩm liên quan.
- Sản phẩm liên quan có thể dựa trên danh mục, loại xe, khoảng giá hoặc hãng.
- Người dùng có thể bấm vào sản phẩm được gợi ý để xem chi tiết.

#### Tiêu chí hoàn thành

- Hiển thị được danh sách sản phẩm liên quan trên trang chi tiết.
- Không hiển thị trùng chính sản phẩm đang xem.
- Có trạng thái rỗng khi không có sản phẩm phù hợp.
- Giao diện gợi ý không làm vỡ layout trang chi tiết sản phẩm.


## Phân công theo thành viên

### Thành viên A

Nhóm chức năng trải nghiệm khách hàng và hồ sơ cá nhân.

| Task | Chức năng | Công việc chính |
| --- | --- | --- |
| Task 1 | Đánh giá và bình luận sản phẩm | Làm chức năng đánh giá sao, bình luận và hiển thị đánh giá trên trang chi tiết sản phẩm. |
| Task 5 | Danh sách yêu thích nâng cao | Cho phép khách hàng thêm/bỏ yêu thích và xem lại danh sách sản phẩm đã lưu. |
| Task 9 | Báo cáo doanh thu cơ bản | Làm màn hình thống kê doanh thu, số đơn hàng và sản phẩm bán chạy cho manager. |
| Task 13 | Quản lý tài khoản cá nhân | Cải thiện hồ sơ người dùng, cập nhật thông tin cá nhân và đổi mật khẩu. |

### Thành viên B

Nhóm chức năng checkout, so sánh sản phẩm, địa chỉ và hỗ trợ khách hàng.

| Task | Chức năng | Công việc chính |
| --- | --- | --- |
| Task 2 | Mã giảm giá/khuyến mãi | Làm chức năng nhập mã giảm giá ở checkout và tính lại tổng tiền đơn hàng. |
| Task 6 | So sánh sản phẩm | Cho phép khách hàng chọn 2-3 sản phẩm để so sánh giá, thông số và đặc điểm. |
| Task 10 | Quản lý địa chỉ giao hàng | Cho phép khách hàng thêm/sửa/xóa địa chỉ và chọn địa chỉ khi đặt hàng. |
| Task 14 | Trang hỗ trợ khách hàng | Tạo trang FAQ và form gửi yêu cầu hỗ trợ cho khách hàng. |

### Thành viên C

Nhóm chức năng đơn hàng, tìm kiếm/lọc sản phẩm, hủy đơn và quản lý ảnh.

| Task | Chức năng | Công việc chính |
| --- | --- | --- |
| Task 3 | Theo dõi trạng thái giao hàng | Hiển thị timeline trạng thái giao hàng trong chi tiết đơn hàng. |
| Task 7 | Tìm kiếm và lọc sản phẩm nâng cao | Bổ sung bộ lọc theo giá, loại xe, hãng, pin, quãng đường hoặc trạng thái còn hàng. |
| Task 11 | Hủy đơn hàng có lý do | Cho phép khách hàng hủy trực tiếp đơn ở trạng thái hợp lệ, bắt buộc nhập lý do; manager/admin chỉ xem lý do. |
| Task 15 | Quản lý hình ảnh sản phẩm | Cải thiện upload nhiều ảnh, chọn ảnh đại diện và xóa ảnh sản phẩm. |

### Thành viên D

Nhóm chức năng thông báo, tồn kho, thanh toán và gợi ý sản phẩm.

| Task | Chức năng | Công việc chính |
| --- | --- | --- |
| Task 4 | Thông báo người dùng | Tạo danh sách thông báo, đánh dấu đã đọc và hiển thị số thông báo chưa đọc. |
| Task 8 | Quản lý tồn kho sản phẩm | Cho manager/admin cập nhật tồn kho và cảnh báo sản phẩm sắp hết hàng. |
| Task 12 | Lịch sử thanh toán | Hiển thị lịch sử thanh toán, trạng thái giao dịch và bộ lọc theo trạng thái. |
| Task 16 | Gợi ý sản phẩm liên quan | Hiển thị sản phẩm tương tự trên trang chi tiết sản phẩm. |

## Mẫu cập nhật tiến độ

| Thành viên | Task chọn | Trạng thái | Ghi chú |
| --- | --- | --- | --- |
| A | Task 1, Task 5, Task 9, Task 13 | Chưa làm | Nhóm chức năng trải nghiệm khách hàng và hồ sơ cá nhân |
| B | Task 2, Task 6, Task 10, Task 14 | Chưa làm | Nhóm checkout, so sánh sản phẩm, địa chỉ và hỗ trợ |
| C | Task 3, Task 7, Task 11, Task 15 | Chưa làm | Nhóm đơn hàng, lọc sản phẩm, hủy đơn và quản lý ảnh |
| D | Task 4, Task 8, Task 12, Task 16 | Chưa làm | Nhóm thông báo, tồn kho, thanh toán và gợi ý sản phẩm |

## Tiêu chí nghiệm thu chung

- Chức năng chạy được luồng chính.
- Không làm hỏng các chức năng hiện có như đăng nhập, xem sản phẩm, đặt hàng, thanh toán.
- Code có cấu trúc rõ ràng, đặt tên dễ hiểu.
- Có xử lý lỗi cơ bản ở cả backend và frontend.
- Có hướng dẫn test ngắn hoặc mô tả cách demo.
