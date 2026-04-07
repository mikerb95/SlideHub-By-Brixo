package com.brixo.slidehub.ui.model;

import java.time.LocalDateTime;

/**
 * Resumen de presentación para listados (JSON).
 *
 * Devuelve solo los campos necesarios para la UI de lista,
 * sin cargar los slides ni los quick links.
 */
public record PresentationSummary(
        String id,
        String name,
        String description,
        String sourceType,
        int totalSlides,
        LocalDateTime createdAt) {
    public static PresentationSummary from(Presentation p) {
        return new PresentationSummary(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getSourceType().name(),
                p.getSlides().size(),
                p.getCreatedAt());
    }
}
