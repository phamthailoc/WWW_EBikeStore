package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.request.ManagerPaymentConfirmationRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.util.order.VnPayUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ManagerPaymentsAndCustomersTest extends ManagerBase {

    static final LocalDateTime BASE = LocalDateTime.of(2025, 3, 10, 12, 0);

    @BeforeEach
    void stubs() {
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(installments.findByOrderId(any())).thenReturn(Optional.empty());
    }

    private Payment pending(long id, PaymentMethod method, OrderStatus orderStatus) {
        Order o = Fx.order(id, "ORD-" + id, orderStatus, BASE.plusDays(id));
        o.setCustomerEmail("cust" + id + "@x.com");
        Payment p = Fx.payment(id, o, method, PaymentStatus.PENDING, "1500000", null);
        when(payments.findById(id)).thenReturn(Optional.of(p));
        when(payments.findOrderIdById(id)).thenReturn(Optional.of(id));
        when(orders.findLockedById(id)).thenReturn(Optional.of(o));
        return p;
    }

    // ------------------------------------------------------------------ list / filters

    @Test
    void payments_sortedNewestOrderFirst_andMappedFields() {
        Payment a = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        Payment b = pending(2, PaymentMethod.VNPAY, OrderStatus.CONFIRMED);
        Fx.shipment(a.getOrder(), com.ebike.model.entity.order.ShipmentStatus.PENDING);
        when(payments.findAll()).thenReturn(List.of(a, b));
        var list = service.getPayments(null, null, null);
        assertThat(list).extracting(r -> r.id()).containsExactly(2L, 1L);
        assertThat(list.get(1).customerName()).isEqualTo("Nguyen Van A");
        assertThat(list.get(1).amount()).isEqualByComparingTo("1500000");
        assertThat(list.get(1).paymentMethod()).isEqualTo("PAY_LATER");
        assertThat(list.get(0).customerName()).isNull();
    }

    @Test
    void payments_filterByStatusAndMethod_caseInsensitive_trimmed() {
        Payment a = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        Payment b = pending(2, PaymentMethod.VNPAY, OrderStatus.CONFIRMED);
        b.setPaymentStatus(PaymentStatus.PAID);
        when(payments.findAll()).thenReturn(List.of(a, b));
        assertThat(service.getPayments(" paid ", null, null)).extracting(r -> r.id()).containsExactly(2L);
        assertThat(service.getPayments(null, "pay_later", null)).extracting(r -> r.id()).containsExactly(1L);
        assertThat(service.getPayments("PAID", "PAY_LATER", null)).isEmpty();
        assertThat(service.getPayments("  ", " ", " ")).hasSize(2);
    }

    @Test
    void payments_invalidFilter_is400() {
        Payment only = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        when(payments.findAll()).thenReturn(List.of(only));
        assertThatThrownBy(() -> service.getPayments("DONE", null, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.getPayments(null, "CASH", null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void payments_search_matchesOrderNumberEmailReferencesRecipientAndPhone_caseInsensitive() {
        Payment a = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        a.setTransactionReference("EBIKE20250310120000AB");
        a.setProviderTxnId("TXN-999");
        Fx.shipment(a.getOrder(), com.ebike.model.entity.order.ShipmentStatus.PENDING);
        Payment b = pending(2, PaymentMethod.VNPAY, OrderStatus.CONFIRMED);
        when(payments.findAll()).thenReturn(List.of(a, b));
        for (String q : List.of("ord-1", "CUST1@X.COM", "ebike2025", "txn-999", "nguyen van", "0901234", "  ORD-1  ")) {
            assertThat(service.getPayments(null, null, q)).as(q).extracting(r -> r.id()).containsExactly(1L);
        }
        assertThat(service.getPayments(null, null, "zzz")).isEmpty();
    }

    @Test
    void payments_instalmentRowsCarryApplicationStatus_inOneBatchQuery() {
        Payment p = pending(5, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        InstallmentApplication app = new InstallmentApplication();
        app.setOrder(p.getOrder());
        app.setStatus(InstallmentApplication.APPROVED);
        app.setReviewedAt(LocalDateTime.now().minusHours(1));
        when(payments.findAll()).thenReturn(List.of(p));
        when(installments.findByOrderIdIn(List.of(5L))).thenReturn(List.of(app));
        var row = service.getPayments(null, null, null).get(0);
        assertThat(row.installmentStatus()).isEqualTo("APPROVED");
        assertThat(row.downPaymentOverdue()).isFalse();
        verify(installments, never()).findByOrderId(any());
    }

    @Test
    void payments_instalmentPastHold_isFlaggedOverdue() {
        Payment p = pending(5, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        InstallmentApplication app = new InstallmentApplication();
        app.setOrder(p.getOrder());
        app.setStatus(InstallmentApplication.APPROVED);
        app.setReviewedAt(LocalDateTime.now().minusHours(73));
        when(payments.findAll()).thenReturn(List.of(p));
        when(installments.findByOrderIdIn(any())).thenReturn(List.of(app));
        assertThat(service.getPayments(null, null, null).get(0).downPaymentOverdue()).isTrue();
    }

    // ------------------------------------------------------------------ confirm pay-later

    @Test
    void confirm_payLater_marksPaid_confirmsPendingOrder_trimsNoteAndTxn_andAudits() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        var res = service.confirmPayLaterPayment(1L, new ManagerPaymentConfirmationRequest("  TX1 ", "  cash at counter "));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getPaidAt()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(p.getProviderTxnId()).isEqualTo("TX1");
        assertThat(p.getProviderResponse()).isEqualTo("cash at counter");
        assertThat(p.getOrder().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(res.paymentStatus()).isEqualTo("PAID");
        verify(audit).record("xác nhận đã thu tiền", "ORD-1");
    }

    @Test
    void confirm_nullRequestIsFine() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PROCESSING);
        service.confirmPayLaterPayment(1L, null);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getOrder().getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void confirm_blankNoteAndTxn_areIgnored() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        service.confirmPayLaterPayment(1L, new ManagerPaymentConfirmationRequest("  ", "  "));
        assertThat(p.getProviderTxnId()).isNull();
        assertThat(p.getProviderResponse()).isNull();
    }

    @Test
    void confirm_unknownPayment_is404_unknownOrder_is404() {
        assertThatThrownBy(() -> service.confirmPayLaterPayment(404L, null)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        when(payments.findOrderIdById(9L)).thenReturn(Optional.of(77L));
        assertThatThrownBy(() -> service.confirmPayLaterPayment(9L, null)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void confirm_cancelledOrder_is409_andNothingChanges() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(payments, never()).save(any());
    }

    @Test
    void confirm_onlinePaymentMethod_cannotBeConfirmedByHand() {
        Payment p = pending(1, PaymentMethod.VNPAY, OrderStatus.PENDING);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_twice_isRejected_idempotentNoSecondTimestamp() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.PENDING);
        service.confirmPayLaterPayment(1L, null);
        LocalDateTime first = p.getPaidAt();
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getPaidAt()).isEqualTo(first);
    }

    @Test
    void confirm_refundedOrFailedOrCancelledPayLater_isRejected() {
        for (PaymentStatus s : List.of(PaymentStatus.REFUNDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.AUTHORIZED)) {
            Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.CONFIRMED);
            p.setPaymentStatus(s);
            assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).as(s.name()).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void confirm_deliveredOrder_staysDelivered() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.DELIVERED);
        service.confirmPayLaterPayment(1L, null);
        assertThat(p.getOrder().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private InstallmentApplication app(Payment p, String status, LocalDateTime reviewedAt) {
        InstallmentApplication a = new InstallmentApplication();
        a.setOrder(p.getOrder());
        a.setStatus(status);
        a.setReviewedAt(reviewedAt);
        when(installments.findByOrderId(p.getOrder().getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void confirm_instalmentDownPayment_needsApprovedApplication() {
        Payment p = pending(1, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        app(p, InstallmentApplication.PENDING, null);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        app(p, InstallmentApplication.REJECTED, LocalDateTime.now());
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_instalmentApproved_withinHold_isCollected() {
        Payment p = pending(1, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        app(p, InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(71));
        service.confirmPayLaterPayment(1L, null);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getOrder().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_instalmentPastHold_is409_noMoneyTaken() {
        Payment p = pending(1, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        app(p, InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(73));
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_instalmentWithOpenCardPage_is409_untilTheWindowCloses() {
        Payment p = pending(1, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        app(p, InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(1));
        String fresh = "EBIKE" + LocalDateTime.now(VnPayUtil.ZONE).minusMinutes(5).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "X";
        p.setTransactionReference(fresh);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        String stale = "EBIKE" + LocalDateTime.now(VnPayUtil.ZONE).minusMinutes(16).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "X";
        p.setTransactionReference(stale);
        service.confirmPayLaterPayment(1L, null);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void confirm_instalmentAfterFailedCardAttempt_clearsGatewayTraces_andIsCollected() {
        Payment p = pending(1, PaymentMethod.INSTALLMENT, OrderStatus.PENDING);
        app(p, InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(1));
        p.setPaymentStatus(PaymentStatus.FAILED);
        p.setProviderTxnId("OLD");
        p.setVnpayResponseCode("24");
        p.setVnpaySecureHash("abc");
        service.confirmPayLaterPayment(1L, null);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getProviderTxnId()).isNull();
        assertThat(p.getVnpayResponseCode()).isNull();
        assertThat(p.getVnpaySecureHash()).isNull();
    }

    @Test
    void confirm_payLaterWithFailedStatus_isNotRetryableByHand() {
        Payment p = pending(1, PaymentMethod.PAY_LATER, OrderStatus.CONFIRMED);
        p.setPaymentStatus(PaymentStatus.FAILED);
        assertThatThrownBy(() -> service.confirmPayLaterPayment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------ refund

    private Payment paidCancelled(long id) {
        Payment p = pending(id, PaymentMethod.VNPAY, OrderStatus.CANCELLED);
        p.setPaymentStatus(PaymentStatus.PAID);
        p.setPaidAt(BASE);
        return p;
    }

    @Test
    void refund_paidPaymentOfCancelledOrder_becomesRefunded_withNote_andAudit() {
        Payment p = paidCancelled(1);
        var res = service.refundPayment(1L, new ManagerPaymentConfirmationRequest(null, "  bank ref 1 "));
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.getProviderResponse()).isEqualTo("bank ref 1");
        assertThat(res.paymentStatus()).isEqualTo("REFUNDED");
        verify(audit).record("ghi nhận hoàn tiền", "ORD-1");
    }

    @Test
    void refund_withoutANote_isRefused() {
        Payment p = paidCancelled(1);
        for (var request : java.util.Arrays.asList(null, new ManagerPaymentConfirmationRequest(null, null), new ManagerPaymentConfirmationRequest(null, ""), new ManagerPaymentConfirmationRequest(null, "   "))) {
            assertThatThrownBy(() -> service.refundPayment(1L, request)).satisfies(t -> {
                assertStatus(t, HttpStatus.BAD_REQUEST);
                assertThat(((org.springframework.web.server.ResponseStatusException) t).getReason()).isEqualTo("Vui lòng nhập ghi chú hoàn tiền.");
            });
        }
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refund_isNotRepeatable() {
        paidCancelled(1);
        service.refundPayment(1L, new ManagerPaymentConfirmationRequest(null, "ref"));
        assertThatThrownBy(() -> service.refundPayment(1L, new ManagerPaymentConfirmationRequest(null, "ref"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(audit, org.mockito.Mockito.times(1)).record(any(), any());
    }

    @Test
    void refund_ofLiveOrder_isRefused_moneyForADeliveredOrderIsNotGivenBack() {
        for (OrderStatus s : List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.READY_FOR_PICKUP)) {
            Payment p = paidCancelled(1);
            p.getOrder().setStatus(s);
            assertThatThrownBy(() -> service.refundPayment(1L, null)).as(s.name()).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
            assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }
    }

    @Test
    void refund_ofUnpaidPayment_isRefused() {
        for (PaymentStatus s : List.of(PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.AUTHORIZED, PaymentStatus.REFUNDED)) {
            Payment p = paidCancelled(1);
            p.setPaymentStatus(s);
            assertThatThrownBy(() -> service.refundPayment(1L, null)).as(s.name()).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void refund_unknownPayment_is404() {
        assertThatThrownBy(() -> service.refundPayment(404L, null)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    /** M-21: a PAID payment of a cancelled order (a VNPay result that came after the cancellation) is flagged for the manager as needing a refund. */
    @Test
    void payments_paidPaymentOfACancelledOrder_isFlaggedRefundRequired() {
        Payment late = paidCancelled(1);
        Payment normal = pending(2, PaymentMethod.VNPAY, OrderStatus.CONFIRMED);
        normal.setPaymentStatus(PaymentStatus.PAID);
        Payment refunded = paidCancelled(3);
        refunded.setPaymentStatus(PaymentStatus.REFUNDED);
        when(payments.findAll()).thenReturn(List.of(late, normal, refunded));
        assertThat(service.getPayments(null, null, null)).extracting(r -> r.id() + ":" + r.refundRequired())
            .containsExactlyInAnyOrder("1:true", "2:false", "3:false");
    }

    // ------------------------------------------------------------------ customers

    private User customer(long id, String username, String email, String first, String last, String... roles) {
        User u = Fx.user(id, username, email, roles.length == 0 ? new String[] {"CUSTOMER"} : roles);
        u.setFirstName(first);
        u.setLastName(last);
        return u;
    }

    @Test
    void customers_onlyCustomerRole_notStaff_notErased_newestFirst() {
        User c1 = customer(1, "anna", "anna@x.com", "Anna", "Le");
        User c2 = customer(2, "bob", "bob@x.com", "Bob", "Tran");
        User mgr = customer(3, "mgr", "mgr@x.com", "M", "G", "MANAGER");
        User adm = customer(4, "adm", "adm@x.com", "A", "D", "ADMIN");
        User erased = customer(5, "deleted_5", "deleted_5@deleted.invalid", "Tài khoản", "đã xóa");
        erased.setAccountStatus("DELETED");
        when(users.findAll()).thenReturn(List.of(c1, c2, mgr, adm, erased));
        when(orders.findAll()).thenReturn(List.of());
        assertThat(service.getCustomers(null)).extracting(r -> r.id()).containsExactly(2L, 1L);
    }

    @Test
    void customers_search_matchesUsernameEmailFirstLast_caseInsensitive() {
        User c1 = customer(1, "anna", "anna@x.com", "Anna", "Le");
        User c2 = customer(2, "bob", "bob@y.com", "Bob", "Tran");
        when(users.findAll()).thenReturn(List.of(c1, c2));
        when(orders.findAll()).thenReturn(List.of());
        assertThat(service.getCustomers("ANNA")).extracting(r -> r.id()).containsExactly(1L);
        assertThat(service.getCustomers(" y.com ")).extracting(r -> r.id()).containsExactly(2L);
        assertThat(service.getCustomers("tran")).extracting(r -> r.id()).containsExactly(2L);
        assertThat(service.getCustomers("   ")).hasSize(2);
        assertThat(service.getCustomers("nobody")).isEmpty();
    }

    @Test
    void customers_orderCountAndTotalSpent_onlyPaidPayments_onlyTheirOwnOrders_guestOrdersIgnored() {
        User c1 = customer(1, "anna", "anna@x.com", "Anna", "Le");
        Order paid = Fx.order(1, "A", OrderStatus.DELIVERED, BASE);
        paid.setUser(c1);
        Fx.payment(1L, paid, PaymentMethod.VNPAY, PaymentStatus.PAID, "100.10", BASE);
        Order paid2 = Fx.order(2, "B", OrderStatus.DELIVERED, BASE);
        paid2.setUser(c1);
        Fx.payment(2L, paid2, PaymentMethod.VNPAY, PaymentStatus.PAID, "200.20", BASE);
        Order unpaid = Fx.order(3, "C", OrderStatus.PENDING, BASE);
        unpaid.setUser(c1);
        Fx.payment(3L, unpaid, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "999", null);
        Order noPay = Fx.order(4, "D", OrderStatus.PENDING, BASE);
        noPay.setUser(c1);
        Order guest = Fx.order(5, "E", OrderStatus.DELIVERED, BASE);
        Fx.payment(5L, guest, PaymentMethod.VNPAY, PaymentStatus.PAID, "5000", BASE);
        Order other = Fx.order(6, "F", OrderStatus.DELIVERED, BASE);
        other.setUser(Fx.user(99L, "other", "o@x.com", "CUSTOMER"));
        Fx.payment(6L, other, PaymentMethod.VNPAY, PaymentStatus.PAID, "7000", BASE);
        when(users.findAll()).thenReturn(List.of(c1));
        when(orders.findAll()).thenReturn(List.of(paid, paid2, unpaid, noPay, guest, other));
        var row = service.getCustomers(null).get(0);
        assertThat(row.orderCount()).isEqualTo(4);
        assertThat(row.totalSpent()).isEqualByComparingTo("300.30");
    }

    @Test
    void customers_response_neverCarriesPasswordHash() {
        when(users.findAll()).thenReturn(List.of(customer(1, "anna", "anna@x.com", "Anna", "Le")));
        when(orders.findAll()).thenReturn(List.of());
        assertThat(service.getCustomers(null).toString()).doesNotContain("hash:");
    }

    /** A manager only has USER_VIEW for customer data. An account that also holds a staff role is not a customer record. */
    @Test
    @Tag("bug")
    void bug_customers_accountWithAdminAndCustomerRoleIsShownToManagers() {
        User both = customer(7, "dual", "dual@x.com", "D", "U", "CUSTOMER", "ADMIN");
        when(users.findAll()).thenReturn(List.of(both));
        when(orders.findAll()).thenReturn(List.of());
        assertThat(service.getCustomers(null)).isEmpty();
    }

    /** The money of a cancelled order that is waiting for its refund is not "spent" by the customer. */
    @Test
    void regression_customers_totalSpentCountsPaidMoneyOfCancelledOrders() {
        User c1 = customer(1, "anna", "anna@x.com", "Anna", "Le");
        Order cancelled = Fx.order(1, "A", OrderStatus.CANCELLED, BASE);
        cancelled.setUser(c1);
        Fx.payment(1L, cancelled, PaymentMethod.VNPAY, PaymentStatus.PAID, "100", BASE);
        when(users.findAll()).thenReturn(List.of(c1));
        when(orders.findAll()).thenReturn(List.of(cancelled));
        assertThat(service.getCustomers(null).get(0).totalSpent()).isEqualByComparingTo("0");
    }
}
