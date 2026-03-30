package com.brixo.slidehub.ui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Slide individual dentro de una presentación (PLAN-EXPANSION.md Fase 2, tarea
 * 19).
 *
 * La URL pública del asset corresponde al objeto en S3:
 * https://{bucket}.s3.{region}.amazonaws.com/slides/{presentationId}/{number}.png
 */
@Entity
@Table(name = "slides")
public class Slide {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    /** Posición dentro de la presentación, 1-based. */
    @Column(nullable = false)
    private int number;

    /** Nombre del archivo almacenado (ej. "1.png"). */
    @Column(nullable = false, length = 100)
    private String filename;

    /**
     * ID del archivo en Google Drive si el origen fue DRIVE; null si fue UPLOAD.
     */
    @Column(name = "drive_file_id", length = 200)
    private String driveFileId;

    /** URL pública del objeto en Amazon S3. */
    @Column(name = "s3_url", columnDefinition = "TEXT")
    private String s3Url;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    // ── Constructores ─────────────────────────────────────────────────────────

    public Slide() {
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

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }

    public String getS3Url() {
        return s3Url;
    }

    public void setS3Url(String s3Url) {
        this.s3Url = s3Url;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
