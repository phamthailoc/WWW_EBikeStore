# Điểm nhỏ chưa sửa

Các điểm review thấy nhưng chưa sửa, gom lại để dọn một lượt sau V35.

## V26 – phiếu nhập kho và lịch sử tồn kho

- **Huy hiệu "Lệch" hiện nhầm** (`StockHistoryService`, so tồn sản phẩm với dòng sổ cấp sản phẩm): xe có mọi màu đều bị tắt thì sổ cấp sản phẩm cộng ra 0. Hiện chỉ xảy ra khi sửa tay DB, vì chưa có mã nào tắt màu.
- **Link phiếu nhập trong lịch sử** (`stock.js`, `reasonCell`): mỗi dòng `PN-…` trỏ tới `/manager/stock-receipts` cần quyền `product:manage-stock`, còn xem lịch sử chỉ cần `order:view-all`; nhân viên chỉ xem sẽ vào trang "không có quyền".
- **Ô tìm phiếu** (`stockreceipts.js:71`, `StockRules.receiptIdOf`): gõ "PN-12" thì thoáng hiện phiếu 1 hoặc không có gì (tìm sau 300 ms).
- **Danh sách phiếu** (`StockReceiptService.list`): nạp dòng của từng phiếu riêng, khoảng 20 truy vấn thừa mỗi trang. Chỉ ảnh hưởng tốc độ.
- **Nhật ký kiểm toán** (`StockReceiptService`): dòng audit ghi bằng REQUIRES_NEW trước khi phiếu commit; nếu commit lỗi sẽ có audit cho phiếu không tồn tại. Hiếm; các service khác dùng cùng kiểu.
- **Xóa cứng sản phẩm/màu** (`update_stock_receipts.sql`): `ON DELETE CASCADE` sẽ xóa luôn dòng phiếu và dòng sổ cũ. Chỉ quan trọng nếu sau này có chức năng xóa cứng.
- **Dữ liệu local cũ**: 34 sản phẩm có `products.stock_quantity` khác tổng các màu (ví dụ sản phẩm 7: 4 so với 52). Có từ trước V26; sổ cái khớp tổng các màu.
