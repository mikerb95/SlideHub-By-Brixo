-- V7: add expiration support for presentation sessions
-- Session join tokens remain valid only while the presentation session stays active.

ALTER TABLE presentation_sessions
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE presentation_sessions
SET expires_at = COALESCE(expires_at, CURRENT_TIMESTAMP + INTERVAL '120' MINUTE);

ALTER TABLE presentation_sessions
    ALTER COLUMN expires_at SET NOT NULL;
