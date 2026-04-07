package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.QuickLink;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.QuickLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD de quick links asociados a una presentación (Fase 4 — PLAN-EXPANSION.md
 * tarea 37).
 *
 * Los quick links aparecen en el sidebar del main-panel y en el bottom sheet
 * del
 * remote, permitiendo al presentador activar demos sin salir de la vista de
 * control.
 */
@Service
public class QuickLinkService {

    private static final Logger log = LoggerFactory.getLogger(QuickLinkService.class);

    private final QuickLinkRepository quickLinkRepository;
    private final PresentationRepository presentationRepository;

    public QuickLinkService(QuickLinkRepository quickLinkRepository,
            PresentationRepository presentationRepository) {
        this.quickLinkRepository = quickLinkRepository;
        this.presentationRepository = presentationRepository;
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    /**
     * Devuelve todos los links de una presentación, ordenados por displayOrder.
     * Validación de propiedad omitida intencionalmente — los links son datos
     * públicos
     * (se muestran en la vista /remote sin auth).
     */
    public List<QuickLink> findByPresentation(String presentationId) {
        return quickLinkRepository.findByPresentationIdOrderByDisplayOrderAsc(presentationId);
    }

    // ── Creación ──────────────────────────────────────────────────────────────

    /**
     * Crea un nuevo quick link para la presentación dada.
     *
     * @param presentationId UUID de la presentación
     * @param title          Texto del botón
     * @param url            URL de destino (demo, recurso, etc.)
     * @param icon           Clase Font Awesome (ej. "fa-brands fa-github"),
     *                       nullable
     * @param description    Descripción corta, nullable
     * @return QuickLink persistido
     * @throws IllegalArgumentException si la presentación no existe
     */
    @Transactional
    public QuickLink create(String presentationId, String title, String url, String icon,
            String description) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Presentación no encontrada: " + presentationId));

        int nextOrder = quickLinkRepository.countByPresentationId(presentationId);

        QuickLink link = new QuickLink();
        link.setId(UUID.randomUUID().toString());
        link.setPresentation(presentation);
        link.setTitle(title);
        link.setUrl(url);
        link.setIcon(icon);
        link.setDescription(description);
        link.setDisplayOrder(nextOrder);

        log.debug("Creando quick link '{}' para presentación {}", title, presentationId);
        return quickLinkRepository.save(link);
    }

    // ── Actualización ─────────────────────────────────────────────────────────

    /**
     * Actualiza los campos de un quick link existente.
     *
     * @throws IllegalArgumentException si el link no existe o no pertenece a la
     *                                  presentación
     */
    @Transactional
    public QuickLink update(String presentationId, String linkId, String title, String url,
            String icon, String description, Integer displayOrder) {
        QuickLink link = quickLinkRepository.findById(linkId)
                .filter(l -> l.getPresentation().getId().equals(presentationId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Quick link no encontrado: " + linkId));

        if (title != null)
            link.setTitle(title);
        if (url != null)
            link.setUrl(url);
        link.setIcon(icon); // puede ser null para borrar icono
        link.setDescription(description);
        if (displayOrder != null)
            link.setDisplayOrder(displayOrder);

        return quickLinkRepository.save(link);
    }

    // ── Eliminación ───────────────────────────────────────────────────────────

    /**
     * Elimina un quick link.
     *
     * @throws IllegalArgumentException si el link no existe o no pertenece a la
     *                                  presentación
     */
    @Transactional
    public void delete(String presentationId, String linkId) {
        QuickLink link = quickLinkRepository.findById(linkId)
                .filter(l -> l.getPresentation().getId().equals(presentationId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Quick link no encontrado: " + linkId));
        log.debug("Eliminando quick link '{}' de presentación {}", link.getTitle(), presentationId);
        quickLinkRepository.delete(link);
    }
}
