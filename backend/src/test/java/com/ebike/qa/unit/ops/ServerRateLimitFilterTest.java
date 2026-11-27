package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.security.filter.ServerRateLimitFilter;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class ServerRateLimitFilterTest {

    private static MockHttpServletRequest req(String method, String path, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        r.setRequestURI(path);
        r.setRemoteAddr(ip);
        return r;
    }

    private static int hit(ServerRateLimitFilter f, String method, String path, String ip) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req(method, path, ip), resp, new MockFilterChain());
        return resp.getStatus();
    }

    static Stream<Arguments> limits() {
        return Stream.of(
            Arguments.of("POST", "/api/v1/auth/login", 5, 60),
            Arguments.of("POST", "/api/v1/orders/email-verification/send", 3, 300),
            Arguments.of("POST", "/api/v1/orders/email-verification/verify", 10, 300),
            Arguments.of("POST", "/api/v1/auth/register", 5, 600),
            Arguments.of("POST", "/api/v1/chatbot/ask", 20, 60),
            Arguments.of("POST", "/api/v1/payments/vnpay/create", 10, 60),
            Arguments.of("GET", "/api/v1/warranty/lookup", 20, 60),
            Arguments.of("POST", "/api/v1/test-rides", 60, 600),
            Arguments.of("GET", "/api/v1/products/12/savings", 90, 60),
            Arguments.of("POST", "/api/v1/manager/stock-receipts", 20, 60),
            Arguments.of("POST", "/api/v1/orders/5/documents/email", 6, 60),
            Arguments.of("GET", "/api/v1/installments/quote", 90, 60),
            Arguments.of("GET", "/api/v1/installments/config", 60, 60),
            Arguments.of("GET", "/api/v1/orders/5/receipt", 40, 60),
            Arguments.of("GET", "/api/v1/orders/5/warranty-card", 40, 60),
            Arguments.of("GET", "/api/v1/manager/orders/export", 10, 60),
            Arguments.of("GET", "/api/v1/manager/revenue-report/export", 10, 60),
            Arguments.of("PUT", "/api/v1/cart", 120, 60));
    }

    @ParameterizedTest(name = "{0} {1}: {2} then 429")
    @MethodSource("limits")
    void exactLimitAllowedLimitPlusOneRefused(String method, String path, int limit, int windowSeconds) throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 1; i <= limit; i++) {
            assertThat(hit(f, method, path, "10.0.0.1")).as("request " + i).isEqualTo(200);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req(method, path, "10.0.0.1"), resp, chain);
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).as("chain must not run").isNull();
        long retry = Long.parseLong(resp.getHeader("Retry-After"));
        assertThat(retry).isBetween(1L, (long) windowSeconds);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("message");
        // another address is not affected
        assertThat(hit(f, method, path, "10.0.0.2")).isEqualTo(200);
    }

    @Test
    void windowResetsWhenItExpires() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 5; i++) hit(f, "POST", "/api/v1/auth/login", "1.1.1.1");
        assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);
        expireAllBuckets(f);
        for (int i = 1; i <= 5; i++) {
            assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).as("after reset " + i).isEqualTo(200);
        }
        assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);
    }

    @SuppressWarnings("unchecked")
    private static void expireAllBuckets(ServerRateLimitFilter f) {
        Map<String, Object> buckets = (Map<String, Object>) ReflectionTestUtils.getField(f, "buckets");
        buckets.values().forEach(b -> ReflectionTestUtils.setField(b, "refillAt", System.nanoTime() - 1));
    }

    @Test
    void disabledFlagLetsEverythingThrough() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(false);
        for (int i = 0; i < 50; i++) assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    void xForwardedForCannotBuyAFreshBucket() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest r = req("POST", "/api/v1/auth/login", "9.9.9.9");
            r.addHeader("X-Forwarded-For", "5.5.5." + i);
            r.addHeader("X-Real-IP", "6.6.6." + i);
            f.doFilter(r, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest sixth = req("POST", "/api/v1/auth/login", "9.9.9.9");
        sixth.addHeader("X-Forwarded-For", "7.7.7.7");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(sixth, resp, new MockFilterChain());
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    void rulesAreIndependentPerPathAndPerMethod() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 5; i++) hit(f, "POST", "/api/v1/auth/login", "1.1.1.1");
        assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);
        assertThat(hit(f, "POST", "/api/v1/chatbot/ask", "1.1.1.1")).isEqualTo(200);
        assertThat(hit(f, "GET", "/api/v1/auth/login", "1.1.1.1")).as("different method has no rule").isEqualTo(200);
    }

    @Test
    void optionsPreflightIsNeverCountedOrBlocked() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 20; i++) assertThat(hit(f, "OPTIONS", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(200);
        for (int i = 0; i < 5; i++) assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    void methodMatchIsCaseInsensitive() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 5; i++) hit(f, "post", "/api/v1/auth/login", "1.1.1.1");
        assertThat(hit(f, "POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);
    }

    @Test
    void unlimitedPathsAreNeverCounted() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 300; i++) assertThat(hit(f, "GET", "/api/v1/products", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    void wildcardSegmentDoesNotSpanSeveralSegments() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 100; i++) assertThat(hit(f, "GET", "/api/v1/orders/5/6/receipt", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    void staleBucketsAreCleanedWhenMapGrows() throws Exception {
        ServerRateLimitFilter f = new ServerRateLimitFilter(true);
        for (int i = 0; i < 1005; i++) hit(f, "POST", "/api/v1/chatbot/ask", "ip-" + i);
        @SuppressWarnings("unchecked")
        Map<String, Object> buckets = (Map<String, Object>) ReflectionTestUtils.getField(f, "buckets");
        assertThat(buckets).hasSizeGreaterThan(1000);
        buckets.values().forEach(b -> ReflectionTestUtils.setField(b, "lastTouchedAt", System.nanoTime() - java.time.Duration.ofMinutes(31).toNanos()));
        hit(f, "POST", "/api/v1/chatbot/ask", "fresh");
        assertThat(buckets.size()).isLessThan(10);
    }

}
