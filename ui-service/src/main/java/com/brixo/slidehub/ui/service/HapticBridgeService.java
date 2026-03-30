package com.brixo.slidehub.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class HapticBridgeService {

    private static final Logger log = LoggerFactory.getLogger(HapticBridgeService.class);

    private final WebClient stateClient;

    public HapticBridgeService(@Value("${slidehub.state-service.url}") String stateServiceUrl) {
        this.stateClient = WebClient.builder()
                .baseUrl(stateServiceUrl)
                .build();
    }

    public void publishSingle(String participantToken, String message) {
        publish(participantToken, "single", message);
    }

    public void publishTriple(String participantToken, String message) {
        publish(participantToken, "triple", message);
    }

    private void publish(String participantToken, String pattern, String message) {
        try {
            stateClient.post()
                    .uri("/api/haptics/events/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "participantToken", participantToken,
                            "pattern", pattern,
                            "message", message))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception ex) {
            log.warn("No se pudo publicar evento háptico ({}) para token {}: {}",
                    pattern, participantToken, ex.getMessage());
        }
    }
}
