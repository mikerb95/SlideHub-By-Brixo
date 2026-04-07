package com.brixo.slidehub.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewayRateLimitFilter extends OncePerRequestFilter {

    private final GatewayRateLimitService rateLimitService;

    public GatewayRateLimitFilter(GatewayRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || path.startsWith("/actuator") || "/error".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        GatewayRateLimitDecision decision = rateLimitService.evaluate(request);
        if (decision.permitted()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"rule\":\""
                + nullToEmpty(decision.ruleName())
                + "\",\"retryAfterSeconds\":"
                + decision.retryAfterSeconds()
                + "}");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}