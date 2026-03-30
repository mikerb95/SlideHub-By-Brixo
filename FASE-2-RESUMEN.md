# FASE-2-RESUMEN.md — SlideHub

> **Documento educativo:** resumen integral de la Fase 2 implementada en SlideHub.
> Covers todo el trabajo de importación de presentaciones (Google Drive + Upload manual).
>
> **Fechas:** Iniciado 27 de febrero de 2026 | **Estado:** ✅ COMPLETADO, BUILD SUCCESS

---

## 1. Contexto y Objetivos

### Fase 1 (previo)
- ✅ Autenticación local (BCrypt + PostgreSQL/H2)
- ✅ Integración OAuth2 (Google, GitHub)
- ✅ Persistencia de usuarios en Aiven (producción)
- ✅ Integración S3 para assets
- ✅ Emails vía Resend API

### Fase 2 (actual)
Implementar **importación de presentaciones** desde dos fuentes:

| Origen          | Descripción                                                           | Entidad `SourceType`   |
|-----------------|-----------------------------------------------------------------------|------------------------|
| **Google Drive** | Usuario vinculado con Google selecciona carpeta → descarga imágenes  | `DRIVE`                |
| **Upload Manual** | Usuario sube archivos directamente (PNG, JPG, GIF, WEBP)             | `UPLOAD`               |

Ambos flujos terminan con:
- Imágenes subidas a Amazon S3 (claves: `slides/{presentationId}/{number}.png`)
- Entidades persistidas en PostgreSQL (Aiven)
- UI para gestión (listado, creación, preview)

**Conexión con Fase 3:** Campo `repoUrl` en `Presentation` almacena URL del repositorio GitHub para que Gemini extraiga contexto técnico por slide.

---

## 2. Arquitectura de Fase 2

### Stack añadido a `ui-service`

| Componente                    | Rol                                                            |
|-------------------------------|----------------------------------------------------------------|
| **GoogleDriveService**        | Cliente HTTP puro (WebClient) → Drive API v3                  |
| **PresentationService**       | Orquestación: Drive → descargas, uploads → S3 → BD            |
| **PresentationImportController** | 6 endpoints REST (carpetas, imágenes, crear desde Drive/Upload) |
| **Presentation, Slide, QuickLink** | Entidades JPA persistidas en PostgreSQL                 |
| **DriveFolder, DriveFile, SlideInfo** | Records DTO para comunicación Drive API          |
| **PresentationRepository**    | Query methods: `findByUserId*`, `findByIdAndUserId`            |
| **templates/presentations/import.html** | UI Bootstrap 5 con 2 tabs (Drive + Upload)     |
| **SecurityConfig**            | Reglas `/presentations/**` → PRESENTER/ADMIN                  |
| **RoutesConfig (gateway)**    | Ruta `/presentations/**` a `ui-service`                        |

---

## 3. Modelo JPA — Presentaciones

### Entidad `Presentation`

```java
@Entity @Table(name = "presentations")
public class Presentation {
    @Id String id;                          // UUID
    @ManyToOne User user;                   // FK usuario propietario
    String name;                             // Nombre visible
    String description;                      // Descripción opcional
    @Enumerated SourceType sourceType;      // DRIVE o UPLOAD
    String driveFolderId;                   // ID de carpeta Drive (null si UPLOAD)
    String driveFolderName;                 // Nombre de carpeta Drive (para UI)
    String repoUrl;                         // URL repo GitHub (Fase 3)
    @OneToMany(mappedBy="presentation") List<Slide> slides;  // 1..N
    @OneToMany(mappedBy="presentation") List<QuickLink> quickLinks;  // 1..N (Fase 4)
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

**En BD PostgreSQL:**
```sql
CREATE TABLE presentations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    source_type VARCHAR(20) NOT NULL,
    drive_folder_id VARCHAR(200),
    drive_folder_name VARCHAR(200),
    repo_url TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Entidad `Slide`

```java
@Entity @Table(name = "slides")
public class Slide {
    @Id String id;                          // UUID
    @ManyToOne Presentation presentation;   // FK presentación
    int number;                             // 1-based, único por presentación
    String filename;                        // Nombre del archivo ("1.png", etc.)
    String driveFileId;                    // ID en Drive (null si upload)
    String s3Url;                          // URL pública: https://bucket.s3.region.amazonaws.com/slides/{id}/{number}.png
    LocalDateTime uploadedAt;
}

// Constraint UNIQUE(presentation_id, number)
```

### Entidad `QuickLink`

```java
@Entity @Table(name = "quick_links")
public class QuickLink {
    @Id String id;                          // UUID
    @ManyToOne Presentation presentation;   // FK presentación
    String title;                           // Ej. "API Docs"
    String url;                             // URL destino
    String icon;                            // Font Awesome class: "fa-solid fa-circle-play"
    String description;                     // Tooltip/help text
    int displayOrder;                       // Ordenamiento en UI
}
// Implementado en Fase 2 (modelo) pero su endpoint está en Fase 4
```

### Extensión: campo en `User`

```java
@Column(name = "default_drive_folder_id", length = 200)
private String defaultDriveFolderId;  // Última carpeta Drive usada (opcional)
```

---

## 4. Servicios de Negocio Implementados

### 4.1 GoogleDriveService

**Responsabilidad:** Cliente HTTP puro a Google Drive REST API v3. Sin SDKs.

```java
@Service
public class GoogleDriveService {
    private final WebClient driveClient = WebClient.builder()
        .baseUrl("https://www.googleapis.com/drive/v3").build();

    // GET /files?q=mimeType='application/vnd.google-apps.folder'
    public List<DriveFolder> listFolders(String accessToken) { ... }

    // GET /files?q='<folderId>' in parents and mimeType contains 'image/'
    public List<DriveFile> listImagesInFolder(String folderId, String accessToken) { ... }

    // GET /files/<fileId>?alt=media
    public byte[] downloadImage(String fileId, String accessToken) { ... }
}
```

**DTOs de comunicación:**
- `record DriveFolder(String id, String name)`
- `record DriveFile(String id, String name, String mimeType)`

**Autenticación:** Bearer token desde `oauth2:google` (OAuth2 de Spring Security).

---

### 4.2 PresentationService

**Responsabilidad:** Orquestación completa de importación: descarga/recibe → sube a S3 → persiste BD.

#### Flujo: Importación desde Google Drive

```
User selectiona carpeta en Drive
     ↓
PresentationService.createFromDrive(..., driveFolderId, ..., googleAccessToken)
     ├─ GoogleDriveService.listImagesInFolder(...)  // Lista imágenes ordenadas
     ├─ Para cada imagen:
     │   ├─ GoogleDriveService.downloadImage(...)  // Descarga bytes
     │   └─ SlideUploadService.upload(...)  // Sube a S3
     │       └─ Clave: slides/{presentationId}/{slideNumber}.png
     └─ Presentation.save() con cascada a Slides
```

**Ejemplo de código:**
```java
@Transactional
public Presentation createFromDrive(User user, String name, String description,
        String driveFolderId, String driveFolderName, String repoUrl,
        String googleAccessToken) {
    // 1. Lista imágenes de Drive (máx 1000, pero típico 10-50)
    List<DriveFile> images = googleDriveService.listImagesInFolder(...);
    if (images.isEmpty()) throw new IllegalArgumentException("No imágenes en carpeta");

    // 2. Crea entidad Presentation vacía, la persiste (obtiene ID único)
    Presentation presentation = new Presentation();
    presentation.setId(UUID.randomUUID().toString());
    presentation.setUser(user);
    presentation.setName(name);
    presentation.setSourceType(SourceType.DRIVE);
    presentation.setDriveFolderId(driveFolderId);
    // ...
    presentationRepository.save(presentation);

    // 3. Para cada imagen: descarga + sube a S3 + crea Slide
    for (int i = 0; i < images.size(); i++) {
        byte[] imageBytes = googleDriveService.downloadImage(images.get(i).id(), token);
        String s3Url = slideUploadService.upload(
            "slides/" + presentation.getId() + "/" + (i+1) + ".png",
            imageBytes,
            "image/png"  // o detectado del mimeType
        );
        Slide slide = new Slide();
        slide.setPresentation(presentation);
        slide.setNumber(i + 1);
        slide.setS3Url(s3Url);
        presentation.getSlides().add(slide);
    }

    // 4. Save con cascada — los Slides se persisten automáticamente
    return presentationRepository.save(presentation);
}
```

#### Flujo: Upload Manual

```
User sube lista de archivos (MultipartFile[])
     ↓
PresentationService.createFromUpload(..., files)
     ├─ Ordena archivos por nombre (alfabético)
     ├─ Para cada archivo:
     │   ├─ Valida tipo MIME (debe ser imagen)
     │   └─ SlideUploadService.upload(...)  // Sube a S3
     │       └─ Clave: slides/{presentationId}/{slideNumber}.png
     └─ Presentation.save() con cascada a Slides
```

**Métodos expuestos:**
- `listPresentations(userId)` → `List<Presentation>` ordenadas por fecha desc
- `getPresentation(userId, presentationId)` → `Optional<Presentation>` (validación ownership)
- `listDriveFolders(accessToken)` → delega a `GoogleDriveService`
- `listDriveImages(folderId, accessToken)` → delega a `GoogleDriveService`
- `createFromDrive(...)` → completo flujo Drive → S3 → BD
- `createFromUpload(...)` → completo flujo Upload → S3 → BD

---

### 4.3 PresentationRepository

```java
public interface PresentationRepository extends JpaRepository<Presentation, String> {
    List<Presentation> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Presentation> findByIdAndUserId(String id, String userId);
}
```

**Prevención de acceso cruzado:** El controller valida `findByIdAndUserId` para asegurar que el usuario solo accede a sus propias presentaciones.

---

## 5. Endpoints REST Implementados

**Base URL:** `http://localhost:8082/api/presentations/` (o via gateway: `http://localhost:8080/api/presentations/`)

| Método | Ruta                                    | Acceso  | Descripción                                          |
|--------|-----------------------------------------|---------|------------------------------------------------------|
| GET    | `/api/presentations`                    | PRESENT | Listado JSON de presentaciones del usuario          |
| GET    | `/api/presentations/drive/folders`      | PRESENT | Carpetas Drive disponibles (requiere token Google)  |
| GET    | `/api/presentations/drive/folders/{folderId}/images` | PRESENT | Imágenes en carpeta Drive |
| POST   | `/api/presentations/create-from-drive`  | PRESENT | Importar desde Drive (form-urlencoded)              |
| POST   | `/api/presentations/create-from-upload` | PRESENT | Crear desde upload (multipart/form-data)            |

### Parámetros `GET /api/presentations/drive/folders`

**Precondición:** Usuario debe tener token OAuth2 de Google activo.

**Respuesta:**
```json
{
  "folders": [
    { "id": "1a2b3c4d", "name": "Mi presentación 2025" },
    { "id": "5e6f7g8h", "name": "Workshop Python" }
  ]
}
```

### Parámetros `GET /api/presentations/drive/folders/{folderId}/images`

**Respuesta:**
```json
{
  "images": [
    { "id": "img1", "name": "slide1.png", "mimeType": "image/png" },
    { "id": "img2", "name": "slide2.jpg", "mimeType": "image/jpeg" }
  ]
}
```

### Parámetros `POST /api/presentations/create-from-drive`

**Content-Type:** `application/x-www-form-urlencoded`

```
name=Mi+Presentación
&description=Charla+sobre+Python
&driveFolderId=folder-uuid
&driveFolderName=Mi+carpeta
&repoUrl=https%3A%2F%2Fgithub.com%2Fuser%2Frepo
```

**Respuesta (200 OK):**
```json
{
  "success": true,
  "presentationId": "pres-uuid",
  "totalSlides": 15
}
```

**Respuesta (400 Bad Request):**
```json
{
  "error": "No se encontraron imágenes en la carpeta de Drive"
}
```

### Parámetros `POST /api/presentations/create-from-upload`

**Content-Type:** `multipart/form-data`

```
name=Upload Manual
&description=Imágenes importadas
&repoUrl=https://github.com/user/repo
&files=<binary-file-1>
&files=<binary-file-2>
...
```

**Respuesta (200 OK):**
```json
{
  "success": true,
  "presentationId": "pres-uuid",
  "totalSlides": 8
}
```

---

## 6. Vista Thymeleaf: `/presentations/import`

### Estructura

```html
Navbar (SlideHub | Panel | Salir)
│
├─ Columna izquierda: Mis Presentaciones
│  └─ Card con listado dinámico (th:each)
│     - Nombre, cantidad de slides, badge (DRIVE/UPLOAD)
│
└─ Columna derecha: Importar Presentación
   ├─ Tabs (Drive | Upload)
   │
   ├─ Tab Drive:
   │  ├─ Campos: Nombre*, Repositorio GitHub, Descripción
   │  ├─ Botón "Cargar" → fetch /api/presentations/drive/folders
   │  ├─ Botón selector de carpeta (toggle CSS)
   │  ├─ Preview dinámico de imágenes en carpeta
   │  └─ Botón "Importar desde Drive"
   │
   └─ Tab Upload:
      ├─ Campos: Nombre*, Repositorio GitHub, Descripción
      ├─ Drop zone (drag & drop + file input)
      ├─ Preview: lista ordenada de archivos
      └─ Botón "Subir presentación"
```

### Validaciones JavaScript

- **Nombre:** requerido
- **Drive:** requiere carpeta seleccionada con imágenes
- **Upload:** mínimo 1 archivo de imagen válido
- **Formatos aceptados:** `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`

### Dinámicas Frontend

```javascript
async function loadFolders() {
    const res = await fetch('/api/presentations/drive/folders');
    const data = await res.json();  // { folders: [...] }
    // Render buttons dinámicamente
}

async function selectFolder(folderId) {
    const res = await fetch(`/api/presentations/drive/folders/${folderId}/images`);
    // Render imagen preview
}

async function importFromDrive() {
    const params = new URLSearchParams({
        name, description, driveFolderId, driveFolderName, repoUrl
    });
    const res = await fetch('/api/presentations/create-from-drive', {
        method: 'POST',
        body: params
    });
    if (res.ok) window.location.reload();
}

async function importFromUpload() {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('description', description);
    formData.append('repoUrl', repoUrl);
    selectedFiles.forEach(f => formData.append('files', f));
    const res = await fetch('/api/presentations/create-from-upload', {
        method: 'POST',
        body: formData
    });
    if (res.ok) window.location.reload();
}
```

---

## 7. Migración Flyway (V2)

**Archivo:** `ui-service/.../db/migration/V2__create_presentations.sql`

```sql
-- Extensión a tabla users
ALTER TABLE users ADD COLUMN default_drive_folder_id VARCHAR(200);

-- Tabla presentaciones
CREATE TABLE presentations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    source_type VARCHAR(20) NOT NULL,
    drive_folder_id VARCHAR(200),
    drive_folder_name VARCHAR(200),
    repo_url TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_presentations_user_id ON presentations(user_id);

-- Tabla slides
CREATE TABLE slides (
    id VARCHAR(36) PRIMARY KEY,
    presentation_id VARCHAR(36) NOT NULL,
    number INT NOT NULL,
    filename VARCHAR(100) NOT NULL,
    drive_file_id VARCHAR(200),
    s3_url TEXT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    FOREIGN KEY (presentation_id) REFERENCES presentations(id) ON DELETE CASCADE,
    UNIQUE(presentation_id, number)
);
CREATE INDEX idx_slides_presentation_id ON slides(presentation_id);

-- Tabla quick_links (usada en Fase 4, creada aquí para completitud)
CREATE TABLE quick_links (
    id VARCHAR(36) PRIMARY KEY,
    presentation_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    url TEXT NOT NULL,
    icon VARCHAR(100),
    description TEXT,
    display_order INT DEFAULT 0,
    FOREIGN KEY (presentation_id) REFERENCES presentations(id) ON DELETE CASCADE
);
```

---

## 8. Cambios en Seguridad y Enrutamiento

### SecurityConfig (`ui-service`)

```java
// Líneas añadidas en authorizeHttpRequests:
.requestMatchers("/presentations/**").hasAnyRole("PRESENTER", "ADMIN")
.requestMatchers("/api/presentations/**").hasAnyRole("PRESENTER", "ADMIN")
```

**Efecto:** Solo usuarios autenticados con rol PRESENTER o ADMIN pueden acceder.

### RoutesConfig (gateway)

```java
@Order(3)
public RouterFunction<ServerResponse> uiRoutes() {
    return route("ui-service-routes")
        .route(
            RequestPredicates.path("/auth/**")
                .or(RequestPredicates.path("/oauth2/**"))
                .or(RequestPredicates.path("/presentations/**"))  // ← AGREGADO Fase 2
                // ... otros paths
            , http())
        .filter(uri(uiServiceUrl))
        .build();
}
```

---

## 9. Enums y DTOs Clave

### SourceType

```java
public enum SourceType {
    DRIVE,   // Importado desde Google Drive
    UPLOAD   // Archivos subidos manualmente
}
```

### Records de transferencia

```java
// Respuesta de carpetas Drive
public record DriveFolder(String id, String name) {}

// Respuesta de imágenes Drive
public record DriveFile(String id, String name, String mimeType) {}

// Metadata de slide (no usado en Fase 2, reservado para Fase 3+)
public record SlideInfo(int number, String filename, String s3Url) {}

// Resumen para listados JSON
public record PresentationSummary(
    String id,
    String name,
    String description,
    String sourceType,  // "DRIVE" | "UPLOAD"
    int totalSlides,
    LocalDateTime createdAt
) {
    public static PresentationSummary from(Presentation p) {
        return new PresentationSummary(
            p.getId(), p.getName(), p.getDescription(),
            p.getSourceType().name(),
            p.getSlides().size(),
            p.getCreatedAt()
        );
    }
}
```

---

## 10. Dependencias Maven

### Adiciones a `ui-service/pom.xml`

```xml
<!-- Data JPA (relacional) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- PostgreSQL driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- H2 embedded (desarrollo) en modo PostgreSQL -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- Flyway para migraciones -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- AWS SDK v2 (S3) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

### Adiciones a `parent pom.xml`

```xml
<dependencyManagement>
    <dependencies>
        <!-- AWS SDK v2 BOM para consistent versioning -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.29.52</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 11. Configuración: `application.properties` y `application-prod.properties`

### `ui-service/src/main/resources/application.properties`

```properties
# ── JPA / H2 Development ──
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.datasource.url=jdbc:h2:mem:slidehub;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# ── Flyway ──
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ── AWS S3 ──
aws.s3.bucket=${AWS_S3_BUCKET:slidehub-assets-local}
aws.s3.region=${AWS_REGION:us-east-1}
aws.access-key-id=${AWS_ACCESS_KEY_ID:}
aws.secret-access-key=${AWS_SECRET_ACCESS_KEY:}
```

### `ui-service/src/main/resources/application-prod.properties`

```properties
# ── PostgreSQL Aiven ──
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# ── Flyway ──
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ── AWS S3 ──
aws.s3.bucket=${AWS_S3_BUCKET}
aws.s3.region=${AWS_REGION:us-east-1}
aws.access-key-id=${AWS_ACCESS_KEY_ID}
aws.secret-access-key=${AWS_SECRET_ACCESS_KEY}

# ── OAuth2 (requerido para Drive) ──
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
# scopes incluyen https://www.googleapis.com/auth/drive.readonly
```

---

## 12. Flujo Completo de Usuario

### Scenario: Importar presentación desde Google Drive

```
1. Usuario navega a /presentations/import (autenticado, rol PRESENTER+)
   └─ Carga template presentations/import.html

2. Hace clic en tab "Google Drive"
   └─ Si no tiene token Google: muestra alerta con link de vinculación
   └─ Si sí tiene token: muestra formulario

3. Hace clic "Cargar"
   └─ JavaScript: fetch GET /api/presentations/drive/folders
   └─ PresentationImportController.listDriveFolders()
      └─ Resuelve OAuth2 access token de Google
      └─ GoogleDriveService.listFolders(token)
         └─ WebClient GET https://www.googleapis.com/drive/v3/files?q=...
      └─ Devuelve { folders: [...] }

4. Selecciona una carpeta
   └─ JavaScript: fetch GET /api/presentations/drive/folders/{id}/images
   └─ PresentationImportController.listDriveImages(id)
      └─ GoogleDriveService.listImagesInFolder(id, token)
      └─ Devuelve { images: [...] }
   └─ Actualiza UI con preview de imágenes

5. Rellena nombre y hace clic "Importar desde Drive"
   └─ JavaScript: fetch POST /api/presentations/create-from-drive
   └─ PresentationImportController.createFromDrive()
      └─ GoogleDriveService.listImagesInFolder()  // Valida de nuevo
      └─ Crea Presentation (vacía), la guarda → obtiene ID único
      └─ Para cada imagen:
         ├─ GoogleDriveService.downloadImage(imageId, token)
         ├─ SlideUploadService.upload(bytes, "slides/{id}/{n}.png", contentType) → S3
         └─ Crea Slide entity con s3Url
      └─ Repositorio.save(presentation) con cascada
      └─ Devuelve { success: true, presentationId, totalSlides }

6. JavaScript recibe respuesta exitosa
   └─ window.location.reload() → actualiza listado
```

### Scenario: Upload manual

```
1. Usuario selecciona archivos (PNG, JPG, GIF, WEBP)
   └─ JavaScript: renderiza preview ordenado por nombre

2. Hace clic "Subir presentación"
   └─ Crea FormData con campos + archivos
   └─ fetch POST /api/presentations/create-from-upload (multipart)

3. PresentationImportController.createFromUpload()
   └─ Valida al menos 1 archivo
   └─ PresentationService.createFromUpload(user, name, ..., files[])
      ├─ Ordena files por nombre alfabético
      ├─ Crea Presentation (vacía), guarda
      ├─ Para cada archivo:
      │  ├─ Valida MIME type (image/*)
      │  ├─ SlideUploadService.upload(bytes, "slides/{id}/{n}.png", ...)
      │  └─ Crea Slide con s3Url
      └─ Repositorio.save() con cascada

4. Devuelve { success: true, ...}
   └─ window.location.reload()
```

---

## 13. Validaciones Implementadas

### Backend (PresentationService, PresentationImportController)

| Regla                                          | Respuesta |
|------------------------------------------------|-----------|
| Carpeta Drive vacía (sin imágenes)             | 400 BadRequest |
| Usuario sin token Google pero intenta Drive    | 401 Unauthorized |
| Nombre de presentación vacío                   | 400 BadRequest |
| Upload sin archivos                            | 400 BadRequest |
| Archivo no es imagen (MIME type incorrecto)    | Ignorado silenciosamente, log warn |
| Usuario X intenta acceder a Presentation de Y  | 404 (findByIdAndUserId devuelve empty) |

### Frontend (JavaScript en import.html)

- Nombre requerido (antes de enviar)
- Carpeta seleccionada (para Drive)
- Al menos 1 archivo (para Upload)
- Tipos MIME permitidos (atributo `accept` en file input)

---

## 14. Decisiones Arquitectónicas

### 1. **WebClient, no SDK de Google Drive**
- **Razón:** Control total sobre payloads, menor footprint de dependencias.
- **Trade-off:** Manejo manual de errores HTTP.

### 2. **S3 puro, sin filesystem local**
- **Razón:** Render filesystem es efímero; S3 es persistente y escalable.
- **Nota:** AWS SDK v2 es la única excepción al patrón "WebClient-only".

### 3. **Orden alfabético para upload manual**
- **Razón:** Determinismo. Usuario espera consistent slide numbering.

### 4. **`@Transactional` en createFromDrive/Upload**
- **Razón:** Si falla en mitad del proceso, hace rollback de TODO (no quedan registros huérfanos).

### 5. **Two-tab UI en lugar de separate endpoints**
- **Razón:** UX unificada, conversiones de flujo consistente.

### 6. **`sourceType` enum en Presentation**
- **Razón:** Fase 3 y 4 pueden adaptar UI/comportamiento según origen.

---

## 15. Testing e Integración

### Unit tests pendientes (Fase 2+1)
- `GoogleDriveServiceTest` — mock WebClient responses
- `PresentationServiceTest` — mock GoogleDrive + SlideUpload
- `PresentationImportControllerTest` — @WebMvcTest

### Integración actuales
- **Build:** ✅ `./mvnw clean compile` SUCCESS
- **Migraciones:** ✅ Flyway V1 + V2 auto-aplican en startup dev (H2) y prod (Aiven)
- **OAuth2:** ✅ Integrado con Spring Security ; obtención de access token automática

---

## 16. Roadmap Futuro

### Fase 3: Presenter Notes con IA
- Usar `repoUrl` guardado en Presentation
- Gemini extrae contexto técnico del repo
- Groq genera notas tabuladas por slide
- Botón "Generar notas con IA" en `/presenter`

### Fase 4: Quick Links y Modo Dual
- Endpoint para crear/editar QuickLinks
- UI en `/main-panel` para administrar links
- `/demo` alterna entre slides e iframes según estado

### Fase 5: Presentaciones Multi-sesión
- Permitir múltiples presentations en paralelo
- Session store en Redis (estado activo)
- Selector en `/presenter`

### Fase 6: Notificaciones y Analytics
- Webhooks a Slack cuando se genera presentación
- Analytics de tiempo por slide (polling desde `state-service`)

---

## 17. Resumen Visual

```
SlideHub Fase 2 — Importación de Presentaciones
═════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────┐
│                   UI Layer (Thymeleaf)                       │
│  presentations/import.html                                   │
│  ├─ Tab Drive: carpeta + preview imágenes                  │
│  └─ Tab Upload: drop zone + ficheros                       │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  REST API (Spring WebMvc)                    │
│  PresentationImportController                               │
│  ├─ GET /api/presentations                                 │
│  ├─ GET /api/presentations/drive/{folderId}/images         │
│  └─ POST /api/presentations/create-from-{drive,upload}     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  Business Logic (Service)                    │
│  PresentationService                                         │
│  ├─ Descarga desde Drive (GoogleDriveService)              │
│  └─ Sube a S3 (SlideUploadService)                         │
└─────────────────────────────────────────────────────────────┘
                    ↙                    ↘
        ┌──────────────────┐      ┌──────────────────┐
        │  Google Drive    │      │  Amazon S3       │
        │  REST API v3     │      │  (slides/*.png)  │
        └──────────────────┘      └──────────────────┘
                                           ↓
                                  ┌──────────────────┐
                                  │   PostgreSQL     │
                                  │   (Aiven prod,   │
                                  │    H2 dev)       │
                                  │                  │
                                  │ Presentations    │
                                  │ ├─ Slides       │
                                  │ └─ QuickLinks   │
                                  └──────────────────┘

Flujo Completo: Usuario → UI → API → GoogleDrive + S3 + BD
Persistencia: PostgreSQL (relacional) + S3 (assets)
Autenticación: OAuth2 (Google para Drive, local para Render)
```

---

## 18. Checklist de Implementación

✅ **Modelos JPA**
- [x] Presentation, Slide, QuickLink entities
- [x] User extension (defaultDriveFolderId)
- [x] Flyway V2 migration

✅ **Servicios**
- [x] GoogleDriveService (WebClient → Drive API)
- [x] PresentationService (orquestación)
- [x] PresentationRepository

✅ **Controllers**
- [x] PresentationImportController (6 endpoints)
- [x] Resolución de OAuth2 token

✅ **UI**
- [x] presentations/import.html (2 tabs, JS fetch, drop zone)
- [x] Validaciones frontend
- [x] Listado dinámico de presentaciones

✅ **Seguridad**
- [x] SecurityConfig: `/presentations/**` → PRESENTER/ADMIN
- [x] RoutesConfig: `/presentations/**` → ui-service

✅ **Base de Datos**
- [x] H2 dev (PostgreSQL mode)
- [x] Aiven PostgreSQL ready (variables de entorno)
- [x] Migraciones auto-aplicadas

✅ **Build**
- [x] `./mvnw clean compile` BUILD SUCCESS
- [x] Sin errores, sin warnings críticos

---

## 19. Recursos Clave del Código

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| GoogleDriveService.java | ~120 | Cliente HTTP puro a Drive API v3 |
| PresentationService.java | ~267 | Orquestación Drive + upload → S3 + BD |
| PresentationImportController.java | ~230 | 6 endpoints REST |
| import.html | ~400 | UI Bootstrap + fetch + drop zone |
| Presentation.java | ~180 | Entidad JPA principal |
| V2__create_presentations.sql | ~60 | Migraciones Flyway |
| SecurityConfig.java | ~100 | Roles `/presentations/**` |
| RoutesConfig.java | ~95 | Ruta gateway `/presentations/**` |

---

## 20. Conclusión

**Fase 2 completada exitosamente.**

SlideHub ahora soporta:
1. **Importación desde Google Drive** — usuarios vinculados con Google pueden seleccionar carpetas y descargar imágenes automáticamente.
2. **Upload manual** — usuarios pueden enviar archivos locales sin necesidad de vinculación Google.
3. **Persistencia en PostgreSQL** — presentaciones y slides almacenados con metadatos (origen, URL repo, etc.).
4. **Assets en S3** — todas las imágenes viven en Amazon S3, nunca en el filesystem efímero de Render.
5. **Entidades preparadas para Fase 3** — campo `repoUrl` y tabla `QuickLink` listos para IA y links demo.

**BUILD STATUS:** ✅ SUCCESS — Todos los módulos compilaron sin errores.

**Siguiente:** Fase 3 (Presenter Notes con Gemini + Groq).

---

*Documento generado: 27 de febrero de 2026*
*Repositorio: SlideHub | Versión: v1.2.0-phase2*
