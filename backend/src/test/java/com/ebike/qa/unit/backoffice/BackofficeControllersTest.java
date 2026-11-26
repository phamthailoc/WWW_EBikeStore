package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.controller.admin.AdminCatalogController;
import com.ebike.controller.admin.AdminController;
import com.ebike.controller.manager.ManagerController;
import com.ebike.controller.manager.ReportExportController;
import com.ebike.controller.support.SupportController;
import com.ebike.controller.user.FavoriteController;
import com.ebike.controller.user.UserController;
import com.ebike.service.admin.AdminCatalogService;
import com.ebike.service.admin.AdminService;
import com.ebike.service.chatbot.ChatbotService;
import com.ebike.service.manager.ManagerService;
import com.ebike.service.order.OrderUnitService;
import com.ebike.service.report.ReportExportService;
import com.ebike.service.support.SupportService;
import com.ebike.service.user.FavoriteService;
import com.ebike.service.user.UserProfileService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BackofficeControllersTest {

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static String guard(Class<?> type, String method) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                return pa == null ? null : pa.value();
            }
        }
        throw new IllegalArgumentException(method);
    }

    // ------------------------------------------------------------------ authority guards

    @Test
    void managerController_everyEndpointDeclaresItsPermission() {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("replaceUnits", PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS),
            Map.entry("getDashboard", PermissionConstants.Reporting.DASHBOARD_VIEW),
            Map.entry("getPayments", PermissionConstants.OrderManagement.PAYMENT_VERIFY),
            Map.entry("confirmPayLaterPayment", PermissionConstants.OrderManagement.PAYMENT_VERIFY),
            Map.entry("refundPayment", PermissionConstants.OrderManagement.PAYMENT_REFUND),
            Map.entry("getCustomers", PermissionConstants.UserManagement.USER_VIEW),
            Map.entry("getRevenueReport", PermissionConstants.Reporting.REPORT_VIEW),
            Map.entry("updatePickupDeadline", PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS),
            Map.entry("updateOrderShipment", PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS),
            Map.entry("updateProductStock", PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK),
            Map.entry("updateVariantStock", PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK));
        expected.forEach((method, authority) ->
            assertThat(guard(ManagerController.class, method)).as(method).isEqualTo("hasAuthority('" + authority + "')"));
    }

    @Test
    void refund_needsAStrongerPermissionThanConfirming() {
        assertThat(guard(ManagerController.class, "refundPayment")).isNotEqualTo(guard(ManagerController.class, "confirmPayLaterPayment"));
    }

    @Test
    void exportController_guardsBothDownloads() {
        assertThat(guard(ReportExportController.class, "revenueReport")).contains(PermissionConstants.Reporting.REPORT_VIEW);
        assertThat(guard(ReportExportController.class, "orders")).contains(PermissionConstants.OrderManagement.ORDER_VIEW_ALL);
    }

    @Test
    void catalogController_everyEndpointDeclaresItsPermission() {
        String update = "hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_UPDATE + "')";
        String category = "hasAuthority('" + PermissionConstants.ProductManagement.CATEGORY_MANAGE + "')";
        assertThat(guard(AdminCatalogController.class, "products")).isEqualTo(update);
        assertThat(guard(AdminCatalogController.class, "product")).isEqualTo(update);
        assertThat(guard(AdminCatalogController.class, "updateProduct")).isEqualTo(update);
        assertThat(guard(AdminCatalogController.class, "createProduct")).contains(PermissionConstants.ProductManagement.PRODUCT_CREATE);
        assertThat(guard(AdminCatalogController.class, "deleteProduct")).contains(PermissionConstants.ProductManagement.PRODUCT_DELETE);
        for (String m : List.of("categories", "category", "createCategory", "updateCategory", "deleteCategory")) {
            assertThat(guard(AdminCatalogController.class, m)).as(m).isEqualTo(category);
        }
    }

    // ------------------------------------------------------------------ routing

    @Test
    void manager_revenueReport_defaultsToDayPeriod_andForwardsRange() throws Exception {
        ManagerService svc = mock(ManagerService.class);
        MockMvc m = mvc(new ManagerController(svc, mock(OrderUnitService.class)));
        m.perform(get("/api/v1/manager/revenue-report")).andExpect(status().isOk());
        verify(svc).getRevenueReport("day", null, null);
        m.perform(get("/api/v1/manager/revenue-report").param("period", "custom").param("from", "2025-01-01").param("to", "2025-01-31"))
            .andExpect(status().isOk());
        verify(svc).getRevenueReport("custom", "2025-01-01", "2025-01-31");
    }

    @Test
    void manager_confirmAcceptsEmptyBody_refundAcceptsBody() throws Exception {
        ManagerService svc = mock(ManagerService.class);
        MockMvc m = mvc(new ManagerController(svc, mock(OrderUnitService.class)));
        m.perform(patch("/api/v1/manager/payments/5/confirm")).andExpect(status().isOk());
        verify(svc).confirmPayLaterPayment(5L, null);
        m.perform(patch("/api/v1/manager/payments/5/refund").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"n\"}"))
            .andExpect(status().isOk());
        verify(svc).refundPayment(eq(5L), any());
    }

    @Test
    void manager_nonNumericIdIs400_notServerError() throws Exception {
        MockMvc m = mvc(new ManagerController(mock(ManagerService.class), mock(OrderUnitService.class)));
        m.perform(patch("/api/v1/manager/payments/abc/confirm")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("paymentId")));
    }

    @Test
    void manager_stockAndShipmentRoutes() throws Exception {
        ManagerService svc = mock(ManagerService.class);
        MockMvc m = mvc(new ManagerController(svc, mock(OrderUnitService.class)));
        m.perform(patch("/api/v1/manager/products/3/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\":4}"))
            .andExpect(status().isOk());
        m.perform(patch("/api/v1/manager/products/3/variants/9/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\":4}"))
            .andExpect(status().isOk());
        m.perform(patch("/api/v1/manager/orders/7/shipment").contentType(MediaType.APPLICATION_JSON).content("{\"shipmentStatus\":\"SHIPPED\"}"))
            .andExpect(status().isOk());
        verify(svc).updateProductStock(eq(3L), any());
        verify(svc).updateVariantStock(eq(3L), eq(9L), any());
        verify(svc).updateOrderShipment(eq(7L), any());
    }

    @Test
    void exportDownload_hasXlsxHeaders_noStore_nosniff_attachment() throws Exception {
        ReportExportService svc = mock(ReportExportService.class);
        when(svc.revenueReport("day", null, null)).thenReturn(new ReportExportService.ExportFile("revenue-report_a_b.xlsx", new byte[] {1, 2, 3}));
        mvc(new ReportExportController(svc)).perform(get("/api/v1/manager/revenue-report/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", ReportExportController.XLSX.toString()))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("attachment"), org.hamcrest.Matchers.containsString("revenue-report_a_b.xlsx"))))
            .andExpect(header().longValue("Content-Length", 3));
    }

    @Test
    void adminController_deleteNeedsConfirmEmailParameter() throws Exception {
        AdminService svc = mock(AdminService.class);
        MockMvc m = mvc(new AdminController(svc));
        m.perform(delete("/api/v1/admin/accounts/5")).andExpect(status().isBadRequest());
        verify(svc, org.mockito.Mockito.never()).deleteAccount(any(), any());
        m.perform(delete("/api/v1/admin/accounts/5").param("confirmEmail", "a@b.com")).andExpect(status().isNoContent());
        verify(svc).deleteAccount(5L, "a@b.com");
    }

    @Test
    void adminController_createsReturn201_andPatchRoutes() throws Exception {
        AdminService svc = mock(AdminService.class);
        MockMvc m = mvc(new AdminController(svc));
        m.perform(post("/api/v1/admin/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\"}")).andExpect(status().isCreated());
        m.perform(post("/api/v1/admin/promotions").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\"}")).andExpect(status().isCreated());
        m.perform(patch("/api/v1/admin/accounts/5/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}")).andExpect(status().isOk());
        m.perform(patch("/api/v1/admin/accounts/5/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}")).andExpect(status().isOk());
        m.perform(patch("/api/v1/admin/pricing-rules/2").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5}")).andExpect(status().isOk());
        m.perform(get("/api/v1/admin/accounts").param("type", "ADMIN").param("q", "bo")).andExpect(status().isOk());
        verify(svc).getAccounts("ADMIN", "bo");
        verify(svc).updateAccountRole(eq(5L), any());
        verify(svc).updateAccountStatus(eq(5L), any());
    }

    @Test
    void adminCatalog_createdIs201_deleteIs204_malformedJsonIs400() throws Exception {
        AdminCatalogService svc = mock(AdminCatalogService.class);
        MockMvc m = mvc(new AdminCatalogController(svc));
        m.perform(post("/api/v1/admin/catalog/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")).andExpect(status().isCreated());
        m.perform(delete("/api/v1/admin/catalog/products/4")).andExpect(status().isNoContent());
        m.perform(post("/api/v1/admin/catalog/categories").contentType(MediaType.APPLICATION_JSON).content("{bad")).andExpect(status().isBadRequest());
        m.perform(post("/api/v1/admin/catalog/products").contentType(MediaType.APPLICATION_JSON).content("{\"price\":\"abc\"}")).andExpect(status().isBadRequest());
        verify(svc).deleteProduct(4L);
    }

    @Test
    void userController_passesAuthenticationToTheService() throws Exception {
        UserProfileService svc = mock(UserProfileService.class);
        MockMvc m = mvc(new UserController(svc));
        var auth = Fx.auth("tien", "profile:view");
        m.perform(get("/api/v1/users/5/addresses").principal(auth)).andExpect(status().isOk());
        verify(svc).getAddresses(5L, auth);
        m.perform(post("/api/v1/users/5/addresses").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{\"street\":\"x\",\"city\":\"y\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void favoriteController_usesTheSignedInName_notAParameter() throws Exception {
        FavoriteService svc = mock(FavoriteService.class);
        MockMvc m = mvc(new FavoriteController(svc));
        var auth = Fx.auth("tien");
        m.perform(post("/api/v1/favorites").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":4}")).andExpect(status().isCreated());
        verify(svc).addFavorite("tien", 4L);
        m.perform(delete("/api/v1/favorites/4").param("username", "victim").principal(auth)).andExpect(status().isNoContent());
        verify(svc).removeFavorite("tien", 4L);
    }

    @Test
    void supportController_createIs201_staffListFiltersByStatus() throws Exception {
        SupportService svc = mock(SupportService.class);
        MockMvc m = mvc(new SupportController(svc, mock(ChatbotService.class)));
        m.perform(post("/api/v1/support-requests").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\"}")).andExpect(status().isCreated());
        m.perform(get("/api/v1/manager/support-requests").param("status", "NEW")).andExpect(status().isOk());
        verify(svc).list("NEW");
        m.perform(patch("/api/v1/manager/support-requests/3").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
        verify(svc).update(eq(3L), any());
    }
}
