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

    private static void add(String key, String vietnamese, String english) {
        TEXT.put(key, new String[] {vietnamese, english});
    }

    static {
        // ---- revenue report ----
        add("sheet.summary", "Tổng quan", "Summary");
        add("sheet.breakdown", "Doanh thu theo kỳ", "Revenue by period");
        add("sheet.top", "Xe bán chạy", "Best sellers");
        add("sheet.slow", "Xe bán chậm", "Slow sellers");
        add("sheet.orders", "Đơn hàng", "Orders");
        add("sheet.filters", "Bộ lọc", "Filters");
        add("summary.title", "Báo cáo doanh thu KINETIC", "KINETIC revenue report");
        add("summary.groupBy", "Gộp theo", "Grouped by");
        add("summary.from", "Từ ngày", "From");
        add("summary.to", "Đến ngày", "To");
        add("summary.generatedAt", "Xuất lúc", "Exported at");
        add("summary.placedOrders", "Đơn đã đặt trong kỳ", "Orders placed in the period");
        add("summary.totalOrders", "Đơn không bị hủy", "Orders not cancelled");
        add("summary.successfulOrders", "Đơn đã giao", "Orders delivered");
        add("summary.cancelledOrders", "Đơn bị hủy", "Orders cancelled");
        add("summary.cancellationRate", "Tỉ lệ hủy", "Cancellation rate");
        add("summary.totalRevenue", "Doanh thu (tiền đã thu trong kỳ)", "Revenue (money received in the period)");
        add("summary.note", "Doanh thu tính theo ngày thu tiền; số đơn tính theo ngày đặt. Đơn bị hủy không có trong doanh thu và xe bán chạy.",
            "Revenue counts money by the day it was received; order counts use the day the order was placed. Cancelled orders are left out of revenue and best sellers.");
        add("period.DAY", "Ngày", "Day");
        add("period.MONTH", "Tháng", "Month");
        add("period.QUARTER", "Quý", "Quarter");
        add("period.YEAR", "Năm", "Year");
        add("col.period", "Kỳ", "Period");
        add("col.orderCount", "Số đơn", "Orders");
        add("col.revenue", "Doanh thu (₫)", "Revenue (VND)");
        add("col.rank", "Hạng", "Rank");
        add("col.product", "Sản phẩm", "Product");
        add("col.quantity", "Số xe đã bán", "Bikes sold");
        add("row.total", "Tổng cộng", "Total");
        add("empty.rows", "Chưa có dữ liệu trong kỳ này.", "No data in this period.");

        // ---- order list ----
        add("order.number", "Mã đơn", "Order number");
        add("order.createdAt", "Ngày đặt", "Placed at");
        add("order.status", "Trạng thái đơn", "Order status");
        add("order.customerType", "Loại khách", "Customer type");
        add("order.customerAccount", "Có tài khoản", "Account");
        add("order.customerGuest", "Khách vãng lai", "Guest");
        add("order.recipient", "Người nhận", "Recipient");
        add("order.email", "Email", "Email");
        add("order.phone", "Điện thoại", "Phone");
        add("order.handover", "Hình thức nhận xe", "Handover");
        add("order.pickup", "Nhận tại cửa hàng", "Store pickup");
        add("order.delivery", "Giao tại nhà", "Home delivery");
        add("order.place", "Cửa hàng / địa chỉ giao", "Showroom / delivery address");
        add("order.items", "Sản phẩm", "Items");
        add("order.frames", "Số khung", "Frame numbers");
        add("order.subtotal", "Tiền hàng (₫)", "Subtotal (VND)");
        add("order.shipping", "Phí giao xe (₫)", "Delivery fee (VND)");
        add("order.registration", "Phí đăng ký (₫)", "Registration fee (VND)");
        add("order.discount", "Giảm giá (₫)", "Discount (VND)");
        add("order.total", "Tổng tiền (₫)", "Total (VND)");
        add("order.method", "Phương thức thanh toán", "Payment method");
        add("order.paymentStatus", "Trạng thái thanh toán", "Payment status");
        add("order.cancelReason", "Lý do hủy", "Cancellation reason");
        add("order.notes", "Ghi chú", "Notes");
        add("order.none", "Không có đơn hàng nào khớp với bộ lọc này.", "No order matches these filters.");
        add("filter.name", "Bộ lọc", "Filter");
        add("filter.value", "Giá trị", "Value");
        add("filter.search", "Tìm kiếm", "Search");
        add("filter.status", "Trạng thái đơn", "Order status");
        add("filter.paymentStatus", "Trạng thái thanh toán", "Payment status");
        add("filter.showroom", "Mã cửa hàng", "Showroom id");
        add("filter.rows", "Số đơn trong tệp", "Orders in the file");
        add("filter.generatedAt", "Xuất lúc", "Exported at");
        add("filter.all", "Tất cả", "All");

        // ---- names of the stored codes ----
        add("OrderStatus.PENDING", "Chờ xử lý", "Pending");
        add("OrderStatus.CONFIRMED", "Đã xác nhận", "Confirmed");
        add("OrderStatus.PROCESSING", "Đang xử lý", "Processing");
        add("OrderStatus.READY_FOR_PICKUP", "Chờ lấy", "Ready for pickup");
        add("OrderStatus.SHIPPED", "Đang giao", "Shipped");
        add("OrderStatus.DELIVERED", "Đã giao", "Delivered");
        add("OrderStatus.CANCELLED", "Đã hủy", "Cancelled");
        add("PaymentMethod.PAY_LATER", "Thanh toán sau", "Pay later");
        add("PaymentMethod.VNPAY", "VNPay", "VNPay");
        add("PaymentMethod.INSTALLMENT", "Trả góp", "Instalment");
        add("PaymentStatus.PENDING", "Chờ thanh toán", "Pending");
        add("PaymentStatus.AUTHORIZED", "Đã ủy quyền", "Authorised");
        add("PaymentStatus.PAID", "Đã thanh toán", "Paid");
        add("PaymentStatus.FAILED", "Thất bại", "Failed");
        add("PaymentStatus.CANCELLED", "Đã hủy", "Cancelled");
        add("PaymentStatus.REFUNDED", "Đã hoàn tiền", "Refunded");
    }

    private ReportText() {
    }

    /** "en" or "vi": anything that does not start with "en" is Vietnamese. */
    public static String language(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    public static String t(String key, String language) {
        String[] pair = TEXT.get(key);
        if (pair == null) {
            return key;
        }
        return "en".equals(language(language)) ? pair[1] : pair[0];
    }

    /** The name of a stored code such as an order status; an unknown code is shown as it is, a missing one as a dash. */
    public static String code(String type, String value, String language) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String[] pair = TEXT.get(type + "." + value);
        if (pair == null) {
            return value;
        }
        return "en".equals(language(language)) ? pair[1] : pair[0];
    }

    /** Every key, for the test that checks both languages are filled in. */
    static Map<String, String[]> all() {
        return TEXT;
    }
}
