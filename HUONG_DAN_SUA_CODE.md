# Hướng dẫn sửa code cho khớp tài liệu Word

Tài liệu Word `TÀI LIỆU WEB BÁN XE_Lập trình WWW (Java).docx` đang mô tả hành vi **đúng nghiệp vụ**, còn code hiện tại chưa làm đúng ở **4 điểm** dưới đây. File này liệt kê từng điểm: code đang làm gì, cần sửa thành gì, sửa ở file nào, và cách kiểm tra. Không cần sửa Word nữa; chỉ sửa code rồi chạy lại test case.

## 0. Trước khi bắt đầu

- Tạo nhánh mới trước khi sửa: `git checkout -b fix/khop-tai-lieu`. Thư mục đang có sẵn một số file chưa commit (`flow.js`, `workspace.js`, `detail.html`, `flow.html`, `home.html`), đừng làm lẫn với việc này.
- Mã nguồn nằm ở `backend/src/main/java/com/ebike/...` (viết tắt `…` bên dưới). Cấu hình `spring.jpa.hibernate.ddl-auto=validate` (không dùng Flyway), nên **không có migration tự chạy**; nếu cần thêm dữ liệu mẫu thì sửa `setup_ebike_database.sql` và chạy tay vào `ebike_db`.
- Cả 4 việc **không cần đổi cấu trúc bảng**: bảng `promotions` đã đủ cột, `payments.payment_status` là `VARCHAR(30)` nên đã chứa được `REFUNDED`, quyền `payment:refund` đã có trong seed và đã gán cho MANAGER.
- Không ghi secret (khóa VNPay, SMTP, Gemini, AWS) vào file nào được commit.

## Việc 1. Mã khuyến mãi lấy từ bảng `promotions`

**Hiện trạng.** `orderModule/service/impl/OrderServiceImpl.java` có hằng số `PROMO_CODE_DISCOUNTS` gán cứng hai mã `EBIKE100` (100.000) và `EBIKE200` (200.000). Hàm `resolvePromoDiscount(String discountCode, BigDecimal subtotal)` chỉ tra Map này. Bảng `promotions` (Admin tạo ở UC-24) hoàn toàn không được dùng khi báo giá hay đặt hàng, và `usage_count` không bao giờ tăng.

**Cần có (theo Word, UC-11 và UC-24).**

1. Khi báo giá (`POST /api/v1/orders/quote`) và khi đặt hàng (`POST /api/v1/orders`), mã được tra trong bảng `promotions`. Mã chỉ hợp lệ khi **cả ba** điều kiện đúng:
   - `status = 'LIVE'`;
   - thời điểm hiện tại nằm trong `starts_at`…`ends_at` (cột có thể `NULL` thì coi là không giới hạn phía đó);
   - còn lượt dùng: `usage_count < usage_limit`.
2. Tính giảm giá theo `discount_type`:
   - `PERCENTAGE`: `subtotal × discount_value / 100`, nếu `max_discount_amount` khác `NULL` thì không vượt quá giá trị đó;
   - `FIXED_AMOUNT`: bằng `discount_value`;
   - cả hai trường hợp đều không vượt quá `subtotal` (giữ nguyên dòng `.min(subtotal)` hiện có).
3. Mã sai, không tồn tại hoặc không hợp lệ: trả `400` với thông báo hiện có `"Mã giảm giá không hợp lệ hoặc đã hết hạn."`.
4. Khi **đặt hàng** (không phải báo giá): tăng `usage_count` thêm 1 **trong cùng transaction** với việc tạo đơn. Nếu bước nào sau đó lỗi thì phải hoàn tác cả lượt dùng (`createOrder` đã là `@Transactional`).
5. Khoản "ưu đãi showroom" `SHOWROOM_INCENTIVE_AMOUNT` (1.200.000) vẫn cộng riêng như hiện nay, không đổi.

**Gợi ý cài đặt.**

- Inject `PromotionRepository` (`adminModule/repository/PromotionRepository.java`) vào `OrderServiceImpl`. Repository đã có `findByCode(String)`.
- Chuẩn hóa mã trước khi tra: `trim()` rồi `toUpperCase()`, giống `normalizedCode` hiện tại. Lưu ý Admin tạo mã bằng `createPromotion` chỉ bỏ khoảng trắng chứ **không** đổi hoa/thường, nên hãy so sánh không phân biệt hoa/thường hoặc đổi `createPromotion` để lưu mã dạng chữ hoa.
- Tách hàm kiểm tra hợp lệ dùng chung cho `buildQuote` và `createOrder`, để báo giá và đặt hàng không bao giờ lệch nhau.
- Tăng lượt dùng nên làm bằng một câu `UPDATE` có điều kiện, tránh hai người dùng cùng lúc vượt giới hạn. Ví dụ thêm vào `PromotionRepository`:

  ```java
  @Modifying
  @Query("update Promotion p set p.usageCount = p.usageCount + 1 "
       + "where p.id = :id and (p.usageLimit = 0 or p.usageCount < p.usageLimit)")
  int tryIncrementUsage(@Param("id") Long id);
  ```

  Kết quả `0` nghĩa là mã vừa hết lượt, khi đó ném `400` như trên và transaction hoàn tác.
- Sau khi xong có thể xóa `PROMO_CODE_DISCOUNTS`.

**Cần anh quyết định: `usage_limit = 0` nghĩa là gì?** Cột mặc định là `0`, và `createPromotion` gán `0` khi Admin để trống. Đề xuất: `0` = không giới hạn (như ví dụ trên). Nếu muốn `0` = hết lượt thì đổi điều kiện cho phù hợp. Chọn xong thì nên ghi một dòng vào Word cho khớp.

**Dữ liệu mẫu.** Seed hiện có là `KINETIC20`, `STUDENT15`, `WEEKEND` và đều hết hạn từ tháng 6–7/2026, nên không dùng để thử được. Tài liệu Word (phần test case) lại nhắc `EBIKE100` như mã mẫu, mà mã này **chưa có trong bảng**. Thêm vào `setup_ebike_database.sql` (và chạy vào DB đang dùng):

```sql
INSERT INTO promotions(code,campaign_name,discount_type,discount_value,max_discount_amount,usage_count,usage_limit,status,starts_at,ends_at) VALUES
('EBIKE100','Giảm 100 nghìn','FIXED_AMOUNT',100000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59'),
('EBIKE200','Giảm 200 nghìn','FIXED_AMOUNT',200000,NULL,0,1000,'LIVE','2026-01-01 00:00:00','2027-12-31 23:59:59');
```

**Kiểm tra.** Báo giá với `EBIKE100` ra giảm đúng 100.000; mã `WEEKEND` (trạng thái `WAITING`) bị từ chối; mã hết hạn hoặc đủ lượt bị từ chối; đặt hàng xong `usage_count` tăng đúng 1; đặt hàng lỗi (ví dụ hết hàng) thì `usage_count` không đổi.

## Việc 2. Hủy đơn hàng: hủy ngay, cộng lại tồn kho

**Hiện trạng.** `OrderServiceImpl.requestCancellation` (endpoint `POST /api/v1/orders/{id}/cancellation-request`) đã làm đúng các phần sau: kiểm tra chủ đơn; chỉ cho hủy khi đơn `PENDING`/`CONFIRMED`/`PROCESSING` và giao nhận chưa `SHIPPED`/`IN_TRANSIT`/`DELIVERED`; bắt buộc có lý do (tối đa 1000 ký tự); chuyển đơn sang `CANCELLED` ngay (không qua `CANCELLATION_REQUESTED`); hủy giao dịch đang `PENDING` thành `CANCELLED`.

**Còn thiếu.**

1. **Cộng lại tồn kho.** Hiện không cộng lại. Với mỗi `OrderItem` của đơn: cộng `quantity` vào `product.stockQuantity`, và nếu `item.getProductVariant() != null` thì cộng thêm vào `variant.stockQuantity`. Đây là phép ngược của `addItems`, nên đối chiếu với hàm đó để cộng đúng cùng chỗ.
2. **Xóa cache sản phẩm.** `createOrder` có `@CacheEvict` cho `PRODUCT_LIST_CACHE` và `PRODUCT_DETAIL_CACHE`; `requestCancellation` chưa có nên tồn kho cũ có thể còn nằm trong cache. Thêm cùng annotation đó (cùng kiểu với `createOrder`, `allEntries = true`) lên `requestCancellation`. Word (NFR-10) đã ghi cache được xóa khi hủy đơn.
3. **Chỉ cộng một lần.** Đã có chặn `status == CANCELLED` ở đầu hàm nên gọi lại sẽ báo lỗi, không cộng lặp. Đừng bỏ chặn đó.
4. **Giao dịch đã thanh toán (VNPay, `PAID`).** Giữ nguyên `PAID`, **không** tự đổi sang `REFUNDED` hay `CANCELLED`. Manager hoàn tiền thủ công ở Việc 3. Hàm `cancelPendingPayment` hiện đã chỉ đụng giao dịch `PENDING`, giữ nguyên.
5. Mọi thao tác trên (đổi trạng thái đơn, cộng tồn kho, cập nhật giao dịch) phải nằm trong cùng transaction; hàm đã `@Transactional`.

**Giữ nguyên URL.** Tên endpoint `/cancellation-request` nghe như "gửi yêu cầu", nhưng giao diện (`static/js/workspace.js`) đang gọi nó, nên **không đổi URL** để khỏi vỡ giao diện. Chỉ cần kiểm tra lại câu thông báo hiển thị cho khách sau khi hủy là "đã hủy đơn", không phải "đã gửi yêu cầu".

**Dọn thêm (tùy chọn).** `ManagerController` còn hai endpoint `/orders/{id}/cancellation/approve` và `/reject`, cùng trạng thái `CANCELLATION_REQUESTED`. Luồng khách hàng không còn tạo trạng thái này, nên hai endpoint đó thành mã chết. Có thể giữ lại (không hại gì) hoặc xóa gọn; nếu xóa thì xóa luôn `getPendingCancellationOrder` và phần `CANCELLATION_REQUESTED` trong `NotificationCreationService`. Word không nhắc tới chúng.

**Kiểm tra.** Đặt đơn 2 xe → tồn kho giảm 2 → khách hủy → tồn kho tăng lại đúng 2 (cả sản phẩm lẫn màu); danh sách xe trên web hiện đúng tồn kho mới ngay (không bị cache cũ); hủy lần hai báo lỗi; đơn đã `SHIPPED` không hủy được; đơn đã trả VNPay hủy xong vẫn `PAID`.

## Việc 3. Manager ghi nhận hoàn tiền (`REFUNDED`)

**Hiện trạng.** Chưa có chức năng này. `ManagerController` chỉ có `/payments` (xem) và `/payments/{id}/confirm` (xác nhận thu tiền PAY_LATER). Enum `PaymentStatus` đã có `REFUNDED`, quyền `PAYMENT_REFUND = "payment:refund"` đã khai báo trong `shared/constants/PermissionConstants.java` và đã seed cho MANAGER.

**Cần làm.**

1. Thêm vào `managerModule/service/ManagerService.java` và `ManagerServiceImpl.java`:

   ```java
   ManagerPaymentResponse refundPayment(Long paymentId, ManagerPaymentConfirmationRequest request);
   ```

   Có thể dùng lại record `ManagerPaymentConfirmationRequest(providerTxnId, note)`; chỉ cần `note`.
2. Quy tắc trong `refundPayment` (`@Transactional`):
   - không tìm thấy giao dịch → `404 "Payment not found"`;
   - giao dịch không ở trạng thái `PAID` → `400`;
   - đơn của giao dịch chưa `CANCELLED` → `400` (chỉ hoàn tiền cho đơn khách đã hủy);
   - hợp lệ → `payment.setPaymentStatus(PaymentStatus.REFUNDED)`, lưu `note` (ví dụ vào `providerResponse`, giống cách `confirmPayLaterPayment` lưu ghi chú), rồi `save` và trả `toPaymentResponse`.
   - Không đổi trạng thái đơn hàng (đơn vẫn `CANCELLED`).
3. Thêm endpoint trong `ManagerController`:

   ```java
   @PatchMapping("/payments/{paymentId}/refund")
   @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.PAYMENT_REFUND + "')")
   public ManagerPaymentResponse refundPayment(
       @PathVariable Long paymentId,
       @RequestBody(required = false) ManagerPaymentConfirmationRequest request) {
       return managerService.refundPayment(paymentId, request);
   }
   ```

4. Giao diện (`static/js/workspace.js`, trang `/manager/payments`): bảng giao dịch hiện có nút `confirm-payment`. Thêm nút "Ghi nhận hoàn tiền" chỉ hiện với giao dịch `PAID` của đơn đã hủy, gọi `PATCH /api/v1/manager/payments/${id}/refund` (xin ghi chú bằng `prompt`, giống cách nút tồn kho đang làm). Để lọc đơn "cần hoàn tiền" dễ hơn, `ManagerPaymentResponse` nên có trạng thái của đơn; xem `toPaymentResponse` trong `ManagerServiceImpl` để biết đã có chưa.
5. Đã có sẵn chặn xác nhận lại giao dịch `REFUNDED` trong `confirmPayLaterPayment` (`"Refunded payments cannot be confirmed again"`), giữ nguyên.
6. Thông báo cho khách khi hoàn tiền: **không bắt buộc** (Word không yêu cầu). `PaymentNotificationJpaListener` hiện chỉ phát sự kiện cho `PAID` và `FAILED`, nên hoàn tiền sẽ không tạo thông báo nếu anh không bổ sung.

**Kiểm tra.** Đơn VNPay đã `PAID` → khách hủy → Manager hoàn tiền → giao dịch `REFUNDED`, đơn vẫn `CANCELLED`; hoàn tiền giao dịch chưa `PAID` hoặc đơn chưa hủy → `400`; tài khoản Customer gọi endpoint này → `403`; giao dịch `REFUNDED` không xác nhận thu tiền lại được.

## Việc 4. Rà lại các điểm Word đã sửa theo code

Các điểm sau Word **đã khớp code**, không cần sửa code, chỉ cần biết để khỏi nhầm khi test:

- **Đăng nhập (UC-02):** code kiểm tra theo thứ tự tìm tài khoản (không có → `404`), còn hoạt động (không → `403`), rồi mới so mật khẩu (sai → `401`). Giao diện hiện thông báo chung cho mọi lỗi. Việc trả `404` khi không có tài khoản và `403` trước khi so mật khẩu tiết lộ tài khoản có tồn tại hay bị khóa; nếu muốn chặt hơn thì đổi thành một thông báo `401` chung, nhưng khi đó phải sửa lại sơ đồ UC-02 trong Word.
- **Tạo đơn (UC-06):** đơn chọn VNPay không có giao dịch lúc tạo; giao dịch `VNPAY` được tạo ở bước `POST /api/v1/payments/vnpay/create`. Đơn chọn trả sau có giao dịch `PAY_LATER` trạng thái `PENDING` ngay khi tạo.
- **Trang danh sách và chi tiết xe:** do `StorefrontController` dựng bằng Thymeleaf; trang chi tiết hiện không hiển thị đánh giá hay xe liên quan (dù controller đã tính `related`).
- **Hồ sơ cá nhân:** giao diện chỉ sửa họ tên, email; đổi mật khẩu, địa chỉ, tùy chọn chỉ có ở REST API.
- **Chatbot (UC-20):** `faq.csv` và PDF nằm trong `src/main/resources`, nên sửa xong phải build lại rồi khởi động lại.

## Thứ tự làm đề xuất

1. Việc 2 (nhỏ nhất, chỉ thêm cộng tồn kho và `@CacheEvict`).
2. Việc 3 (thêm một endpoint và một nút).
3. Việc 1 (đụng nhiều nhất: repository, hàm tính giảm giá, tăng lượt dùng, dữ liệu mẫu).
4. Build (`mvn -q -DskipTests package`) và chạy thử bằng tay theo các mục "Kiểm tra" ở trên, rồi điền cột "Kết quả thực tế" cho các test case liên quan trong Word.

## Câu lệnh gợi ý để dán vào Claude Code trong VS Code

> Đọc file `HUONG_DAN_SUA_CODE.md` ở thư mục gốc dự án. Làm lần lượt Việc 2, Việc 3, Việc 1. Mỗi việc: nêu trước các file sẽ sửa, sửa tối thiểu, build lại bằng Maven và báo kết quả. Với Việc 1, hỏi tôi về ý nghĩa `usage_limit = 0` trước khi viết code. Không đổi URL endpoint hiện có và không ghi secret vào mã nguồn.
