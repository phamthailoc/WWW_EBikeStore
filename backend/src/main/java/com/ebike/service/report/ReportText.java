package com.ebike.service.report;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * V28: the fixed words of the Excel files (sheet names, column titles, status names), in Vietnamese or English. Anything other than "en" is Vietnamese,
 * the shop's base language. A key without a text falls back to the key itself, so a missing translation shows up as a plain word instead of an error.
 */
public final class ReportText {

    private static final Map<String, String[]> TEXT = new LinkedHashMap<>();

    private static void add(String key, String vietnamese) {
        TEXT.put(key, new String[] {vietnamese});
    }

    static {
        // ---- revenue report ----
        add("sheet.summary", "Tổng quan");
        add("sheet.breakdown", "Doanh thu theo kỳ");
        add("sheet.top", "Xe bán chạy");
        add("sheet.slow", "Xe bán chậm");
        add("sheet.orders", "Đơn hàng");
        add("sheet.filters", "Bộ lọc");
        add("summary.title", "Báo cáo doanh thu KINETIC");
        add("summary.groupBy", "Gộp theo");
        add("summary.from", "Từ ngày");
        add("summary.to", "Đến ngày");
        add("summary.generatedAt", "Xuất lúc");
        add("summary.placedOrders", "Đơn đã đặt trong kỳ");
        add("summary.totalOrders", "Đơn không bị hủy");
        add("summary.successfulOrders", "Đơn đã giao");
        add("summary.cancelledOrders", "Đơn bị hủy");
        add("summary.cancellationRate", "Tỉ lệ hủy");
        add("summary.totalRevenue", "Doanh thu (tiền đã thu trong kỳ)");
        add("summary.note", "Doanh thu tính theo ngày thu tiền; số đơn tính theo ngày đặt. Đơn bị hủy không có trong doanh thu và xe bán chạy.");
        add("period.DAY", "Ngày");
        add("period.MONTH", "Tháng");
        add("period.QUARTER", "Quý");
        add("period.YEAR", "Năm");
        add("col.period", "Kỳ");
        add("col.orderCount", "Số đơn");
        add("col.revenue", "Doanh thu (₫)");
        add("col.rank", "Hạng");
        add("col.product", "Sản phẩm");
        add("col.quantity", "Số xe đã bán");
        add("row.total", "Tổng cộng");
        add("empty.rows", "Chưa có dữ liệu trong kỳ này.");

        // ---- order list ----
        add("order.number", "Mã đơn");
        add("order.createdAt", "Ngày đặt");
        add("order.status", "Trạng thái đơn");
        add("order.customerType", "Loại khách");
        add("order.customerAccount", "Có tài khoản");
        add("order.customerGuest", "Khách vãng lai");
        add("order.recipient", "Người nhận");
        add("order.email", "Email");
        add("order.phone", "Điện thoại");
        add("order.handover", "Hình thức nhận xe");
        add("order.pickup", "Nhận tại cửa hàng");
        add("order.delivery", "Giao tại nhà");
        add("order.place", "Cửa hàng / địa chỉ giao");
        add("order.items", "Sản phẩm");
        add("order.frames", "Số khung");
        add("order.subtotal", "Tiền hàng (₫)");
        add("order.shipping", "Phí giao xe (₫)");
        add("order.registration", "Phí đăng ký (₫)");
        add("order.discount", "Giảm giá (₫)");
        add("order.total", "Tổng tiền (₫)");
        add("order.method", "Phương thức thanh toán");
        add("order.paymentStatus", "Trạng thái thanh toán");
        add("order.cancelReason", "Lý do hủy");
        add("order.notes", "Ghi chú");
        add("order.none", "Không có đơn hàng nào khớp với bộ lọc này.");
        add("filter.name", "Bộ lọc");
        add("filter.value", "Giá trị");
        add("filter.search", "Tìm kiếm");
        add("filter.status", "Trạng thái đơn");
        add("filter.paymentStatus", "Trạng thái thanh toán");
        add("filter.showroom", "Mã cửa hàng");
        add("filter.rows", "Số đơn trong tệp");
        add("filter.generatedAt", "Xuất lúc");
        add("filter.all", "Tất cả");

        // ---- names of the stored codes ----
        add("OrderStatus.PENDING", "Chờ xử lý");
        add("OrderStatus.CONFIRMED", "Đã xác nhận");
        add("OrderStatus.PROCESSING", "Đang xử lý");
        add("OrderStatus.READY_FOR_PICKUP", "Chờ lấy");
        add("OrderStatus.SHIPPED", "Đang giao");
        add("OrderStatus.DELIVERED", "Đã giao");
        add("OrderStatus.CANCELLED", "Đã hủy");
        add("PaymentMethod.PAY_LATER", "Thanh toán sau");
        add("PaymentMethod.VNPAY", "VNPay");
        add("PaymentMethod.INSTALLMENT", "Trả góp");
        add("PaymentStatus.PENDING", "Chờ thanh toán");
        add("PaymentStatus.AUTHORIZED", "Đã ủy quyền");
        add("PaymentStatus.PAID", "Đã thanh toán");
        add("PaymentStatus.FAILED", "Thất bại");
        add("PaymentStatus.CANCELLED", "Đã hủy");
        add("PaymentStatus.REFUNDED", "Đã hoàn tiền");
    }

    private ReportText() {
    }

    public static String t(String key) {
        String[] pair = TEXT.get(key);
        if (pair == null) {
            return key;
        }
        return pair[0];
    }

    /** The name of a stored code such as an order status; an unknown code is shown as it is, a missing one as a dash. */
    public static String code(String type, String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String[] pair = TEXT.get(type + "." + value);
        if (pair == null) {
            return value;
        }
        return pair[0];
    }

    /** Every key, for the test that checks both languages are filled in. */
    static Map<String, String[]> all() {
        return TEXT;
    }
}
