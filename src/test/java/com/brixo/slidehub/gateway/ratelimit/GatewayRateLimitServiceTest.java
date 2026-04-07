package com.brixo.slidehub.gateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRateLimitServiceTest {

    private GatewayRateLimitProperties properties;
    private GatewayRateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new GatewayRateLimitProperties();
        properties.setRetryAfterSeconds(60);

        GatewayRateLimitProperties.Rule assistAudio = new GatewayRateLimitProperties.Rule();
        assistAudio.setName("ai-assist-audio");
        assistAudio.setPattern("/api/ai/assist/audio");
        assistAudio.setMethods(List.of("POST"));
        assistAudio.setLimit(2);
        assistAudio.setWindow(Duration.ofMinutes(1));

        GatewayRateLimitProperties.Rule meetingAudio = new GatewayRateLimitProperties.Rule();
        meetingAudio.setName("meeting-assist-audio");
        meetingAudio.setPattern("/api/presentations/*/meeting/assist/audio");
        meetingAudio.setMethods(List.of("POST"));
        meetingAudio.setLimit(1);
        meetingAudio.setWindow(Duration.ofMinutes(1));
        meetingAudio.setKeyQueryParams(List.of("joinToken", "participantToken"));

        properties.setRules(List.of(assistAudio, meetingAudio));
        service = new GatewayRateLimitService(properties);
    }

    @Test
    void evaluate_allowsRequestsWithinLimit() {
        GatewayRateLimitDecision first = service.evaluate(request("POST", "/api/ai/assist/audio"));
        GatewayRateLimitDecision second = service.evaluate(request("POST", "/api/ai/assist/audio"));

        assertThat(first.permitted()).isTrue();
        assertThat(second.permitted()).isTrue();
    }

    @Test
    void evaluate_blocksAfterLimit() {
        service.evaluate(request("POST", "/api/ai/assist/audio"));
        service.evaluate(request("POST", "/api/ai/assist/audio"));

        GatewayRateLimitDecision third = service.evaluate(request("POST", "/api/ai/assist/audio"));

        assertThat(third.permitted()).isFalse();
        assertThat(third.ruleName()).isEqualTo("ai-assist-audio");
        assertThat(third.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void evaluate_separatesBucketsByQueryTokens() {
        GatewayRateLimitDecision firstToken = service.evaluate(request("POST",
                "/api/presentations/pres-1/meeting/assist/audio", "joinToken", "join-a", "participantToken",
                "part-a"));
        GatewayRateLimitDecision secondToken = service.evaluate(request("POST",
                "/api/presentations/pres-1/meeting/assist/audio", "joinToken", "join-b", "participantToken",
                "part-b"));

        assertThat(firstToken.permitted()).isTrue();
        assertThat(secondToken.permitted()).isTrue();
    }

    @Test
    void evaluate_ignoresUnmatchedPaths() {
        GatewayRateLimitDecision decision = service.evaluate(request("GET", "/api/slide"));

        assertThat(decision.permitted()).isTrue();
    }

    private HttpServletRequest request(String method, String path, String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setRemoteAddr("203.0.113.9");
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        return request;
    }
}