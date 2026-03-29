# Plan: Expansión SlideHub — OAuth, Drive, IA y Deploy Tutor

**Fecha de creación:** 27 de febrero de 2026  
**Versión del plan:** v1.0  
**Estado:** En evaluación

---

## TL;DR

Expandir SlideHub con:
- **(A)** Autenticación dual: local (BCrypt) + OAuth2 (GitHub para repos, Google para Drive)
- **(B)** Importación de slides desde Google Drive + upload manual de PNGs
- **(C)** Pipeline de IA ampliado: Gemini Vision (analiza slides) + Gemini (extrae repo) + Groq (amplía notas)
- **(D)** Controles de iframe mejorados en el remote con quick links y "volver al slide"
- **(E)** Tutor de deployment con generación automática de Dockerfiles dentro del `ai-service`

Esto requiere actualizar AGENTS.md y CLAUDE.md, resolver decisiones abiertas, y ejecutar 6 fases incrementales de implementación.

---

## Decisiones Confirmadas para Incorporar

| Decisión | Resolución |
|----------|------------|
| **Auth model** | Local (BCrypt) + OAuth2 (GitHub + Google) coexistiendo — sin reemplazar uno al otro |
| **Fuente de slides** | Google Drive (primario) + upload manual de PNGs (fallback) |
| **Deploy tutor** | Dentro de `ai-service` como `/api/ai/deploy/**` endpoints |
| **Roles** | Se mantiene separación PRESENTER + ADMIN (no todos son admin) |
| **Persistencia** | **RESOLVER**: Decisión abierta AGENTS.md §9 #1 — este plan resuelve a favor de **Redis + PostgreSQL** (Redis para estado efímero, PostgreSQL para datos duraderos) |

---

## Fase 0 — Fundación (Prerequisito)

> **Estado:** No completada aún. Esta es la base para todas las fases siguientes.

Convertir el proyecto actual (single-module) en arquitectura multi-módulo Maven.

### Tareas

1. **Convertir `pom.xml` a parent POM multi-módulo**
   - Cambiar `<packaging>` de `jar` a `pom`
   - Añadir bloque `<modules>` con 4 servicios: `state-service`, `ui-service`, `ai-service`, `gateway-service`
   - Mover todas las dependencias a `<dependencyManagement>` (dejar vacío `<dependencies>`)
   - Crear `pom.xml` individual para cada submódulo
   - **Referencia:** [CLAUDE.md §6](CLAUDE.md#L184)

2. **Crear estructura base de cada servicio**
   - Directorios: `state-service/`, `ui-service/`, `ai-service/`, `gateway-service/`
   - Cada uno con `src/main/java/com/brixo/slidehub/<service>/`, `src/main/resources/`, `pom.xml`
   - Corregir paquete raíz: de `com.brixo.SlideHub` (S mayúscula) a `com.brixo.slidehub` (minúscula)
   - Crear `SlideHubApplication.java` con `@SpringBootApplication` en cada servicio
   - **Referencia:** [CLAUDE.md §4](CLAUDE.md#L54)

3. **Configurar `application.properties` por servicio**
   - `state-service`: puerto 8081, Redis config
   - `ui-service`: puerto 8082, URLs de state-service y ai-service
   - `ai-service`: puerto 8083, MongoDB URI, API keys de Gemini/Groq
   - `gateway-service`: puerto 8080, config server
   - **Referencia:** [CLAUDE.md §7](CLAUDE.md#L214)

4. **Configurar API Gateway**
   - Crear `GatewayConfig.java` con `@Bean RouteLocator`
   - Definir tabla de rutas según [AGENTS.md §2.4](AGENTS.md#L168-L178)
   - **Orden crítico:** `/api/ai/**` ANTES de `/api/**`
   - Rutas de UI: `/slides`, `/remote`, `/presenter`, `/main-panel`, `/demo`, `/showcase` → `ui-service:8082`

5. **Implementar API de estado core en `state-service`**
   - Endpoints: `GET/POST /api/slide`, `GET/POST /api/demo` (HU-004, HU-008)
   - Modelos: `SlideState`, `DemoState` (records)
   - Redis store: clave `current_slide`, clave `demo_state`
   - **Referencia:** [AGENTS.md §2.1](AGENTS.md#L45), HU-008

6. **Implementar vistas públicas básicas en `ui-service`**
   - Rutas: `/slides`, `/remote`, `/demo`, `/showcase` (todas público, sin auth)
   - Templates Thymeleaf: polling a `/api/slide` y `/api/demo` vía gateway
   - Estáticos: colocar `static/slides/` con imágenes de ejemplo
   - **Referencia:** [AGENTS.md §2.2](AGENTS.md#L94)

### Verificación Fase 0

```bash
./mvnw clean compile

# Todos los servicios deben compilar sin errores
# Verificar que el gateway enruta correctamente entre servicios
```

---

## Fase 1 — Autenticación Dual (Local + OAuth2)

> **Dependencia:** Fase 0 completada

Implementar login local con BCrypt + OAuth2 con GitHub y Google.

### Tareas

7. **Implementar autenticación local en `ui-service`**
   - Crear modelo `User` con campos: `id`, `username`, `email`, `passwordHash` (BCrypt), `role` (PRESENTER|ADMIN), `createdAt`
   - Crear `UserService` y `UserRepository` (interface) — **decisión abierta:** almacenar en PostgreSQL o Redis? Este plan resuelve a PostgreSQL
   - Crear `AuthController` con:
     - `GET /auth/login` → formulario, redirige a `/presenter` si ya autenticado (HU-001 §3)
     - `POST /auth/login` → valida con BCrypt, crea sesión HTTP
     - `GET /auth/register` → formulario
     - `POST /auth/register` → crea usuario si username no existe (HU-002)
     - `POST /auth/logout` → invalida sesión (HU-003)
   - Error genérico: "Usuario o contraseña incorrectos" sin indicar cuál falló (HU-001 §2)
   - **Referencia:** [CLAUDE.md §11](CLAUDE.md#L488), HU-001/002/003

8. **Actualizar dependencias de `ui-service`**
   - Añadir `spring-boot-starter-security` al pom.xml
   - Añadir `spring-boot-starter-oauth2-client` (nuevo)
   - Añadir `spring-boot-starter-data-jpa` (nuevo) para persistencia de usuarios
   - Añadir `postgresql` driver
   - **Referencia:** [CLAUDE.md §6](CLAUDE.md#L184)

9. **Configurar GitHub OAuth2**
   - Registrar aplicación en GitHub Settings → Developer settings → OAuth Apps
   - Authorization callback URL: `http://localhost:8082/login/oauth2/code/github`
   - Guardar `GITHUB_CLIENT_ID` y `GITHUB_CLIENT_SECRET` en variables de entorno
   - Configurar en `application.properties`:
     ```properties
     spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID}
     spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET}
     spring.security.oauth2.client.registration.github.scope=repo
     spring.security.oauth2.client.provider.github.user-name-attribute=login
     ```
   - Crear `GithubOAuth2Service` para almacenar token de acceso: campo `githubAccessToken` en modelo `User`

10. **Configurar Google OAuth2**
    - Registrar proyecto en Google Cloud Console
    - Crear OAuth2 credential (Web application)
    - Authorized redirect URIs: `http://localhost:8082/login/oauth2/code/google`
    - Scopes: `openid profile email drive.readonly`
    - Guardar `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` en env vars
    - Configurar en `application.properties`:
      ```properties
      spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
      spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
      spring.security.oauth2.client.registration.google.scope=openid,profile,email,drive.readonly
      spring.security.oauth2.client.provider.google.user-name-attribute=email
      ```
    - Crear `GoogleOAuth2Service` para almacenar token de acceso: campo `googleRefreshToken` en modelo `User`

11. **Ampliar modelo `User` para OAuth**
    - Nuevos campos:
      - `githubId` (String, único, nullable)
      - `githubUsername` (String, nullable)
      - `githubAccessToken` (String, encrypted, nullable)
      - `googleId` (String, único, nullable)
      - `googleEmail` (String, nullable)
      - `googleRefreshToken` (String, encrypted, nullable)
    - Método de encriptación: usar Spring Security `PasswordEncoder` (BCrypt) para tokens, o biblioteca `jasypt` para campos sensibles

12. **Implementar flujo de vinculación de cuentas OAuth**
    - Nueva vista `/auth/profile` (protegida PRESENTER): mostrar cuentas vinculadas
    - Botónes: "Vincular GitHub" → redirige a OAuth flow → almacena token
    - Botónes: "Vincular Google" → redirige a OAuth flow → almacena token
    - Endpoint `POST /auth/unlink/{provider}` para desvincular (GitHub|Google)

13. **Crear `SecurityConfig` en `ui-service`**
    - `@Configuration @EnableWebSecurity`
    - Rutas públicas: `/slides`, `/remote`, `/demo`, `/showcase`, `/auth/**`, `/presentation/**`, `/api/**` (polling)
    - Rutas protegidas PRESENTER|ADMIN: `/presenter`, `/main-panel`, `/auth/profile`
    - Rutas protegidas ADMIN: `/api/devices/**`
    - Form login → `/auth/login`
    - OAuth2 login → `/login/oauth2/code/{github,google}`
    - Success URL → `/presenter`
    - **Referencia:** [CLAUDE.md §11.2](CLAUDE.md#L511)

14. **Añadir rutas OAuth al gateway**
    - `/auth/oauth2/**` → `ui-service:8082`
    - `POST /login/oauth2/**` → `ui-service:8082`

15. **Actualizar tabla de vistas en AGENTS.md**
    - Añadir: `/auth/profile | profile.html | PRESENTER | Vincular/desvincular OAuth`

### Verificación Fase 1

```bash
./mvnw clean compile -pl ui-service -am

# Login local: crear usuario, ingresar credenciales → sesión creada
# GitHub OAuth: vincular cuenta → verificar token almacenado
# Google OAuth: vincular cuenta → verificar token almacenado
# Session activa + acceso a /auth/login → redirect a /presenter ✓
```

---

## Fase 2 — Importación de Slides (Google Drive + Upload)

> **Dependencia:** Fase 0 completada. Fase 1 es recomendada (Google Drive requiere token OAuth).

Permitir importar slides desde Google Drive o cargar PNGs manualmente.

### Tareas

16. **Crear `GoogleDriveService` en `ui-service`**
    - Usar `WebClient` (no SDK) para llamar Google Drive REST API v3
    - Método `listFolders(googleAccessToken)` → `GET https://www.googleapis.com/drive/v3/files?q=mimeType='application/vnd.google-apps.folder'`
      - Respuesta: lista de folders con `id`, `name`
    - Método `listImagesInFolder(folderId, token)` → `GET https://www.googleapis.com/drive/v3/files?q='<folderId>' in parents and mimeType contains 'image/'`
      - Respuesta: lista de archivos con `id`, `name`, `mimeType`
    - Método `downloadImage(fileId, token)` → `GET https://www.googleapis.com/drive/v3/files/<fileId>?alt=media`
      - Devuelve byte[] de la imagen
    - **Referencia:** [CLAUDE.md — nuevo §: Google Drive REST API via WebClient]

17. **Ampliar modelo `User`**
    - Nuevo campo: `defaultDriveFolderId` (String, nullable) — último folder seleccionado

18. **Crear modelo `Presentation`**
    - Tabla en PostgreSQL (via JPA/Hibernate)
    - Campos:
      - `id` (UUID, primary key)
      - `userId` (FK a User)
      - `name` (String, not null)
      - `description` (String, nullable)
      - `sourceType` (enum: "DRIVE", "UPLOAD")
      - `driveFolderId` (String, nullable)
      - `driveFolderName` (String, nullable)
      - `repoUrl` (String, nullable) — URL del repo GitHub para análisis de IA
      - `slides` (List<Slide> vía One-to-Many)
      - `quickLinks` (List<QuickLink> vía One-to-Many)
      - `createdAt` (LocalDateTime)
      - `updatedAt` (LocalDateTime)
    - **Nueva decisión para docs:** Presentaciones se almacenan en PostgreSQL, no en Redis

19. **Crear modelo `Slide`**
    - Tabla en PostgreSQL
    - Campos:
      - `id` (UUID, primary key)
      - `presentationId` (FK a Presentation)
      - `number` (int, 1-based)
      - `filename` (String) — nombre local de la imagen
      - `driveFileId` (String, nullable) — ID del archivo en Drive si vino desde Drive
      - `localPath` (String) — ruta local donde se almacena: `/static/slides/{presentationId}/{number}.png`
      - `uploadedAt` (LocalDateTime)

20. **Crear modelo `QuickLink`** (para Fase 4)
    - Tabla en PostgreSQL
    - Campos:
      - `id` (UUID)
      - `presentationId` (FK)
      - `title` (String)
      - `url` (String)
      - `icon` (String) — nombre de Font Awesome icon
      - `description` (String, nullable)
      - `order` (int) — para ordenar visualmente

21. **Implementar vista `/presentations/import` en `ui-service`**
    - Ruta protegida PRESENTER (GET /POST)
    - GET: formulario con dos tabs: "Google Drive" | "Upload Manual"
      - Tab 1: si el usuario tiene Google vinculado, mostrar árbol navegable de carpetas
        - Usar JS con fetch a `/api/presentations/drive/folders` → árbol dinámico
        - Seleccionar folder → se cargan las imágenes
        - Click en "Importar" → POST a `/api/presentations/create-from-drive`
      - Tab 2: input de archivo múltiple (accept=".png,.jpg,.jpeg,.gif")
        - Click en "Importar" → POST a `/api/presentations/create-from-upload`

22. **Implementar endpoints de importación en `ui-service`**
    - `GET /api/presentations/drive/folders` (protegido PRESENTER)
      - Requiere: `Authorization: Bearer <googleAccessToken>`
      - Responde: `{ folders: [{ id, name }] }`
      - Delega a `GoogleDriveService.listFolders()`
    
    - `GET /api/presentations/drive/folders/{folderId}/images` (protegido PRESENTER)
      - Responde: `{ images: [{ id, name, mimeType }] }`
    
    - `POST /api/presentations/create-from-drive` (protegido PRESENTER)
      - Request: `{ name, description, driveFolderId, repoUrl? }`
      - Workflow:
        1. Crear entrada `Presentation` en PostgreSQL
        2. Listar imágenes de la carpeta Drive
        3. Por cada imagen (en orden alfabético/numérico):
           - Descargar via `GoogleDriveService.downloadImage()`
           - Guardar localmente en `static/slides/{presentationId}/{slideNumber}.png`
           - Crear entrada `Slide` en PostgreSQL
        4. Responder: `{ presentationId, slides: [{ number, filename }, ...] }`
      - Transaccional: si algo falla, rollback de todo
    
    - `POST /api/presentations/create-from-upload` (protegido PRESENTER)
      - Request: `multipart/form-data` con { name, description, repoUrl?, files: [...] }
      - Workflow:
        1. Crear entrada `Presentation` en PostgreSQL
        2. Por cada archivo uploadado (en orden recibido):
           - Guardar localmente en `static/slides/{presentationId}/{slideNumber}.png`
           - Crear entrada `Slide`
        3. Responder: `{ presentationId, slides: [...] }`

23. **Crear `PresentationService` en `ui-service`**
    - Métodos:
      - `createFromDrive(userId, name, driveFolderId, token)` — orquesta el download y persistencia
      - `createFromUpload(userId, name, files)` — orquesta la persistencia de archivos uploadados
      - `getPresentation(userId, presentationId)` — obtiene datos de la presentación
      - `listPresentations(userId)` — lista todas las presentaciones del usuario

24. **Auto-generar slideshow en `state-service`**
    - Una vez importados los N slides, inicializar estado:
      - Enviar `POST /api/slide { slide: 1 }` (aunque sea automático)
      - `totalSlides` se calcula escaneando `static/slides/{presentationId}/` en cada `GET /api/slide`
    - O, mejor: pasar `presentationId` al estado de `state-service` para que sepa cuál presentación está activa

25. **Soportar upload manual como fallback**
    - Tab 2 en `/presentations/import` con form de subida de archivos
    - Aceptar PNG, JPG, JPEG, GIF
    - Guardar en `static/slides/{presentationId}/`

26. **Actualizar rutas del gateway**
    - `/presentations/**` → `ui-service:8082`

### Verificación Fase 2

```bash
./mvnw clean compile -pl ui-service -am

# Google Drive:
#   - Usuario vinculado con Google
#   - Accede a /presentations/import
#   - Selecciona folder de Drive con N imágenes
#   - Click en "Importar"
#   - N slides aparecen en /slides ✓

# Upload manual:
#   - Sube 5 PNGs
#   - 5 slides aparecen en /slides ✓

# Estado sincronizado:
#   - slide = 1, totalSlides = N ✓
```

---

## Fase 3 — Pipeline de IA Ampliado

> **Dependencia:** Fase 0 + Fase 2 completadas

Implementar análisis de imágenes con Gemini Vision + pipeline de 3 pasos para generar notas.

### Tareas

27. **Añadir Gemini Vision a `GeminiService` en `ai-service`**
    - Nuevo método: `analyzeSlideImage(byte[] imageData)` → `String`
    - Solicitud a Google Gemini Vision API: `POST /v1beta/models/gemini-pro-vision:generateContent`
    - Payload:
      ```json
      {
        "contents": [{
          "parts": [
            { "inlineData": { "mimeType": "image/png", "data": "<base64-imagen>" } },
            { "text": "Analiza esta diapositiva. ¿Cuál es el tema principal? ¿Qué conceptos técnicos se muestran?" }
          ]
        }]
      }
      ```
    - Respuesta: JSON con `candidates[0].content.parts[0].text` → descripción del slide

28. **Ampliar `GeminiService.extractRepoContext()`**
    - Ahora recibe: `repoUrl`, `slideDescription` (antes: `slideContext`)
    - Adapta el prompt para usar la descripción extraída de Gemini Vision:
      ```
      Analiza el repositorio en {repoUrl} y extrae contenido relevante para un slide 
      que trata sobre: {slideDescription}. Devuelve puntos clave técnicos en formato estructurado.
      ```

29. **Ampliar `GroqService.generateNote()`**
    - Ahora recibe: `repoContext`, `slideDescription`, `slideNumber` (antes: solo estos dos últimos)
    - Prompt refinado:
      ```
      Basándote en esta descripción del slide: {slideDescription}
      Y este contexto técnico del repositorio: {repoContext}
      
      Genera notas estructuradas en JSON:
      {
        "title": "Título corto del slide",
        "points": ["punto técnico 1", "punto técnico 2", ...],
        "suggestedTime": "~2 min",
        "keyPhrases": ["frase clave 1", ...],
        "demoTags": ["tag de demo 1", ...]
      }
      ```

30. **Crear modelo `RepoAnalysis` en MongoDB**
    - Colección: `repo_analysis`
    - Documento:
      ```json
      {
        "_id": ObjectId,
        "repoUrl": "https://github.com/...",
        "analyzedAt": ISODate(),
        "language": "Java|PHP|JavaScript|...",
        "framework": "Spring Boot|Laravel|Next.js|...",
        "technologies": ["Redis", "PostgreSQL", "Docker", ...],
        "buildSystem": "Maven|Gradle|npm|composer|...",
        "summary": "Descripción breve del proyecto...",
        "structure": "Explicación de la arquitectura...",
        "deploymentHints": "Recomendaciones de despliegue en Render/Vercel/...",
        "dockerfile": "Contenido de Dockerfile sugerido..."
      }
      ```

31. **Ampliar endpoint `POST /api/ai/notes/generate` en `ai-service`**
    - Nuevo request body:
      ```json
      {
        "presentationId": "uuid",
        "slideNumber": 1,
        "repoUrl": "https://github.com/...",
        "imageData": "base64 de la imagen del slide o null",
        "slideContext": "Descripción breve (fallback si no hay imagen)"
      }
      ```
    - Workflow (pipeline de 3 pasos):
      1. Si `imageData` no es null:
         - Verificar si ya existe análisis de IA para esta imagen
         - Si no, llamar `GeminiService.analyzeSlideImage(imageData)` → `slideDescription`
      2. Si `slideContext` viene, usarlo; sino, usar `slideDescription`
      3. Llamar `GeminiService.extractRepoContext(repoUrl, slideDescription)` → `repoContext`
      4. Llamar `GroqService.generateNote(repoContext, slideDescription, slideNumber)` → structuredNote
      5. Guardar `PresenterNote` en MongoDB (sobreescribir si existe — HU-016 §2)
      6. Responder: `{ success: true, note: { ... } }`
    - En caso de error IA: `{ success: false, errorMessage: "..." }` (HU-016 §3)

32. **Crear endpoint `POST /api/ai/notes/generate-all` en `ai-service`**
    - Recibe: `{ presentationId, repoUrl }`
    - Itera por cada slide de la presentación (obtenido del `ui-service`):
      - Descarga la imagen desde `static/slides/{presentationId}/{slideNumber}.png`
      - Llama `POST /api/ai/notes/generate` con `imageData`
      - Espera (sleep) 1-2 segundos entre slides para no saturar las APIs
    - Responder al final: `{ success: true, notesGenerated: N }`
    - **Este endpoint es largo:** implementar con `@Async` o devolver 202 Accepted + estado de progreso

33. **Crear endpoint `POST /api/ai/analyze-repo` en `ai-service`**
    - Recibe: `{ repoUrl }`
    - Workflow:
      - Verificar si ya existe análisis en `repo_analysis`
      - Si no:
        - Llamar Gemini: extraer lenguaje, framework, tecnologías, estructura, hints de deployment
        - Generar Dockerfile candidato (será usado en Fase 5)
        - Guardar en MongoDB colección `repo_analysis`
    - Responder: `{ language, framework, technologies[], summary, dockerfile }`
    - Reutilizar este análisis en Fase 3 paso 31 y en Fase 5

34. **Ampliar `PresenterNoteRepository` en `ai-service`**
    - Método: `List<PresenterNote> findByPresentationIdOrderBySlideNumberAsc()` (ya existe)
    - Nuevo: método para batch delete + batch create (para generation-all)

35. **Crear vista `/presentations/{id}/generate-notes` en `ui-service`** (opcional, para Fase 3)
    - Ruta protegida PRESENTER
    - Formulario con campo `repoUrl` (o detectar automáticamente del modelo `Presentation`)
    - Botón "Generar Notas de IA"
    - Llamar `POST /api/ai/notes/generate-all` vía AJAX
    - Mostrar progreso: "Analizando slide 1 de N..."
    - Al finalizar, mostrar notas generadas

### Verificación Fase 3

```bash
./mvnw clean compile -pl ai-service -am

# Generar nota para un slide:
#   - POST /api/ai/notes/generate { imageData, repoUrl, ... }
#   - Gemini Vision analiza imagen → descripción ✓
#   - Gemini extrae repo context → resumen técnico ✓
#   - Groq genera nota ampliada → title + points + keyPhrases ✓

# Análisis de repositorio:
#   - POST /api/ai/analyze-repo { repoUrl: "github.com/..." }
#   - Guardado en MongoDB colección repo_analysis ✓

# Batch generation:
#   - POST /api/ai/notes/generate-all { presentationId, repoUrl }
#   - N notas generadas en orden ✓
```

---

## Fase 4 — Presentación con iframe Mejorado

> **Dependencia:** Fase 0 + Fase 2 completadas

Ampliar controles del remote y main-panel para navegar proyectos en vivo dentro del iframe.

### Tareas

36. **Ampliar modelo `DemoState` en `state-service`**
    - Nuevo campo: `returnSlide` (Integer, nullable)
    - Estructura:
      ```json
      {
        "mode": "slides|url",
        "slide": 1,
        "url": "/project-path",
        "returnSlide": 3
      }
      ```
    - Cuando se activa modo URL, se guarda el slide actual: `returnSlide = currentSlide`

37. **Crear modelo `QuickLink` en PostgreSQL** (si no se hizo en Fase 2)
    - Campos: `id`, `presentationId`, `title`, `url`, `icon`, `description`, `order`
    - CRUD endpoints en `ui-service`:
      - `GET /api/presentations/{id}/links` (protegido PRESENTER)
      - `POST /api/presentations/{id}/links` (protegido PRESENTER)
      - `PUT /api/presentations/{id}/links/{linkId}` (protegido PRESENTER)
      - `DELETE /api/presentations/{id}/links/{linkId}` (protegido PRESENTER)

38. **Ampliar vista `/main-panel`**
    - Sidebar derecho: "Quick Links de Demo"
      - Cargar links desde `GET /api/presentations/{presentationId}/links`
      - Mostrar como lista clicable: [🌐] Titulo | Descripción
      - Click: envía `POST /api/demo { mode: "url", url: "...", returnSlide: currentSlide }`
    - Botón "Editar Links" (solo visible para ADMIN): abre modal con CRUD

39. **Ampliar vista `/remote` (smartphone)**
    - Botón "Mostrar Demo" (icono de play o globe)
    - Click abre un bottom sheet o modal con lista de quick links
    - Click en un link:
      - Envía `POST /api/demo { mode: "url", url: "...", returnSlide: currentSlide }`
      - Bottom sheet se cierra
      - Pantalla `/demo` (si está abierta) muestra el iframe
    - Botón "Volver al Slide" (solo visible mientras mode="url"):
      - Envía `POST /api/demo { mode: "slides" }`
      - Envía `POST /api/slide { slide: returnSlide }` para restaurar el número exacto

40. **Ampliar vista `/demo`**
    - Al recibir modo "url" con `returnSlide`:
      - Guardar `returnSlide` en memoria (variable JS)
      - Mostrar botón "Volver al Slide X" en el iframe
    - Al volver a slides:
      - El polling de `/api/demo` detecta que `mode: "slides"` y restaura la vista de slides
      - El polling de `/api/slide` debería estar sincronizado — si el servidor envió `returnSlide`, la pantalla se actualiza

41. **Crear lógica de "return to slide" en state-service**
    - Cuando se envía `POST /api/demo { mode: "url", returnSlide: 3 }`:
      - Guardar en Redis: `demo_state = { mode: "url", returnSlide: 3, ... }`
    - Cuando se envía `POST /api/demo { mode: "slides" }`:
      - Enviar `POST /api/slide { slide: demo_state.returnSlide }`
      - Devolver `demo_state = { mode: "slides" }`

### Verificación Fase 4

```bash
./mvnw clean compile -pl ui-service,state-service -am

# Quick Links:
#   - Admin configura 3 links para el proyecto
#   - Links aparecen en /main-panel sidebar ✓

# Demo desde remote:
#   - Click "Mostrar Demo"
#   - Bottom sheet lista los quick links ✓
#   - Click en un link → modo URL activado ✓

# Volver al slide:
#   - Botón "Volver al Slide 5"
#   - Click → returnSlide restaura el estado anterior ✓

# iframe sincronización:
#   - /demo muestra iframe mientras mode="url" ✓
#   - Botón "Volver" lo restaura a slides ✓
```

---

## Fase 5 — Tutor de Deployment (ai-service)

> **Dependencia:** Fase 0 + Fase 3 completadas

Crear asistente de IA para generar Dockerfiles y guías de deployment.

### Tareas

42. **Crear nuevos endpoints en `ai-service`**
    
    **A) `POST /api/ai/deploy/analyze`**
    - Recibe: `{ repoUrl }`
    - Workflow:
      - Verificar si existe análisis en colección `repo_analysis`
      - Si no, accionar `GeminiService.analyzeRepo()` → nuevo método que extrae:
        - Lenguaje (Java, PHP, JavaScript, Python, Go, etc.)
        - Framework (Spring Boot, Laravel, Next.js, FastAPI, etc.)
        - Build system (Maven, Gradle, npm, composer, pip, go mod, etc.)
        - Puertos expuestos
        - Variables de entorno necesarias
        - Dependencias de BD (PostgreSQL, MongoDB, Redis, etc.)
      - Guardar en `repo_analysis`
    - Responder:
      ```json
      {
        "language": "Java",
        "framework": "Spring Boot",
        "buildSystem": "Maven",
        "ports": [8080, 8081],
        "environment": ["DATABASE_URL", "REDIS_HOST"],
        "databases": ["PostgreSQL", "Redis"],
        "summary": "Sistema de presentación multi-servicio..."
      }
      ```

    **B) `POST /api/ai/deploy/dockerfile`**
    - Recibe:
      ```json
      {
        "repoUrl": "https://github.com/...",
        "language": "Java",
        "framework": "Spring Boot",
        "ports": [8080],
        "environment": ["SPRING_DATASOURCE_URL"]
      }
      ```
    - Workflow:
      - Usar Groq para generar Dockerfile optimizado:
        ```
        Genera un Dockerfile para una aplicación {framework} en {language}.
        Puertos expuestos: {ports}
        Variables de entorno: {environment}
        
        El Dockerfile debe:
        - Usar imagen base apropiada (openjdk:21-slim para Java, etc.)
        - Optimizar las capas (multi-stage si aplica)
        - Incluir healthcheck
        - Respetar buenas prácticas de seguridad
        
        Responde SOLO el contenido del Dockerfile, sin ```dockerfile```.
        ```
      - Guardar en `repo_analysis.dockerfile`
    - Responder: `{ dockerfile: "FROM openjdk:21-slim\n..." }`

    **C) `POST /api/ai/deploy/guide`**
    - Recibe: `{ repoUrl, platform: "render"|"vercel"|"netlify" }`
    - Workflow:
      - Para Render: generar guía con PostgreSQL en Render, variables de entorno, env.example, build command, start command
      - Para Vercel: generar guía con API routes (si backend bajo /api), environment variables, deployment
      - Para Netlify: asumir frontend static + backend separado en Render
      - Usar Groq:
        ```
        Genera una guía paso a paso para desplegar esta aplicación {framework} en {platform}.
        
        La guía debe incluir:
        1. Conectar repositorio
        2. Configurar bases de datos (si aplica)
        3. Establecer variables de entorno
        4. Revisar build/start commands
        5. Desplegar
        6. Verificar logs
        
        Formato: lista numerada, concisa, con ejemplos de comandos.
        ```
    - Responder: `{ guide: "1. Crear cuenta en Render...", tips: [...] }`

43. **Crear modelo `DeploymentGuide` en MongoDB**
    - Colección: `deployment_guides`
    - Documento:
      ```json
      {
        "_id": ObjectId,
        "repoUrl": "https://github.com/...",
        "platform": "render|vercel|netlify",
        "analyzedAt": ISODate(),
        "dockerfile": "Contenido del Dockerfile...",
        "guide": "Pasos de deployment...",
        "tips": ["Tip 1", "Tip 2"],
        "environmentExample": "DATABASE_URL=\nREDIS_HOST=\n..."
      }
      ```

44. **Crear `DeploymentService` en `ai-service`**
    - Métodos:
      - `analyzeRepository(repoUrl)` → `RepositoryAnalysis`
      - `generateDockerfile(analysis)` → `String` (contenido Dockerfile)
      - `generateDeploymentGuide(repoUrl, platform)` → `DeploymentGuide`

45. **Crear vista `/deploy-tutor` en `ui-service`**
    - Ruta protegida PRESENTER
    - Formulario:
      - Input: "URL del repositorio GitHub"
      - Selector: "Plataforma de despliegue" (Render, Vercel, Netlify)
      - Botón: "Analizar y Generar Guía"
    - Al hacer clic:
      - Mostrar spinner: "Analizando repositorio..."
      - `POST /api/ai/deploy/analyze { repoUrl }`
      - Mostrar análisis: "Lenguaje: Java, Framework: Spring Boot, Build: Maven"
      - Luego: "Generando Dockerfile..."
      - `POST /api/ai/deploy/dockerfile { ... }`
      - Mostrar Dockerfile en editor (copiable)
      - Luego: "Generando guía de despliegue..."
      - `POST /api/ai/deploy/guide { repoUrl, platform }`
      - Mostrar guía en markdown (steps numerados, copyable)
    - Opciones de descarga:
      - Descargar Dockerfile
      - Descargar guía como PDF o .md

46. **Actualizar rutas del gateway**
    - `/api/ai/deploy/**` → `ai-service:8083` (ya cubierto por `/api/ai/**`)

### Verificación Fase 5

```bash
./mvnw clean compile -pl ai-service -am

# Análisis de repositorio:
#   - POST /api/ai/deploy/analyze { repoUrl: "github.com/mikerb95/..." }
#   - Responde con language, framework, build system ✓

# Generación de Dockerfile:
#   - POST /api/ai/deploy/dockerfile { ... datos del análisis ... }
#   - Dockerfile válido (verificable con `docker build --dry-run`) ✓

# Guía de deployment:
#   - POST /api/ai/deploy/guide { repoUrl, platform: "render" }
#   - Guía paso a paso para Render ✓

# Vista /deploy-tutor:
#   - Usuario ingresa URL de repo
#   - Selecciona Render
#   - Ve análisis + Dockerfile + guía ✓
```

---

## Fase 6 — Actualización de Documentación

> **Dependencia:** Todas las fases anteriores completadas

Actualizar AGENTS.md y CLAUDE.md con las nuevas características.

### Tareas

47. **Actualizar AGENTS.md**
    
    - **§1 Tabla de features:** Añadir OAuth, Drive, Vision, Quick Links, Deploy Tutor
    - **§2.2 (ui-service):** Añadir nuevas vistas: `/auth/profile`, `/presentations`, `/presentations/import`, `/deploy-tutor`
    - **§2.3 (ai-service):** Ampliar tabla con nuevos endpoints de deploy
    - **§3 Stack:** Añadir Google Drive API, OAuth2, PostgreSQL, Gemini Vision
    - **§6 Catálogo HU:** Añadir HU-021 a HU-030 para nuevas features
    - **§9 Decisiones:** Mover a "Ya decidida" las decisiones resueltas en este plan

48. **Actualizar CLAUDE.md**
    
    - **§1 Stack:** Actualizar con OAuth2, Google Drive, PostgreSQL, Gemini Vision
    - **§6 POMs:** Añadir dependencias para ui-service: oauth2-client, data-jpa, postgresql
    - **§7 Configuración:** Añadir propiedades para OAuth2, PostgreSQL, Gemini Vision
    - **§8-9:** Añadir nuevas secciones para Google Drive REST API, OAuth2 patterns, Deploy Tutor endpoints
    - **§13 Vocabulario:** Agregar términos nuevos: `presentationId`, `driveFolderId`, `quickLink`, `returnSlide`, `deploymentGuide`

---

## Decisiones Tomadas en Este Plan

| Decisión | Resolución | Razón |
|----------|------------|-------|
| **OAuth vs Login local** | Ambos coexisten (no reemplazar local) | Flexibilidad: usuarios pueden elegir |
| **PostgreSQL en decisión abierta #1** | SÍ — PostgreSQL + Redis + JPA | Usuarios y presentaciones necesitan persistencia durable |
| **Deploy tutor en ai-service** | SÍ, no nuevo microservicio | Comparte servicios IA (Gemini, Groq) |
| **Gemini Vision para imágenes** | SÍ, nuevo endpoint | Proporciona contexto visual para notas más precisas |
| **returnSlide en DemoState** | SÍ, ampliación backcompat | Permite volver al slide exacto tras demo |
| **Google Drive REST API** | via WebClient sin SDK | Consistente con rule: "no third-party SDKs" |
| **Encriptación de tokens OAuth** | BCrypt o jasypt | Tokens en PostgreSQL deben protegerse |

---

## Riesgos Identificados

1. **Rate limits de Gemini/Groq**
   - Riesgo: batch generation de 50 slides puede saturar las APIs
   - Mitigación: implementar delays entre llamadas (1-2s)

2. **Almacenamiento de imágenes locales**
   - Riesgo: `static/slides/` puede crecer mucho
   - Mitigación: considerar S3 o similar en fase posterior

3. **Seguridad de tokens OAuth**
   - Riesgo: tokens en PostgreSQL en texto plano
   - Mitigación: usar encriptación con jasypt

4. **Complejidad de multi-plataforma**
   - Riesgo: Groq debe generar diferentes Dockerfiles por plataforma
   - Mitigación: testing exhaustivo, guardar templates

5. **Race condition en returnSlide**
   - Riesgo: usuario cambia slide mientras está en demo
   - Mitigación: validar returnSlide antes de restaurar

---

## Próximos Pasos

1. **Revisión del plan** — validar coherencia con AGENTS.md y CLAUDE.md
2. **Crear HU-021 a HU-030** en el CSV de historias de usuario
3. **Comenzar Fase 0** — convertir pom.xml a multi-módulo
4. **Sprint planning** — asignar fases a sprints de 2 semanas

---

*Documento generado: 27 de febrero de 2026*  
*Plan version: 1.0*  
*Estado: Listo para revisión y ejecución*
