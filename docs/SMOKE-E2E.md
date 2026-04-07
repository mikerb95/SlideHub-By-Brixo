# Smoke E2E local

Script de verificación rápida para los flujos nuevos (meeting + haptics + assist + playback por presentationId).

## Ejecutar

Desde la raíz del repo:

```bash
./scripts/smoke-e2e.sh
```

## Qué valida

- `state-service`:
  - `POST/GET /api/slide`
  - `POST /api/haptics/events/publish`
  - `GET /api/haptics/events/next`
- `ui-service`:
  - rutas públicas con `presentationId` (`/slides`, `/demo`, `/remote`)
  - catálogo de slides (`/api/presentations/{id}/slides`) en caso inexistente (`404`)
  - join-options inválido (`400`)
- `ai-service`:
  - `POST /api/ai/assist/audio` (verifica que el endpoint responde con payload JSON)

## Dependencias

- `docker`
- `curl`
- Maven wrapper (`./mvnw`)

## Notas

- El script crea contenedores temporales de Redis y Mongo.
- Por defecto fuerza `SPRING_JPA_HIBERNATE_DDL_AUTO=update` en `ui-service` para evitar bloqueo por esquema durante smoke.
- Logs quedan en `target/smoke-logs/`.

## Variables opcionales

- `SMOKE_FORCE_DDL_UPDATE=false` para no forzar `ddl-auto=update`.
- `KEEP_SMOKE_CONTAINERS=true` para no eliminar Redis/Mongo al finalizar.

## Ejecutar desde GitHub Actions (manual)

- Workflow: `.github/workflows/smoke-e2e.yml`
- Trigger: **Actions → Smoke E2E → Run workflow**
- Artifact al finalizar: `smoke-logs` (contenido de `target/smoke-logs/`)
