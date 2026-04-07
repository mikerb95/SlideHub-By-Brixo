package com.brixo.slidehub.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayRateLimitFilterTest {

    private GatewayRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();

        GatewayRateLimitProperties.Rule rule = new GatewayRateLimitProperties.Rule();
        rule.setName("ai-assist-audio");
        rule.setPattern("/api/ai/assist/audio");
        rule.setMethods(List.of("POST"));
        rule.setLimit(1);
        rule.setWindow(Duration.ofMinutes(1));
        properties.setRules(List.of(rule));

        filter = new GatewayRateLimitFilter(new GatewayRateLimitService(properties));
    }

    @Test
    void doFilterInternal_blocksWhenLimitIsExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/ai/assist/audio");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilterInternal_skipsActuatorPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/actuator/health");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}