package com.brixo.slidehub.gateway.ratelimit;

public record GatewayRateLimitDecision(boolean permitted, String ruleName, long retryAfterSeconds) {

    public static GatewayRateLimitDecision allowed() {
        return new GatewayRateLimitDecision(true, null, 0L);
    }

    public static GatewayRateLimitDecision denied(String ruleName, long retryAfterSeconds) {
        return new GatewayRateLimitDecision(false, ruleName, retryAfterSeconds);
    }
}