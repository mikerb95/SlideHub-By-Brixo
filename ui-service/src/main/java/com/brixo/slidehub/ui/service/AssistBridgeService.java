package com.brixo.slidehub.ui.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class AssistBridgeService {

    private final WebClient aiClient;

    public AssistBridgeService(@Value("${slidehub.ai-service.url}") String aiServiceUrl) {
        this.aiClient = WebClient.builder()
                .baseUrl(aiServiceUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processAudio(byte[] audio,
            String filename,
            String contentType,
            String repoUrl,
            int slideNumber,
            String slideContext) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("audio", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "question.webm";
            }
        }).contentType(MediaType.parseMediaType(
                contentType != null && !contentType.isBlank() ? contentType : "audio/webm"));
        builder.part("repoUrl", repoUrl != null ? repoUrl : "");
        builder.part("slideNumber", String.valueOf(slideNumber));
        builder.part("slideContext", slideContext != null ? slideContext : "");

        Map<String, Object> response = aiClient.post()
                .uri("/api/ai/assist/audio")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return response != null ? response : Map.of("success", false, "errorMessage", "Sin respuesta de ai-service");
    }
}
