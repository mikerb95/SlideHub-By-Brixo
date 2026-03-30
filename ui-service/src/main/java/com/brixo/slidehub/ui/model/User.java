package com.brixo.slidehub.ui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entidad JPA de usuario (AGENTS.md §6 HU-001/002/003, PLAN-EXPANSION.md Fase 1
 * tarea 7).
 *
 * Soporta login local (BCrypt) y OAuth2 (GitHub + Google) de forma coexistente.
 * No usa record — necesita mutabilidad para JPA y el flujo de vinculación
 * OAuth.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Null para cuentas creadas solo via OAuth2 sin contraseña local. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verification_token", length = 36)
    private String emailVerificationToken;

    // ── GitHub OAuth ──────────────────────────────────────────────────────────

    @Column(name = "github_id", unique = true, length = 100)
    private String githubId;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @Column(name = "github_access_token", columnDefinition = "TEXT")
    private String githubAccessToken;

    // ── Google OAuth ──────────────────────────────────────────────────────────

    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    @Column(name = "google_email", length = 255)
    private String googleEmail;

    @Column(name = "google_refresh_token", columnDefinition = "TEXT")
    private String googleRefreshToken;

    // ── Preferencias ──────────────────────────────────────────────────────────

    /**
     * Última carpeta de Google Drive seleccionada (para pre-selección en la UI).
     */
    @Column(name = "default_drive_folder_id", length = 200)
    private String defaultDriveFolderId;

    // ── Auditoría ─────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Constructores ─────────────────────────────────────────────────────────

    public User() {
        // Requerido por JPA
    }

    // ── Getters y setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }

    public String getGithubId() {
        return githubId;
    }

    public void setGithubId(String githubId) {
        this.githubId = githubId;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getGithubAccessToken() {
        return githubAccessToken;
    }

    public void setGithubAccessToken(String githubAccessToken) {
        this.githubAccessToken = githubAccessToken;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public void setGoogleEmail(String googleEmail) {
        this.googleEmail = googleEmail;
    }

    public String getGoogleRefreshToken() {
        return googleRefreshToken;
    }

    public void setGoogleRefreshToken(String googleRefreshToken) {
        this.googleRefreshToken = googleRefreshToken;
    }

    public String getDefaultDriveFolderId() {
        return defaultDriveFolderId;
    }

    public void setDefaultDriveFolderId(String defaultDriveFolderId) {
        this.defaultDriveFolderId = defaultDriveFolderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
