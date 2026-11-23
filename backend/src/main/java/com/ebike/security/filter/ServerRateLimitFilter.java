package com.ebike.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ebike.common.i18n.RequestLanguage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ServerRateLimitFilter extends OncePerRequestFilter {

    private static final Duration STALE_BUCKET_TTL = Duration.ofMinutes(30);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final List<RateLimitRule> rules = List.of(
        new RateLimitRule("auth-login", HttpMethod.POST.name(), "/api/v1/auth/login", 5, Duration.ofMinutes(1)),
        new RateLimitRule("email-otp-send", HttpMethod.POST.name(), "/api/v1/orders/email-verification/send", 3, Duration.ofMinutes(5)),
        new RateLimitRule("chatbot-ask", HttpMethod.POST.name(), "/api/v1/chatbot/ask", 20, Duration.ofMinutes(1)),
        new RateLimitRule("vnpay-create", HttpMethod.POST.name(), "/api/v1/payments/vnpay/create", 10, Duration.ofMinutes(1)),
        // a frame number is a short code: slow down anyone trying many of them
        new RateLimitRule("warranty-lookup", HttpMethod.GET.name(), "/api/v1/warranty/lookup", 20, Duration.ofMinutes(1)),
        // V23: only a flood guard per address, wide enough for a showroom's shared computer or an office behind one address, because a refused
        // choice of a full slot counts here too. What stops one customer from filling a day of slots is the per-account limit in TestRideService.book.
        new RateLimitRule("test-ride-book", HttpMethod.POST.name(), "/api/v1/test-rides", 60, Duration.ofMinutes(10)),
        // V24: the calculator asks again as the slider moves (the page waits a moment between questions); a flood guard per address, wide enough for a shared computer
        new RateLimitRule("installment-quote", HttpMethod.GET.name(), "/api/v1/installments/quote", 90, Duration.ofMinutes(1)),
        // V24: the terms on offer are read from the pricing table (about six queries each time) and are public; each page of the calculator reads them once
        new RateLimitRule("installment-config", HttpMethod.GET.name(), "/api/v1/installments/config", 60, Duration.ofMinutes(1)),
        // V27: each download makes a PDF on the spot (a few tens of milliseconds of CPU, more for an order of many bikes), so one address may not ask in a flood;
        // wide enough for a shop counter that prints one order after another
        new RateLimitRule("order-receipt", HttpMethod.GET.name(), "/api/v1/orders/*/receipt", 40, Duration.ofMinutes(1)),
        new RateLimitRule("order-warranty-card", HttpMethod.GET.name(), "/api/v1/orders/*/warranty-card", 40, Duration.ofMinutes(1)),
        // V27: sending the hand-over email again renders two PDFs and talks to the mail server: a few a minute is plenty for staff
        new RateLimitRule("order-documents-email", HttpMethod.POST.name(), "/api/v1/orders/*/documents/email", 6, Duration.ofMinutes(1)),
        // V26: a receipt adds stock; one address (the stock room's computer) may save a handful a minute, never a flood
        new RateLimitRule("stock-receipt", HttpMethod.POST.name(), "/api/v1/manager/stock-receipts", 20, Duration.ofMinutes(1)),
        // V33: the calculator asks again after the rider stops typing; each answer reads the bike and about three pricing rules, so only a flood guard per address
        new RateLimitRule("product-savings", HttpMethod.GET.name(), "/api/v1/products/*/savings", 90, Duration.ofMinutes(1)),
        // V28: an Excel export reads every order and builds a workbook in memory; staff need a few a minute, never a flood
        new RateLimitRule("revenue-report-export", HttpMethod.GET.name(), "/api/v1/manager/revenue-report/export", 10, Duration.ofMinutes(1)),
        new RateLimitRule("orders-export", HttpMethod.GET.name(), "/api/v1/manager/orders/export", 10, Duration.ofMinutes(1))
    );

    private final boolean enabled;

    public ServerRateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = matchingRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = rule.name() + ":" + clientIdentity(request);
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new TokenBucket(rule.capacity(), rule.window()));
        RateLimitDecision decision = bucket.tryConsume();
        if (!decision.allowed()) {
            writeTooManyRequests(request, response, decision.retryAfterSeconds());
            return;
        }

        cleanupStaleBuckets();
        filterChain.doFilter(request, response);
    }

    private RateLimitRule matchingRule(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return rules.stream()
            .filter(rule -> rule.method().equalsIgnoreCase(request.getMethod()))
            .filter(rule -> pathMatcher.match(rule.pathPattern(), servletPath))
            .findFirst()
            .orElse(null);
    }

    private String clientIdentity(HttpServletRequest request) {
        // Never read X-Forwarded-For here: any client can send it and get a fresh limit with every request.
        // Behind a proxy, Tomcat (server.forward-headers-strategy=native in the prod profile) replaces the address
        // with the real client only when the proxy is a trusted internal one.
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        String message = RequestLanguage.text(request, "Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút.", "You are doing that too fast. Please try again in a few minutes.");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private void cleanupStaleBuckets() {
        if (buckets.size() < 1_000) {
            return;
        }
        long staleBefore = System.nanoTime() - STALE_BUCKET_TTL.toNanos();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastTouchedAt() < staleBefore);
    }

    private record RateLimitRule(String name, String method, String pathPattern, int capacity, Duration window) {
    }

    private record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class TokenBucket {
        private final int capacity;
        private final long windowNanos;
        private int tokens;
        private long refillAt;
        private long lastTouchedAt;

        private TokenBucket(int capacity, Duration window) {
            this.capacity = capacity;
            this.windowNanos = window.toNanos();
            this.tokens = capacity;
            this.refillAt = System.nanoTime() + windowNanos;
            this.lastTouchedAt = System.nanoTime();
        }

        private synchronized RateLimitDecision tryConsume() {
            long now = System.nanoTime();
            lastTouchedAt = now;
            if (now >= refillAt) {
                tokens = capacity;
                refillAt = now + windowNanos;
            }
            if (tokens > 0) {
                tokens -= 1;
                return new RateLimitDecision(true, 0);
            }
            long retryAfterNanos = Math.max(0, refillAt - now);
            long retryAfterSeconds = (long) Math.ceil(retryAfterNanos / 1_000_000_000.0);
            return new RateLimitDecision(false, retryAfterSeconds);
        }

        private long lastTouchedAt() {
            return lastTouchedAt;
        }
    }
}
