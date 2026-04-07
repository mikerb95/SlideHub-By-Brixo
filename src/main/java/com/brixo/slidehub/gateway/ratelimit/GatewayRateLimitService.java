package com.brixo.slidehub.gateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GatewayRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitService.class);

    private final GatewayRateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, WindowBucket> buckets = new ConcurrentHashMap<>();

    public GatewayRateLimitService(GatewayRateLimitProperties properties) {
        this.properties = properties;
    }

    public GatewayRateLimitDecision evaluate(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return GatewayRateLimitDecision.allowed();
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        for (GatewayRateLimitProperties.Rule rule : properties.getRules()) {
            if (!matches(rule, path, method)) {
                continue;
            }

            return evaluateRule(request, rule, path);
        }

        return GatewayRateLimitDecision.allowed();
    }

    private boolean matches(GatewayRateLimitProperties.Rule rule, String path, String method) {
        if (rule == null || !rule.isEnabled()) {
            return false;
        }

        if (rule.getPattern() == null || !pathMatcher.match(rule.getPattern(), path)) {
            return false;
        }

        List<String> methods = rule.getMethods();
        return methods == null || methods.isEmpty() || methods.stream().anyMatch(m -> m.equalsIgnoreCase(method));
    }

    private GatewayRateLimitDecision evaluateRule(HttpServletRequest request, GatewayRateLimitProperties.Rule rule,
            String path) {
        long now = System.currentTimeMillis();
        String key = buildKey(request, rule, path);
        String bucketKey = rule.getName() + "|" + key;

        WindowBucket bucket = buckets.computeIfAbsent(bucketKey,
                ignored -> new WindowBucket(now + rule.getWindow().toMillis()));

        synchronized (bucket) {
            if (bucket.isExpired(now)) {
                bucket.reset(now + rule.getWindow().toMillis());
            }

            if (bucket.getCount() < rule.getLimit()) {
                bucket.increment();
                return GatewayRateLimitDecision.allowed();
            }

            long retryAfterSeconds = Math.max(1L,
                    (bucket.getExpiresAt() - now + 999L) / 1000L);
            log.warn("Rate limit exceeded for rule={} key={}", rule.getName(), key);
            return GatewayRateLimitDecision.denied(rule.getName(), retryAfterSeconds);
        }
    }

    private String buildKey(HttpServletRequest request, GatewayRateLimitProperties.Rule rule, String path) {
        StringBuilder key = new StringBuilder(resolveClientIp(request)).append('|').append(path);

        List<String> params = rule.getKeyQueryParams();
        if (params != null) {
            for (String param : params) {
                key.append('|').append(param).append('=').append(nullToEmpty(request.getParameter(param)));
            }
        }

        return key.toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    @Scheduled(fixedDelayString = "${slidehub.gateway.rate-limit.cleanup-interval-ms:600000}")
    public void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class WindowBucket {

        private long expiresAt;
        private int count;

        private WindowBucket(long expiresAt) {
            this.expiresAt = expiresAt;
            this.count = 0;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }

        private void reset(long expiresAt) {
            this.expiresAt = expiresAt;
            this.count = 0;
        }

        private void increment() {
            this.count++;
        }

        private long getExpiresAt() {
            return expiresAt;
        }

        private int getCount() {
            return count;
        }
    }
}