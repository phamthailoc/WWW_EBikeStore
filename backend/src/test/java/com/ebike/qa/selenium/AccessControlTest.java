package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.WorkspacePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class AccessControlTest extends QaBase {

    private String locationOf(Api.Reply r) { return r.headers().firstValue("Location").orElse(""); }

    @Test
    void guestIsSentToLoginWithReturnTo() {
        for (String path : new String[] {"/customer", "/customer/orders", "/manager", "/admin", "/favorites"}) {
            Api.Reply r = new Api().get(path);
            assertEquals(302, r.status(), path);
            assertTrue(locationOf(r).contains("/login"), path + " -> " + locationOf(r));
        }
        ui.open("/customer");
        ui.until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().contains("returnTo="), ui.url());
    }

    @Test
    void guestApiCallsAreRefused() {
        assertEquals(401, new Api().get("/api/v1/orders").status());
        assertEquals(401, new Api().get("/api/v1/auth/profile").status());
        assertEquals(401, new Api().get("/api/v1/admin/accounts").status());
    }

    @Test
    void customerCannotOpenManagerOrAdminPages() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        assertEquals(403, api.get("/manager").status());
        assertEquals(403, api.get("/manager/orders").status());
        assertEquals(403, api.get("/admin").status());
        assertEquals(403, api.get("/admin/accounts").status());
        loginCustomer(c);
        ui.open("/manager");
        assertFalse(ui.exists(By.cssSelector(".work-sidebar a[href='/manager/orders']")), "no manager menu for a customer");
        ui.open("/admin");
        assertFalse(ui.exists(By.cssSelector(".work-sidebar a[href='/admin/accounts']")), "no admin menu for a customer");
    }

    @Test
    void customerCannotCallStaffApis() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        assertEquals(403, api.get("/api/v1/admin/accounts").status());
        assertEquals(403, api.get("/api/v1/admin/promotions").status());
        assertEquals(403, api.get("/api/v1/manager/dashboard").status());
        assertEquals(403, api.get("/api/v1/manager/customers").status());
    }

    @Test
    void managerCannotOpenAdminConsole() {
        Api api = Api.staff("manager");
        assertEquals(403, api.get("/admin").status());
        assertEquals(403, api.get("/admin/accounts").status());
        assertEquals(403, api.get("/api/v1/admin/accounts").status());
        assertEquals(200, api.get("/manager/orders").status());
    }

    @Test
    void managerMenuListsOnlyManagerPages() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        assertTrue(w.sidebar().contains("/manager/orders"));
        assertTrue(w.sidebar().contains("/manager/payments"));
        assertFalse(w.sidebar().stream().anyMatch(h -> h.startsWith("/admin")), w.sidebar().toString());
    }

    @Test
    void adminMenuListsAdminPages() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        assertTrue(w.sidebar().containsAll(java.util.List.of("/admin/accounts", "/admin/pricing", "/admin/roles", "/admin/audit-logs", "/admin/catalog")), w.sidebar().toString());
    }

    @Test
    void customerMenuHasNoStaffLinks() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        assertTrue(w.sidebar().contains("/customer/orders"));
        assertFalse(w.sidebar().stream().anyMatch(h -> h.startsWith("/manager") || h.startsWith("/admin")));
    }

    @Test
    void staffApiNeedsStaffRole() {
        Api admin = Api.staff("admin");
        assertEquals(200, admin.get("/api/v1/admin/accounts").status());
        assertEquals(200, admin.get("/api/v1/admin/roles").status());
        Api manager = Api.staff("manager");
        assertEquals(200, manager.get("/api/v1/manager/dashboard").status());
    }
}
