# AGENTS.md — SlideHub

> **Guía de referencia para agentes de IA** que trabajen sobre este repositorio.
> Leer este archivo **antes de tocar cualquier código**.

## Propiedad y licencia

Este repositorio es **código cerrado** y se publica en GitHub solo para visibilidad y referencia.

- Licencia: **All Rights Reserved**
- No se permite usar, copiar, modificar, redistribuir, sublicenciar ni comercializar el código sin autorización escrita previa.
- La publicación pública del repositorio no concede permisos de uso más allá de los expresamente otorgados por el titular.

---

## 1. Visión del Proyecto

**SlideHub** es un sistema de control de diapositivas en tiempo real, multi-pantalla,
extraído y reescrito completamente en Java desde un módulo PHP/CodeIgniter 4.

El módulo original está documentado en [`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).
Úsalo **solo como referencia funcional**: no como guía tecnológica.

Las historias de usuario definitivas están en
[`docs/Historias de Usuario - SlideHub.csv`](<docs/Historias de Usuario - SlideHub.csv>).
Son la **fuente de verdad de requisitos** — tienen prioridad sobre el doc de análisis PHP
si hay diferencias de comportamiento.

### Propuesta de valor

| Feature clave               | Descripción                                                                            |
|-----------------------------|----------------------------------------------------------------------------------------|
| Multi-pantalla sincronizada | N pantallas se sincronizan vía REST API + polling HTTP                                |
| Control remoto táctil       | Interfaz para smartphone con swipe y vibración háptica                                |
| Presenter notes con IA      | Notas generadas automáticamente por Groq a partir del contenido del repo              |
| Ingesta de repo con Gemini  | Gemini lee el repositorio de GitHub y extrae contexto por slide                       |
| Gemini Vision               | Analiza visualmente los slides PNG para generar notas más precisas                    |
| Modo dual slides/URL        | La pantalla `demo` alterna entre diapositivas e iframe; `returnSlide` auto-restaura   |
| Quick Links con returnSlide | Links rápidos de demo desde `main-panel`; vuelve al slide exacto al cerrar            |
| Panel maestro tablet        | Grid optimizado para tablet 11", thumbnails, navegación, links de demo                |
| Autenticación dual          | Login local (BCrypt) + OAuth2 GitHub + OAuth2 Google coexistiendo                     |
| Importación Google Drive    | Importa slides PNG directamente desde una carpeta de Google Drive                     |
| Gestión de presentaciones   | CRUD de presentaciones persistido en PostgreSQL; upload manual de PNGs a S3           |
| Deploy Tutor                | Genera Dockerfile y guía de despliegue para cualquier repo usando Gemini + Groq       |
| Registro de dispositivos    | Admin puede listar y buscar dispositivos conectados por token                         |
| Zero-config                 | Detecta slides del directorio `static/slides/` automáticamente                        |

---

## 2. Arquitectura (Monolito Modular)

Este proyecto evolucionó a un **monolito modular** en el módulo `slidehub-service`. Los servicios antiguos se conservan como legacy.

```
SlideHub/                          ← Parent POM
├── slidehub-service/             ← Aplicación principal (puerto 8080)
│   └── src/main/java/com/brixo/slidehub
│       ├── state/
│       ├── ui/
│       └── ai/
├── state-service/                 ← (Deprecated / Legacy)
├── ui-service/                    ← (Deprecated / Legacy)
├── ai-service/                    ← (Deprecated / Legacy)
└── gateway-service/               ← (Deprecated / Legacy)
```

### 2.1 Módulo `state`

**Responsabilidad única:** gestionar y persistir el estado de presentación activo
y el registro de dispositivos.

| Aspecto        | Decisión                                                          |
|----------------|-------------------------------------------------------------------|
| Store          | Redis (TTL 3600s por defecto)                                     |
| API            | REST JSON, Spring WebMVC                                          |
| Endpoints clave | `GET/POST /api/slide`, `GET/POST /api/demo`, `GET /api/devices` |
| Sin UI         | Solo API, sin Thymeleaf                                           |

**Modelo de estado en Redis:**

```json
// Clave: current_slide
{ "slide": 1 }

// Clave: demo_state
{ "mode": "slides", "slide": 1 }
// o
{ "mode": "url", "url": "/some-path" }
```

**Respuesta de `GET /api/slide` (HU-008):**

```json
{ "slide": 5, "totalSlides": 11 }
```

> El campo `totalSlides` se determina escaneando el directorio de slides en cada respuesta
> (o se cachea brevemente). No se persiste en Redis.

**Registro de dispositivos (HU-014, HU-015):**

| Endpoint                          | Acceso  | Descripción                           |
|-----------------------------------|---------|---------------------------------------|
| `GET /api/devices`                | ADMIN   | Lista todos los dispositivos          |
| `GET /api/devices/token/{token}`  | ADMIN   | Busca un dispositivo por token único  |

Cada dispositivo tiene: `name`, `type`, `token`, `lastIp`, `lastConnection`.

### 2.2 Módulo `ui`

**Responsabilidad única:** servir las vistas HTML y gestionar autenticación de sesión.

| Aspecto       | Decisión                                                                     |
|---------------|------------------------------------------------------------------------------|
| UI            | Thymeleaf 3 + Spring WebMVC                                                  |
| Seguridad     | Spring Security — sesiones HTTP, BCrypt para passwords                       |
| HTTP client   | Llamadas a nivel de métodos (inyección de dependencias directas) hacia módulos `state` y `ai`          |
| Estáticos     | Archivos de slides en `src/main/resources/static/slides/`                   |
| Sin estado    | No mantiene estado de presentación propio; delega todo a `state-service`    |

**Vistas y acceso:**

| Ruta                       | Vista Thymeleaf          | Acceso    | Para                                          |
|----------------------------|--------------------------|-----------|-----------------------------------------------|
| `/slides`                  | `slides.html`            | Público   | Proyector/TV fullscreen                       |
| `/remote`                  | `remote.html`            | Público   | Control remoto smartphone                     |
| `/demo`                    | `demo.html`              | Público   | Pantalla dual slides/iframe                   |
| `/showcase`                | `showcase.html`          | Público   | Landing page                                  |
| `/presenter`               | `presenter.html`         | PRESENTER | Laptop del presentador con notas              |
| `/main-panel`              | `main-panel.html`        | PRESENTER | Panel maestro tablet 11"                      |
| `/deploy-tutor`            | `deploy-tutor.html`      | PRESENTER | Generador de Dockerfiles y guías de deploy    |
| `/presentations`           | `presentations.html`     | PRESENTER | Lista de presentaciones del usuario           |
| `/presentations/import`    | `import.html`            | PRESENTER | Importar presentación desde Google Drive      |
| `/auth/login`              | `login.html`             | Público   | Formulario de login local                     |
| `/auth/register`           | `register.html`          | Público   | Registro de cuenta nueva                      |
| `/auth/profile`            | `profile.html`           | PRESENTER | Perfil de usuario y cuentas OAuth vinculadas  |

**Rutas de autenticación (HU-001, HU-002, HU-003, HU-021, HU-022):**
- Login local: usuario/contraseña → valida con BCrypt → crea sesión → redirige a `/presenter`
- Login OAuth2: GitHub (`/login/oauth2/code/github`) o Google (`/login/oauth2/code/google`) → crea o vincula cuenta → redirige a `/presenter`
- Si sesión ya activa → redirige directo a `/presenter` sin mostrar el formulario
- Logout → invalida sesión → redirige a `/auth/login`
- Roles: `PRESENTER` (acceso a control), `ADMIN` (además del panel de devices)

### 2.3 Módulo `ai`

**Responsabilidad única:** integración con IA externa (Gemini + Groq) y persistencia de
notas del presentador y guías de deploy en MongoDB.

| Aspecto         | Decisión                                                                                   |
|-----------------|--------------------------------------------------------------------------------------------|
| Store           | MongoDB — colecciones `presenter_notes`, `repo_analysis`, `deployment_guides`             |
| API             | REST JSON, Spring WebMVC                                                                   |
| IA externa      | Gemini (texto: repos) + Gemini Vision (imágenes: slides) + Groq (generación de contenido) |
| Sin UI          | Solo API                                                                                   |

**Flujo de generación de notas (HU-016, HU-030):**

```
1. Cliente envía POST /api/ai/notes/generate con { presentationId, slideNumber, repoUrl, slideContext }
2. ai-service llama a Gemini Vision API → analiza visualmente la imagen del slide PNG
3. ai-service llama a Gemini API → extrae contenido relevante del repo de GitHub para ese slide
4. ai-service envía contexto combinado a Groq API → genera nota estructurada
5. Nota se guarda en MongoDB: { presentationId, slideNumber, title, points[], suggestedTime, keyPhrases[], demoTags[] }
6. Responde { success: true } o { success: false, errorMessage: "..." }
```

**Endpoints de notas:**

| Método   | Ruta                                           | Descripción                               |
|----------|------------------------------------------------|-------------------------------------------|
| `POST`   | `/api/ai/notes/generate`                       | Genera nota para un slide vía IA          |
| `GET`    | `/api/ai/notes/{presentationId}`               | Lista todas las notas de una presentación |
| `GET`    | `/api/ai/notes/{presentationId}/{slideNumber}` | Obtiene nota de un slide específico       |
| `DELETE` | `/api/ai/notes/{presentationId}`               | Elimina todas las notas de una presentación |
| `GET`    | `/api/ai/notes/health`                         | Health check del servicio de IA           |

> Si la nota ya existe para un slide, `generate` la **sobreescribe** (HU-016 §2).
> `DELETE` responde `204 No Content` aunque no existan notas previas (HU-019 §2).
> `GET /{presentationId}/{slideNumber}` responde `204 No Content` si no existe la nota (HU-018 §2).

**Endpoints de Deploy Tutor:**

| Método | Ruta                            | Descripción                                         |
|--------|---------------------------------|-----------------------------------------------------|
| `POST` | `/api/ai/deploy/analyze`        | Analiza repo con Gemini → detecta lenguaje/puertos  |
| `POST` | `/api/ai/deploy/dockerfile`     | Genera Dockerfile optimizado con Groq               |
| `POST` | `/api/ai/deploy/guide`          | Genera guía de despliegue (cache MongoDB)           |
| `POST` | `/api/ai/deploy/guide/refresh`  | Regenera guía descartando el cache                  |

> Los resultados de análisis (`repo_analysis`) y guías (`deployment_guides`) se cachean en MongoDB.
> `/guide/refresh` fuerza regeneración descartando el documento cacheado.

### 2.4 Gateway (Ref. Legacy)

**Responsabilidad única:** punto de entrada único + Spring Cloud Config Server.

| Aspecto         | Decisión                                          |
|-----------------|---------------------------------------------------|
| Routing         | Spring Cloud Gateway (WebMVC flavor)             |
| Config Server   | Spring Cloud Config (archivos locales en `/config-repo/`) |
| Sin lógica      | Solo enruta y provee configuración               |

**Tabla de rutas:**

| Prefijo                          | Destino                             |
|----------------------------------|-------------------------------------|
| `/api/ai/**`                     | `ai-service:8083/api/ai/**`         |
| `/api/**`                        | `state-service:8081/api/**`         |
| `/auth/**`                       | `ui-service:8082/auth/**`           |
| `/slides`, `/remote`, `/presenter`, `/main-panel`, `/demo`, `/showcase` | `ui-service:8082/**` |
| `/deploy-tutor`                  | `ui-service:8082/deploy-tutor`      |
| `/presentations/**`              | `ui-service:8082/presentations/**`  |
| `/presentation/**`               | `ui-service:8082/presentation/**`   |
| `/actuator/**`                   | Endpoints internos del gateway      |

> **Orden importa:** la ruta `/api/ai/**` debe definirse **antes** de `/api/**` en la configuración
> del gateway para que los requests de IA no sean capturados por el `state-service`.

---

## 3. Stack Tecnológico

| Capa                  | Tecnología                                      | Versión / Nota                        |
|-----------------------|-------------------------------------------------|---------------------------------------|
| Lenguaje              | Java                                            | 21 (LTS)                              |
| Framework             | Spring Boot                                     | 4.0.3                                 |
| Cloud                 | Spring Cloud                                    | 2025.1.0                              |
| UI templating         | Thymeleaf 3                                     | incluido en Boot                      |
| HTTP client           | Spring WebClient (WebFlux)                      | incluido en Boot                      |
| Cache / State         | Redis via `spring-boot-starter-data-redis`      | incluido en Boot                      |
| Base de datos IA      | MongoDB via `spring-boot-starter-mongodb`       | incluido en Boot                      |
| Base de datos app     | PostgreSQL (Aiven) via `spring-boot-starter-data-jpa` | DSN como `DATABASE_URL`         |
| Migraciones BD        | Flyway                                          | incluido en Boot                      |
| Autenticación local   | Spring Security + BCrypt                        | `spring-boot-starter-security`        |
| Autenticación OAuth2  | Spring Security OAuth2 Client                   | `spring-boot-starter-oauth2-client`   |
| Gateway               | Spring Cloud Gateway (WebMVC)                   | incluido en Cloud                     |
| Config                | Spring Cloud Config Server                      | incluido en Cloud                     |
| IA — Análisis repo    | Google Gemini API (texto)                       | llamadas HTTP vía WebClient           |
| IA — Análisis visual  | Google Gemini Vision API (imágenes)             | llamadas HTTP vía WebClient           |
| IA — Generación       | Groq API (LLM)                                  | llamadas HTTP vía WebClient           |
| Google Drive          | Google Drive REST API v3                        | llamadas HTTP vía WebClient (sin SDK) |
| Build                 | Maven (Wrapper `mvnw`)                          | —                                     |
| Testing               | JUnit 5, Spring Boot Test, MockMvc              | incluido en Boot                      |
| CSS (vistas)          | Bootstrap 5.3 + Tailwind CDN, Font Awesome 6.5  | CDN only                              |
| JS (vistas)           | Vanilla ES6 (fetch API, DOM, polling)           | sin bundler                           |
| Email                 | Resend API                                      | llamadas HTTP vía WebClient           |
| Storage (assets)      | Amazon S3                                       | AWS SDK v2 — excepción justificada    |
| Despliegue            | Render                                          | Web Services por microservicio        |

> **Regla:** No añadir Lombok, MapStruct ni ninguna librería de generación de código
> sin discutirlo primero. Preferir código explícito y legible.
>
> Gemini, Groq y Google Drive se integran **únicamente vía HTTP** (`WebClient`) — no se usan SDKs
> de terceros para mantener control total sobre el payload y evitar dependencias pesadas.
> La única excepción es AWS SDK v2 para S3 (requiere firma SigV4).

---

## 4. Estructura de Paquetes Java

Convención de paquetes por módulo:

```
com.brixo.slidehub.<service>
├── controller/     ← @RestController o @Controller
├── service/        ← @Service (lógica de negocio)
├── model/          ← Records o POJOs (estado, DTOs)
├── config/         ← @Configuration, beans de infraestructura
└── exception/      ← Excepciones de dominio + @ControllerAdvice
```

Ejemplos concretos:
- `com.brixo.slidehub.state.controller.SlideController`
- `com.brixo.slidehub.state.service.SlideStateService`
- `com.brixo.slidehub.state.service.DeviceRegistryService`
- `com.brixo.slidehub.ui.controller.PresentationViewController`
- `com.brixo.slidehub.ui.controller.AuthController`
- `com.brixo.slidehub.ui.service.GoogleDriveImportService`
- `com.brixo.slidehub.ui.service.PresentationService`
- `com.brixo.slidehub.ui.model.User`
- `com.brixo.slidehub.ui.model.Presentation`
- `com.brixo.slidehub.ui.model.Slide`
- `com.brixo.slidehub.ui.model.QuickLink`
- `com.brixo.slidehub.ai.controller.NotesController`
- `com.brixo.slidehub.ai.controller.DeployTutorController`
- `com.brixo.slidehub.ai.service.GeminiService`
- `com.brixo.slidehub.ai.service.GroqService`
- `com.brixo.slidehub.ai.service.DeploymentService`
- `com.brixo.slidehub.ai.service.RepoAnalysisService`
- `com.brixo.slidehub.ai.model.PresenterNote`
- `com.brixo.slidehub.ai.model.RepoAnalysis`
- `com.brixo.slidehub.ai.model.DeploymentGuide`
- `com.brixo.slidehub.gateway.config.RoutesConfig`

---

## 5. Convenciones de Código

### 5.1 Java

- **Records** para DTOs/modelos inmutables: `record SlideState(int slide) {}`
- **`@RestController`** en `state-service` (solo JSON)
- **`@Controller`** en `ui-service` (devuelve nombres de vistas Thymeleaf)
- **Programmatic configuration** preferida sobre anotaciones mágicas cuando la intención no es obvia
- **`ResponseEntity<T>`** para todos los endpoints de `state-service`
- Métodos de servicio **síncronos** en `state-service`; `WebClient` con `.block()` en `ui-service`
  (o reactive chain si se evalúa necesario — decidir en contexto)
- **No** `@Autowired` en campos; inyección **únicamente por constructor**

### 5.2 Thymeleaf / HTML

- Variables del modelo con `th:text`, `th:each`, `th:if` — sin lógica compleja en templates
- Datos de configuración (notas, links) inyectados desde el modelo del controller, no hardcodeados en HTML
- El polling JavaScript usa `fetch()` nativo — sin jQuery ni Axios

### 5.3 Configuración

- `application.properties` por perfil: `application-dev.properties`, `application-prod.properties`
- Secrets y URLs de servicio en variables de entorno — jamás hardcodeadas en código
- El Config Server centraliza la configuración compartida entre servicios

### 5.4 Tests

- Cada servicio tiene tests en `src/test/java/...`
- Preferir `@WebMvcTest` para controllers, `@DataRedisTest` para repositorios Redis
- Mocks con `@MockitoBean` (Spring Boot 4+)
- Nombre de test: `methodName_scenario_expectedResult()`

---

## 6. Catálogo de Historias de Usuario

Fuente canónica: [`docs/Historias de Usuario - SlideHub.csv`](<docs/Historias de Usuario - SlideHub.csv>)

| ID     | Rol               | Funcionalidad                                         | Servicio destino                 |
|--------|-------------------|-------------------------------------------------------|----------------------------------|
| HU-001 | Presentador       | Login con usuario y contraseña                        | `ui-service` (Spring Security)   |
| HU-002 | Presentador       | Registro de cuenta nueva                              | `ui-service` (Spring Security)   |
| HU-003 | Presentador       | Cierre de sesión                                      | `ui-service` (Spring Security)   |
| HU-004 | Presentador       | Avanzar/retroceder slides desde smartphone (`/remote`) | `state-service` + `ui-service`  |
| HU-005 | Audiencia         | Ver slide activo en proyector (`/slides`, polling)    | `state-service` + `ui-service`   |
| HU-006 | Presentador       | Ver slide actual + notas + preview en `/presenter`    | `ai-service` + `ui-service`      |
| HU-007 | Responsable tec.  | Navegar a cualquier slide desde `/main-panel`         | `state-service` + `ui-service`   |
| HU-008 | Dispositivo       | `GET /api/slide` devuelve `{slide, totalSlides}`      | `state-service`                  |
| HU-009 | Dispositivo       | _(duplicado funcional de HU-008 — no implementar por separado)_ | —               |
| HU-010 | Responsable tec.  | Desactivar modo URL y volver a slides en `/demo`      | `state-service` + `ui-service`   |
| HU-011 | Audiencia         | `/demo` sincroniza modo slides o iframe automáticamente | `state-service` + `ui-service` |
| HU-012 | Visitante         | Ver landing page pública en `/showcase`               | `ui-service`                     |
| HU-013 | Vista HTML        | Cargar imágenes `GET /presentation/Slide*.PNG`        | `ui-service` (recurso estático)  |
| HU-014 | Admin             | `GET /api/devices` — listar todos los dispositivos    | `state-service`                  |
| HU-015 | Admin             | `GET /api/devices/token/{token}` — buscar por token   | `state-service`                  |
| HU-016 | Presentador       | `POST /api/ai/notes/generate` — generar nota con IA   | `ai-service`                     |
| HU-017 | Presentador       | `GET /api/ai/notes/{presentationId}` — listar notas   | `ai-service`                     |
| HU-018 | Vista presentador | `GET /api/ai/notes/{presentationId}/{slideNumber}`    | `ai-service`                     |
| HU-019 | Presentador       | `DELETE /api/ai/notes/{presentationId}` — borrar notas | `ai-service`                    |
| HU-020 | Ops               | `GET /api/ai/notes/health` + `GET /actuator/health`   | `ai-service` + todos             |
| HU-021 | Presentador       | Login OAuth2 con GitHub                               | `ui-service` (Spring Security)   |
| HU-022 | Presentador       | Login OAuth2 con Google                               | `ui-service` (Spring Security)   |
| HU-023 | Presentador       | Ver y editar perfil + cuentas OAuth vinculadas        | `ui-service` (PostgreSQL)        |
| HU-024 | Presentador       | Importar presentación (slides PNG) desde Google Drive | `ui-service` + Google Drive API  |
| HU-025 | Presentador       | Upload manual de slides PNG a una presentación       | `ui-service` + S3                |
| HU-026 | Presentador       | Listar y gestionar presentaciones guardadas           | `ui-service` (PostgreSQL)        |
| HU-027 | Presentador       | `POST /api/ai/deploy/analyze` — analizar repo          | `ai-service`                     |
| HU-028 | Presentador       | `POST /api/ai/deploy/dockerfile` — generar Dockerfile | `ai-service`                     |
| HU-029 | Presentador       | `POST /api/ai/deploy/guide` — generar guía + cache   | `ai-service`                     |
| HU-030 | Presentador       | Análisis visual de slides con Gemini Vision           | `ai-service`                     |
| HU-031 | Presentador       | Unirse por QR a una sesión de presentación             | `ui-service` + `state-service`   |
| HU-032 | Presentador       | Gestionar participantes y asignar responsables por slide | `ui-service` + `state-service` |
| HU-033 | Presentador       | Solicitar ayuda con vibración triple al equipo         | `ui-service` + `state-service`   |
| HU-034 | Presentador       | Dictado por audio con transcripción y respuesta IA     | `ui-service` + `ai-service`      |
| HU-035 | Presentador       | Generar quick slides usando colores dominantes         | `ui-service` + `ai-service`      |
| HU-036 | Presentador       | Consumir catálogo de slides por presentación           | `ui-service`                     |

**Comportamientos críticos a respetar:**
- `GET /api/slide` siempre responde `{ "slide": N, "totalSlides": M }` — nunca solo `{ "slide": N }` (HU-008)
- Si no se ha navegado ningún slide, slide por defecto = 1 (HU-008 §2)
- Login incorrecto → error genérico sin indicar qué campo falló (HU-001 §2)
- Sesión activa + acceso a `/auth/login` → redirect directo a `/presenter` (HU-001 §3)
- Nota de slide inexistente → `204 No Content`, no `404` (HU-018 §2)
- `DELETE` de presentación sin notas → `204 No Content`, no error (HU-019 §2)
- Slide en limite superior/inferior → no avanza/retrocede, sin mostrar error (HU-004 §3 y §4)

---

## 7. Fuente de Verdad Funcional

Antes de implementar cualquier endpoint o vista, consultar el CSV de historias y
[`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).

El CSV de historias es la fuente primaria de requisitos funcionales. El documento de análisis se usa como referencia histórica y arquitectónica, pero no reemplaza al CSV cuando hay diferencias.

Secciones clave del doc de análisis por tarea:

| Si vas a implementar…         | Leer sección del doc     |
|-------------------------------|--------------------------|
| API de estado (slide/demo)    | §2 Endpoints, §8 API REST |
| Vistas HTML                   | §7 Detalle de Componentes |
| Notas del presentador         | §7.5, §9.2               |
| Links del main-panel          | §7.6, §9.2               |
| Lógica de polling             | §3 Arquitectura          |
| Config JSON (notas, links)    | §9.2 Configuración Propuesta |

---

## 8. Flujo de Trabajo para el Agente

1. **Leer AGENTS.md** (este archivo) y `CLAUDE.md` antes de cualquier cambio.
2. **Verificar la historia de usuario** relevante en `§6` y el doc de análisis.
3. **Seguir la estructura de paquetes** definida en `§4`.
4. **No crear dependencias entre servicios desde el código** — todo el cross-service
   communication va por HTTP (`WebClient`).
5. **Correr el check de compilación** antes de reportar la tarea como completada:
   ```bash
   ./mvnw clean compile -pl slidehub-service -am
   ```
6. **Reportar qué archivos se crearon/modificados** al finalizar cada tarea.

---

## 9. Decisiones Abiertas (Pendientes)

> Estas decisiones NO están tomadas todavía. No implementar hasta resolverlas.

| # | Decisión                                   | Opciones                              |
|---|---------------------------------------------|---------------------------------------|
| 1 | Multi-sesión (varias presentaciones)        | Fuera de alcance v1 — no implementar |

**Decisiones ya tomadas (no reabrir):**

| Decisión                        | Resolución                                                                          |
|---------------------------------|-------------------------------------------------------------------------------------|
| Autenticación                   | Spring Security + BCrypt + sesiones HTTP en `ui-service`                            |
| OAuth2                          | GitHub + Google coexistiendo con login local; tokens almacenados en PostgreSQL      |
| Roles                           | `PRESENTER` (control) y `ADMIN` (devices + control)                                 |
| Store de notas IA               | MongoDB en `ai-service`                                                             |
| Store de guías deploy           | MongoDB en `ai-service` (colección `deployment_guides`)                             |
| Integración Gemini y Groq       | HTTP puro vía `WebClient` — sin SDKs de terceros                                    |
| Integración Google Drive        | Google Drive REST API v3 vía `WebClient` — sin SDK oficial                         |
| Gemini Vision                   | `gemini-pro-vision` vía HTTP para análisis de slides PNG                            |
| Deploy Tutor                    | Dentro de `ai-service` como `/api/ai/deploy/**` — no servicio separado              |
| Módulo `showcase`               | Ruta pública de `ui-service` — no servicio propio                                   |
| Despliegue                      | **Render** — un Web Service por microservicio                                       |
| Envío de emails                 | **Resend API** — HTTP vía `WebClient`, sin librerías SMTP ni SDKs                  |
| Almacenamiento de assets        | **Amazon S3** — uploads de usuarios (slides, imágenes) — AWS SDK v2                |
| Base de datos PostgreSQL        | **Aiven** — DSN completo leído de variable de entorno `DATABASE_URL`               |
| Persistence backend             | Redis (estado efímero) + PostgreSQL en Aiven (usuarios, presentaciones persistidas) |
| returnSlide                     | Campo adicional en `DemoState` — compatibilidad hacia atrás (campo nullable)        |

---

## 10. Comandos Útiles

```bash
# Compilar todo el proyecto
./mvnw clean compile

# Compilar un módulo específico
./mvnw clean compile -pl state-service -am

# Ejecutar tests de un módulo
./mvnw test -pl ai-service

# Correr state-service localmente
./mvnw spring-boot:run -pl state-service

# Correr todos los servicios (cuando existan)
./mvnw spring-boot:run -pl gateway-service &
./mvnw spring-boot:run -pl state-service &
./mvnw spring-boot:run -pl ui-service &
./mvnw spring-boot:run -pl ai-service &
```

---

## 11. Lo que NO hacer

- ❌ No añadir lógica de negocio al `gateway-service`
- ❌ No hacer que `ui-service` acceda a Redis directamente
- ❌ No usar SDKs oficiales de Gemini o Groq — integrar solo vía HTTP con `WebClient`
- ❌ No usar el SDK oficial de Google Drive — integrar solo vía HTTP con `WebClient`
- ❌ No hardcodear API keys de Gemini/Groq — siempre leer de variables de entorno
- ❌ No hardcodear URLs de otros servicios — usar Config Server o variables de entorno
- ❌ No crear un servicio de BD/JPA si Redis es suficiente para el estado
- ❌ No mezclar `@Controller` (MVC) y `@RestController` en el mismo servicio de manera inconsistente
- ❌ No implementar WebSockets todavía — el polling es suficiente para v1
- ❌ No tocar el `pom.xml` raíz sin entender que es el parent de un multi-módulo Maven
- ❌ No almacenar notas del presentador en Redis — pertenecen a MongoDB en `ai-service`
- ❌ No almacenar guías de deploy en Redis — pertenecen a MongoDB en `ai-service`
- ❌ No implementar multi-sesión (varias presentaciones en paralelo) — fuera de alcance v1
- ❌ No usar Resend SDK oficial — integrar solo vía HTTP (`WebClient`) igual que Gemini/Groq
- ❌ No hardcodear credenciales de AWS S3 ni Aiven — siempre leer de variables de entorno
- ❌ No almacenar archivos de usuarios en el sistema de archivos local — todo a S3
- ❌ No conectarse a una PostgreSQL fuera de Aiven en producción — usar `DATABASE_URL`
- ❌ No configurar CORS, headers de trust ni IPs de Render manualmente por servicio; centralizar en `gateway-service`
- ❌ No almacenar tokens OAuth2 (GitHub/Google) en texto plano en PostgreSQL — encriptar

---

## 12. Infraestructura de Despliegue

### Render (plataforma de hosting)

Cada microservicio es un **Web Service** independiente en Render:

| Servicio | Nombre en Render | Build Command | Start Command |
|---|---|---|---|
| `slidehub-service` | `slidehub-service` | `./mvnw -pl slidehub-service package -am` | `java -jar slidehub-service/target/*.jar` |

**Variables de entorno en Render (por servicio):**
```
# Globales (todos los servicios)
SPRING_PROFILES_ACTIVE=prod

# state-service
REDIS_HOST=<redis-url>
REDIS_PORT=6379

# ui-service
STATE_SERVICE_URL=https://slidehub-state.onrender.com
AI_SERVICE_URL=https://slidehub-ai.onrender.com

# ai-service
MONGODB_URI=<mongodb-uri>
GEMINI_API_KEY=<key>
GROQ_API_KEY=<key>
RESEND_API_KEY=<key>

# Servicios con PostgreSQL (Fase 1)
DATABASE_URL=<aiven-dsn>   # formato: jdbc:postgresql://host:port/dbname?ssl=...
```

> Los servicios en Render usan HTTPS automáticamente. No configurar SSL manual.

### Resend (envío de emails)

- **Uso:** verificación de correo en registro (Fase 1), notificaciones.
- **Integración:** HTTP POST a `https://api.resend.com/emails` con `Authorization: Bearer ${RESEND_API_KEY}`
- **No usar:** Resend SDK, JavaMail, Spring Mail — solo `WebClient`.
- **Servicio responsable:** `ui-service` para emails de auth; `ai-service` para notificaciones de notas generadas si aplica.

```java
// Ejemplo de llamada a Resend vía WebClient
webClient.post()
    .uri("https://api.resend.com/emails")
    .header("Authorization", "Bearer " + resendApiKey)
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(Map.of(
        "from", "noreply@slidehub.app",
        "to", List.of(userEmail),
        "subject", "Confirma tu cuenta",
        "html", "<p>Tu código: " + code + "</p>"
    ))
    .retrieve()
    .bodyToMono(Void.class)
    .block();
```

### Amazon S3 (almacenamiento de assets)

- **Uso:** slides PNG subidos por usuarios, portadas de presentaciones.
- **Integración:** AWS SDK v2 (`software.amazon.awssdk:s3`) — es una excepción justificada al patrón WebClient-only porque S3 requiere firma SigV4.
- **Servicio responsable:** `ui-service` maneja los uploads; las URLs públicas se devuelven al frontend.
- **Variables requeridas:**
  - `AWS_ACCESS_KEY_ID`
  - `AWS_SECRET_ACCESS_KEY`
  - `AWS_S3_BUCKET`
  - `AWS_REGION` (p.ej. `us-east-1`)
- **URL de objeto:** `https://${AWS_S3_BUCKET}.s3.${AWS_REGION}.amazonaws.com/<key>`
- **No** almacenar archivos en el filesystem de Render — es efímero (se borra en cada deploy).

### Aiven (PostgreSQL)

- **Uso:** usuarios, sesiones persistidas, presentaciones (Fase 1+).
- **DSN:** Aiven provee un DSN completo con SSL. Se lee de `DATABASE_URL`.
- **SSL obligatorio:** Aiven requiere `sslmode=require` en la URL de conexión.
- **Configuración en `application-prod.properties`:**
  ```properties
  spring.datasource.url=${DATABASE_URL}
  spring.datasource.driver-class-name=org.postgresql.Driver
  spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
  spring.jpa.hibernate.ddl-auto=validate
  ```
- **Migraciones:** Liquibase o Flyway — no usar `ddl-auto=create` en producción.

---

*Actualizado: Marzo 2026 — v1 completada (Fases 0-5)*