package com.brixo.slidehub.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Guía de despliegue generada por IA para un repositorio y plataforma dados
 * (PLAN-EXPANSION.md Fase 5, tarea 43).
 *
 * Colección: {@code deployment_guides}
 * Indexada por {@code (repoUrl, platform)} para consultas de cache rápidas.
 */
@Document(collection = "deployment_guides")
@CompoundIndex(def = "{'repoUrl': 1, 'platform': 1}", unique = true)
public class DeploymentGuide {

    @Id
    private String id;

    /** URL del repositorio GitHub analizado. */
    private String repoUrl;

    /** Plataforma de despliegue: "render", "vercel" o "netlify". */
    private String platform;

    /** Momento en que se generó esta guía. */
    private LocalDateTime generatedAt;

    /** Contenido del Dockerfile generado para este proyecto. */
    private String dockerfile;

    /** Guía paso a paso como texto con saltos de línea numerados. */
    private String guide;

    /**
     * Consejos adicionales de despliegue para esta combinación proyecto-plataforma.
     */
    private List<String> tips;

    /** Contenido de ejemplo de .env para este proyecto. */
    private String environmentExample;

    // ── Constructores ─────────────────────────────────────────────────────────

    public DeploymentGuide() {
    }

    // ── Getters y setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    public String getGuide() {
        return guide;
    }

    public void setGuide(String guide) {
        this.guide = guide;
    }

    public List<String> getTips() {
        return tips;
    }

    public void setTips(List<String> tips) {
        this.tips = tips;
    }

    public String getEnvironmentExample() {
        return environmentExample;
    }

    public void setEnvironmentExample(String environmentExample) {
        this.environmentExample = environmentExample;
    }
}
