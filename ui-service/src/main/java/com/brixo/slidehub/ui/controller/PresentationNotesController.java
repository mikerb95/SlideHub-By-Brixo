package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.Slide;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.NotesBridgeService;
import com.brixo.slidehub.ui.service.PresentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller de generación de notas del presentador con IA (PLAN-EXPANSION.md
 * Fase 3,
 * tareas 35).
 *
 * Actúa como puente entre ui-service (datos de presentación en PostgreSQL)
 * y ai-service (generación de notas con Gemini + Groq).
 */
@Controller
public class PresentationNotesController {

    private static final Logger log = LoggerFactory.getLogger(PresentationNotesController.class);

    private final PresentationService presentationService;
    private final NotesBridgeService notesBridgeService;
    private final UserRepository userRepository;

    public PresentationNotesController(PresentationService presentationService,
            NotesBridgeService notesBridgeService,
            UserRepository userRepository) {
        this.presentationService = presentationService;
        this.notesBridgeService = notesBridgeService;
        this.userRepository = userRepository;
    }

    // ── Vista MVC ─────────────────────────────────────────────────────────────

    /**
     * Vista principal de generación de notas para una presentación.
     * Muestra los slides con thumbnails y permite lanzar la generación con IA.
     */
    @GetMapping("/presentations/{id}/generate-notes")
    public String generateNotesPage(@PathVariable String id,
            Authentication authentication,
            Model model) {
        User user = resolveUser(authentication);
        Optional<Presentation> opt = presentationService.getPresentation(user.getId(), id);

        if (opt.isEmpty()) {
            return "redirect:/presentations/import";
        }

        Presentation presentation = opt.get();
        // Carga notas existentes para mostrarlas en la página
        List<Map<String, Object>> existingNotes = notesBridgeService.getNotes(id);

        model.addAttribute("presentation", presentation);
        model.addAttribute("existingNotes", existingNotes);
        model.addAttribute("totalSlides", presentation.getSlides().size());
        model.addAttribute("hasRepoUrl",
                presentation.getRepoUrl() != null && !presentation.getRepoUrl().isBlank());
        return "presentations/generate-notes";
    }

    // ── API JSON ──────────────────────────────────────────────────────────────

    /**
     * Dispara la generación de notas para todos los slides de una presentación.
     *
     * Flujo:
     * 1. Obtiene la presentación del usuario (con slides y S3 URLs)
     * 2. Construye la lista de referencias de slides
     * 3. Llama a ai-service generate-all
     * 4. Devuelve número de notas generadas
     *
     * @param id      ID de la presentación
     * @param repoUrl URL del repositorio (opcional; si en blanco, usa el guardado
     *                en la presentación)
     */
    @PostMapping("/api/presentations/{id}/generate-notes")
    @ResponseBody
    public ResponseEntity<?> triggerGenerateNotes(
            @PathVariable String id,
            @RequestParam(required = false) String repoUrl,
            Authentication authentication) {

        User user = resolveUser(authentication);
        Optional<Presentation> opt = presentationService.getPresentation(user.getId(), id);

        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Presentation presentation = opt.get();

        // Prioridad: param repoUrl → guardado en la presentación
        String effectiveRepoUrl = (repoUrl != null && !repoUrl.isBlank())
                ? repoUrl
                : presentation.getRepoUrl();

        if (presentation.getSlides().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La presentación no tiene slides."));
        }

        // Construir lista de referencias de slides para ai-service
        List<Map<String, Object>> slideRefs = presentation.getSlides().stream()
                .map(slide -> {
                    Map<String, Object> ref = new HashMap<>();
                    ref.put("slideNumber", slide.getNumber());
                    ref.put("imageUrl", slide.getS3Url());
                    return ref;
                })
                .toList();

        try {
            int generated = notesBridgeService.generateAllNotes(id, effectiveRepoUrl, slideRefs);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "notesGenerated", generated,
                    "totalSlides", presentation.getSlides().size()));
        } catch (Exception e) {
            log.error("Error generando notas para presentación {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al generar notas: " + e.getMessage()));
        }
    }

    /**
     * Obtiene las notas generadas de una presentación (proxy a ai-service).
     *
     * @param id ID de la presentación
     */
    @GetMapping("/api/presentations/{id}/notes")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getNotes(
            @PathVariable String id,
            Authentication authentication) {
        User user = resolveUser(authentication);
        // Validar ownership
        if (presentationService.getPresentation(user.getId(), id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> notes = notesBridgeService.getNotes(id);
        return ResponseEntity.ok(notes);
    }

    /**
     * Analiza el repositorio asociado a la presentación y devuelve metadatos
     * técnicos.
     *
     * @param id ID de la presentación
     */
    @PostMapping("/api/presentations/{id}/analyze-repo")
    @ResponseBody
    public ResponseEntity<?> analyzeRepo(@PathVariable String id,
            Authentication authentication) {
        User user = resolveUser(authentication);
        Optional<Presentation> opt = presentationService.getPresentation(user.getId(), id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String repoUrl = opt.get().getRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Esta presentación no tiene URL de repositorio configurada."));
        }
        Map<String, Object> analysis = notesBridgeService.analyzeRepo(repoUrl);
        return ResponseEntity.ok(analysis);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User resolveUser(Authentication authentication) {
        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado: " + identifier));
    }
}
