-- V6: Flag para indicar si el usuario completó su perfil tras OAuth2.
-- Usuarios existentes y locales se marcan como completados.
ALTER TABLE users ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT TRUE;
