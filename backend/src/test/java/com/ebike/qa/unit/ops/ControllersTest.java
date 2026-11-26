package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.controller.common.HealthController;
import com.ebike.controller.notification.NotificationController;
import com.ebike.controller.stock.StockController;
import com.ebike.controller.user.StockAlertController;
import com.ebike.controller.warranty.WarrantyController;
import com.ebike.dto.notification.response.UnreadNotificationCountResponse;
import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.dto.stock.StockDtos.ReceiptPage;
import com.ebike.service.notification.NotificationService;
import com.ebike.service.notification.NotificationStreamService;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockAlertSubscription;
import com.ebike.service.stock.StockHistoryService;
import com.ebike.service.stock.StockReceiptService;
import com.ebike.service.warranty.WarrantyService;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ControllersTest {

    private static final Authentication ALICE = new UsernamePasswordAuthenticationToken("alice", null, List.of());

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()))
            .build();
    }

    // ---------------- stock ----------------
    @Test
    void stockEndpointsAreNeverCachedAndRouteCorrectly() throws Exception {
        StockReceiptService receipts = mock(StockReceiptService.class);
        StockHistoryService history = mock(StockHistoryService.class);
        when(receipts.list(any(), any(), any())).thenReturn(new ReceiptPage(List.of(), false));
        when(receipts.options()).thenReturn(List.of());
        MockMvc m = mvc(new StockController(receipts, history));
        m.perform(get("/api/v1/manager/stock-receipts?q=acme&beforeId=5&limit=7")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(receipts).list("acme", 5L, 7);
        m.perform(get("/api/v1/manager/stock-receipts/options")).andExpect(status().isOk());
        verify(receipts).options();
        verify(receipts, never()).get(anyLong());
    }

    @Test
    void receiptIdMustBeNumeric() throws Exception {
        StockReceiptService receipts = mock(StockReceiptService.class);
        MockMvc m = mvc(new StockController(receipts, mock(StockHistoryService.class)));
        m.perform(get("/api/v1/manager/stock-receipts/abc")).andExpect(status().isNotFound());
        verify(receipts, never()).get(anyLong());
    }

    @Test
    void createReceiptAnswers201AndPassesBody() throws Exception {
        StockReceiptService receipts = mock(StockReceiptService.class);
        MockMvc m = mvc(new StockController(receipts, mock(StockHistoryService.class)));
        m.perform(post("/api/v1/manager/stock-receipts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierName\":\"ACME\",\"lines\":[{\"productId\":1,\"variantId\":2,\"quantity\":3}]}"))
            .andExpect(status().isCreated());
        verify(receipts).create(org.mockito.ArgumentMatchers.argThat(r -> r.supplierName().equals("ACME") && r.lines().get(0).quantity() == 3));
    }

    @Test
    void createReceiptServiceRefusalSurfacesAs400() throws Exception {
        StockReceiptService receipts = mock(StockReceiptService.class);
        when(receipts.create(any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "x"));
        mvc(new StockController(receipts, mock(StockHistoryService.class)))
            .perform(post("/api/v1/manager/stock-receipts").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void malformedReceiptJsonIs400() throws Exception {
        mvc(new StockController(mock(StockReceiptService.class), mock(StockHistoryService.class)))
            .perform(post("/api/v1/manager/stock-receipts").contentType(MediaType.APPLICATION_JSON).content("{bad"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void historyForwardsParameters() throws Exception {
        StockHistoryService history = mock(StockHistoryService.class);
        MockMvc m = mvc(new StockController(mock(StockReceiptService.class), history));
        m.perform(get("/api/v1/manager/products/3/stock-history?variantId=4&reason=SALE&beforeId=9&limit=10")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(history).history(3L, 4L, "SALE", 9L, 10);
        m.perform(get("/api/v1/manager/products/x/stock-history")).andExpect(status().isBadRequest());
    }

    @Test
    void receiptEndpointsDemandTheStockPermission() throws Exception {
        for (String name : new String[] {"list", "options", "get", "create"}) {
            Method found = null;
            for (Method method : StockController.class.getDeclaredMethods()) {
                if (method.getName().equals(name)) found = method;
            }
            PreAuthorize pre = found.getAnnotation(PreAuthorize.class);
            assertThat(pre).as(name).isNotNull();
            assertThat(pre.value()).contains(PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK);
        }
    }

    // ---------------- warranty ----------------
    @Test
    void warrantyLookupIsPublicAndForwardsFrame() throws Exception {
        WarrantyService svc = mock(WarrantyService.class);
        MockMvc m = mvc(new WarrantyController(svc));
        m.perform(get("/api/v1/warranty/lookup?frame=AB123456")).andExpect(status().isOk());
        verify(svc).lookup("AB123456");
        m.perform(get("/api/v1/warranty/lookup")).andExpect(status().isOk());
        verify(svc).lookup(isNull());
    }

    @Test
    void warrantyBadFrameBubblesUp400And404() throws Exception {
        WarrantyService svc = mock(WarrantyService.class);
        when(svc.lookup("bad")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "x"));
        when(svc.lookup("none12345")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "x"));
        MockMvc m = mvc(new WarrantyController(svc));
        m.perform(get("/api/v1/warranty/lookup?frame=bad")).andExpect(status().isBadRequest());
        m.perform(get("/api/v1/warranty/lookup?frame=none12345")).andExpect(status().isNotFound());
    }

    @Test
    void createClaimUsesAuthenticatedNameNotBody() throws Exception {
        WarrantyService svc = mock(WarrantyService.class);
        mvc(new WarrantyController(svc)).perform(post("/api/v1/warranty/claims").principal(ALICE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitId\":5,\"description\":\"brake broken\",\"username\":\"mallory\"}"))
            .andExpect(status().isCreated());
        verify(svc).createClaim("alice", 5L, "brake broken");
    }

    @Test
    void staffQueueReportsHasNextHeader() throws Exception {
        WarrantyService svc = mock(WarrantyService.class);
        when(svc.listClaims(any(), anyInt(), anyInt())).thenReturn(new WarrantyService.ClaimPage(List.of(), true));
        mvc(new WarrantyController(svc)).perform(get("/api/v1/manager/warranty-claims?status=NEW&page=2&size=5"))
            .andExpect(status().isOk()).andExpect(header().string("X-Has-Next", "true"));
        verify(svc).listClaims("NEW", 2, 5);
        mvc(new WarrantyController(svc)).perform(get("/api/v1/manager/warranty-claims"));
        verify(svc).listClaims(isNull(), eq(0), eq(WarrantyService.DEFAULT_PAGE_SIZE));
    }

    @Test
    void patchClaimPassesIdAndBody() throws Exception {
        WarrantyService svc = mock(WarrantyService.class);
        mvc(new WarrantyController(svc)).perform(patch("/api/v1/manager/warranty-claims/9")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RECEIVED\",\"staffNote\":\"n\"}"))
            .andExpect(status().isOk());
        verify(svc).updateClaim(eq(9L), org.mockito.ArgumentMatchers.argThat(r -> "RECEIVED".equals(r.status()) && "n".equals(r.staffNote())));
    }

    // ---------------- notifications ----------------
    @Test
    void notificationEndpointsDelegateWithAuthentication() throws Exception {
        NotificationService svc = mock(NotificationService.class);
        NotificationStreamService stream = mock(NotificationStreamService.class);
        when(svc.getUnreadCount(ALICE)).thenReturn(new UnreadNotificationCountResponse(3));
        when(svc.getCurrentUserId(ALICE)).thenReturn(7L);
        when(stream.subscribe(7L)).thenReturn(new SseEmitter());
        MockMvc m = mvc(new NotificationController(svc, stream));
        m.perform(get("/api/v1/notifications/unread-count").principal(ALICE)).andExpect(jsonPath("$.unreadCount").value(3));
        m.perform(get("/api/v1/notifications").principal(ALICE)).andExpect(status().isOk());
        m.perform(patch("/api/v1/notifications/12/read").principal(ALICE)).andExpect(status().isOk());
        verify(svc).markAsRead(12L, ALICE);
        m.perform(patch("/api/v1/notifications/read-all").principal(ALICE)).andExpect(status().isOk());
        verify(svc).markAllAsRead(ALICE);
        m.perform(get("/api/v1/notifications/stream").principal(ALICE)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());
        verify(stream).subscribe(7L);
    }

    @Test
    void markReadOfForeignNotificationIs404FromService() throws Exception {
        NotificationService svc = mock(NotificationService.class);
        when(svc.markAsRead(anyLong(), any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "x"));
        mvc(new NotificationController(svc, mock(NotificationStreamService.class)))
            .perform(patch("/api/v1/notifications/5/read").principal(ALICE)).andExpect(status().isNotFound());
        mvc(new NotificationController(svc, mock(NotificationStreamService.class)))
            .perform(patch("/api/v1/notifications/abc/read").principal(ALICE)).andExpect(status().isBadRequest());
    }

    // ---------------- stock alerts ----------------
    private static StockAlertResponse alert() {
        return new StockAlertResponse(1L, 1L, "B", "b", 11L, "Red", 0, java.time.LocalDateTime.now(), null, true);
    }

    @Test
    void subscribeAnswers201WhenCreatedAnd200WhenAlreadyWaiting() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        when(svc.subscribe("alice", 11L)).thenReturn(new StockAlertSubscription(alert(), true));
        when(svc.subscribe("alice", 12L)).thenReturn(new StockAlertSubscription(alert(), false));
        MockMvc m = mvc(new StockAlertController(svc));
        m.perform(post("/api/v1/stock-alerts").principal(ALICE).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":11}"))
            .andExpect(status().isCreated());
        m.perform(post("/api/v1/stock-alerts").principal(ALICE).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":12}"))
            .andExpect(status().isOk());
    }

    @Test
    void subscribeBadBodiesGetFriendly400() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        MockMvc m = mvc(new StockAlertController(svc));
        m.perform(post("/api/v1/stock-alerts").principal(ALICE).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":\"abc\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng chọn màu xe."));
        m.perform(post("/api/v1/stock-alerts").principal(ALICE).contentType(MediaType.APPLICATION_JSON).content("not json"))
            .andExpect(status().isBadRequest());
        m.perform(post("/api/v1/stock-alerts").principal(ALICE).contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType());
        m.perform(post("/api/v1/stock-alerts").principal(ALICE)).andExpect(status().isBadRequest());
        verify(svc, never()).subscribe(anyString(), any());
    }

    @Test
    void missingVariantIdReachesServiceAs400() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        when(svc.subscribe("alice", null)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "x"));
        mvc(new StockAlertController(svc)).perform(post("/api/v1/stock-alerts").principal(ALICE)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void jsonNullBodyIsBadRequestNotServerError() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        var result = mvc(new StockAlertController(svc)).perform(post("/api/v1/stock-alerts").principal(ALICE)
            .contentType(MediaType.APPLICATION_JSON).content("null")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void unsubscribeAnswers204AndRejectsNonNumeric() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        MockMvc m = mvc(new StockAlertController(svc));
        m.perform(delete("/api/v1/stock-alerts/11").principal(ALICE)).andExpect(status().isNoContent());
        verify(svc).unsubscribe("alice", 11L);
        m.perform(delete("/api/v1/stock-alerts/abc").principal(ALICE)).andExpect(status().isBadRequest());
    }

    @Test
    void listIsScopedToAuthenticatedUser() throws Exception {
        StockAlertService svc = mock(StockAlertService.class);
        when(svc.list("alice")).thenReturn(List.of(alert()));
        mvc(new StockAlertController(svc)).perform(get("/api/v1/stock-alerts").principal(ALICE))
            .andExpect(jsonPath("$[0].variantId").value(11));
    }

    // ---------------- health ----------------
    private static HealthController health(DataSource ds) {
        HealthController c = new HealthController(ds);
        ReflectionTestUtils.setField(c, "applicationName", "ebike-backend");
        ReflectionTestUtils.setField(c, "instanceId", "i1");
        return c;
    }

    @Test
    void healthLiveAndReadyUp() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.createStatement()).thenReturn(st);
        MockMvc m = mvc(health(ds));
        m.perform(get("/api/v1/health")).andExpect(jsonPath("$.status").value("UP"));
        m.perform(get("/api/v1/health/live")).andExpect(jsonPath("$.status").value("UP"));
        m.perform(get("/api/v1/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.dependencies.database").value("UP"));
        verify(c).close();
        verify(st).close();
    }

    @Test
    void readyIs503WhenDatabaseDownButLiveStaysUp() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("down"));
        MockMvc m = mvc(health(ds));
        m.perform(get("/api/v1/health/ready")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN")).andExpect(jsonPath("$.dependencies.database").value("DOWN"));
        m.perform(get("/api/v1/health/live")).andExpect(status().isOk());
        m.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void readyIs503WhenQueryFailsAndConnectionStillClosed() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.createStatement()).thenReturn(st);
        doThrow(new SQLException("bad")).when(st).execute(anyString());
        mvc(health(ds)).perform(get("/api/v1/health/ready")).andExpect(status().isServiceUnavailable());
        verify(c).close();
    }
}
