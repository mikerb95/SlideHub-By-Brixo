-- V2: Presentaciones, slides y quick links (Fase 2)
-- También amplía la tabla users con la columna default_drive_folder_id.

-- Añadir preferencia de Drive a users
ALTER TABLE users ADD COLUMN default_drive_folder_id VARCHAR(200);

-- Tabla de presentaciones
CREATE TABLE presentations
(
    id                 VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    description        TEXT,
    source_type        VARCHAR(20)  NOT NULL,
    drive_folder_id    VARCHAR(200),
    drive_folder_name  VARCHAR(200),
    repo_url           TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_presentations PRIMARY KEY (id),
    CONSTRAINT fk_presentations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Tabla de slides individuales
CREATE TABLE slides
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    number           INT          NOT NULL,
    filename         VARCHAR(100) NOT NULL,
    drive_file_id    VARCHAR(200),
    s3_url           TEXT,
    uploaded_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_slides PRIMARY KEY (id),
    CONSTRAINT fk_slides_presentation FOREIGN KEY (presentation_id) REFERENCES presentations (id),
    CONSTRAINT uq_slides_number UNIQUE (presentation_id, number)
);

-- Tabla de quick links (usada en Fase 4)
CREATE TABLE quick_links
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    url              TEXT         NOT NULL,
    icon             VARCHAR(100),
    description      TEXT,
    display_order    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_quick_links PRIMARY KEY (id),
    CONSTRAINT fk_quick_links_presentation FOREIGN KEY (presentation_id) REFERENCES presentations (id)
);
