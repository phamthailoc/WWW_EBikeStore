package com.ebike.service.document;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.Showroom;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.warranty.MaintenanceMilestones;
import com.ebike.service.warranty.WarrantyRules;
import com.ebike.service.warranty.WarrantyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V27: turns an order into the plain values a page template prints. Everything is already formatted text (amounts, dates, labels), so the
 * template only places it, and a test can read the numbers without opening a PDF. Maps are used because the template engine reads a map by
 * its keys on every version, while a record's accessors are not properties to it.
 */
public final class DocumentModels {

    /** What fits on one A4 card: the servicing dates (two to a row) and the length of the showroom's name and address. */
    static final int MAX_MILESTONES = 12;
    static final int MAX_PLACE = 160;

    /** The receipt says "Provisional" until the bike was handed over. */
    public static boolean isProvisional(Order order) {
        return order.getStatus() != OrderStatus.DELIVERED;
    }

    /** A provisional receipt has its own number (TT-), so it is never mistaken for the final receipt (PBH-) of the same order, which says something else. */
    public static String receiptNumber(Order order) {
        return (isProvisional(order) ? "TT-" : "PBH-") + order.getOrderNumber();
    }

    /** The data of the sales receipt of one order. {@code installment} is the order's instalment application, or null. */
    public static Map<String, Object> receipt(Order order, InstallmentApplication installment, StoreProfile store, LocalDateTime issuedAt) {
        boolean provisional = isProvisional(order);
        Shipment shipment = order.getShipment();
        Payment payment = order.getPayment();

        Map<String, Object> model = base(store);
        model.put("title", DocumentText.text(provisional ? "receipt.titleProvisional" : "receipt.title"));
        model.put("provisional", provisional);
        model.put("number", receiptNumber(order));
        model.put("orderNumber", order.getOrderNumber());
        model.put("status", DocumentText.text("status." + order.getStatus().name()));
        model.put("orderedAt", DocumentFormat.dateTime(order.getCreatedAt()));
        model.put("deliveredAt", provisional ? null : DocumentFormat.dateTime(handoverTime(order)));
        model.put("issuedAt", DocumentFormat.dateTime(issuedAt));

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", customerName(order));
        customer.put("phone", dash(shipment == null ? null : shipment.getPhoneNumber()));
        customer.put("email", dash(printedEmail(order)));
        model.put("customer", customer);

        Map<String, Object> handover = new LinkedHashMap<>();
        Showroom showroom = shipment == null ? null : shipment.getPickupShowroom();
        handover.put("method", DocumentText.text(showroom != null ? "receipt.pickup" : "receipt.delivery"));
        handover.put("place", showroom != null ? join(showroom.getName(), showroom.getAddress()) : dash(shipment == null ? null : shipment.getShippingAddress()));
        String tracking = shipment == null ? null : blankToNull(shipment.getTrackingNumber());
        handover.put("tracking", tracking);
        model.put("handover", handover);

        List<Map<String, Object>> lines = new ArrayList<>();
        int number = 1;
        for (OrderItem item : sortedItems(order)) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("no", number++);
            line.put("name", dash(item.getProductName()));
            line.put("colour", dash(colourOf(item)));
            line.put("qty", item.getQuantity() == null ? 0 : item.getQuantity());
            line.put("unitPrice", DocumentFormat.money(item.getUnitPrice()));
            line.put("amount", DocumentFormat.money(item.getLineTotal()));
            List<String> numbers = new ArrayList<>();
            item.getUnits().stream().sorted(Comparator.comparing(OrderItemUnit::getId, Comparator.nullsLast(Comparator.naturalOrder()))).forEach(unit ->
                numbers.add(DocumentText.text("receipt.frame") + " " + xmlSafe(unit.getFrameNumber()) + " · " + DocumentText.text("receipt.engine") + " " + xmlSafe(unit.getEngineNumber())));
            line.put("numbers", numbers);
            lines.add(line);
        }
        model.put("lines", lines);

        List<Map<String, Object>> totals = new ArrayList<>();
        totals.add(row(DocumentText.text("receipt.subtotal"), DocumentFormat.money(order.getSubtotal()), false));
        if (Boolean.TRUE.equals(order.getIncludeRegistrationService())) {
            totals.add(row(DocumentText.text("receipt.registration"), DocumentFormat.money(order.getRegistrationFee()), false));
        }
        if (positive(order.getShippingFee())) {
            totals.add(row(DocumentText.text("receipt.shipping"), DocumentFormat.money(order.getShippingFee()), false));
        }
        if (positive(order.getDiscountAmount())) {
            totals.add(row(DocumentText.text("receipt.discount"), "−" + DocumentFormat.money(order.getDiscountAmount()), false));
        }
        totals.add(row(DocumentText.text("receipt.total"), DocumentFormat.money(order.getTotalAmount()), true));
        model.put("totals", totals);
        model.put("inWords", DocumentFormat.moneyInWords(order.getTotalAmount()));

        model.put("payment", paymentBlock(order, payment, installment));
        model.put("notes", blankToNull(order.getNotes()));
        return model;
    }

    private static Map<String, Object> paymentBlock(Order order, Payment payment, InstallmentApplication installment) {
        // every key is always present: the template engine fails on a missing key of a map, but reads a null one as "nothing to print"
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("status", null);
        block.put("paidAt", null);
        block.put("reference", null);
        block.put("installment", null);
        if (payment == null) {
            // checkout creates no payment row for VNPay until the first payment link is made, so an order without one is a VNPay order still waiting to be paid
            block.put("method", DocumentText.text("method.VNPAY"));
            block.put("status", DocumentText.text("receipt.noPayment"));
            return block;
        }
        PaymentMethod method = payment.getPaymentMethod();
        block.put("method", DocumentText.text("method." + method.name()));
        boolean instalment = method == PaymentMethod.INSTALLMENT;
        PaymentStatus status = payment.getPaymentStatus();
        String statusKey = instalment && status == PaymentStatus.PAID ? "pay.DOWN_PAID"
            : instalment && status == PaymentStatus.PENDING ? "pay.DOWN_PENDING"
            : "pay." + status.name();
        block.put("status", DocumentText.text(statusKey));
        block.put("paidAt", status == PaymentStatus.PAID && payment.getPaidAt() != null ? DocumentFormat.dateTime(payment.getPaidAt()) : null);
        block.put("reference", blankToNull(payment.getTransactionReference()));
        if (instalment && installment != null) {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row(DocumentText.text("receipt.instDown"), DocumentFormat.money(installment.getDownPaymentAmount()) + " (" + installment.getDownPaymentPercent() + "%)", false));
            rows.add(row(DocumentText.text("receipt.instFinanced"), DocumentFormat.money(installment.getFinancedAmount()), false));
            rows.add(row(DocumentText.text("receipt.instTerm"), DocumentText.text(installment.getTermMonths() != null && installment.getTermMonths() == 1 ? "card.month1" : "card.months", installment.getTermMonths()), false));
            rows.add(row(DocumentText.text("receipt.instRate"), installment.getAnnualRatePercent() == null ? "—" : DocumentText.text("receipt.perYear", installment.getAnnualRatePercent().stripTrailingZeros().toPlainString()), false));
            rows.add(row(DocumentText.text("receipt.instMonthly"), DocumentFormat.money(installment.getMonthlyPayment()), false));
            rows.add(row(DocumentText.text("receipt.instTotal"), DocumentFormat.money(installment.getTotalPayable()), false));
            block.put("installment", rows);
        }
        return block;
    }

    /**
     * The cards of the delivered vehicles of an order: one entry per unit (frame and engine number). {@code intervals} are the maintenance
     * milestones (V20); {@code units} are the vehicles to print, already chosen by the caller.
     */
    public static Map<String, Object> warrantyCards(Order order, List<OrderItemUnit> units, StoreProfile store, List<Period> intervals, LocalDateTime issuedAt) {
        Map<String, Object> model = base(store);
        model.put("title", DocumentText.text("card.title"));
        model.put("issuedAt", DocumentFormat.dateTime(issuedAt));
        model.put("lookupUrl", store.warrantyLookupUrl().isEmpty() ? null : store.warrantyLookupUrl());
        model.put("terms", List.of("card.term1", "card.term2", "card.term3", "card.term4", "card.term5", "card.term6").stream().map(key -> DocumentText.text(key)).toList());

        Shipment shipment = order.getShipment();
        Showroom showroom = shipment == null ? null : shipment.getPickupShowroom();
        LocalDateTime starts = handoverTime(order);

        List<Map<String, Object>> cards = new ArrayList<>();
        for (OrderItemUnit unit : units) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("number", "BH-" + xmlSafe(unit.getFrameNumber()));
            card.put("orderNumber", order.getOrderNumber());
            card.put("ownerName", customerName(order));
            card.put("ownerPhone", dash(shipment == null ? null : shipment.getPhoneNumber()));
            card.put("model", dash(unit.getOrderItem().getProductName()));
            card.put("colour", dash(WarrantyService.colourOf(unit.getOrderItem())));
            card.put("frame", xmlSafe(unit.getFrameNumber()));
            card.put("engine", xmlSafe(unit.getEngineNumber()));
            card.put("place", showroom != null ? shorten(join(showroom.getName(), showroom.getAddress()), MAX_PLACE) : DocumentText.text("card.homeDelivery"));
            Integer months = WarrantyService.warrantyMonths(unit);
            LocalDateTime ends = WarrantyRules.endsAt(starts, months);
            boolean hasPeriod = ends != null;
            card.put("hasPeriod", hasPeriod);
            card.put("periodText", hasPeriod ? DocumentText.text(months == 1 ? "card.month1" : "card.months", months) : DocumentText.text("card.noPeriod"));
            card.put("startsAt", DocumentFormat.date(starts));
            card.put("endsAt", hasPeriod ? DocumentFormat.date(ends) : "—");
            List<Map<String, Object>> schedule = new ArrayList<>();
            for (Period interval : intervals.stream().limit(MAX_MILESTONES).toList()) {
                schedule.add(row(
                    DocumentText.text("card.scheduleAfter", MaintenanceMilestones.describe(interval.toString())),
                    DocumentFormat.date(WarrantyRules.dueAt(starts, interval)), false));
            }
            card.put("schedule", schedule);
            // two milestones to a row, and at most MAX_MILESTONES of them, keep the whole card on one page
            List<Map<String, Object>> pairs = new ArrayList<>();
            for (int index = 0; index < schedule.size(); index += 2) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("leftLabel", schedule.get(index).get("label"));
                pair.put("leftValue", DocumentText.text("card.scheduleDate") + " " + schedule.get(index).get("value"));
                boolean hasRight = index + 1 < schedule.size();
                pair.put("rightLabel", hasRight ? schedule.get(index + 1).get("label") : "");
                pair.put("rightValue", hasRight ? DocumentText.text("card.scheduleDate") + " " + schedule.get(index + 1).get("value") : "");
                pairs.add(pair);
            }
            card.put("schedulePairs", pairs);
            cards.add(card);
        }
        model.put("cards", cards);
        return model;
    }

    // ---- shared pieces ----

    private static Map<String, Object> base(StoreProfile store) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("t", DocumentText.labels());
        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("name", store.getName());
        shop.put("legalName", store.getLegalName());
        shop.put("taxCode", store.getTaxCode());
        shop.put("address", store.getAddress());
        shop.put("phone", store.getPhone());
        shop.put("email", store.getEmail());
        List<String> contact = new ArrayList<>();
        if (!store.getPhone().isEmpty()) {
            contact.add(DocumentText.text("receipt.phone") + ": " + store.getPhone());
        }
        if (!store.getEmail().isEmpty()) {
            contact.add(DocumentText.text("receipt.email") + ": " + store.getEmail());
        }
        shop.put("contact", String.join(" · ", contact));
        model.put("store", shop);
        return model;
    }

    private static Map<String, Object> row(String label, String value, boolean strong) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        row.put("strong", strong);
        return row;
    }

    /** When the bike was handed over: the shipment's time, else the time fixed on the order, else the last change (same rule as the warranty book). */
    public static LocalDateTime handoverTime(Order order) {
        LocalDateTime shipped = order.getShipment() == null ? null : order.getShipment().getDeliveredAt();
        return WarrantyRules.startsAt(shipped != null ? shipped : order.getDeliveredAt(), order.getUpdatedAt());
    }

    public static List<OrderItem> sortedItems(Order order) {
        return order.getItems().stream().sorted(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    /** The name on the document: who receives the bike, else the account holder, else a dash. The name printed on an order is part of the sales record and stays after an account is erased. */
    static String customerName(Order order) {
        Shipment shipment = order.getShipment();
        String recipient = shipment == null ? null : blankToNull(shipment.getRecipientName());
        if (recipient != null) {
            return recipient;
        }
        User user = order.getUser();
        if (user != null && AccountErasure.DELETED.equals(user.getAccountStatus())) {
            return DocumentText.text("name.deleted");
        }
        if (user != null) {
            String name = xmlSafe((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return "—";
    }

    /**
     * The email printed on a document. The account of an erased customer is anonymised and its owner asked to be forgotten, so no address is printed:
     * neither the {@code deleted_<id>@deleted.invalid} one nor the one typed at checkout (the name, phone and address of past orders stay, see AccountErasure).
     */
    static String printedEmail(Order order) {
        User user = order.getUser();
        return user != null && AccountErasure.DELETED.equals(user.getAccountStatus()) ? null : contactEmail(order);
    }

    /** The address a receipt is sent to: the one typed at checkout, else the recipient's, else the account's. */
    public static String contactEmail(Order order) {
        String email = blankToNull(order.getCustomerEmail());
        if (email == null && order.getShipment() != null) {
            email = blankToNull(order.getShipment().getRecipientEmail());
        }
        if (email == null && order.getUser() != null) {
            email = blankToNull(order.getUser().getEmail());
        }
        return email;
    }

    private static String colourOf(OrderItem item) {
        return WarrantyService.colourOf(item);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String blankToNull(String value) {
        String text = xmlSafe(value);
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Customer text goes into an XHTML page, and a control character such as a vertical tab is not allowed in XML 1.0, so the page would not
     * parse and the receipt could not be made at all. Such characters are dropped (a tab, a line feed and a carriage return stay).
     */
    static String xmlSafe(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder clean = null;
        for (int index = 0; index < value.length(); ) {
            int code = value.codePointAt(index);
            int width = Character.charCount(code);
            boolean valid = code == 0x9 || code == 0xA || code == 0xD || (code >= 0x20 && code <= 0xD7FF && code != 0xFFFE && code != 0xFFFF)
                || (code >= 0xE000 && code <= 0xFFFD) || (code >= 0x10000 && code <= 0x10FFFF);
            if (!valid && clean == null) {
                clean = new StringBuilder(value.substring(0, index));
            }
            if (valid && clean != null) {
                clean.appendCodePoint(code);
            }
            index += width;
        }
        return clean == null ? value : clean.toString();
    }

    /** Cuts a text that is too long for its place on a card, so a card can never run onto a second page. */
    private static String shorten(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1).trim() + "…";
    }

    private static String dash(String value) {
        String text = blankToNull(value);
        return text == null ? "—" : text;
    }

    private static String join(String first, String second) {
        String a = blankToNull(first);
        String b = blankToNull(second);
        if (a == null) {
            return dash(b);
        }
        return b == null ? a : a + ", " + b;
    }

    private DocumentModels() {
    }
}
