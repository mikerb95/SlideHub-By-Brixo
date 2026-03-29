package com.brixo.slidehub.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para Google Gemini API (CLAUDE.md §9.1, PLAN-EXPANSION.md Fase
 * 3).
 *
 * Integración exclusivamente vía HTTP / WebClient — sin SDK de Google.
 * Modelo: gemini-1.5-flash (soporta texto + visión multimodal).
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final WebClient geminiClient;

    @Value("${slidehub.ai.gemini.api-key}")
    private String apiKey;

    public GeminiService(@Value("${slidehub.ai.gemini.base-url}") String baseUrl) {
        this.geminiClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16 MB para imágenes
                .build();
    }

    // ── Análisis de imágenes (Vision) ─────────────────────────────────────────

    /**
     * Analiza una diapositiva con Gemini Vision y devuelve la descripción del tema
     * y conceptos técnicos visibles (PLAN-EXPANSION.md Fase 3, tarea 27).
     *
     * @param imageData bytes de la imagen PNG/JPG
     * @return descripción textual del slide, o cadena vacía si hay error
     */
    public String analyzeSlideImage(byte[] imageData) {
        String base64 = Base64.getEncoder().encodeToString(imageData);

        var requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inlineData", Map.of(
                                        "mimeType", "image/png",
                                        "data", base64)),
                                Map.of("text",
                                        "Analiza esta diapositiva de presentación. "
                                                + "¿Cuál es el tema principal? ¿Qué conceptos técnicos se muestran? "
                                                + "¿Qué tecnologías o herramientas se mencionan? "
                                                + "Responde de forma concisa y estructurada en español, "
                                                + "máximo 3-4 oraciones.")))));

        try {
            String text = callGemini(requestBody);
            log.debug("Gemini Vision respondió: {} chars", text.length());
            return text;
        } catch (Exception e) {
            log.error("Error analizando imagen con Gemini Vision: {}", e.getMessage());
            return "";
        }
    }

    // ── Extracción de contexto de repositorio ─────────────────────────────────

    /**
     * Extrae contenido técnico relevante del repositorio GitHub para el contexto
     * del
     * slide (PLAN-EXPANSION.md Fase 3, tarea 28 / CLAUDE.md §9.1).
     *
     * @param repoUrl          URL del repositorio GitHub
     * @param slideDescription descripción del slide (generada por Vision o provista
     *                         como fallback)
     * @return resumen de puntos técnicos relevantes del repositorio, o cadena vacía
     *         si hay
     *         error
     */
    public String extractRepoContext(String repoUrl, String slideDescription) {
        if (repoUrl == null || repoUrl.isBlank()) {
            log.debug("repoUrl vacío, omitiendo extracción de contexto de repositorio.");
            return "";
        }

        String prompt = """
                Analiza el repositorio de GitHub en %s y extrae el contenido más relevante
                para el siguiente tema de diapositiva: "%s"

                Devuelve únicamente los puntos técnicos clave en forma de lista concisa,
                relevantes para que un presentador pueda explicar este slide con profundidad.
                Sin introducción ni conclusión, solo los puntos directamente útiles.
                """.formatted(repoUrl, slideDescription);

        var requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt)))));

        try {
            String text = callGemini(requestBody);
            log.debug("Gemini extractRepoContext respondió: {} chars", text.length());
            return text;
        } catch (Exception e) {
            log.error("Error extrayendo contexto de repo {}: {}", repoUrl, e.getMessage());
            return "";
        }
    }

    // ── Análisis integral de repositorio ─────────────────────────────────────

    /**
     * Análisis técnico profundo de un repositorio GitHub (Fase 3, tarea 33).
     * Detecta lenguaje, framework, stack, arquitectura y genera un Dockerfile
     * candidato.
     *
     * @param repoUrl URL del repositorio GitHub
     * @return mapa con los campos de análisis (language, framework, technologies,
     *         buildSystem, summary, structure, deploymentHints, dockerfile)
     */
    public Map<String, Object> analyzeRepo(String repoUrl) {
        String prompt = """
                Analiza el repositorio de GitHub en %s y responde SOLO con un objeto JSON
                con exactamente estas claves (sin texto adicional antes ni después):
                {
                  "language": "Lenguaje principal (Java, PHP, JavaScript, TypeScript, Python, etc.)",
                  "framework": "Framework principal (Spring Boot, Laravel, Next.js, Django, etc.)",
                  "technologies": ["lista", "de", "tecnologías", "y", "librerías"],
                  "buildSystem": "Maven | Gradle | npm | Composer | pip | etc.",
                  "ports": [8080],
                  "environment": ["DATABASE_URL", "REDIS_HOST"],
                  "databases": ["PostgreSQL", "Redis"],
                  "summary": "Resumen de 1-2 oraciones del propósito del proyecto.",
                  "structure": "Descripción de la arquitectura (microservicios, MVC, monolito, etc.).",
                  "deploymentHints": "Recomendaciones para desplegar en Render, Vercel o Railway.",
                  "dockerfile": "Contenido completo de un Dockerfile apropiado para este proyecto."
                }
                """.formatted(repoUrl);

        var requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"));

        try {
            String rawJson = callGemini(requestBody);
            rawJson = stripMarkdownJson(rawJson);
            log.debug("Gemini analyzeRepo respondió: {} chars", rawJson.length());
            return Map.of("raw", rawJson);
        } catch (Exception e) {
            log.error("Error analizando repositorio {}: {}", repoUrl, e.getMessage());
            return Map.of(
                    "language", "Desconocido",
                    "framework", "Desconocido",
                    "technologies", List.of(),
                    "buildSystem", "Desconocido",
                    "summary", "No se pudo analizar el repositorio.",
                    "structure", "",
                    "deploymentHints", "",
                    "dockerfile", "");
        }
    }

    /**
     * Invocación directa a Gemini para análisis de repositorio.
     * Devuelve la respuesta cruda como texto (JSON o texto según generationConfig).
     */
    public String analyzeRepoRaw(String repoUrl) {
        String prompt = """
                Analiza el repositorio de GitHub en %s y responde SOLO con un objeto JSON
                con exactamente estas claves (sin texto adicional antes ni después):
                {
                  "language": "Lenguaje principal (Java, PHP, JavaScript, TypeScript, Python, etc.)",
                  "framework": "Framework principal (Spring Boot, Laravel, Next.js, Django, etc.)",
                  "technologies": ["lista", "de", "tecnologías"],
                  "buildSystem": "Maven | Gradle | npm | Composer | pip | etc.",
                  "ports": [8080],
                  "environment": ["DATABASE_URL", "REDIS_HOST"],
                  "databases": ["PostgreSQL", "Redis"],
                  "summary": "Resumen de 1-2 oraciones del propósito del proyecto.",
                  "structure": "Descripción de la arquitectura (microservicios, MVC, monolito, etc.).",
                  "deploymentHints": "Recomendaciones para desplegar en Render / Vercel / Railway.",
                  "dockerfile": "Contenido completo de un Dockerfile apropiado para este proyecto."
                }
                """.formatted(repoUrl);

        var requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt)))));

        try {
            return callGemini(requestBody);
        } catch (Exception e) {
            log.error("Error en analyzeRepoRaw para {}: {}", repoUrl, e.getMessage());
            throw new RuntimeException("Error al analizar repositorio con Gemini: " + e.getMessage(), e);
        }
    }

    // ── Método genérico de llamada HTTP ───────────────────────────────────────

    /**
     * Realiza la llamada HTTP a Gemini y extrae el texto de la respuesta.
     *
     * @param requestBody mapa con el cuerpo de la solicitud
     * @return texto generado por Gemini
     */
    @SuppressWarnings("unchecked")
    private String callGemini(Map<?, ?> requestBody) {
        Map<?, ?> response = geminiClient.post()
                .uri("/v1beta/models/gemini-1.5-flash:generateContent?key={key}", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractTextFromResponse(response);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<?, ?> response) {
        if (response == null)
            return "";
        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty())
            return "";
        Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
        if (content == null)
            return "";
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty())
            return "";
        Object text = ((Map<?, ?>) parts.get(0)).get("text");
        return text != null ? text.toString() : "";
    }

    /**
     * Elimina los marcadores de bloque de código Markdown que Gemini a veces añade.
     * Convierte {@code ```json\n...\n```} en el JSON puro.
     */
    private String stripMarkdownJson(String text) {
        if (text == null)
            return "";
        text = text.strip();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.strip();
    }
}
