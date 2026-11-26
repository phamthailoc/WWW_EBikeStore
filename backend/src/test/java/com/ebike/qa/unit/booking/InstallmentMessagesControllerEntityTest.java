package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.controller.installment.InstallmentController;
import com.ebike.dto.installment.InstallmentApplicationResponse;
import com.ebike.dto.installment.InstallmentBacklogResponse;
import com.ebike.dto.installment.InstallmentConfigResponse;
import com.ebike.dto.installment.InstallmentDecisionRequest;
import com.ebike.dto.installment.InstallmentQuoteResponse;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.service.installment.InstallmentMessages;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.installment.InstallmentService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class InstallmentMessagesControllerEntityTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static final OffsetDateTime DEADLINE = OffsetDateTime.of(2026, 11, 25, 9, 30, 0, 0, ZoneOffset.ofHours(7));

    // ------------------------------------------------------------------ messages

    @Test
    void moneyUsesDotsAsThousandsSeparatorAndTheDongSign() {
        assertThat(InstallmentMessages.money(d("12345678"))).isEqualTo("12.345.678 ₫");
        assertThat(InstallmentMessages.money(d("0"))).isEqualTo("0 ₫");
        assertThat(InstallmentMessages.money(d("999"))).isEqualTo("999 ₫");
        assertThat(InstallmentMessages.money(d("1000"))).isEqualTo("1.000 ₫");
        assertThat(InstallmentMessages.money(d("300000000"))).isEqualTo("300.000.000 ₫");
        assertThat(InstallmentMessages.money(null)).isEqualTo("0 ₫");
        assertThat(InstallmentMessages.money(d("2826667.00"))).isEqualTo("2.826.667 ₫");
    }

    @Test
    void whenShowsTheShopOffset() {
        assertThat(InstallmentMessages.when(DEADLINE)).isEqualTo("09:30 25/11/2026 (GMT+7)");
        assertThat(InstallmentMessages.when(DEADLINE.withOffsetSameInstant(ZoneOffset.UTC))).isEqualTo("02:30 25/11/2026 (GMT)");
    }

    @Test
    void titlesDifferByVerdict() {
        assertThat(InstallmentMessages.title(InstallmentMessages.APPROVED)).isEqualTo("Hồ sơ trả góp đã được duyệt");
        assertThat(InstallmentMessages.title(InstallmentMessages.REJECTED)).isEqualTo("Hồ sơ trả góp bị từ chối");
    }

    @Test
    void approvedBodyHasOrderDownPaymentDeadlinePlanAndSimulationDisclaimer() {
        String body = InstallmentMessages.body(InstallmentMessages.APPROVED, "ORD-1", d("4000000"), d("2826667"), 6, DEADLINE, "Bring ID");
        assertThat(body).contains("ORD-1").contains("4.000.000 ₫").contains("trước 09:30 25/11/2026 (GMT+7)")
            .contains("2.826.667 ₫ mỗi tháng trong 6 tháng").contains("mô phỏng").contains("Ghi chú của cửa hàng: Bring ID");
    }

    @Test
    void approvedBodyOmitsDeadlineAndNoteWhenAbsent() {
        String body = InstallmentMessages.body(InstallmentMessages.APPROVED, "ORD-1", d("4000000"), d("2826667"), 6, null, "  ");
        assertThat(body).doesNotContain("Vui lòng thanh toán trước").doesNotContain("Ghi chú của cửa hàng").doesNotContain("null");
    }

    @Test
    void rejectedBodyGivesTheReasonAndSaysTheOrderWasCancelled() {
        String body = InstallmentMessages.body(InstallmentMessages.REJECTED, "ORD-1", d("4000000"), d("2826667"), 6, null, " Income too low ");
        assertThat(body).contains("bị từ chối").contains("Lý do: Income too low").contains("Đơn hàng đã được hủy").doesNotContain("4.000.000");
        assertThat(InstallmentMessages.body(InstallmentMessages.REJECTED, "ORD-1", null, null, 6, null, null)).doesNotContain("Lý do").doesNotContain("null");
    }

    @Test
    void downPaymentNoticesForPaidAndFailed() {
        assertThat(InstallmentMessages.downPaymentTitle(true)).contains("Đã nhận");
        assertThat(InstallmentMessages.downPaymentTitle(false)).contains("thất bại");
        assertThat(InstallmentMessages.downPaymentBody(true, "ORD-1")).contains("ORD-1").contains("đã được thanh toán");
        assertThat(InstallmentMessages.downPaymentBody(false, "ORD-1")).contains("ORD-1").contains("chưa thanh toán được");
    }

    @Test
    void orderUrlJoinsBaseAndOrderIdAndIsNullWhenAnythingIsMissing() {
        assertThat(InstallmentMessages.orderUrl("https://shop.vn", 42L)).isEqualTo("https://shop.vn/customer/orders/42");
        assertThat(InstallmentMessages.orderUrl(" https://shop.vn/ ", 42L)).isEqualTo("https://shop.vn/customer/orders/42");
        assertThat(InstallmentMessages.orderUrl(null, 42L)).isNull();
        assertThat(InstallmentMessages.orderUrl("   ", 42L)).isNull();
        assertThat(InstallmentMessages.orderUrl("https://shop.vn", null)).isNull();
    }

    @Test
    void emailSubjectsNameTheOrder() {
        assertThat(InstallmentMessages.emailSubject(InstallmentMessages.APPROVED, "ORD-1")).contains("ORD-1").contains("đã được duyệt");
        assertThat(InstallmentMessages.emailSubject(InstallmentMessages.REJECTED, "ORD-1")).contains("ORD-1").contains("bị từ chối");
    }

    @Test
    void emailBodyHasGreetingBodyOptionalLinkAndTheSimulationNotice() {
        String with = InstallmentMessages.emailBody(InstallmentMessages.APPROVED, "ORD-1", d("4000000"), d("2826667"), 6, DEADLINE, null, " https://shop.vn/customer/orders/1 ");
        assertThat(with).startsWith("Xin chào,").contains("Đơn hàng của bạn: https://shop.vn/customer/orders/1").contains("chưa phát sinh khoản vay thật").endsWith("Kinetic E-Bike");
        String without = InstallmentMessages.emailBody(InstallmentMessages.REJECTED, "ORD-1", null, null, 6, null, "x", null);
        assertThat(without).doesNotContain("Đơn hàng của bạn:").doesNotContain("null");
    }

    // ------------------------------------------------------------------ controller

    InstallmentPlanner planner;
    InstallmentService service;
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("boss", "n/a", List.of());

    @BeforeEach
    void setUp() {
        planner = mock(InstallmentPlanner.class);
        service = mock(InstallmentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new InstallmentController(planner, service)).build();
    }

    static InstallmentApplicationResponse app(long id) {
        return new InstallmentApplicationResponse(id, 100L, "ORD-100", "PENDING", "An", "Aero X × 1", "********6789", d("10000000"), d("20000000"), 20,
            d("4000000"), d("16000000"), 6, d("12"), d("2826667"), d("20960002"), "PENDING", null, null, null, null, null, null, null, false, true);
    }

    @Test
    void configIsPublicAndNeverCached() throws Exception {
        when(planner.config()).thenReturn(new InstallmentConfigResponse(20, 50, List.of(), d("3000000"), d("1000000000"), 60, 2, 1000, 973));
        mvc.perform(get("/api/v1/installments/config"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.minDownPaymentPercent").value(20))
            .andExpect(jsonPath("$.maxOpenApplications").value(2));
    }

    @Test
    void quotePassesTheThreeParameters() throws Exception {
        when(planner.quote(d("20000000"), 20, 6)).thenReturn(new InstallmentQuoteResponse(d("20000000"), 20, d("4000000"), d("16000000"), 6, d("12"), d("960000"),
            d("2826667"), d("20960002"), true, null));
        mvc.perform(get("/api/v1/installments/quote").param("amount", "20000000").param("downPaymentPercent", "20").param("termMonths", "6"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.monthlyPayment").value(2826667))
            .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    void quoteMissingOrMalformedParametersIs400() throws Exception {
        mvc.perform(get("/api/v1/installments/quote")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/installments/quote").param("amount", "20000000").param("downPaymentPercent", "20")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/installments/quote").param("amount", "abc").param("downPaymentPercent", "20").param("termMonths", "6")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/installments/quote").param("amount", "1").param("downPaymentPercent", "x").param("termMonths", "6")).andExpect(status().isBadRequest());
    }

    @Test
    void quoteServiceRefusalKeepsItsStatus() throws Exception {
        when(planner.quote(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad"));
        mvc.perform(get("/api/v1/installments/quote").param("amount", "1").param("downPaymentPercent", "20").param("termMonths", "6")).andExpect(status().isBadRequest());
    }

    @Test
    void ofOrderAnswers204WhenThereIsNoApplicationOrItIsNotTheCallers() throws Exception {
        when(service.forOrder(eq(100L), any())).thenReturn(null);
        mvc.perform(get("/api/v1/installments/orders/100").principal(auth))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(""));
    }

    @Test
    void ofOrderAnswers200WithTheApplication() throws Exception {
        when(service.forOrder(eq(100L), any())).thenReturn(app(50));
        mvc.perform(get("/api/v1/installments/orders/100").principal(auth))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.id").value(50))
            .andExpect(jsonPath("$.monthlyPayment").value(2826667));
        mvc.perform(get("/api/v1/installments/orders/abc").principal(auth)).andExpect(status().isBadRequest());
    }

    @Test
    void mineIsNoStore() throws Exception {
        when(service.mine(any())).thenReturn(List.of(app(1), app(2)));
        mvc.perform(get("/api/v1/installments/mine").principal(auth))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void managerQueueForwardsStatusAndLimit() throws Exception {
        when(service.queue(eq("ALL"), any(), eq(10))).thenReturn(List.of(app(1)));
        mvc.perform(get("/api/v1/manager/installments").param("status", "ALL").param("limit", "10").principal(auth))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].id").value(1));
        when(service.queue(isNull(), any(), isNull())).thenReturn(List.of());
        mvc.perform(get("/api/v1/manager/installments").principal(auth)).andExpect(status().isOk());
    }

    @Test
    void managerQueueWithAnInvalidStatusIs400() throws Exception {
        when(service.queue(eq("BOGUS"), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "x"));
        mvc.perform(get("/api/v1/manager/installments").param("status", "BOGUS").principal(auth)).andExpect(status().isBadRequest());
    }

    @Test
    void backlogIsNoStore() throws Exception {
        when(service.backlog()).thenReturn(new InstallmentBacklogResponse(3));
        mvc.perform(get("/api/v1/manager/installments/backlog"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.pending").value(3));
    }

    @Test
    void decideIsAPatchWithTheDecisionBody() throws Exception {
        when(service.decide(eq(50L), any(InstallmentDecisionRequest.class), any())).thenReturn(app(50));
        mvc.perform(patch("/api/v1/manager/installments/50").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"note\":\"ok\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(50));
        verify(service).decide(eq(50L), eq(new InstallmentDecisionRequest("APPROVE", "ok")), any());
    }

    @Test
    void decideErrorsKeepTheirStatus() throws Exception {
        when(service.decide(eq(50L), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "done"));
        mvc.perform(patch("/api/v1/manager/installments/50").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/manager/installments/50").principal(auth).contentType(MediaType.APPLICATION_JSON).content("nope")).andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoWriteEndpointForCustomersToDecideTheirOwnApplication() throws Exception {
        mvc.perform(get("/api/v1/installments/50/approve")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/installments/50").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        boolean anyPostOrPutOnPublicPaths = false;
        for (Method m : InstallmentController.class.getDeclaredMethods()) {
            anyPostOrPutOnPublicPaths |= m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
        }
        assertThat(anyPostOrPutOnPublicPaths).isFalse();
    }

    // ------------------------------------------------------------------ entity

    @Test
    void entityDefaultsToPendingAndMapsToItsTable() {
        InstallmentApplication a = new InstallmentApplication();
        assertThat(a.getStatus()).isEqualTo("PENDING");
        assertThat(InstallmentApplication.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(InstallmentApplication.class.getAnnotation(Table.class).name()).isEqualTo("installment_applications");
        assertThat(List.of(InstallmentApplication.PENDING, InstallmentApplication.APPROVED, InstallmentApplication.REJECTED, InstallmentApplication.CANCELLED))
            .containsExactly("PENDING", "APPROVED", "REJECTED", "CANCELLED");
    }

    @Test
    void prePersistStampsBothTimesAndPreUpdateOnlyTheSecond() throws Exception {
        InstallmentApplication a = new InstallmentApplication();
        Method create = InstallmentApplication.class.getDeclaredMethod("onCreate");
        create.setAccessible(true);
        LocalDateTime before = LocalDateTime.now();
        create.invoke(a);
        assertThat(a.getCreatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(a.getUpdatedAt()).isEqualTo(a.getCreatedAt());

        LocalDateTime created = a.getCreatedAt();
        Fx.set(a, "updatedAt", created.minusDays(1));
        Method update = InstallmentApplication.class.getDeclaredMethod("onUpdate");
        update.setAccessible(true);
        update.invoke(a);
        assertThat(a.getCreatedAt()).isEqualTo(created);
        assertThat(a.getUpdatedAt()).isAfterOrEqualTo(created);
    }

    @Test
    void theOrderColumnIsOneToOneUniqueSoAnOrderHasAtMostOneApplication() throws Exception {
        var join = InstallmentApplication.class.getDeclaredField("order").getAnnotation(jakarta.persistence.JoinColumn.class);
        assertThat(join.unique()).isTrue();
        assertThat(join.nullable()).isFalse();
    }

    @Test
    void moneyColumnsAreWideEnoughForTheLargestAllowedPlan() throws Exception {
        // largest order 300,000,000 (+ interest up to 60% a year x 1 year of the financed half) fits every column; the quote cap 1e12 does not need to
        assertThat(precision("orderTotal")).isGreaterThanOrEqualTo(9 + 2);
        assertThat(precision("totalPayable")).isGreaterThanOrEqualTo(9 + 2);
        assertThat(precision("monthlyIncome")).isGreaterThanOrEqualTo("1000000000".length() + 2); // max income 1,000,000,000
        assertThat(scale("monthlyPayment")).isEqualTo(2);
        assertThat(precision("annualRatePercent")).isGreaterThanOrEqualTo(5); // 60.00
        assertThat(scale("annualRatePercent")).isEqualTo(2);
    }

    @Test
    void personalDataColumnsAreNullableForErasureAndTheRestIsNot() throws Exception {
        assertThat(column("idNumberMasked").nullable()).isTrue();
        assertThat(column("monthlyIncome").nullable()).isTrue();
        for (String f : List.of("orderTotal", "downPaymentPercent", "downPaymentAmount", "financedAmount", "termMonths", "annualRatePercent", "monthlyPayment",
            "totalPayable", "status", "createdAt", "updatedAt")) {
            assertThat(column(f).nullable()).as(f).isFalse();
        }
        assertThat(column("createdAt").updatable()).isFalse();
        assertThat(column("idNumberMasked").length()).isGreaterThanOrEqualTo(12); // 12 digits masked
        assertThat(column("reviewNote").length()).isEqualTo(1000);
        assertThat(column("status").length()).isGreaterThanOrEqualTo("CANCELLED".length());
    }

    @Test
    void settersAndGettersRoundTrip() {
        InstallmentApplication a = new InstallmentApplication();
        LocalDateTime t = LocalDateTime.parse("2026-11-23T10:00");
        a.setStatus("APPROVED");
        a.setReviewNote("n");
        a.setReviewedAt(t);
        a.setIdNumberMasked("*6789");
        a.setMonthlyIncome(d("1"));
        a.setOrderTotal(d("2"));
        a.setDownPaymentPercent(20);
        a.setDownPaymentAmount(d("3"));
        a.setFinancedAmount(d("4"));
        a.setTermMonths(6);
        a.setAnnualRatePercent(d("5"));
        a.setMonthlyPayment(d("6"));
        a.setTotalPayable(d("7"));
        assertThat(a.getStatus()).isEqualTo("APPROVED");
        assertThat(a.getReviewNote()).isEqualTo("n");
        assertThat(a.getReviewedAt()).isEqualTo(t);
        assertThat(a.getIdNumberMasked()).isEqualTo("*6789");
        assertThat(a.getMonthlyIncome()).isEqualTo(d("1"));
        assertThat(a.getOrderTotal()).isEqualTo(d("2"));
        assertThat(a.getDownPaymentPercent()).isEqualTo(20);
        assertThat(a.getDownPaymentAmount()).isEqualTo(d("3"));
        assertThat(a.getFinancedAmount()).isEqualTo(d("4"));
        assertThat(a.getTermMonths()).isEqualTo(6);
        assertThat(a.getAnnualRatePercent()).isEqualTo(d("5"));
        assertThat(a.getMonthlyPayment()).isEqualTo(d("6"));
        assertThat(a.getTotalPayable()).isEqualTo(d("7"));
    }

    private static Column column(String field) throws NoSuchFieldException {
        return InstallmentApplication.class.getDeclaredField(field).getAnnotation(Column.class);
    }

    private static int precision(String field) throws NoSuchFieldException {
        return column(field).precision();
    }

    private static int scale(String field) throws NoSuchFieldException {
        return column(field).scale();
    }
}
