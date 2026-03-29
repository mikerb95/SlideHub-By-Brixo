package com.brixo.slidehub.ai.controller;

import com.brixo.slidehub.ai.model.RepoAnalysis;
import com.brixo.slidehub.ai.service.RepoAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint para análisis técnico de repositorios GitHub (PLAN-EXPANSION.md Fase
 * 3,
 * tarea 33).
 *
 * Cachea los resultados en MongoDB para evitar llamadas repetidas a Gemini.
 * El análisis se reutiliza en la generación de notas (Fase 3) y en el tutor de
 * despliegue (Fase 5).
 */
@RestController
@RequestMapping("/api/ai")
public class RepoAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(RepoAnalysisController.class);

    private final RepoAnalysisService repoAnalysisService;

    public RepoAnalysisController(RepoAnalysisService repoAnalysisService) {
        this.repoAnalysisService = repoAnalysisService;
    }

    /**
     * Analiza un repositorio GitHub y devuelve su análisis técnico completo.
     *
     * Comprueba primero la cache MongoDB; si no existe, llama a Gemini.
     *
     * @param request cuerpo con campo {@code repoUrl}
     */
    @PostMapping("/analyze-repo")
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> request) {
        String repoUrl = request.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        try {
            RepoAnalysis analysis = repoAnalysisService.analyze(repoUrl);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error analizando repositorio {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al analizar el repositorio: " + e.getMessage()));
        }
    }

    /**
     * Fuerza un nuevo análisis del repositorio, ignorando la cache MongoDB.
     *
     * @param request cuerpo con campo {@code repoUrl}
     */
    @PostMapping("/analyze-repo/refresh")
    public ResponseEntity<?> reanalyzeRepo(@RequestBody Map<String, String> request) {
        String repoUrl = request.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        try {
            RepoAnalysis analysis = repoAnalysisService.reanalyze(repoUrl);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error re-analizando repositorio {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al re-analizar el repositorio: " + e.getMessage()));
        }
    }
}
