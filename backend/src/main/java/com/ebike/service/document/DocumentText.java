package com.ebike.service.document;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V27: the fixed words printed on the sales receipt, the warranty card and the delivery email, in Vietnamese or English.
 * Anything other than "en" is Vietnamese, the shop's base language. The page templates read them as {@code t['key']}.
 */
public final class DocumentText {

    private static final Map<String, String[]> TEXT = new LinkedHashMap<>();

    private static void add(String key, String vietnamese, String english) {
        TEXT.put(key, new String[] {vietnamese, english});
    }

    static {
        // ---- sales receipt ----
        add("receipt.title", "PHIẾU BÁN HÀNG", "SALES RECEIPT");
        add("receipt.titleProvisional", "PHIẾU TẠM TÍNH (XE CHƯA BÀN GIAO)", "PROVISIONAL RECEIPT (BIKE NOT HANDED OVER YET)");
        add("receipt.notInvoice", "Phiếu này là chứng từ bán hàng của cửa hàng, không phải hóa đơn điện tử theo quy định của pháp luật về thuế.",
            "This receipt is the shop's own sales record. It is not an electronic invoice under tax law.");
        add("receipt.provisionalNote", "Đơn hàng chưa hoàn tất: số liệu có thể thay đổi cho đến khi xe được bàn giao.",
            "The order is not complete yet: the figures can change until the bike is handed over.");
        add("receipt.number", "Số phiếu", "Receipt no.");
        add("receipt.orderNumber", "Mã đơn hàng", "Order number");
        add("receipt.orderedAt", "Ngày đặt", "Order date");
        add("receipt.deliveredAt", "Ngày bàn giao", "Handed over");
        add("receipt.status", "Trạng thái đơn", "Order status");
        add("receipt.issuedAt", "Ngày lập phiếu", "Issued");
        add("receipt.seller", "Đơn vị bán hàng", "Seller");
        add("receipt.taxCode", "Mã số thuế", "Tax code");
        add("receipt.address", "Địa chỉ", "Address");
        add("receipt.phone", "Điện thoại", "Phone");
        add("receipt.email", "Email", "Email");
        add("receipt.customer", "Khách hàng", "Customer");
        add("receipt.name", "Họ tên", "Name");
        add("receipt.handover", "Nhận xe", "Handover");
        add("receipt.pickup", "Nhận tại cửa hàng", "Pick up at the showroom");
        add("receipt.delivery", "Giao tại nhà", "Home delivery");
        add("receipt.tracking", "Mã vận đơn", "Tracking number");
        add("receipt.colNo", "STT", "No.");
        add("receipt.colItem", "Sản phẩm", "Item");
        add("receipt.colColour", "Màu", "Colour");
        add("receipt.colQty", "SL", "Qty");
        add("receipt.colUnitPrice", "Đơn giá", "Unit price");
        add("receipt.colAmount", "Thành tiền", "Amount");
        add("receipt.frame", "Số khung", "Frame no.");
        add("receipt.engine", "Số máy", "Engine no.");
        add("receipt.subtotal", "Tiền hàng", "Subtotal");
        add("receipt.registration", "Dịch vụ đăng ký xe", "Registration service");
        add("receipt.shipping", "Phí giao hàng", "Delivery fee");
        add("receipt.discount", "Giảm giá", "Discount");
        add("receipt.total", "Tổng cộng", "Total");
        add("receipt.inWords", "Bằng chữ", "In words");
        add("receipt.payment", "Thanh toán", "Payment");
        add("receipt.method", "Hình thức", "Method");
        add("receipt.paymentStatus", "Tình trạng", "Status");
        add("receipt.paidAt", "Đã thanh toán lúc", "Paid at");
        add("receipt.reference", "Mã giao dịch", "Transaction reference");
        add("receipt.noPayment", "Chưa có giao dịch", "No transaction yet");
        add("receipt.installment", "Trả góp (mô phỏng)", "Instalment (simulated)");
        add("receipt.instDown", "Trả trước", "Down payment");
        add("receipt.instFinanced", "Số tiền trả góp", "Financed amount");
        add("receipt.instTerm", "Kỳ hạn", "Term");
        add("receipt.instMonthly", "Trả mỗi tháng", "Monthly payment");
        add("receipt.instRate", "Lãi phẳng", "Flat rate");
        add("receipt.perYear", "{0}%/năm", "{0}%/year");
        add("receipt.instTotal", "Tổng phải trả", "Total payable");
        add("receipt.instNote", "Kế hoạch trả góp chỉ là mô phỏng, không có hợp đồng tín dụng thật.",
            "The instalment plan is a simulation only; there is no real credit contract.");
        add("receipt.notes", "Ghi chú của khách", "Customer note");
        add("receipt.thanks", "Cảm ơn quý khách đã mua xe tại cửa hàng.", "Thank you for buying from us.");
        add("receipt.signCustomer", "Khách hàng (ký, ghi rõ họ tên)", "Customer (signature and name)");
        add("receipt.signSeller", "Đại diện cửa hàng (ký, đóng dấu)", "Shop representative (signature and stamp)");
        add("receipt.page", "Trang", "Page");
        add("receipt.of", "/", "/");

        // ---- warranty card ----
        add("card.title", "PHIẾU BẢO HÀNH", "WARRANTY CARD");
        add("card.number", "Số phiếu", "Card no.");
        add("card.owner", "Chủ xe", "Owner");
        add("card.vehicle", "Thông tin xe", "Vehicle");
        add("card.model", "Dòng xe", "Model");
        add("card.colour", "Màu", "Colour");
        add("card.frame", "Số khung", "Frame no.");
        add("card.engine", "Số máy", "Engine no.");
        add("card.orderNumber", "Mã đơn hàng", "Order number");
        add("card.handoverPlace", "Nơi nhận xe", "Handover place");
        add("card.homeDelivery", "Giao tại nhà", "Home delivery");
        add("card.warranty", "Thời hạn bảo hành", "Warranty period");
        add("card.startsAt", "Bắt đầu từ", "Starts on");
        add("card.endsAt", "Hết hạn ngày", "Ends on");
        add("card.months", "{0} tháng", "{0} months");
        add("card.month1", "1 tháng", "1 month");
        add("card.noPeriod", "Chưa có thời hạn bảo hành trên hồ sơ, vui lòng liên hệ cửa hàng.", "No warranty period is on record yet; please contact the shop.");
        add("card.schedule", "Lịch bảo dưỡng định kỳ", "Regular service schedule");
        add("card.scheduleAfter", "Sau {0} kể từ ngày bàn giao", "{0} after hand-over");
        add("card.scheduleDate", "Dự kiến từ ngày", "From about");
        add("card.terms", "Điều kiện bảo hành", "Warranty terms");
        add("card.term1", "Cửa hàng sửa chữa hoặc thay thế miễn phí các lỗi do nhà sản xuất (khung, động cơ, bộ điều khiển, pin) trong thời hạn ghi trên phiếu, tính từ ngày bàn giao xe.",
            "The shop repairs or replaces, free of charge, defects caused by manufacturing (frame, motor, controller, battery) within the period on this card, counted from the hand-over date.");
        add("card.term2", "Khi bảo hành, vui lòng mang xe đến cửa hàng cùng phiếu này; xe được đối chiếu theo số khung ghi trên phiếu.",
            "For warranty service please bring the bike and this card to the shop; the bike is checked against the frame number printed here.");
        add("card.term3", "Không bảo hành hư hỏng do va chạm, ngập nước quá mức chịu đựng, tự ý tháo sửa hoặc thay linh kiện không chính hãng, chở quá tải hay dùng sai mục đích.",
            "The warranty does not cover damage from collisions, water beyond the rated level, unauthorised repairs or non-genuine parts, overloading or misuse.");
        add("card.term4", "Phụ tùng hao mòn (lốp, má phanh, bóng đèn, xích...) không thuộc phạm vi bảo hành.",
            "Wear parts (tyres, brake pads, bulbs, chain...) are not covered.");
        add("card.term5", "Nên mang xe đi bảo dưỡng đúng lịch ở trên để xe luôn trong tình trạng tốt.",
            "Please bring the bike for service on the schedule above to keep it in good condition.");
        add("card.term6", "Sau khi hết hạn, cửa hàng vẫn nhận sửa chữa và tính phí theo bảng giá.",
            "After the period ends the shop still repairs the bike for a fee.");
        add("card.lookup", "Tra cứu bảo hành theo số khung tại", "Look up a warranty by frame number at");
        add("card.sample", "Phiếu này do hệ thống lập tự động theo hồ sơ bán hàng.", "This card was produced automatically from the sales record.");
        add("card.signSeller", "Đại diện cửa hàng (ký, đóng dấu)", "Shop representative (signature and stamp)");

        // ---- order status / payment words ----
        add("status.PENDING", "Chờ xử lý", "Pending");
        add("status.CONFIRMED", "Đã xác nhận", "Confirmed");
        add("status.PROCESSING", "Đang xử lý", "Processing");
        add("status.READY_FOR_PICKUP", "Chờ lấy xe", "Ready for pickup");
        add("status.SHIPPED", "Đang giao", "Shipping");
        add("status.DELIVERED", "Đã giao", "Delivered");
        add("status.CANCELLED", "Đã hủy", "Cancelled");
        add("method.PAY_LATER", "Thanh toán khi nhận xe", "Pay on receipt");
        add("method.VNPAY", "VNPay", "VNPay");
        add("method.INSTALLMENT", "Trả góp (mô phỏng)", "Instalment (simulated)");
        add("pay.PENDING", "Chờ thanh toán", "Awaiting payment");
        add("pay.AUTHORIZED", "Đã ủy quyền", "Authorised");
        add("pay.PAID", "Đã thanh toán", "Paid");
        add("pay.FAILED", "Thất bại", "Failed");
        add("pay.CANCELLED", "Đã hủy", "Cancelled");
        add("pay.REFUNDED", "Đã hoàn tiền", "Refunded");
        add("pay.DOWN_PAID", "Đã trả trước", "Down payment paid");
        add("pay.DOWN_PENDING", "Chờ khoản trả trước", "Awaiting the down payment");

        // ---- API errors (the controller words them in the request's language) ----
        add("error.notFound", "Không tìm thấy đơn hàng.", "Order not found.");
        add("error.cancelled", "Đơn hàng đã hủy nên không có phiếu bán hàng.", "A cancelled order has no sales receipt.");
        add("error.notDelivered", "Phiếu bảo hành chỉ có sau khi xe đã được bàn giao.", "The warranty card is available only after the bike was handed over.");
        add("error.noNumbers", "Đơn này chưa có số khung xe nên chưa lập được phiếu bảo hành. Vui lòng liên hệ cửa hàng.",
            "This order has no frame numbers on record, so no warranty card can be made. Please contact the shop.");
        add("error.unitNotFound", "Không tìm thấy xe này trong đơn hàng.", "This vehicle is not part of the order.");
        add("error.render", "Không tạo được tệp PDF. Vui lòng thử lại sau.", "The PDF could not be made. Please try again later.");
        add("error.resendMailOff", "Chưa bật email hoặc chưa cấu hình máy chủ thư nên không gửi được.", "Email is turned off or no mail server is set up, so nothing can be sent.");
        add("error.resendNothing", "Không có gì để gửi: đơn chưa được bàn giao, chưa có địa chỉ email hoặc tài khoản đã bị xóa.",
            "Nothing to send: the order was not handed over yet, has no email address, or its account was erased.");
        add("error.resendFailed", "Máy chủ thư không nhận email. Vui lòng thử lại sau.", "The mail server did not accept the email. Please try again later.");
        add("resend.done", "Đã gửi email phiếu tới {0}.", "The receipt email was sent to {0}.");

        // ---- delivery email ----
        add("mail.subject", "{0}: phiếu bán hàng và phiếu bảo hành cho đơn {1}", "{0}: your sales receipt and warranty card for order {1}");
        add("mail.subjectReceiptOnly", "{0}: phiếu bán hàng cho đơn {1}", "{0}: your sales receipt for order {1}");
        add("mail.greeting", "Xin chào {0},", "Hello {0},");
        add("mail.body", "Đơn hàng {0} đã được bàn giao. Cảm ơn bạn đã mua xe tại {1}.",
            "Order {0} has been handed over. Thank you for buying from {1}.");
        add("mail.attached", "Đính kèm email này:", "Attached to this email:");
        add("mail.receipt", "- Phiếu bán hàng (PDF)", "- Sales receipt (PDF)");
        add("mail.card", "- Phiếu bảo hành cho từng xe (PDF)", "- Warranty card for each bike (PDF)");
        add("mail.notInvoice", "Lưu ý: phiếu bán hàng không phải hóa đơn điện tử theo quy định của pháp luật về thuế.",
            "Note: the sales receipt is not an electronic invoice under tax law.");
        add("mail.signIn", "Bạn cũng có thể tải lại các phiếu này trong chi tiết đơn hàng khi đăng nhập: {0}",
            "You can also download these again from the order page after signing in: {0}");
        add("mail.reason", "Bạn nhận email này vì đã đặt hàng tại {0}.", "You are receiving this email because you placed an order at {0}.");
        add("mail.guestName", "quý khách", "customer");
        add("name.deleted", "Tài khoản đã xóa", "Deleted account");
    }

    private DocumentText() {
    }

    /** One word or sentence in the language; an unknown key comes back as the key itself so a typo shows on the page instead of failing the whole file. */
    public static String text(String language, String key) {
        String[] pair = TEXT.get(key);
        if (pair == null) {
            return key;
        }
        return "en".equals(DocumentFormat.language(language)) ? pair[1] : pair[0];
    }

    /** A sentence with {0}, {1} holes filled. */
    public static String text(String language, String key, Object... values) {
        // one pass over the pattern: a value that itself contains "{1}" is printed as it is, never filled in again
        java.util.regex.Matcher holes = HOLE.matcher(text(language, key));
        StringBuilder result = new StringBuilder();
        while (holes.find()) {
            int index = Integer.parseInt(holes.group(1));
            holes.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(index < values.length ? String.valueOf(values[index]) : holes.group()));
        }
        holes.appendTail(result);
        return result.toString();
    }

    private static final java.util.regex.Pattern HOLE = java.util.regex.Pattern.compile("\\{(\\d{1,3})}");

    /** Every word in one language, for the page templates ({@code t['receipt.title']}). */
    public static Map<String, String> labels(String language) {
        boolean english = "en".equals(DocumentFormat.language(language));
        Map<String, String> labels = new HashMap<>();
        TEXT.forEach((key, pair) -> labels.put(key, english ? pair[1] : pair[0]));
        return labels;
    }

    /** The keys, so a test can check that no text is missing in either language. */
    static Map<String, String[]> all() {
        return TEXT;
    }
}
