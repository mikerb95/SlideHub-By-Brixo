package com.brixo.slidehub.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Servicio de email via Resend API (CLAUDE.md §9.5.1).
 *
 * Integración HTTP pura con WebClient — sin JavaMail, Spring Mail ni Resend
 * SDK.
 * Endpoint: POST https://api.resend.com/emails
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final WebClient resendClient;

    @Value("${slidehub.resend.api-key}")
    private String apiKey;

    @Value("${slidehub.resend.from}")
    private String fromAddress;

    public EmailService() {
        this.resendClient = WebClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    /**
     * Envía un email HTML via Resend API.
     *
     * @param to      dirección de destino
     * @param subject asunto del mensaje
     * @param html    cuerpo HTML
     */
    public void send(String to, String subject, String html) {
        try {
            resendClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "from", fromAddress,
                            "to", List.of(to),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnError(e -> log.error("Error enviando email a {}: {}", to, e.getMessage()))
                    .block();
        } catch (Exception e) {
            // No propagar el error de email para no bloquear el flujo de registro
            log.error("Fallo al enviar email a {} (asunto: {}): {}", to, subject, e.getMessage());
        }
    }
}
