-- V1: Tabla de usuarios (HU-001/002/003, Fase 1)
-- Soporta login local (BCrypt) + OAuth2 GitHub/Google.

CREATE TABLE users
(
    id                        VARCHAR(36)  NOT NULL,
    username                  VARCHAR(50)  NOT NULL,
    email                     VARCHAR(255) NOT NULL,
    password_hash             VARCHAR(255),
    role                      VARCHAR(20)  NOT NULL DEFAULT 'PRESENTER',
    email_verified            BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verification_token  VARCHAR(36),
    github_id                 VARCHAR(100),
    github_username           VARCHAR(100),
    github_access_token       TEXT,
    google_id                 VARCHAR(100),
    google_email              VARCHAR(255),
    google_refresh_token      TEXT,
    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_github_id UNIQUE (github_id),
    CONSTRAINT uq_users_google_id UNIQUE (google_id)
);
