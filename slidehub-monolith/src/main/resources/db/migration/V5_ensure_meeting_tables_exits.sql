-- V5: Ensure all meeting/participant tables exist (idempotent)
-- Addresses startup failure: missing table [presentation_participants]

CREATE TABLE IF NOT EXISTS presentation_participants
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    display_name     VARCHAR(120) NOT NULL,
    is_presenter     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_presentation_participants PRIMARY KEY (id),
    CONSTRAINT fk_presentation_participants_presentation
        FOREIGN KEY (presentation_id) REFERENCES presentations (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_participant_name_per_presentation
    ON presentation_participants (presentation_id, display_name);

CREATE TABLE IF NOT EXISTS slide_assignments
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
        FOREIGN KEY (participant_id) REFERENCES presentation_participants (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_slide_assignment
    ON slide_assignments (presentation_id, slide_number);

CREATE TABLE IF NOT EXISTS presentation_sessions
(
    id               VARCHAR(36)  NOT NULL,
    presentation_id  VARCHAR(36)  NOT NULL,
    join_token       VARCHAR(120) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_presentation_sessions PRIMARY KEY (id),
    CONSTRAINT fk_presentation_sessions_presentation
        FOREIGN KEY (presentation_id) REFERENCES presentations (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_presentation_sessions_token
    ON presentation_sessions (join_token);

CREATE TABLE IF NOT EXISTS session_members
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
        FOREIGN KEY (participant_id) REFERENCES presentation_participants (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_session_members_participant
    ON session_members (session_id, participant_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_session_members_token
    ON session_members (participant_token);
