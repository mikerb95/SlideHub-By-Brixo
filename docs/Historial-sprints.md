# Historial de Sprints — SlideHub

> Estado: 4 de 6 sprints completados. Cada sprint corresponde a una fase de implementación.
>
> Equipo: Jerson Molina, Daniel Guacheta, David Pino, Edwin Mora (desarrolladores), Mike Rodriguez (infraestructura).
>
> Nota: Equipo con experiencia junior; hemos utilizado asistencia de IA como pair-programming en tareas puntuales (revisión de snippets, generación de tests y propuestas de diseño).

## Resumen general

- Sprints completados: Sprint 1 (Fase 1), Sprint 2 (Fase 2), Sprint 3 (Fase 3), Sprint 4 (Fase 4).
- Sprints pendientes: Sprint 5 (Fase 5), Sprint 6 (Fase 6).

---

## Sprint 1 — Fase 1: Infraestructura y scaffolding básico

Descripción: Inicialización del monorepo, parent POM y estructura de módulos. Configuración básica de Render/entorno local, CI mínima y plantillas de proyecto.


Asignaciones (con servicios asignados):

- **Jerson Molina — `state-service`**: Crear la estructura y scaffolding del `state-service`, agregar records y DTOs (`SlideState`, `DemoState`) y dejar endpoints base documentados. También coordinar con el parent POM para incluir el módulo.
- **Daniel Guacheta — `ui-service`**: Scaffolding inicial de `ui-service`, plantillas mínimas (`slides.html`, `remote.html`) y readme de arranque para desarrolladores front-end.
- **David Pino — `ai-service`**: Crear el módulo `ai-service` vacío/esquelético (Dockerfile, pom, estructura de paquetes) y definir contratos iniciales para futuras integraciones IA (endpoints mocks).
- **Edwin Mora — `gateway-service`**: Scaffolding del `gateway-service`, definición de rutas base en `RoutesConfig` y configuración de enrutamiento hacia los servicios locales.
- **Mike Rodriguez (Infra)**: Provisionar cuentas y entornos en Render, crear bucket S3 de prueba y credenciales temporales para desarrollo; documentar variables de entorno necesarias.

Resultados (completado):

- Repo con módulos iniciales y `mvnw` funcionando localmente.
- Plantillas mínimas en `ui-service` y endpoints base en `state-service`.
- Entorno Render configurado con servicios vacíos para despliegue continuo.

---

## Sprint 2 — Fase 2: State-service y sincronización de slides

Descripción: Implementación del servicio de estado (Redis), endpoints `GET/POST /api/slide`, conteo de `totalSlides`, y polling desde vistas públicas.


Asignaciones (alineadas por servicio):

- **Jerson Molina — `state-service`**: Implementar `SlideController` y `SlideStateService` en `state-service`, definir `SlideStateResponse` record, lógica de límite (no avanzar más allá) y pruebas unitarias `@WebMvcTest` para los endpoints.
- **Daniel Guacheta — `ui-service`**: Implementar el polling en `slides.html`, consumir `GET /api/slide` y actualizar la UI con `totalSlides`; agregar pruebas de integración de frontend mínimo.
- **David Pino — `ai-service`**: Implementar mocks de integración en `ai-service` que devuelvan respuestas controladas para las vistas del frontend (permitir pruebas end-to-end sin consumir keys reales).
- **Edwin Mora — `gateway-service`**: Asegurar el enrutamiento en `gateway-service` para que `/api/slide` llegue a `state-service`; añadir tests de rutas y validación de orden de reglas.
- **Mike Rodriguez (Infra)**: Desplegar Redis (instancia o add-on), configurar `REDIS_HOST`/`REDIS_PORT` en Render y verificar conexiones desde `state-service`.

Resultados (completado):

- `GET /api/slide` devuelve `{slide, totalSlides}` y el polling público muestra la diapositiva correcta.
- Redis provisionado en entorno de staging; variables de entorno en Render y pruebas básicas OK.

---

## Sprint 3 — Fase 3: UI, autenticación y presentador

Descripción: Desarrollo de vistas Thymeleaf completas para `presenter`, `main-panel`, login local + OAuth2 (GitHub/Google), subida de presentaciones y manejo de sesiones.


Asignaciones (por servicio):

- **Jerson Molina — `state-service`**: Extender APIs necesarias para sesiones y estado requerido por la vista de presentador (`/api/slide` apoyo adicional, sesiones por presentationId).
- **Daniel Guacheta — `ui-service`**: Implementar `PresentationViewController`, integrar `WebClient` para llamadas a `state-service`, vistas `presenter` y `main-panel`, y manejar upload flow en la UI.
- **David Pino — `ai-service`**: Diseñar e implementar el servicio de uploads/transformaciones básicas en `ai-service` (p. ej. generar mini-metadata o thumbnails con mock), y preparar `SlideUpload` hooks para cuando el frontend suba imágenes.
- **Edwin Mora — `gateway-service`**: Completar las reglas de enrutamiento y la integración con OAuth2 en el gateway (redirigir flujos OAuth a `ui-service` correctamente); tests de seguridad de rutas.
- **Mike Rodriguez (Infra)**: Configurar bucket S3 en AWS, crear credenciales y políticas mínimas; añadir `DATABASE_URL` para Postgres (Aiven) en Render.

Resultados (completado):

- Login local y OAuth2 esquelético funcionando; subida de assets a S3 habilitada en entorno de pruebas.
- Vistas de presentador y main-panel con datos básicos y polling integrados.

---

## Sprint 4 — Fase 4: AI-service básico (notas) y conectores IA

Descripción: Implementación de `ai-service` para generación y persistencia de notas de presentador (MongoDB), integración con Gemini y Groq vía HTTP (`WebClient`). Endpoints para `POST /api/ai/notes/generate` y consultas `GET`/`DELETE`.


Asignaciones (claras por microservicio):

- **Jerson Molina — `state-service`**: Asegurar que los endpoints de estado entreguen la información necesaria para las notas del presentador (p. ej. slide activo, metadata básica) y documentar los contratos consumidos por `ai-service`.
- **Daniel Guacheta — `ui-service`**: Integrar la UI del presentador con los endpoints de `ai-service` (consumir `/api/ai/notes/*`), mostrar resultados y manejar casos 204/204 No Content en la vista.
- **David Pino — `ai-service`**: Implementar `NotesController`, `NotesService` y `PresenterNoteRepository` en `ai-service`; integrar llamadas a Gemini/Groq vía `WebClient` (inicialmente con mocks), lógica de upsert y endpoints `POST/GET/DELETE`.
- **Edwin Mora — `gateway-service`**: Asegurar el enrutamiento correcto de `/api/ai/**` hacia `ai-service` antes de `/api/**` y añadir checks de salud/routing para `ai-service`.
- **Mike Rodriguez (Infra)**: Provisionar MongoDB (Atlas o addon), configurar `MONGODB_URI` en Render y validar integración segura de claves para `ai-service`.

Resultados (completado):

- Endpoints de generación y consulta de notas implementados y documentados minimalmente.
- Conexión a MongoDB en entorno de staging; integración IA en modo controlado (mocks) para evitar consumo de keys reales durante pruebas.

---

## Sprint 5 — Fase 5: Deploy Tutor y análisis de repositorios (Planificado)

Descripción: Añadir capacidad de analizar repositorios con Gemini, generar Dockerfiles y guías de despliegue con Groq; cache en MongoDB.

Asignaciones (plan):

- **Jerson Molina**: `DeployTutorController` y endpoints `/api/ai/deploy/*` (analyze, dockerfile, guide).
- **Daniel Guacheta**: `RepoAnalysisService` que orquesta llamadas a Gemini para detectar lenguaje, puertos y stack.
- **David Pino**: Formulario UI en `deploy-tutor.html` y cliente que consume los endpoints; mostrar resultados cacheados.
- **Edwin Mora**: Tests E2E y seguridad de endpoints (rate-limits / validación de entrada) y opciones para refresh de cache.
- **Mike Rodriguez (Infra)**: Revisar cuotas y límites (Gemini/Groq), configurar secrets seguros y planear rotación de keys; preparar variables de entorno en Render para producción.

Estado: Planificado (pendiente ejecución en Sprint 5).

---

## Sprint 6 — Fase 6: Pulido, pruebas E2E y despliegue final (Planificado)

Descripción: Completar E2E, cobertura de tests, documentación, ajustes de UX para presenter notes, y despliegue a producción con monitoreo.

Asignaciones (plan):

- **Jerson Molina**: Documentación final y guías de uso para presentadores; revisión de endpoints y contratos.
- **Daniel Guacheta**: Completar migraciones Flyway, optimizaciones JPA y ajustes de performance backend.
- **David Pino**: Mejoras de UI/UX, thumbnails en `main-panel`, y optimización de carga de imágenes.
- **Edwin Mora**: Pruebas E2E (smoke) automatizadas, configuración de `actuator` y dashboards básicos en Prometheus/Grafana (si aplica).
- **Mike Rodriguez (Infra)**: Ejecutar despliegue final en Render, validar certificados/URLs, configurar backups de DBs y políticas de acceso; preparar runbook de recuperación.

Estado: Planificado (Sprint 6 por ejecutar).

---

## Observaciones finales

- El equipo es junior pero pragmático: preferimos soluciones explícitas, pruebas y despliegues pequeños y frecuentes.
- La IA se usa como asistente (pair-programming): revisión de snippets, generación de tests y propuestas de diseño; las decisiones finales las toma el equipo.
- Próximo paso recomendado: ejecutar Sprint 5 con foco en `ai-service` Deploy Tutor y coordinación de claves/quotas con Infra (Mike).

---

Archivo generado por: Equipo SlideHub — Historial automatizado (marzo 2026).
