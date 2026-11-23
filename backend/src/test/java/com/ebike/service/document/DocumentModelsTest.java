package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.service.warranty.WarrantyRules;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V27: what the receipt and the warranty card print, read from the model without opening a PDF. */
class DocumentModelsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 8, 0);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void aDeliveredOrderGetsTheSalesReceiptWithTheHandoverDate() {
        Order order = DocumentFixtures.order(1, OrderStatus.DELIVERED, 2, true);

        Map<String, Object> model = DocumentModels.receipt(order, null, DocumentFixtures.store(), "vi", NOW);

        assertEquals("PHIẾU BÁN HÀNG", model.get("title"));
        assertEquals(false, model.get("provisional"));
        assertEquals("PBH-" + order.getOrderNumber(), model.get("number"));
        assertEquals("20/11/2026 15:45", model.get("deliveredAt"));
        assertEquals("23/11/2026 08:00", model.get("issuedAt"));
        assertEquals("Nguyễn Văn An", map(model.get("customer")).get("name"));
        assertEquals("Nhận tại cửa hàng", map(model.get("handover")).get("method"));
    }

    @Test
    void anOrderNotHandedOverYetIsMarkedProvisionalAndShowsNoHandoverDate() {
        Order order = DocumentFixtures.order(2, OrderStatus.PROCESSING, 1, false);

        Map<String, Object> model = DocumentModels.receipt(order, null, DocumentFixtures.store(), "en", NOW);

        assertEquals("PROVISIONAL RECEIPT (BIKE NOT HANDED OVER YET)", model.get("title"));
        assertEquals(true, model.get("provisional"));
        assertEquals("TT-" + order.getOrderNumber(), model.get("number"));
        assertNull(model.get("deliveredAt"));
        assertEquals("Awaiting payment", map(model.get("payment")).get("status"));
    }

    @Test
    void linesCarryTheFrameAndEngineNumbersOfEachBike() {
        Order order = DocumentFixtures.order(3, OrderStatus.DELIVERED, 2, true);

        Map<String, Object> line = list(DocumentModels.receipt(order, null, DocumentFixtures.store(), "vi", NOW).get("lines")).get(0);

        assertEquals("Kinetic Aero X", line.get("name"));
        assertEquals("Xanh rêu", line.get("colour"));
        assertEquals(2, line.get("qty"));
        assertEquals("20.000.000 ₫", line.get("unitPrice"));
        assertEquals("40.000.000 ₫", line.get("amount"));
        assertEquals(List.of("Số khung RLEBK00001 · Số máy EN00001", "Số khung RLEBK00002 · Số máy EN00002"), line.get("numbers"));
    }

    @Test
    void theTotalsShowOnlyTheFeesThatApplyAndTheDiscountAsANegative() {
        Order order = DocumentFixtures.order(4, OrderStatus.DELIVERED, 1, true);
        order.setIncludeRegistrationService(true);
        order.setRegistrationFee(new BigDecimal("1500000"));
        order.setShippingFee(new BigDecimal("500000"));
        order.setDiscountAmount(new BigDecimal("2000000"));
        order.setTotalAmount(new BigDecimal("20000000"));

        List<Map<String, Object>> totals = list(DocumentModels.receipt(order, null, DocumentFixtures.store(), "en", NOW).get("totals"));

        assertEquals(List.of("Subtotal", "Registration service", "Delivery fee", "Discount", "Total"), totals.stream().map(row -> row.get("label")).toList());
        assertEquals("−2,000,000 ₫", totals.get(3).get("value"));
        assertEquals(true, totals.get(4).get("strong"));
        assertEquals("Twenty million Vietnamese dong", DocumentModels.receipt(order, null, DocumentFixtures.store(), "en", NOW).get("inWords"));
    }

    @Test
    void aPlainOrderHasNoFeeOrDiscountRows() {
        Order order = DocumentFixtures.order(5, OrderStatus.DELIVERED, 1, true);

        List<Map<String, Object>> totals = list(DocumentModels.receipt(order, null, DocumentFixtures.store(), "vi", NOW).get("totals"));

        assertEquals(List.of("Tiền hàng", "Tổng cộng"), totals.stream().map(row -> row.get("label")).toList());
    }

    @Test
    void anInstalmentOrderShowsTheDownPaymentAndThePlan() {
        Order order = DocumentFixtures.order(6, OrderStatus.CONFIRMED, 1, false);
        order.getPayment().setPaymentMethod(PaymentMethod.INSTALLMENT);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        InstallmentApplication plan = new InstallmentApplication();
        plan.setDownPaymentPercent(30);
        plan.setDownPaymentAmount(new BigDecimal("6000000"));
        plan.setFinancedAmount(new BigDecimal("14000000"));
        plan.setTermMonths(12);
        plan.setAnnualRatePercent(new BigDecimal("10.00"));
        plan.setMonthlyPayment(new BigDecimal("1283333"));
        plan.setTotalPayable(new BigDecimal("15400000"));

        Map<String, Object> payment = map(DocumentModels.receipt(order, plan, DocumentFixtures.store(), "vi", NOW).get("payment"));

        assertEquals("Trả góp (mô phỏng)", payment.get("method"));
        assertEquals("Đã trả trước", payment.get("status"));
        List<Map<String, Object>> rows = list(payment.get("installment"));
        assertEquals("6.000.000 ₫ (30%)", rows.get(0).get("value"));
        assertEquals("12 tháng", rows.get(2).get("value"));
        assertEquals("10%/năm", rows.get(3).get("value"));
        Map<String, Object> english = map(DocumentModels.receipt(order, plan, DocumentFixtures.store(), "en", NOW).get("payment"));
        assertEquals("10%/year", list(english.get("installment")).get(3).get("value"));
    }

    @Test
    void anOrderWithoutAPaymentRowSaysSoInsteadOfFailing() {
        Order order = DocumentFixtures.order(7, OrderStatus.PENDING, 1, false);
        order.setPayment(null);

        Map<String, Object> payment = map(DocumentModels.receipt(order, null, DocumentFixtures.store(), "vi", NOW).get("payment"));

        // checkout makes no payment row for VNPay until the first link is made: the order says VNPay, with no transaction yet
        assertEquals("VNPay", payment.get("method"));
        assertEquals("Chưa có giao dịch", payment.get("status"));
    }

    @Test
    void anErasedAccountIsNamedInTheLanguageOfTheDocument() {
        Order order = DocumentFixtures.order(8, OrderStatus.DELIVERED, 1, true);
        order.getShipment().setRecipientName(null);
        order.getUser().setAccountStatus(com.ebike.service.admin.AccountErasure.DELETED);
        order.getUser().setFirstName("Tài khoản");
        order.getUser().setLastName("đã xóa");

        assertEquals("Deleted account", DocumentModels.customerName(order, "en"));
        assertEquals("Tài khoản đã xóa", DocumentModels.customerName(order, "vi"));
    }

    @Test
    void anErasedAccountPrintsNoEmailOnTheReceipt() {
        Order order = DocumentFixtures.order(8, OrderStatus.DELIVERED, 1, true);
        order.setCustomerEmail(null);
        order.getUser().setEmail("deleted_7@deleted.invalid");
        order.getUser().setAccountStatus(com.ebike.service.admin.AccountErasure.DELETED);

        Map<String, Object> customer = map(DocumentModels.receipt(order, null, DocumentFixtures.store(), "vi", NOW).get("customer"));

        assertEquals("—", customer.get("email"));
    }

    @Test
    void theCustomerNameFallsBackToTheAccountThenToADash() {
        Order order = DocumentFixtures.order(8, OrderStatus.DELIVERED, 1, true);
        order.getShipment().setRecipientName("  ");
        assertEquals("Nguyễn Văn An", DocumentModels.customerName(order));

        order.setUser(null);
        assertEquals("—", DocumentModels.customerName(order));
    }

    @Test
    void theContactEmailIsTheCheckoutOneThenTheRecipientThenTheAccount() {
        Order order = DocumentFixtures.order(9, OrderStatus.DELIVERED, 1, true);
        assertEquals("an@example.com", DocumentModels.contactEmail(order));

        order.setCustomerEmail(" ");
        order.getShipment().setRecipientEmail("nhan@example.com");
        assertEquals("nhan@example.com", DocumentModels.contactEmail(order));

        order.getShipment().setRecipientEmail(null);
        assertEquals("an@example.com", DocumentModels.contactEmail(order));

        order.setUser(null);
        assertNull(DocumentModels.contactEmail(order));
    }

    @Test
    void theHandoverTimeIsTheShipmentsThenTheOrdersThenTheLastChange() {
        Order order = DocumentFixtures.order(10, OrderStatus.DELIVERED, 1, true);
        assertEquals(DocumentFixtures.HANDED_OVER, DocumentModels.handoverTime(order));

        order.getShipment().setDeliveredAt(null);
        order.setDeliveredAt(LocalDateTime.of(2026, 11, 19, 10, 0));
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 0), DocumentModels.handoverTime(order));

        order.setDeliveredAt(null);
        assertEquals(DocumentFixtures.HANDED_OVER, DocumentModels.handoverTime(order));
    }

    @Test
    void theWarrantyCardCountsTheMonthsFromTheHandoverAndListsTheServiceDates() {
        Order order = DocumentFixtures.order(11, OrderStatus.DELIVERED, 2, true);
        List<OrderItemUnit> units = List.copyOf(order.getItems().iterator().next().getUnits());

        Map<String, Object> model = DocumentModels.warrantyCards(order, units, DocumentFixtures.store(),
            WarrantyRules.parseIntervals("P1M,P6M"), "vi", NOW);

        List<Map<String, Object>> cards = list(model.get("cards"));
        assertEquals(2, cards.size());
        Map<String, Object> card = cards.get(0);
        assertEquals("BH-RLEBK00001", card.get("number"));
        assertEquals("RLEBK00001", card.get("frame"));
        assertEquals("EN00001", card.get("engine"));
        assertEquals("24 tháng", card.get("periodText"));
        assertEquals("20/11/2026", card.get("startsAt"));
        assertEquals("20/11/2028", card.get("endsAt"));
        assertEquals("Showroom Quận 7, 12 Nguyễn Văn Linh, Quận 7", card.get("place"));
        List<Map<String, Object>> schedule = list(card.get("schedule"));
        assertEquals(2, schedule.size());
        assertEquals("Sau 1 tháng kể từ ngày bàn giao", schedule.get(0).get("label"));
        assertEquals("20/12/2026", schedule.get(0).get("value"));
        assertEquals("https://shop.example.com/warranty", model.get("lookupUrl"));
        assertEquals(6, ((List<?>) model.get("terms")).size());
    }

    @Test
    void aBikeWithoutAWarrantyLengthSaysSoAndHasNoEndDate() {
        Order order = DocumentFixtures.order(12, OrderStatus.DELIVERED, 1, true);
        OrderItemUnit unit = order.getItems().iterator().next().getUnits().iterator().next();
        unit.setWarrantyMonths(null);
        order.getItems().iterator().next().getProduct().getSpecification().setWarrantyMonths(null);

        Map<String, Object> card = list(DocumentModels.warrantyCards(order, List.of(unit), DocumentFixtures.store(),
            WarrantyRules.parseIntervals(""), "en", NOW).get("cards")).get(0);

        assertEquals(false, card.get("hasPeriod"));
        assertEquals("—", card.get("endsAt"));
        assertTrue(String.valueOf(card.get("periodText")).startsWith("No warranty period"));
    }

    @Test
    void aShopWithoutAPublicAddressPrintsNoLookupLine() {
        Order order = DocumentFixtures.order(13, OrderStatus.DELIVERED, 1, true);
        StoreProfile bare = new StoreProfile("Shop", "", "", "", "", "", "");

        Map<String, Object> model = DocumentModels.warrantyCards(order, List.copyOf(order.getItems().iterator().next().getUnits()), bare,
            WarrantyRules.parseIntervals(null), "vi", NOW);

        assertNull(model.get("lookupUrl"));
        assertEquals("Shop", map(model.get("store")).get("legalName"));
        assertEquals("", map(model.get("store")).get("contact"));
        assertFalse(((List<?>) model.get("cards")).isEmpty());
    }
}
