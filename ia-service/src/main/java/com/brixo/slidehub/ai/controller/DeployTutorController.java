package com.brixo.slidehub.ai.controller;

import com.brixo.slidehub.ai.model.DeploymentGuide;
import com.brixo.slidehub.ai.model.RepoAnalysis;
import com.brixo.slidehub.ai.service.DeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints del Tutor de Deployment (PLAN-EXPANSION.md Fase 5, tarea 42).
 *
 * Tres operaciones disponibles:
 * <ol>
 * <li>{@code POST /api/ai/deploy/analyze} — análisis técnico del
 * repositorio</li>
 * <li>{@code POST /api/ai/deploy/dockerfile} — genera Dockerfile
 * optimizado</li>
 * <li>{@code POST /api/ai/deploy/guide} — guía de despliegue paso a paso</li>
 * </ol>
 *
 * Acceso: público (sin autenticación en ai-service; la seguridad se aplica en
 * ui-service para la vista {@code /deploy-tutor}).
 */
@RestController
@RequestMapping("/api/ai/deploy")
public class DeployTutorController {

    private static final Logger log = LoggerFactory.getLogger(DeployTutorController.class);

    private final DeploymentService deploymentService;

    public DeployTutorController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    // ── Request records ───────────────────────────────────────────────────────

    /** Body para la generación de Dockerfile. */
    record DockerfileRequest(String repoUrl, String language, String framework,
            List<Integer> ports, List<String> environment) {
    }

    /** Body para la generación de guía de despliegue. */
    record GuideRequest(String repoUrl, String platform) {
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Analiza el repositorio GitHub y devuelve su análisis técnico completo.
     *
     * Consulta la cache de MongoDB antes de llamar a Gemini.
     *
     * @param body mapa con clave {@code repoUrl}
     * @return {@link RepoAnalysis} con language, framework, technologies, ports,
     *         databases, etc.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        try {
            RepoAnalysis analysis = deploymentService.analyzeRepository(repoUrl);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error analizando repositorio {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al analizar el repositorio: " + e.getMessage()));
        }
    }

    /**
     * Genera un Dockerfile optimizado para el proyecto descrito.
     *
     * Acepta los datos del análisis previo para evitar una llamada adicional a
     * Gemini.
     *
     * @param req cuerpo con repoUrl, language, framework, ports y environment
     * @return {@code { dockerfile: "FROM ..." }}
     */
    @PostMapping("/dockerfile")
    public ResponseEntity<?> generateDockerfile(@RequestBody DockerfileRequest req) {
        if (req.repoUrl() == null || req.repoUrl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }
        if (req.language() == null || req.language().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'language' es obligatorio."));
        }
        if (req.framework() == null || req.framework().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'framework' es obligatorio."));
        }

        try {
            String dockerfile = deploymentService.generateDockerfile(
                    req.repoUrl(), req.language(), req.framework(),
                    req.ports(), req.environment());
            return ResponseEntity.ok(Map.of("dockerfile", dockerfile));
        } catch (Exception e) {
            log.error("Error generando Dockerfile para {}: {}", req.repoUrl(), e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al generar Dockerfile: " + e.getMessage()));
        }
    }

    /**
     * Genera (o recupera del cache) una guía de despliegue para la plataforma
     * indicada.
     *
     * @param req cuerpo con repoUrl y platform ("render" | "vercel" | "netlify")
     * @return {@link DeploymentGuide} con guide, tips, dockerfile y
     *         environmentExample
     */
    @PostMapping("/guide")
    public ResponseEntity<?> generateGuide(@RequestBody GuideRequest req) {
        if (req.repoUrl() == null || req.repoUrl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        String platform = req.platform();
        if (platform == null || platform.isBlank()) {
            platform = "render"; // default
        }
        if (!List.of("render", "vercel", "netlify").contains(platform.toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Plataforma no soportada. Usar: render, vercel o netlify."));
        }

        try {
            DeploymentGuide guide = deploymentService.generateDeploymentGuide(
                    req.repoUrl(), platform.toLowerCase());
            return ResponseEntity.ok(guide);
        } catch (Exception e) {
            log.error("Error generando guía de despliegue para {}/{}: {}",
                    req.repoUrl(), platform, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al generar la guía: " + e.getMessage()));
        }
    }

    /**
     * Regenera la guía ignorando el cache de MongoDB.
     *
     * @param req cuerpo con repoUrl y platform
     * @return nueva {@link DeploymentGuide} generada
     */
    @PostMapping("/guide/refresh")
    public ResponseEntity<?> regenerateGuide(@RequestBody GuideRequest req) {
        if (req.repoUrl() == null || req.repoUrl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        String platform = req.platform() != null ? req.platform().toLowerCase() : "render";

        try {
            DeploymentGuide guide = deploymentService.regenerateDeploymentGuide(
                    req.repoUrl(), platform);
            return ResponseEntity.ok(guide);
        } catch (Exception e) {
            log.error("Error regenerando guía para {}/{}: {}", req.repoUrl(), platform, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al regenerar la guía: " + e.getMessage()));
        }
    }
}
