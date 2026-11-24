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

    private static void add(String key, String vietnamese) {
        TEXT.put(key, new String[] {vietnamese});
    }

    static {
        // ---- sales receipt ----
        add("receipt.title", "PHIẾU BÁN HÀNG");
        add("receipt.titleProvisional", "PHIẾU TẠM TÍNH (XE CHƯA BÀN GIAO)");
        add("receipt.notInvoice", "Phiếu này là chứng từ bán hàng của cửa hàng, không phải hóa đơn điện tử theo quy định của pháp luật về thuế.");
        add("receipt.provisionalNote", "Đơn hàng chưa hoàn tất: số liệu có thể thay đổi cho đến khi xe được bàn giao.");
        add("receipt.number", "Số phiếu");
        add("receipt.orderNumber", "Mã đơn hàng");
        add("receipt.orderedAt", "Ngày đặt");
        add("receipt.deliveredAt", "Ngày bàn giao");
        add("receipt.status", "Trạng thái đơn");
        add("receipt.issuedAt", "Ngày lập phiếu");
        add("receipt.taxCode", "Mã số thuế");
        add("receipt.address", "Địa chỉ");
        add("receipt.phone", "Điện thoại");
        add("receipt.email", "Email");
        add("receipt.customer", "Khách hàng");
        add("receipt.name", "Họ tên");
        add("receipt.handover", "Nhận xe");
        add("receipt.pickup", "Nhận tại cửa hàng");
        add("receipt.delivery", "Giao tại nhà");
        add("receipt.tracking", "Mã vận đơn");
        add("receipt.colNo", "STT");
        add("receipt.colItem", "Sản phẩm");
        add("receipt.colColour", "Màu");
        add("receipt.colQty", "SL");
        add("receipt.colUnitPrice", "Đơn giá");
        add("receipt.colAmount", "Thành tiền");
        add("receipt.frame", "Số khung");
        add("receipt.engine", "Số máy");
        add("receipt.subtotal", "Tiền hàng");
        add("receipt.registration", "Dịch vụ đăng ký xe");
        add("receipt.shipping", "Phí giao hàng");
        add("receipt.discount", "Giảm giá");
        add("receipt.total", "Tổng cộng");
        add("receipt.inWords", "Bằng chữ");
        add("receipt.payment", "Thanh toán");
        add("receipt.method", "Hình thức");
        add("receipt.paymentStatus", "Tình trạng");
        add("receipt.paidAt", "Đã thanh toán lúc");
        add("receipt.reference", "Mã giao dịch");
        add("receipt.noPayment", "Chưa có giao dịch");
        add("receipt.instDown", "Trả trước");
        add("receipt.instFinanced", "Số tiền trả góp");
        add("receipt.instTerm", "Kỳ hạn");
        add("receipt.instMonthly", "Trả mỗi tháng");
        add("receipt.instRate", "Lãi phẳng");
        add("receipt.perYear", "{0}%/năm");
        add("receipt.instTotal", "Tổng phải trả");
        add("receipt.instNote", "Kế hoạch trả góp chỉ là mô phỏng, không có hợp đồng tín dụng thật.");
        add("receipt.notes", "Ghi chú của khách");
        add("receipt.thanks", "Cảm ơn quý khách đã mua xe tại cửa hàng.");
        add("receipt.signCustomer", "Khách hàng (ký, ghi rõ họ tên)");
        add("receipt.signSeller", "Đại diện cửa hàng (ký, đóng dấu)");

        // ---- warranty card ----
        add("card.title", "PHIẾU BẢO HÀNH");
        add("card.number", "Số phiếu");
        add("card.owner", "Chủ xe");
        add("card.vehicle", "Thông tin xe");
        add("card.model", "Dòng xe");
        add("card.colour", "Màu");
        add("card.frame", "Số khung");
        add("card.engine", "Số máy");
        add("card.orderNumber", "Mã đơn hàng");
        add("card.handoverPlace", "Nơi nhận xe");
        add("card.homeDelivery", "Giao tại nhà");
        add("card.warranty", "Thời hạn bảo hành");
        add("card.startsAt", "Bắt đầu từ");
        add("card.endsAt", "Hết hạn ngày");
        add("card.months", "{0} tháng");
        add("card.month1", "1 tháng");
        add("card.noPeriod", "Chưa có thời hạn bảo hành trên hồ sơ, vui lòng liên hệ cửa hàng.");
        add("card.schedule", "Lịch bảo dưỡng định kỳ");
        add("card.scheduleAfter", "Sau {0} kể từ ngày bàn giao");
        add("card.scheduleDate", "Dự kiến từ ngày");
        add("card.terms", "Điều kiện bảo hành");
        add("card.term1", "Cửa hàng sửa chữa hoặc thay thế miễn phí các lỗi do nhà sản xuất (khung, động cơ, bộ điều khiển, pin) trong thời hạn ghi trên phiếu, tính từ ngày bàn giao xe.");
        add("card.term2", "Khi bảo hành, vui lòng mang xe đến cửa hàng cùng phiếu này; xe được đối chiếu theo số khung ghi trên phiếu.");
        add("card.term3", "Không bảo hành hư hỏng do va chạm, ngập nước quá mức chịu đựng, tự ý tháo sửa hoặc thay linh kiện không chính hãng, chở quá tải hay dùng sai mục đích.");
        add("card.term4", "Phụ tùng hao mòn (lốp, má phanh, bóng đèn, xích...) không thuộc phạm vi bảo hành.");
        add("card.term5", "Nên mang xe đi bảo dưỡng đúng lịch ở trên để xe luôn trong tình trạng tốt.");
        add("card.term6", "Sau khi hết hạn, cửa hàng vẫn nhận sửa chữa và tính phí theo bảng giá.");
        add("card.lookup", "Tra cứu bảo hành theo số khung tại");
        add("card.sample", "Phiếu này do hệ thống lập tự động theo hồ sơ bán hàng.");
        add("card.signSeller", "Đại diện cửa hàng (ký, đóng dấu)");

        // ---- order status / payment words ----
        add("status.PENDING", "Chờ xử lý");
        add("status.CONFIRMED", "Đã xác nhận");
        add("status.PROCESSING", "Đang xử lý");
        add("status.READY_FOR_PICKUP", "Chờ lấy xe");
        add("status.SHIPPED", "Đang giao");
        add("status.DELIVERED", "Đã giao");
        add("status.CANCELLED", "Đã hủy");
        add("method.PAY_LATER", "Thanh toán khi nhận xe");
        add("method.VNPAY", "VNPay");
        add("method.INSTALLMENT", "Trả góp (mô phỏng)");
        add("pay.PENDING", "Chờ thanh toán");
        add("pay.AUTHORIZED", "Đã ủy quyền");
        add("pay.PAID", "Đã thanh toán");
        add("pay.FAILED", "Thất bại");
        add("pay.CANCELLED", "Đã hủy");
        add("pay.REFUNDED", "Đã hoàn tiền");
        add("pay.DOWN_PAID", "Đã trả trước");
        add("pay.DOWN_PENDING", "Chờ khoản trả trước");

        // ---- API errors (the controller words them in the request's language) ----
        add("error.notFound", "Không tìm thấy đơn hàng.");
        add("error.cancelled", "Đơn hàng đã hủy nên không có phiếu bán hàng.");
        add("error.notDelivered", "Phiếu bảo hành chỉ có sau khi xe đã được bàn giao.");
        add("error.noNumbers", "Đơn này chưa có số khung xe nên chưa lập được phiếu bảo hành. Vui lòng liên hệ cửa hàng.");
        add("error.unitNotFound", "Không tìm thấy xe này trong đơn hàng.");
        add("error.render", "Không tạo được tệp PDF. Vui lòng thử lại sau.");
        add("error.resendMailOff", "Chưa bật email hoặc chưa cấu hình máy chủ thư nên không gửi được.");
        add("error.resendNothing", "Không có gì để gửi: đơn chưa được bàn giao, chưa có địa chỉ email hoặc tài khoản đã bị xóa.");
        add("error.resendFailed", "Máy chủ thư không nhận email. Vui lòng thử lại sau.");
        add("resend.done", "Đã gửi email phiếu tới {0}.");

        // ---- delivery email ----
        add("mail.subject", "{0}: phiếu bán hàng và phiếu bảo hành cho đơn {1}");
        add("mail.subjectReceiptOnly", "{0}: phiếu bán hàng cho đơn {1}");
        add("mail.greeting", "Xin chào {0},");
        add("mail.body", "Đơn hàng {0} đã được bàn giao. Cảm ơn bạn đã mua xe tại {1}.");
        add("mail.attached", "Đính kèm email này:");
        add("mail.receipt", "- Phiếu bán hàng (PDF)");
        add("mail.card", "- Phiếu bảo hành cho từng xe (PDF)");
        add("mail.notInvoice", "Lưu ý: phiếu bán hàng không phải hóa đơn điện tử theo quy định của pháp luật về thuế.");
        add("mail.signIn", "Bạn cũng có thể tải lại các phiếu này trong chi tiết đơn hàng khi đăng nhập: {0}");
        add("mail.reason", "Bạn nhận email này vì đã đặt hàng tại {0}.");
        add("mail.guestName", "quý khách");
        add("name.deleted", "Tài khoản đã xóa");
    }

    private DocumentText() {
    }

    /** One word or sentence in the language; an unknown key comes back as the key itself so a typo shows on the page instead of failing the whole file. */
    public static String text(String key) {
        String[] pair = TEXT.get(key);
        if (pair == null) {
            return key;
        }
        return pair[0];
    }

    /** A sentence with {0}, {1} holes filled. */
    public static String text(String key, Object... values) {
        // one pass over the pattern: a value that itself contains "{1}" is printed as it is, never filled in again
        java.util.regex.Matcher holes = HOLE.matcher(text(key));
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
    public static Map<String, String> labels() {
        Map<String, String> labels = new HashMap<>();
        TEXT.forEach((key, pair) -> labels.put(key, pair[0]));
        return labels;
    }

    /** The keys, so a test can check that no text is missing in either language. */
    static Map<String, String[]> all() {
        return TEXT;
    }
}
