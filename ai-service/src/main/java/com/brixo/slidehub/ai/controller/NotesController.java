package com.brixo.slidehub.ai.controller;

import com.brixo.slidehub.ai.model.GenerateAllRequest;
import com.brixo.slidehub.ai.model.GenerateNoteRequest;
import com.brixo.slidehub.ai.model.PresenterNote;
import com.brixo.slidehub.ai.repository.PresenterNoteRepository;
import com.brixo.slidehub.ai.service.NotesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller de notas del presentador (HU-016, HU-017, HU-018, HU-019, HU-020).
 *
 * Fase 3: implementa pipeline completo Gemini Vision + Gemini repo + Groq.
 */
@RestController
@RequestMapping("/api/ai/notes")
public class NotesController {

    private static final Logger log = LoggerFactory.getLogger(NotesController.class);

    private final PresenterNoteRepository repository;
    private final NotesService notesService;

    public NotesController(PresenterNoteRepository repository, NotesService notesService) {
        this.repository = repository;
        this.notesService = notesService;
    }

    /**
     * Health check del servicio de IA (HU-020).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "ai-service"));
    }

    /**
     * Genera nota para un slide vía pipeline de IA:
     * Gemini Vision → Gemini repo context → Groq (HU-016, Fase 3 tarea 31).
     *
     * @param request cuerpo con presentationId, slideNumber, repoUrl,
     *                imageData/imageUrl,
     *                slideContext
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestBody GenerateNoteRequest request) {
        try {
            PresenterNote note = notesService.generate(request);
            return ResponseEntity.ok(Map.of("success", true, "note", note));
        } catch (Exception e) {
            log.error("Error generando nota para slide {}: {}",
                    request.slideNumber(), e.getMessage());
            return ResponseEntity.ok(
                    Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Genera notas para todos los slides de una presentación (Fase 3, tarea 32).
     *
     * @param request cuerpo con presentationId, repoUrl y lista de slides (número +
     *                URL imagen)
     */
    @PostMapping("/generate-all")
    public ResponseEntity<Map<String, Object>> generateAll(
            @RequestBody GenerateAllRequest request) {
        try {
            int generated = notesService.generateAll(request);
            return ResponseEntity.ok(Map.of("success", true, "notesGenerated", generated));
        } catch (Exception e) {
            log.error("Error en generate-all para {}: {}", request.presentationId(), e.getMessage());
            return ResponseEntity.ok(
                    Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Lista todas las notas de una presentación (HU-017).
     */
    @GetMapping("/{presentationId}")
    public ResponseEntity<List<PresenterNote>> listNotes(@PathVariable String presentationId) {
        List<PresenterNote> notes = repository.findByPresentationIdOrderBySlideNumberAsc(presentationId);
        return ResponseEntity.ok(notes);
    }

    /**
     * Obtiene la nota de un slide específico (HU-018).
     * Responde 204 No Content si no existe — nunca 404 (HU-018 §2).
     */
    @GetMapping("/{presentationId}/{slideNumber}")
    public ResponseEntity<PresenterNote> getNote(@PathVariable String presentationId,
            @PathVariable int slideNumber) {
        return repository.findByPresentationIdAndSlideNumber(presentationId, slideNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Elimina todas las notas de una presentación (HU-019).
     * Responde 204 No Content aunque no existan notas (HU-019 §2).
     */
    @DeleteMapping("/{presentationId}")
    public ResponseEntity<Void> deleteNotes(@PathVariable String presentationId) {
        repository.deleteByPresentationId(presentationId);
        return ResponseEntity.noContent().build();
    }
}
