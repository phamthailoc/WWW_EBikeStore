package com.ebike.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Audit: the login limit (5 per minute) counts the connection's address, not headers a client can make up. */
class ServerRateLimitFilterTest {

    private static int login(ServerRateLimitFilter filter, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
            request.addHeader("X-Real-IP", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void aNewForwardedForOnEveryRequestDoesNotResetTheLimit() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, login(filter, "203.0.113.7", "10.0.0." + i), "attempt " + i);
        }
        assertEquals(429, login(filter, "203.0.113.7", "10.0.0.99"));
        assertEquals(429, login(filter, "203.0.113.7", null));
    }

    @Test
    void anotherAddressHasItsOwnLimit() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        for (int i = 0; i < 5; i++) {
            login(filter, "203.0.113.7", null);
        }
        assertEquals(429, login(filter, "203.0.113.7", null));
        assertEquals(200, login(filter, "198.51.100.4", null));
    }


    @Test
    void theTooManyRequestsMessageFollowsTheLanguageOfTheRequest() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        for (int i = 0; i < 20; i++) {
            lookup(filter, null);
        }
        assertTrue(lookup(filter, "en").contains("too fast"));
        assertTrue(lookup(filter, null).contains("quá nhanh"));
        assertTrue(lookup(filter, "vi").contains("quá nhanh"));
    }

    private static String lookup(ServerRateLimitFilter filter, String languageCookie) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/warranty/lookup");
        request.setServletPath("/api/v1/warranty/lookup");
        request.setRemoteAddr("203.0.113.9");
        if (languageCookie != null) {
            request.setCookies(new jakarta.servlet.http.Cookie("ebike_lang", languageCookie));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getContentAsString();
    }
    @Test
    void theSavingsCalculatorHasAFloodGuardPerAddressSharedByEveryBike() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        int status = 200;
        int allowed = 0;
        while (status == 200 && allowed < 300) {
            String path = "/api/v1/products/" + (allowed % 2 == 0 ? "amy" : "ella") + "/savings";
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            request.setRemoteAddr("203.0.113.40");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
            if (status == 200) {
                allowed++;
            }
        }
        assertEquals(429, status);
        assertEquals(90, allowed);
    }

    @Test
    void thePublicInstalmentConfigHasAFloodGuardPerAddress() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        int status = 200;
        int allowed = 0;
        while (status == 200 && allowed < 200) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/installments/config");
            request.setServletPath("/api/v1/installments/config");
            request.setRemoteAddr("203.0.113.30");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
            if (status == 200) {
                allowed++;
            }
        }
        assertEquals(429, status);
        assertEquals(60, allowed);
    }

    @Test
    void thePdfDownloadsHaveAFloodGuardPerAddressAndEachKindCountsOnItsOwn() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(true);
        for (int i = 0; i < 40; i++) {
            assertEquals(200, download(filter, "/api/v1/orders/" + i + "/receipt"), "receipt " + i);
        }
        assertEquals(429, download(filter, "/api/v1/orders/41/receipt"));
        assertEquals(200, download(filter, "/api/v1/orders/41/warranty-card"));
        // the order detail itself is not limited by these rules
        assertEquals(200, download(filter, "/api/v1/orders/41"));
    }

    private static int download(ServerRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.40");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void switchedOffItNeverBlocks() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, login(filter, "203.0.113.7", null));
        }
    }
}
