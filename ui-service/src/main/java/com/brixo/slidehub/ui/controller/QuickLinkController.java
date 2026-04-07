package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.QuickLink;
import com.brixo.slidehub.ui.service.QuickLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD REST de quick links de una presentación (Fase 4 — PLAN-EXPANSION.md
 * tarea 37).
 *
 * La ruta GET es pública (se consume desde /remote sin autenticación).
 * POST / PUT / DELETE requieren PRESENTER o ADMIN (configurado en
 * SecurityConfig).
 *
 * Seguridad:<br>
 * - GET /api/presentations/{id}/links → permitAll (desde SecurityConfig vía
 * /api/**)<br>
 * - POST / PUT / DELETE → hasAnyRole("PRESENTER","ADMIN") (desde
 * SecurityConfig)
 */
@RestController
@RequestMapping("/api/presentations/{presentationId}/links")
public class QuickLinkController {

    private static final Logger log = LoggerFactory.getLogger(QuickLinkController.class);

    private final QuickLinkService quickLinkService;

    public QuickLinkController(QuickLinkService quickLinkService) {
        this.quickLinkService = quickLinkService;
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    /** Body para creación de quick link. */
    record CreateRequest(String title, String url, String icon, String description) {
    }

    /** Body para actualización de quick link. */
    record UpdateRequest(String title, String url, String icon, String description,
            Integer displayOrder) {
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Lista todos los quick links de la presentación.
     * Acceso público — utilizado por /remote sin autenticación.
     */
    @GetMapping
    public ResponseEntity<List<QuickLink>> listLinks(
            @PathVariable String presentationId) {
        List<QuickLink> links = quickLinkService.findByPresentation(presentationId);
        return ResponseEntity.ok(links);
    }

    /**
     * Crea un nuevo quick link.
     * Requiere PRESENTER o ADMIN.
     */
    @PostMapping
    public ResponseEntity<?> createLink(
            @PathVariable String presentationId,
            @RequestBody CreateRequest body) {
        if (body.title() == null || body.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'title' es obligatorio."));
        }
        if (body.url() == null || body.url().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'url' es obligatorio."));
        }
        try {
            QuickLink link = quickLinkService.create(
                    presentationId, body.title(), body.url(), body.icon(), body.description());
            return ResponseEntity.ok(link);
        } catch (IllegalArgumentException ex) {
            log.warn("Error creando quick link: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Actualiza un quick link existente.
     * Requiere PRESENTER o ADMIN.
     */
    @PutMapping("/{linkId}")
    public ResponseEntity<?> updateLink(
            @PathVariable String presentationId,
            @PathVariable String linkId,
            @RequestBody UpdateRequest body) {
        try {
            QuickLink link = quickLinkService.update(
                    presentationId, linkId,
                    body.title(), body.url(), body.icon(), body.description(),
                    body.displayOrder());
            return ResponseEntity.ok(link);
        } catch (IllegalArgumentException ex) {
            log.warn("Error actualizando quick link {}: {}", linkId, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Elimina un quick link.
     * Requiere PRESENTER o ADMIN.
     */
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> deleteLink(
            @PathVariable String presentationId,
            @PathVariable String linkId) {
        try {
            quickLinkService.delete(presentationId, linkId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Error eliminando quick link {}: {}", linkId, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
