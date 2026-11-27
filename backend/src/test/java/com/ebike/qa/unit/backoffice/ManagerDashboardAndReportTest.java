package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ManagerDashboardAndReportTest extends ManagerBase {

    static final LocalDateTime D = LocalDateTime.of(2025, 3, 10, 12, 0);

    private Order paidOrder(long id, OrderStatus status, LocalDateTime created, LocalDateTime paidAt, String amount) {
        Order o = Fx.order(id, "ORD-" + id, status, created);
        Fx.payment(id, o, PaymentMethod.VNPAY, PaymentStatus.PAID, amount, paidAt);
        return o;
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void dashboard_emptyShop_isAllZero() {
        when(orders.findAll()).thenReturn(List.of());
        var d = service.getDashboard();
        assertThat(d.totalOrders()).isZero();
        assertThat(d.todayRevenue()).isEqualByComparingTo("0");
        assertThat(d.recentOrders()).isEmpty();
    }

    @Test
    void dashboard_countsPendingAndUnpaidPayLater_andInstalmentWaitingFromRepository() {
        LocalDateTime now = LocalDateTime.now();
        Order a = Fx.order(1L, "A", OrderStatus.PENDING, now);
        Fx.payment(1L, a, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "100", null);
        Order b = Fx.order(2L, "B", OrderStatus.CONFIRMED, now);
        Fx.payment(2L, b, PaymentMethod.PAY_LATER, PaymentStatus.PAID, "100", now);
        Order c = Fx.order(3L, "C", OrderStatus.PENDING, now);
        Fx.payment(3L, c, PaymentMethod.VNPAY, PaymentStatus.PENDING, "100", null);
        Order noPayment = Fx.order(4L, "D", OrderStatus.PENDING, now);
        when(orders.findAll()).thenReturn(List.of(a, b, c, noPayment));
        when(installments.countAwaitingDownPayment()).thenReturn(4L);
        var d = service.getDashboard();
        assertThat(d.totalOrders()).isEqualTo(4);
        assertThat(d.pendingOrders()).isEqualTo(3);
        assertThat(d.unpaidPayLaterOrders()).isEqualTo(1);
        assertThat(d.unpaidInstallmentDownPayments()).isEqualTo(4);
    }

    @Test
    void dashboard_revenueWindows_todayWeekMonth_byPaymentDate() {
        LocalDate today = LocalDate.now();
        LocalDateTime t = today.atTime(10, 0);
        List<Order> list = new ArrayList<>();
        list.add(paidOrder(1, OrderStatus.DELIVERED, t.minusDays(40), t, "1000.50"));                 // today
        list.add(paidOrder(2, OrderStatus.DELIVERED, t.minusDays(40), t.minusDays(6), "200"));        // weekStart (inclusive)
        list.add(paidOrder(3, OrderStatus.DELIVERED, t.minusDays(40), t.minusDays(7), "40000"));      // outside week
        when(orders.findAll()).thenReturn(list);
        var d = service.getDashboard();
        assertThat(d.todayRevenue()).isEqualByComparingTo("1000.50");
        assertThat(d.weekRevenue()).isEqualByComparingTo("1200.50");
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimalExpect.month(d.monthRevenue(), today, monthStart);
    }

    /** Helper kept apart so the month window is asserted against the same calendar the service uses. */
    static final class BigDecimalExpect {
        static void month(java.math.BigDecimal actual, LocalDate today, LocalDate monthStart) {
            java.math.BigDecimal expected = new java.math.BigDecimal("1000.50");
            if (!today.minusDays(6).isBefore(monthStart)) {
                expected = expected.add(new java.math.BigDecimal("200"));
            }
            if (!today.minusDays(7).isBefore(monthStart)) {
                expected = expected.add(new java.math.BigDecimal("40000"));
            }
            assertThat(actual).isEqualByComparingTo(expected);
        }
    }

    @Test
    void dashboard_monthWindow_excludesLastDayOfPreviousMonth_includesFirstDay() {
        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1);
        Order inMonth = paidOrder(1, OrderStatus.DELIVERED, D, first.atTime(0, 0), "500");
        Order prev = paidOrder(2, OrderStatus.DELIVERED, D, first.minusDays(1).atTime(23, 59), "900");
        when(orders.findAll()).thenReturn(List.of(inMonth, prev));
        assertThat(service.getDashboard().monthRevenue()).isEqualByComparingTo("500");
    }

    @Test
    void dashboard_onlyPaidPaymentsCount_notPendingFailedRefundedOrMissingPaidAt() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> list = new ArrayList<>();
        for (PaymentStatus s : List.of(PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.REFUNDED, PaymentStatus.CANCELLED)) {
            Order o = Fx.order((long) s.ordinal() + 1, "X" + s, OrderStatus.CONFIRMED, now);
            Fx.payment(o.getId(), o, PaymentMethod.VNPAY, s, "700", now);
            list.add(o);
        }
        list.add(paidOrder(50, OrderStatus.CONFIRMED, now, null, "700"));
        when(orders.findAll()).thenReturn(list);
        assertThat(service.getDashboard().todayRevenue()).isEqualByComparingTo("0");
    }

    /**
     * The revenue report leaves cancelled orders out (isCountedOrder) and says so on the Excel note, but the dashboard sums every PAID
     * payment, including one whose order was cancelled and is waiting for its refund. Money that is owed back is not revenue; the two
     * screens must agree. Expected today revenue 0, actual 700. ManagerServiceImpl.java:617-624 (sumPaidOrdersWithin has no status filter).
     */
    @Test
    void regression_dashboard_countsPaidMoneyOfCancelledOrdersAsRevenue() {
        LocalDateTime now = LocalDateTime.now();
        when(orders.findAll()).thenReturn(List.of(paidOrder(1, OrderStatus.CANCELLED, now, now, "700")));
        assertThat(service.getDashboard().todayRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void dashboard_recentOrders_areTheFiveNewest() {
        List<Order> list = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Order o = Fx.order((long) i, "O" + i, OrderStatus.PENDING, D.plusDays(i));
            list.add(o);
        }
        when(orders.findAll()).thenReturn(list);
        var recent = service.getDashboard().recentOrders();
        assertThat(recent).extracting(r -> r.orderNumber()).containsExactly("O7", "O6", "O5", "O4", "O3");
    }

    // ------------------------------------------------------------------ revenue report: validation

    private ManagerRevenueReportResponse report(String period, String from, String to) {
        return service.getRevenueReport(period, from, to);
    }

    @ParameterizedTest
    @ValueSource(strings = {"week", "hour", "DAYS", "", "  ", "1"})
    void report_unknownPeriod_is400(String period) {
        when(orders.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> report(period, null, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void report_periodIsCaseInsensitiveAndTrimmed() {
        when(orders.findAll()).thenReturn(List.of());
        assertThat(report(" MoNtH ", "2025-01-01", "2025-03-31").periodType()).isEqualTo("MONTH");
    }

    @Test
    void report_nullPeriod_defaultsToDay() {
        when(orders.findAll()).thenReturn(List.of());
        var r = report(null, "2025-03-01", "2025-03-03");
        assertThat(r.periodType()).isEqualTo("DAY");
        assertThat(r.breakdown()).hasSize(3);
    }

    @Test
    void report_custom_requiresBothDates() {
        when(orders.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> report("custom", null, "2025-01-01")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> report("custom", "2025-01-01", null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> report("custom", " ", " ")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-13-01", "2025-02-30", "01/02/2025", "abc", "2025-1-1", "20250101"})
    void report_badDateFormat_is400(String bad) {
        when(orders.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> report("custom", bad, "2025-12-31")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> report("day", "2025-01-01", bad)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void report_fromAfterTo_is400_equalDatesAreFine() {
        when(orders.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> report("custom", "2025-03-11", "2025-03-10")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> report("month", "2025-03-11", "2025-03-10")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        var r = report("custom", "2025-03-10", "2025-03-10");
        assertThat(r.breakdown()).hasSize(1);
        assertThat(r.fromDate()).isEqualTo("2025-03-10");
    }

    @Test
    void report_datesAreTrimmed() {
        when(orders.findAll()).thenReturn(List.of());
        assertThat(report("custom", " 2025-03-01 ", " 2025-03-02 ").breakdown()).hasSize(2);
    }

    @Test
    void report_defaultRanges_forEachPeriod_whenOnlyToIsGiven() {
        when(orders.findAll()).thenReturn(List.of());
        var day = report("day", null, "2025-06-15");
        assertThat(day.fromDate()).isEqualTo("2025-05-17");               // 30 days including the last
        assertThat(day.breakdown()).hasSize(30);
        var month = report("month", null, "2025-06-15");
        assertThat(month.fromDate()).isEqualTo("2024-07-01");
        assertThat(month.breakdown()).hasSize(12).extracting(p -> p.label()).startsWith("2024-07").endsWith("2025-06");
        var quarter = report("quarter", null, "2025-06-15");
        assertThat(quarter.fromDate()).isEqualTo("2023-09-01");
        assertThat(quarter.breakdown().get(0).label()).isEqualTo("2023-Q3");
        assertThat(quarter.breakdown().get(quarter.breakdown().size() - 1).label()).isEqualTo("2025-Q2");
        var year = report("year", null, "2025-06-15");
        assertThat(year.fromDate()).isEqualTo("2021-01-01");
        assertThat(year.breakdown()).extracting(p -> p.label()).containsExactly("2021", "2022", "2023", "2024", "2025");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 31, 62})
    void report_customRange_upTo62Days_isBucketedByDay(int days) {
        when(orders.findAll()).thenReturn(List.of());
        LocalDate from = LocalDate.of(2025, 1, 1);
        var r = report("custom", from.toString(), from.plusDays(days - 1).toString());
        assertThat(r.periodType()).isEqualTo("DAY");
        assertThat(r.breakdown()).hasSize(days);
    }

    @Test
    void report_customRange_63Days_switchesToMonths_730Days_stillMonths_731Days_years() {
        when(orders.findAll()).thenReturn(List.of());
        LocalDate from = LocalDate.of(2025, 1, 1);
        assertThat(report("custom", from.toString(), from.plusDays(62).toString()).periodType()).isEqualTo("MONTH");
        assertThat(report("custom", from.toString(), from.plusDays(729).toString()).periodType()).isEqualTo("MONTH");
        assertThat(report("custom", from.toString(), from.plusDays(730).toString()).periodType()).isEqualTo("YEAR");
    }

    // ------------------------------------------------------------------ revenue report: figures

    @Test
    void report_revenueIsByPaymentDate_notOrderDate() {
        Order a = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2025, 2, 20, 9, 0), LocalDateTime.of(2025, 3, 2, 9, 0), "1000");
        Order b = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 5, 9, 0), LocalDateTime.of(2025, 4, 1, 9, 0), "5000");
        when(orders.findAll()).thenReturn(List.of(a, b));
        var r = report("custom", "2025-03-01", "2025-03-31");
        assertThat(r.totalRevenue()).isEqualByComparingTo("1000");      // a paid in March, b paid in April
        assertThat(r.totalOrders()).isEqualTo(1);                       // only b was placed in March
        assertThat(r.successfulOrders()).isEqualTo(1);
    }

    @Test
    void report_rangeEndsAreInclusive_wholeDay() {
        Order first = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 3, 1, 0, 0), "10");
        Order last = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 31, 23, 59, 59), LocalDateTime.of(2025, 3, 31, 23, 59, 59), "20");
        Order before = paidOrder(3, OrderStatus.DELIVERED, LocalDateTime.of(2025, 2, 28, 23, 59, 59), LocalDateTime.of(2025, 2, 28, 23, 59, 59), "40");
        Order after = paidOrder(4, OrderStatus.DELIVERED, LocalDateTime.of(2025, 4, 1, 0, 0), LocalDateTime.of(2025, 4, 1, 0, 0), "80");
        when(orders.findAll()).thenReturn(List.of(first, last, before, after));
        var r = report("custom", "2025-03-01", "2025-03-31");
        assertThat(r.totalRevenue()).isEqualByComparingTo("30");
        assertThat(r.totalOrders()).isEqualTo(2);
    }

    @Test
    void report_cancelledOrdersAreOutOfEverything_butPendingUnpaidOnesAreInOrderCountNotRevenue() {
        Order cancelled = paidOrder(1, OrderStatus.CANCELLED, D, D, "999");
        Order pending = Fx.order(2, "P", OrderStatus.PENDING, D);
        Fx.payment(2L, pending, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "300", null);
        Order delivered = paidOrder(3, OrderStatus.DELIVERED, D, D, "100.25");
        Order refunded = Fx.order(4, "R", OrderStatus.DELIVERED, D);
        Fx.payment(4L, refunded, PaymentMethod.VNPAY, PaymentStatus.REFUNDED, "50", D);
        when(orders.findAll()).thenReturn(List.of(cancelled, pending, delivered, refunded));
        var r = report("custom", "2025-03-10", "2025-03-10");
        assertThat(r.totalOrders()).isEqualTo(3);
        assertThat(r.successfulOrders()).isEqualTo(2);
        assertThat(r.totalRevenue()).isEqualByComparingTo("100.25");
    }

    @Test
    void report_paymentWithoutPaidAt_fallsBackToOrderDate() {
        Order o = paidOrder(1, OrderStatus.DELIVERED, D, null, "77");
        when(orders.findAll()).thenReturn(List.of(o));
        assertThat(report("custom", "2025-03-10", "2025-03-10").totalRevenue()).isEqualByComparingTo("77");
    }

    @Test
    void report_bigDecimalSums_haveNoFloatingPointDrift() {
        List<Order> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(paidOrder(i + 1, OrderStatus.DELIVERED, D, D, "0.10"));
        }
        when(orders.findAll()).thenReturn(list);
        assertThat(report("custom", "2025-03-10", "2025-03-10").totalRevenue()).isEqualByComparingTo("1.00");
    }

    @Test
    void report_breakdownTotalsEqualHeadlineTotals_acrossBuckets() {
        Order a = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 1, 9, 0), LocalDateTime.of(2025, 3, 1, 9, 0), "10");
        Order b = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 2, 9, 0), LocalDateTime.of(2025, 3, 3, 9, 0), "20");
        when(orders.findAll()).thenReturn(List.of(a, b));
        var r = report("custom", "2025-03-01", "2025-03-03");
        assertThat(r.breakdown()).extracting(p -> p.label()).containsExactly("2025-03-01", "2025-03-02", "2025-03-03");
        assertThat(r.breakdown()).extracting(p -> p.orderCount()).containsExactly(1L, 1L, 0L);
        assertThat(r.breakdown().stream().map(p -> p.revenue()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
            .isEqualByComparingTo(r.totalRevenue());
    }

    @Test
    void report_monthBuckets_areClippedToTheRequestedRange() {
        Order a = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2025, 1, 31, 9, 0), LocalDateTime.of(2025, 1, 31, 9, 0), "10");
        Order b = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 2, 1, 9, 0), LocalDateTime.of(2025, 2, 1, 9, 0), "20");
        Order c = paidOrder(3, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 20, 9, 0), LocalDateTime.of(2025, 3, 20, 9, 0), "40");
        when(orders.findAll()).thenReturn(List.of(a, b, c));
        var r = report("month", "2025-01-15", "2025-03-10");
        assertThat(r.breakdown()).extracting(p -> p.label()).containsExactly("2025-01", "2025-02", "2025-03");
        assertThat(r.breakdown().get(0).revenue()).isEqualByComparingTo("10");
        assertThat(r.breakdown().get(1).revenue()).isEqualByComparingTo("20");
        assertThat(r.breakdown().get(2).revenue()).isEqualByComparingTo("0");   // 20 March is after the range end
        assertThat(r.totalRevenue()).isEqualByComparingTo("30");
    }

    @Test
    void report_quarterBuckets_splitOnCalendarQuarters() {
        Order a = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2025, 3, 31, 23, 0), LocalDateTime.of(2025, 3, 31, 23, 0), "1");
        Order b = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 4, 1, 0, 0), LocalDateTime.of(2025, 4, 1, 0, 0), "2");
        Order c = paidOrder(3, OrderStatus.DELIVERED, LocalDateTime.of(2025, 12, 31, 23, 59), LocalDateTime.of(2025, 12, 31, 23, 59), "4");
        when(orders.findAll()).thenReturn(List.of(a, b, c));
        var r = report("quarter", "2025-01-01", "2025-12-31");
        assertThat(r.breakdown()).extracting(p -> p.label()).containsExactly("2025-Q1", "2025-Q2", "2025-Q3", "2025-Q4");
        assertThat(r.breakdown()).extracting(p -> p.revenue().intValue()).containsExactly(1, 2, 0, 4);
    }

    @Test
    void report_yearBuckets_acrossNewYear() {
        Order a = paidOrder(1, OrderStatus.DELIVERED, LocalDateTime.of(2024, 12, 31, 23, 59), LocalDateTime.of(2024, 12, 31, 23, 59), "5");
        Order b = paidOrder(2, OrderStatus.DELIVERED, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 1, 1, 0, 0), "6");
        when(orders.findAll()).thenReturn(List.of(a, b));
        var r = report("year", "2024-06-01", "2025-06-01");
        assertThat(r.breakdown()).extracting(p -> p.label()).containsExactly("2024", "2025");
        assertThat(r.breakdown()).extracting(p -> p.revenue().intValue()).containsExactly(5, 6);
    }

    @Test
    void report_topProducts_byRevenueDesc_limitTen_ignoreCancelledAndUnpaid() {
        List<Order> list = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Product p = Fx.product((long) i, "Bike" + i);
            Order o = paidOrder(i, OrderStatus.DELIVERED, D, D, String.valueOf(i * 100));
            Fx.item((long) i, o, p, 1, String.valueOf(i * 100));
            list.add(o);
        }
        Product ghost = Fx.product(99L, "Ghost");
        Order cancelled = paidOrder(100, OrderStatus.CANCELLED, D, D, "999999");
        Fx.item(100L, cancelled, ghost, 1, "999999");
        Order unpaid = Fx.order(101, "U", OrderStatus.PENDING, D);
        Fx.payment(101L, unpaid, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "888888", null);
        Fx.item(101L, unpaid, ghost, 1, "888888");
        list.add(cancelled);
        list.add(unpaid);
        when(orders.findAll()).thenReturn(list);
        when(products.findAll()).thenReturn(List.of());
        var top = report("custom", "2025-03-10", "2025-03-10").topProducts();
        assertThat(top).hasSize(10);
        assertThat(top.get(0).productName()).isEqualTo("Bike12");
        assertThat(top.get(0).revenue()).isEqualByComparingTo("1200");
        assertThat(top).extracting(t -> t.productName()).doesNotContain("Ghost");
    }

    @Test
    void report_topProducts_aggregateQuantityAndRevenueOverOrders() {
        Product p = Fx.product(1L, "Amy");
        Order a = paidOrder(1, OrderStatus.DELIVERED, D, D, "300");
        Fx.item(1L, a, p, 2, "300");
        Order b = paidOrder(2, OrderStatus.DELIVERED, D, D, "150");
        Fx.item(2L, b, p, 1, "150");
        when(orders.findAll()).thenReturn(List.of(a, b));
        when(products.findAll()).thenReturn(List.of(p));
        var top = report("custom", "2025-03-10", "2025-03-10").topProducts();
        assertThat(top).hasSize(1);
        assertThat(top.get(0).quantitySold()).isEqualTo(3);
        assertThat(top.get(0).revenue()).isEqualByComparingTo("450");
    }

    @Test
    void report_slowProducts_listActiveUnsoldFirst_excludeInactive_limitTen() {
        List<Product> catalogue = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            catalogue.add(Fx.product((long) i, "P" + (char) ('A' + i)));
        }
        Product inactive = Fx.product(50L, "Hidden");
        inactive.setActive(false);
        catalogue.add(inactive);
        Order o = paidOrder(1, OrderStatus.DELIVERED, D, D, "100");
        Fx.item(1L, o, catalogue.get(0), 5, "100");
        when(orders.findAll()).thenReturn(List.of(o));
        when(products.findAll()).thenReturn(catalogue);
        var slow = report("custom", "2025-03-10", "2025-03-10").slowProducts();
        assertThat(slow).hasSize(10);
        assertThat(slow).extracting(s -> s.productName()).doesNotContain("Hidden", "PB");
        assertThat(slow).allMatch(s -> s.quantitySold() == 0);
    }

    @Test
    void export_exposesPlacedAndCancelledCounts_forTheCancellationRate() {
        Order ok = paidOrder(1, OrderStatus.DELIVERED, D, D, "1");
        Order c1 = Fx.order(2, "C1", OrderStatus.CANCELLED, D);
        Order c2 = Fx.order(3, "C2", OrderStatus.CANCELLED, D);
        Order outside = Fx.order(4, "OUT", OrderStatus.CANCELLED, D.minusDays(30));
        when(orders.findAll()).thenReturn(List.of(ok, c1, c2, outside));
        var data = service.getRevenueReportForExport("custom", "2025-03-10", "2025-03-10");
        assertThat(data.placedOrders()).isEqualTo(3);
        assertThat(data.cancelledOrders()).isEqualTo(2);
        assertThat(data.cancellationRate()).isEqualTo(2.0 / 3.0);
    }

    @Test
    void export_cancellationRateIsZero_whenNothingWasPlaced() {
        when(orders.findAll()).thenReturn(List.of());
        assertThat(service.getRevenueReportForExport("custom", "2025-03-10", "2025-03-10").cancellationRate()).isZero();
    }

    /** M-25: the dashboard, the revenue report and a customer's total spent use ONE definition: PAID payments of orders that are not CANCELLED. */
    @Test
    void revenue_dashboardReportAndTotalSpentAgree() {
        LocalDateTime now = LocalDateTime.now();
        com.ebike.model.entity.auth.User anna = Fx.user(1, "anna", "anna@x.com", new String[] {"CUSTOMER"});
        Order normal = paidOrder(1, OrderStatus.DELIVERED, now, now, "16800000");
        Order cancelledPaid = paidOrder(2, OrderStatus.CANCELLED, now, now, "16800000");
        Order refunded = Fx.order(3, "ORD-3", OrderStatus.CANCELLED, now);
        Fx.payment(3, refunded, PaymentMethod.VNPAY, PaymentStatus.REFUNDED, "16800000", now);
        Order refundedLive = Fx.order(4, "ORD-4", OrderStatus.CONFIRMED, now);
        Fx.payment(4, refundedLive, PaymentMethod.VNPAY, PaymentStatus.REFUNDED, "16800000", now);
        List<Order> all = List.of(normal, cancelledPaid, refunded, refundedLive);
        all.forEach(o -> o.setUser(anna));
        when(orders.findAll()).thenReturn(all);
        when(users.findAll()).thenReturn(List.of(anna));

        var dashboard = service.getDashboard();
        var report = service.getRevenueReport("custom", now.toLocalDate().toString(), now.toLocalDate().toString());
        var spent = service.getCustomers(null).get(0).totalSpent();

        assertThat(dashboard.todayRevenue()).isEqualByComparingTo("16800000");
        assertThat(report.totalRevenue()).isEqualByComparingTo(dashboard.todayRevenue());
        assertThat(report.breakdown().stream().map(p -> p.revenue()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)).isEqualByComparingTo(dashboard.todayRevenue());
        assertThat(spent).isEqualByComparingTo(dashboard.todayRevenue());
    }
}
