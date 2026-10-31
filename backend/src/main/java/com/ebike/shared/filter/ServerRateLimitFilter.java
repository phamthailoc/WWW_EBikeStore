package com.ebike.shared.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        new RateLimitRule("auth-login", HttpMethod.POST.name(), "/auth/login", 5, Duration.ofMinutes(1)),
        new RateLimitRule("email-otp-send", HttpMethod.POST.name(), "/orders/email-verification/send", 3, Duration.ofMinutes(5)),
        new RateLimitRule("chatbot-ask", HttpMethod.POST.name(), "/chatbot/ask", 20, Duration.ofMinutes(1)),
        new RateLimitRule("vnpay-create", HttpMethod.POST.name(), "/payments/vnpay/create", 10, Duration.ofMinutes(1))
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
            writeTooManyRequests(response, decision.retryAfterSeconds());
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
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.getWriter().write("{\"message\":\"Too many requests. Please retry later.\"}");
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
