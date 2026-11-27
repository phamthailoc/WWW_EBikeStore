package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.controller.cart.CartController;
import com.ebike.controller.order.OrderController;
import com.ebike.controller.order.OrderDocumentController;
import com.ebike.controller.order.OrderDocumentEmailController;
import com.ebike.controller.order.OrderEmailVerificationController;
import com.ebike.controller.order.PaymentController;
import com.ebike.controller.order.ShowroomController;
import com.ebike.controller.order.VnPayController;
import com.ebike.dto.cart.CartLine;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.dto.order.response.GuestOrderLinkResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShowroomResponse;
import com.ebike.dto.order.response.VnPayCreatePaymentResponse;
import com.ebike.service.cart.SessionCartService;
import com.ebike.service.document.DeliveryResendService;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.document.PdfFile;
import com.ebike.service.order.OrderEmailVerificationService;
import com.ebike.service.order.OrderService;
import com.ebike.service.order.PaymentHistoryService;
import com.ebike.service.order.ShowroomService;
import com.ebike.service.order.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class OrderControllersTest {

    OrderService orderService = mock(OrderService.class);
    SessionCartService sessionCart = mock(SessionCartService.class);
    VnPayService vnPay = mock(VnPayService.class);
    MockMvc orders;
    MockMvc payments;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orders = MockMvcBuilders.standaloneSetup(new OrderController(orderService, sessionCart)).build();
        payments = MockMvcBuilders.standaloneSetup(new VnPayController(vnPay)).build();
    }

    OrderResponse prebuilt = mock(OrderResponse.class);

    /** Built before any stubbing starts: creating a mock inside thenReturn(...) would break the pending stubbing. */
    private OrderResponse orderResponse(long id) {
        return prebuilt;
    }

    private static String authority(Class<?> controller, String method, Class<?>... types) throws Exception {
        PreAuthorize preAuthorize = controller.getMethod(method, types).getAnnotation(PreAuthorize.class);
        return preAuthorize == null ? null : preAuthorize.value();
    }

    private static final String CREATE_JSON = """
        {"customerName":"Nguyen Van A","phoneNumber":"0901234567","customerEmail":"a@b.vn","pickupShowroomId":1,
         "paymentMethod":"PAY_LATER","items":[{"productId":1,"quantity":2}],"deliveryMethod":"STORE_PICKUP"}
        """;

    // --- security annotations ------------------------------------------------------------------------

    @Test
    void staffOnlyEndpointsRequireTheUpdateStatusPermission() throws Exception {
        assertThat(authority(OrderController.class, "updateOrderStatus", Long.class, OrderStatusUpdateRequest.class, Authentication.class))
            .isEqualTo("hasAuthority('order:update-status')");
        assertThat(authority(OrderController.class, "updateItemQuantity", Long.class, Long.class,
            com.ebike.dto.order.request.OrderItemQuantityRequest.class, Authentication.class)).isEqualTo("hasAuthority('order:update-status')");
        assertThat(authority(OrderDocumentEmailController.class, "resend", Long.class)).isEqualTo("hasAuthority('order:update-status')");
    }

    @Test
    void customerEndpointsRequireTheirOwnPermissions() throws Exception {
        assertThat(authority(OrderController.class, "requestCancellation", Long.class, OrderCancellationRequest.class, Authentication.class))
            .isEqualTo("hasAuthority('order:cancel-own')");
        assertThat(authority(OrderController.class, "linkGuestOrders", com.ebike.dto.order.request.GuestOrderLinkRequest.class, Authentication.class))
            .isEqualTo("hasAuthority('order:view-own')");
    }

    @Test
    void publicCheckoutEndpointsCarryNoRoleRequirementBecauseGuestsMayOrder() throws Exception {
        assertThat(authority(OrderController.class, "createOrder", com.ebike.dto.order.request.OrderCreateRequest.class, Authentication.class,
            String.class, jakarta.servlet.http.HttpServletRequest.class)).isNull();
        assertThat(authority(OrderController.class, "quoteOrder", com.ebike.dto.order.request.OrderQuoteRequest.class)).isNull();
    }

    // --- OrderController -----------------------------------------------------------------------------

    @Test
    void listPassesEveryFilterAndTheLimitToTheService() throws Exception {
        when(orderService.getOrders(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        orders.perform(get("/api/v1/orders").param("userId", "5").param("status", "PENDING").param("paymentStatus", "PAID")
                .param("search", "abc").param("showroomId", "3").param("limit", "20"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(orderService).getOrders(any(), eq(5L), eq("PENDING"), eq("PAID"), eq("abc"), eq(3L), eq(20));
    }

    @Test
    void listWithoutFiltersPassesNulls() throws Exception {
        when(orderService.getOrders(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        orders.perform(get("/api/v1/orders")).andExpect(status().isOk());
        verify(orderService).getOrders(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void aNonNumericLimitIs400() throws Exception {
        orders.perform(get("/api/v1/orders").param("limit", "many")).andExpect(status().isBadRequest());
        orders.perform(get("/api/v1/orders/abc")).andExpect(status().isBadRequest());
        verify(orderService, never()).getOrders(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getByIdDelegatesWithTheCaller() throws Exception {
        Authentication auth = OrderFixtures.customer("alice");
        when(orderService.getOrderById(eq(7L), any())).thenReturn(orderResponse(7L));
        orders.perform(get("/api/v1/orders/7").principal(auth)).andExpect(status().isOk());
        verify(orderService).getOrderById(7L, auth);
    }

    @Test
    void aServiceErrorKeepsItsStatus() throws Exception {
        when(orderService.getOrderById(eq(7L), any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"));
        orders.perform(get("/api/v1/orders/7")).andExpect(status().isForbidden());
    }

    @Test
    void createReturns201AndDoesNotTouchTheCartWithoutTheHeader() throws Exception {
        when(orderService.createOrder(any(), any())).thenReturn(orderResponse(1L));
        orders.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON)).andExpect(status().isCreated());
        verify(sessionCart, never()).removePurchased(any(), any());
    }

    @Test
    void createFromTheCartRemovesTheBoughtQuantitiesFromTheSessionCart() throws Exception {
        when(orderService.createOrder(any(), any())).thenReturn(orderResponse(1L));
        MockHttpSession session = new MockHttpSession();
        orders.perform(post("/api/v1/orders").session(session).header("X-Cart-Checkout", "1")
            .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON)).andExpect(status().isCreated());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderCreateItemRequest>> bought = ArgumentCaptor.forClass(List.class);
        verify(sessionCart).removePurchased(eq(session), bought.capture());
        assertThat(bought.getValue()).hasSize(1);
        assertThat(bought.getValue().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void anyOtherHeaderValueIsBuyNowAndLeavesTheCart() throws Exception {
        when(orderService.createOrder(any(), any())).thenReturn(orderResponse(1L));
        orders.perform(post("/api/v1/orders").header("X-Cart-Checkout", "0").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
            .andExpect(status().isCreated());
        orders.perform(post("/api/v1/orders").header("X-Cart-Checkout", "true").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
            .andExpect(status().isCreated());
        verify(sessionCart, never()).removePurchased(any(), any());
    }

    @Test
    void aCartFailureAfterTheOrderWasPlacedNeverFailsTheRequest() throws Exception {
        when(orderService.createOrder(any(), any())).thenReturn(orderResponse(1L));
        doThrow(new IllegalStateException("session store down")).when(sessionCart).removePurchased(any(), any());
        orders.perform(post("/api/v1/orders").header("X-Cart-Checkout", "1").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
            .andExpect(status().isCreated());
    }

    @Test
    void aRejectedOrderNeverTouchesTheCart() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "sold out"));
        orders.perform(post("/api/v1/orders").header("X-Cart-Checkout", "1").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
            .andExpect(status().isBadRequest());
        verify(sessionCart, never()).removePurchased(any(), any());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        orders.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
        orders.perform(post("/api/v1/orders/quote").contentType(MediaType.APPLICATION_JSON).content("")).andExpect(status().isBadRequest());
        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void quoteDelegates() throws Exception {
        orders.perform(post("/api/v1/orders/quote").contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[{\"productId\":1,\"quantity\":1}],\"deliveryMethod\":\"HOME_DELIVERY\",\"province\":\"Hà Nội\"}")).andExpect(status().isOk());
        verify(orderService).quoteOrder(any());
    }

    @Test
    void statusPatchSendsStatusAndReason() throws Exception {
        when(orderService.updateOrderStatus(eq(3L), any(), any())).thenReturn(orderResponse(3L));
        orders.perform(patch("/api/v1/orders/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\",\"reason\":\"x\"}"))
            .andExpect(status().isOk());
        ArgumentCaptor<OrderStatusUpdateRequest> captor = ArgumentCaptor.forClass(OrderStatusUpdateRequest.class);
        verify(orderService).updateOrderStatus(eq(3L), captor.capture(), any());
        assertThat(captor.getValue().status()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().reason()).isEqualTo("x");
    }

    @Test
    void itemPatchSendsTheNewQuantityAndTheIds() throws Exception {
        when(orderService.updateItemQuantity(any(), any(), any(), any())).thenReturn(orderResponse(3L));
        orders.perform(patch("/api/v1/orders/3/items/9").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}")).andExpect(status().isOk());
        verify(orderService).updateItemQuantity(eq(3L), eq(9L), eq(4), any());
    }

    @Test
    void cancellationAndGuestLinkAcceptAnEmptyBody() throws Exception {
        when(orderService.requestCancellation(any(), any(), any())).thenReturn(orderResponse(3L));
        when(orderService.linkGuestOrders(any(), any())).thenReturn(new GuestOrderLinkResponse(2));
        orders.perform(post("/api/v1/orders/3/cancellation-request")).andExpect(status().isOk());
        verify(orderService).requestCancellation(eq(3L), isNull(), any());
        orders.perform(post("/api/v1/orders/guest-orders/link")).andExpect(status().isOk()).andExpect(jsonPath("$.linkedOrders").value(2));
        verify(orderService).linkGuestOrders(isNull(), any());
    }

    // --- VnPayController -----------------------------------------------------------------------------

    private VnPayCreatePaymentResponse paymentResponse() {
        return new VnPayCreatePaymentResponse("https://pay", 1L, "EBIKE1", new BigDecimal("100"), null, "ORD-1");
    }

    @Test
    void createPaymentIgnoresAForgedForwardedForHeader() throws Exception {
        when(vnPay.createPaymentUrl(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(paymentResponse());
        payments.perform(post("/api/v1/payments/vnpay/create").header("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1")
            .with(request -> {
                request.setRemoteAddr("192.0.2.9");
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.paymentUrl").value("https://pay"));
        verify(vnPay).createPaymentUrl(eq(new VnPayCreatePaymentRequest(5L, null)), eq("192.0.2.9"), isNull(), eq(false));
    }

    @Test
    void createPaymentIgnoresAForgedRealIpHeaderAndUsesTheRemoteAddress() throws Exception {
        when(vnPay.createPaymentUrl(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(paymentResponse());
        payments.perform(post("/api/v1/payments/vnpay/create").header("X-Real-IP", " 198.51.100.4 ").header("X-Forwarded-For", "  ")
            .with(request -> {
                request.setRemoteAddr("192.0.2.9");
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5}")).andExpect(status().isOk());
        verify(vnPay).createPaymentUrl(any(), eq("192.0.2.9"), any(), eq(false));
        org.mockito.Mockito.verify(vnPay, org.mockito.Mockito.never()).createPaymentUrl(any(), eq("198.51.100.4"), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void onlyThePaymentVerifyPermissionMakesTheCallerBackOffice() throws Exception {
        when(vnPay.createPaymentUrl(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(paymentResponse());
        payments.perform(post("/api/v1/payments/vnpay/create").principal(OrderFixtures.staff("staffy"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5}")).andExpect(status().isOk());
        verify(vnPay).createPaymentUrl(any(), any(), eq("staffy"), eq(true));
        payments.perform(post("/api/v1/payments/vnpay/create").principal(OrderFixtures.auth("viewer", "order:view-all"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5}")).andExpect(status().isOk());
        verify(vnPay).createPaymentUrl(any(), any(), eq("viewer"), eq(false));
        payments.perform(post("/api/v1/payments/vnpay/create").principal(OrderFixtures.customer("alice"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":5}")).andExpect(status().isOk());
        verify(vnPay).createPaymentUrl(any(), any(), eq("alice"), eq(false));
    }

    @Test
    void ipnAcceptsGetAndPostAndReturnsTheGatewayJson() throws Exception {
        when(vnPay.handleIpnCallback(any())).thenReturn(Map.of("RspCode", "00", "Message", "Confirm Success"));
        payments.perform(get("/api/v1/payments/vnpay/ipn").param("vnp_TxnRef", "X").param("vnp_Amount", "100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00")).andExpect(jsonPath("$.Message").value("Confirm Success"));
        payments.perform(post("/api/v1/payments/vnpay/ipn").param("vnp_TxnRef", "X")).andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vnPay, org.mockito.Mockito.times(2)).handleIpnCallback(captor.capture());
        assertThat(captor.getAllValues().get(0)).containsEntry("vnp_TxnRef", "X").containsEntry("vnp_Amount", "100");
    }

    @Test
    void returnUrlIsGetOnly() throws Exception {
        when(vnPay.handleReturnUrl(any())).thenReturn(null);
        payments.perform(get("/api/v1/payments/vnpay/return").param("vnp_TxnRef", "X")).andExpect(status().isOk());
        payments.perform(post("/api/v1/payments/vnpay/return")).andExpect(status().isMethodNotAllowed());
    }

    // --- other controllers ---------------------------------------------------------------------------

    @Test
    void emailVerificationEndpointsDelegate() throws Exception {
        OrderEmailVerificationService verification = mock(OrderEmailVerificationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderEmailVerificationController(verification)).build();
        mvc.perform(post("/api/v1/orders/email-verification/send").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.vn\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/orders/email-verification/verify").contentType(MediaType.APPLICATION_JSON)
            .content("{\"verificationSessionId\":\"" + java.util.UUID.randomUUID() + "\",\"code\":\"123456\"}")).andExpect(status().isOk());
        verify(verification).sendVerificationCode(any());
        verify(verification).verifyCode(any());
    }

    @Test
    void paymentHistoryPassesTheStatusFilter() throws Exception {
        PaymentHistoryService history = mock(PaymentHistoryService.class);
        when(history.getMyPaymentHistory(any(), any())).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(history)).build();
        mvc.perform(get("/api/v1/payments/history").param("status", "PAID")).andExpect(status().isOk());
        verify(history).getMyPaymentHistory(any(), eq("PAID"));
    }

    @Test
    void showroomListPassesTheDistrict() throws Exception {
        ShowroomService showrooms = mock(ShowroomService.class);
        when(showrooms.getShowrooms(any())).thenReturn(List.of(new ShowroomResponse(1L, "S", "Hà Nội", "Cầu Giấy", "addr", "0900", "8-17", true)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ShowroomController(showrooms)).build();
        mvc.perform(get("/api/v1/showrooms").param("district", "Cầu Giấy")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        verify(showrooms).getShowrooms("Cầu Giấy");
    }

    @Test
    void documentDownloadsAreAttachmentsThatAreNeverCachedOrSniffed() throws Exception {
        OrderDocumentService documents = mock(OrderDocumentService.class);
        byte[] pdf = "%PDF-1.4 x".getBytes();
        when(documents.receipt(eq(4L), any())).thenReturn(new PdfFile("PBH-ORD-4.pdf", pdf));
        when(documents.warrantyCard(eq(4L), eq(8L), any())).thenReturn(new PdfFile("BH-8.pdf", pdf));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderDocumentController(documents)).build();
        mvc.perform(get("/api/v1/orders/4/receipt")).andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().longValue("Content-Length", pdf.length));
        mvc.perform(get("/api/v1/orders/4/warranty-card").param("unitId", "8")).andExpect(status().isOk());
        verify(documents).warrantyCard(eq(4L), eq(8L), any());
    }

    @Test
    void resendingTheHandOverEmailReturnsTheAddress() throws Exception {
        DeliveryResendService resender = mock(DeliveryResendService.class);
        when(resender.resend(4L)).thenReturn("khach@example.com");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderDocumentEmailController(resender)).build();
        mvc.perform(post("/api/v1/orders/4/documents/email")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("khach@example.com"));
    }

    // --- CartController ------------------------------------------------------------------------------

    private MockMvc cartMvc() {
        return MockMvcBuilders.standaloneSetup(new CartController(sessionCart, mapper)).build();
    }

    @Test
    void readingTheCartNeverStartsASession() throws Exception {
        when(sessionCart.read(any())).thenReturn(List.of());
        var result = cartMvc().perform(get("/api/v1/cart")).andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verify(sessionCart).read(isNull());
    }

    @Test
    void replacingTheCartStartsASessionAndReturnsTheCheckedLines() throws Exception {
        when(sessionCart.replace(any(), any())).thenReturn(List.of(new CartLine(1L, null, 2, "x1")));
        var result = cartMvc().perform(put("/api/v1/cart").contentType(MediaType.APPLICATION_JSON)
            .content("[{\"id\":1,\"variantId\":null,\"quantity\":2,\"slug\":\"evil\"}]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("x1")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CartLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessionCart).replace(any(), captor.capture());
        assertThat(captor.getValue()).containsExactly(new CartLine(1L, null, 2, "evil"));
    }

    @Test
    void invalidCartBodiesAre400() throws Exception {
        for (String body : new String[] {"nope", "null", "{\"id\":1}", "[1,2]", "[{\"id\":\"x\"}]"}) {
            cartMvc().perform(put("/api/v1/cart").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(sessionCart, never()).replace(any(), any());
    }

    @Test
    void aBodyOverEightKilobytesIs413() throws Exception {
        String padding = " ".repeat(8193);
        cartMvc().perform(put("/api/v1/cart").contentType(MediaType.APPLICATION_JSON).content("[]" + padding)).andExpect(status().isPayloadTooLarge());
        verify(sessionCart, never()).replace(any(), any());
    }

    @Test
    void aBodyOfExactlyEightKilobytesIsStillRead() throws Exception {
        when(sessionCart.replace(any(), any())).thenReturn(List.of());
        String padded = "[]" + " ".repeat(8190);
        assertThat(padded.length()).isEqualTo(8192);
        cartMvc().perform(put("/api/v1/cart").contentType(MediaType.APPLICATION_JSON).content(padded)).andExpect(status().isOk());
    }

    @Test
    void anOversizedBodyWithoutAContentLengthIsStillCutOff() throws Exception {
        CartController controller = new CartController(sessionCart, mapper);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/cart") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked upload: the length is not announced
            }
        };
        request.setContent(("[]" + " ".repeat(9000)).getBytes());
        try {
            controller.replace(request);
            org.junit.jupiter.api.Assertions.fail("expected 413");
        } catch (ResponseStatusException expected) {
            assertThat(expected.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    @Test
    void theCartOnlyAcceptsJson() throws Exception {
        cartMvc().perform(put("/api/v1/cart").contentType(MediaType.TEXT_PLAIN).content("[]")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void deletingTheCartAnswers204AndDoesNotCreateASession() throws Exception {
        var result = cartMvc().perform(delete("/api/v1/cart")).andExpect(status().isNoContent()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verify(sessionCart).clear(isNull());
    }
}
