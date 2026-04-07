package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.HapticEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
public class HapticEventService {

    private static final Logger log = LoggerFactory.getLogger(HapticEventService.class);
    private static final Duration EVENTS_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public HapticEventService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(String participantToken, String pattern, String message) {
        if (participantToken == null || participantToken.isBlank()) {
            throw new IllegalArgumentException("participantToken es obligatorio.");
        }

        String safePattern = (pattern == null || pattern.isBlank()) ? "single" : pattern;
        HapticEvent event = new HapticEvent(safePattern, message != null ? message : "", System.currentTimeMillis());

        String key = eventsKey(participantToken);
        try {
            String json = objectMapper.writeValueAsString(event);
            redis.opsForList().rightPush(key, json);
            redis.expire(key, EVENTS_TTL);
        } catch (Exception ex) {
            log.error("Error guardando evento háptico en Redis: {}", ex.getMessage());
        }
    }

    public Optional<HapticEvent> popNext(String participantToken) {
        if (participantToken == null || participantToken.isBlank()) {
            return Optional.empty();
        }

        String raw = redis.opsForList().leftPop(eventsKey(participantToken));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(raw, HapticEvent.class));
        } catch (Exception ex) {
            log.warn("Error parseando evento háptico: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String eventsKey(String participantToken) {
        return "haptic_events:" + participantToken;
    }
}
