# Presentation Module Analysis — SlideHub

Documento de referencia funcional y arquitectónica del sistema actual.

## Alcance

- `state-service`: estado activo de presentación y eventos hápticos efímeros.
- `ui-service`: vistas Thymeleaf, autenticación, importación, reuniones QR, quick slides y playback.
- `ai-service`: notas del presentador, asistencia por audio y generación de guías.
- `gateway-service`: enrutamiento y configuración central.

## Flujo de presentación

- El slide activo se consulta por polling desde las vistas y dispositivos.
- `GET /api/slide` devuelve `slide` y `totalSlides`.
- Las vistas consumen el catálogo por `presentationId` cuando está disponible.

## Reuniones y colaboración

- Un organizador crea una sesión activa por presentación.
- El equipo entra con `joinToken` y recibe `participantToken`.
- Los participantes pueden asociarse a responsables por slide.
- La ayuda y la confirmación de slide usan eventos hápticos.

## Asistencia por audio

- El dispositivo captura audio por push-to-talk.
- El audio se transcribe con Groq Whisper.
- La respuesta se genera con contexto del slide y del repositorio.

## Quick slides

- Un quick slide se agrega al final de la presentación activa.
- Se guarda en el catálogo de la presentación y se hace visible en las vistas públicas.

## Endpoints relevantes

### state-service

- `GET /api/slide`
- `POST /api/slide`
- `GET /api/demo`
- `POST /api/demo`
- `GET /api/haptics/events/next`
- `POST /api/haptics/events/publish`

### ui-service

- `GET /slides`
- `GET /remote`
- `GET /demo`
- `GET /presenter`
- `GET /main-panel`
- `GET /api/presentations/{presentationId}/slides`
- `GET | POST /api/presentations/{presentationId}/meeting/**`
- `POST /api/presentations/{presentationId}/quick-slide`

### ai-service

- `POST /api/ai/notes/generate`
- `GET /api/ai/notes/{presentationId}`
- `GET /api/ai/notes/{presentationId}/{slideNumber}`
- `DELETE /api/ai/notes/{presentationId}`
- `GET /api/ai/notes/health`
- `POST /api/ai/assist/audio`
- `POST /api/ai/deploy/analyze`
- `POST /api/ai/deploy/dockerfile`
- `POST /api/ai/deploy/guide`
- `POST /api/ai/deploy/guide/refresh`

## Reglas funcionales clave

- `GET /api/slide` siempre incluye `totalSlides`.
- `204 No Content` se usa para nota inexistente y para borrado sin contenido previo.
- La ayuda notifica a todos los miembros activos excepto al emisor.
- El catálogo de slides por presentación tiene prioridad sobre el fallback estático.
- Los endpoints de reunión usan tokens y no exponen credenciales de sesión.
