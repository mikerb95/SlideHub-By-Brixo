package com.brixo.slidehub.ui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Enlace rápido asociado a una presentación (PLAN-EXPANSION.md Fase 2 tarea 20
 * / Fase 4).
 *
 * Se muestra en el main-panel como acceso directo a demos, recursos externos,
 * etc.
 * Estructura: icono (Font Awesome) + título + URL + descripción opcional.
 */
@Entity
@Table(name = "quick_links")
public class QuickLink {

    @Id
    @Column(length = 36)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** Nombre de clase de Font Awesome 6 (ej. "fa-brands fa-github"). */
    @Column(length = 100)
    private String icon;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Orden de visualización (ascendente). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // ── Constructores ─────────────────────────────────────────────────────────

    public QuickLink() {
    }

    // ── Getters y setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Presentation getPresentation() {
        return presentation;
    }

    public void setPresentation(Presentation presentation) {
        this.presentation = presentation;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
