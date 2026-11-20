package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Audit: a sign-in cookie the server no longer accepts (expired, signed with an old key after a restart, or simply
 * broken) must not lock the browser out. Pages open as signed out and the cookie is cleared; an API call with a bad
 * {@code Authorization: Bearer} header is still refused with 401.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StaleCookieSeleniumTest extends StorefrontSeleniumTest {

    private static final String COOKIE = "ebike_access_token";
    /** A well-formed JWT signed with a key this server does not have. */
    private static final String FOREIGN_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6NDEwMjQ0NDgwMH0."
        + "c2lnbmVkLXdpdGgtYW4tb2xkLWtleS1hZnRlci1yZXN0YXJ0";

    private void plantStaleCookie() {
        driver.get(baseUrl + "/favicon.ico");
        driver.manage().deleteAllCookies();
        driver.manage().addCookie(new Cookie(COOKIE, FOREIGN_TOKEN, "/"));
    }

    @Test
    void loginPageOpensAndTheStaleCookieIsCleared() {
        plantStaleCookie();
        driver.get(baseUrl + "/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("loginForm")));
        assertFalse(driver.getPageSource().contains("Invalid JWT token"));
        assertNull(driver.manage().getCookieNamed(COOKIE), "the server told the browser to drop the cookie");

        // and the customer can sign in again right away
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("main h1")));
    }

    @Test
    void storefrontPagesOpenAsSignedOut() {
        plantStaleCookie();
        for (String path : java.util.List.of("/", "/products", "/product/" + productSlug)) {
            driver.get(baseUrl + path);
            wait.until(d -> Boolean.TRUE.equals(js("return document.readyState==='complete'")));
            assertFalse(driver.getPageSource().contains("Invalid JWT token"), path + " opened");
            assertTrue(driver.findElements(By.cssSelector("header")).size() > 0, path + " rendered the page");
        }
    }

    @Test
    void signedInAreaSendsTheVisitorToSignIn() {
        plantStaleCookie();
        driver.get(baseUrl + "/customer/orders");
        wait.until(d -> d.getCurrentUrl().contains("/login") || !d.findElements(By.id("loginForm")).isEmpty());
    }

    @Test
    void anApiCallWithABadBearerTokenIsStillRefused() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/orders"))
                .header("Authorization", "Bearer " + FOREIGN_TOKEN)
                .header("Accept", "application/json")
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(), response.body());
    }
}
