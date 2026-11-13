package com.ebike.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void switchedOffItNeverBlocks() throws Exception {
        ServerRateLimitFilter filter = new ServerRateLimitFilter(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(200, login(filter, "203.0.113.7", null));
        }
    }
}
