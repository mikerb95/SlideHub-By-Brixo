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
 * Servicio puente entre ui-service y ai-service para generación de notas
 * (PLAN-EXPANSION.md Fase 3).
 *
 * El ui-service tiene acceso a los datos de la presentación (slides + S3 URLs)
 * en
 * PostgreSQL. Este servicio construye la solicitud completa para ai-service,
 * incluyendo las URLs de las imágenes, y la envía vía WebClient.
 */
@Service
public class NotesBridgeService {

    private static final Logger log = LoggerFactory.getLogger(NotesBridgeService.class);

    private final WebClient aiClient;

    public NotesBridgeService(@Value("${slidehub.ai-service.url}") String aiServiceUrl) {
        this.aiClient = WebClient.builder()
                .baseUrl(aiServiceUrl)
                .build();
    }

    // ── Generación de notas ───────────────────────────────────────────────────

    /**
     * Solicita al ai-service que genere notas para todos los slides de una
     * presentación.
     *
     * @param presentationId ID de la presentación
     * @param repoUrl        URL del repo GitHub (puede ser null)
     * @param slideRefs      lista de pares {slideNumber, imageUrl} de S3
     * @return número de notas generadas
     */
    public int generateAllNotes(String presentationId, String repoUrl,
            List<Map<String, Object>> slideRefs) {
        var requestBody = Map.of(
                "presentationId", presentationId,
                "repoUrl", repoUrl != null ? repoUrl : "",
                "slides", slideRefs);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aiClient.post()
                    .uri("/api/ai/notes/generate-all")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null)
                return 0;
            Object notesGenerated = response.get("notesGenerated");
            return notesGenerated instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            log.error("Error llamando a ai-service generate-all para {}: {}",
                    presentationId, e.getMessage());
            throw new RuntimeException("Error al generar notas en ai-service: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene todas las notas generadas de una presentación.
     *
     * @param presentationId ID de la presentación
     * @return lista de notas (o lista vacía si no hay ninguna)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getNotes(String presentationId) {
        try {
            List<Map<String, Object>> notes = aiClient.get()
                    .uri("/api/ai/notes/{id}", presentationId)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .cast(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block();
            return notes != null ? notes : List.of();
        } catch (Exception e) {
            log.error("Error obteniendo notas de ai-service para {}: {}",
                    presentationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Solicita al ai-service un análisis técnico del repositorio.
     *
     * @param repoUrl URL del repositorio GitHub
     * @return mapa con los campos del análisis (language, framework, technologies,
     *         etc.)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRepo(String repoUrl) {
        try {
            Map<String, Object> result = (Map<String, Object>) aiClient.post()
                    .uri("/api/ai/analyze-repo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("repoUrl", repoUrl))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.error("Error analizando repo {} en ai-service: {}", repoUrl, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}
