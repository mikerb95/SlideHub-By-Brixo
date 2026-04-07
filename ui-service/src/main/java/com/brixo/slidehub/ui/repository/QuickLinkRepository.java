package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.QuickLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio JPA de quick links (Fase 4 — PLAN-EXPANSION.md tarea 37).
 */
public interface QuickLinkRepository extends JpaRepository<QuickLink, String> {

    /** Lista los links de una presentación ordenados por displayOrder. */
    List<QuickLink> findByPresentationIdOrderByDisplayOrderAsc(String presentationId);

    /** Cuenta los links de una presentación (para determinar el displayOrder). */
    int countByPresentationId(String presentationId);
}
