-- V3: Participantes de presentación, sesión de reunión y asignaciones slide-responsable

CREATE TABLE presentation_participants
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    display_name     VARCHAR(120) NOT NULL,
    is_presenter     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_presentation_participants PRIMARY KEY (id),
    CONSTRAINT fk_presentation_participants_presentation
        FOREIGN KEY (presentation_id) REFERENCES presentations (id),
    CONSTRAINT uq_participant_name_per_presentation UNIQUE (presentation_id, display_name)
);

CREATE TABLE slide_assignments
(
    id               VARCHAR(36) NOT NULL,
    presentation_id  VARCHAR(36) NOT NULL,
    slide_number     INT         NOT NULL,
    participant_id   VARCHAR(36) NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_slide_assignments PRIMARY KEY (id),
    CONSTRAINT fk_slide_assignments_presentation
        FOREIGN KEY (presentation_id) REFERENCES presentations (id),
    CONSTRAINT fk_slide_assignments_participant
        FOREIGN KEY (participant_id) REFERENCES presentation_participants (id),
    CONSTRAINT uq_slide_assignment UNIQUE (presentation_id, slide_number)
);

CREATE TABLE presentation_sessions
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    join_token       VARCHAR(120) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_presentation_sessions PRIMARY KEY (id),
    CONSTRAINT fk_presentation_sessions_presentation
        FOREIGN KEY (presentation_id) REFERENCES presentations (id),
    CONSTRAINT uq_presentation_sessions_token UNIQUE (join_token)
);

CREATE TABLE session_members
(
    id                 VARCHAR(36)  NOT NULL,
    session_id         VARCHAR(36)  NOT NULL,
    participant_id     VARCHAR(36)  NOT NULL,
    participant_token  VARCHAR(120) NOT NULL,
    joined_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_session_members PRIMARY KEY (id),
    CONSTRAINT fk_session_members_session
        FOREIGN KEY (session_id) REFERENCES presentation_sessions (id),
    CONSTRAINT fk_session_members_participant
        FOREIGN KEY (participant_id) REFERENCES presentation_participants (id),
    CONSTRAINT uq_session_members_participant UNIQUE (session_id, participant_id),
    CONSTRAINT uq_session_members_token UNIQUE (participant_token)
);
