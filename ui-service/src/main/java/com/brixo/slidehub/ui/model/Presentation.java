package com.brixo.slidehub.ui.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Presentación de diapositivas (PLAN-EXPANSION.md Fase 2, tarea 18).
 *
 * Contiene metadatos, origen (Drive o upload) y la lista ordenada de slides.
 * Persistida en PostgreSQL (Aiven en prod, H2 en dev).
 */
@Entity
@Table(name = "presentations")
public class Presentation {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    /** ID de la carpeta de Drive de la que se importaron los slides. */
    @Column(name = "drive_folder_id", length = 200)
    private String driveFolderId;

    /** Nombre de la carpeta de Drive (solo a efectos de mostrar en UI). */
    @Column(name = "drive_folder_name", length = 200)
    private String driveFolderName;

    /**
     * URL del repositorio GitHub asociado a esta presentación.
     * Usada en Fase 3 por Gemini para extraer contexto técnico.
     */
    @Column(name = "repo_url", columnDefinition = "TEXT")
    private String repoUrl;

    @OneToMany(mappedBy = "presentation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("number ASC")
    private List<Slide> slides = new ArrayList<>();

    @OneToMany(mappedBy = "presentation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<QuickLink> quickLinks = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Constructores ─────────────────────────────────────────────────────────

    public Presentation() {
    }

    // ── Getters y setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }

    public void setDriveFolderName(String driveFolderName) {
        this.driveFolderName = driveFolderName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public List<Slide> getSlides() {
        return slides;
    }

    public void setSlides(List<Slide> slides) {
        this.slides = slides;
    }

    public List<QuickLink> getQuickLinks() {
        return quickLinks;
    }

    public void setQuickLinks(List<QuickLink> quickLinks) {
        this.quickLinks = quickLinks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
