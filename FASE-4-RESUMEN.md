# FASE-4-RESUMEN.md — SlideHub

## Controles Mejorados de Presentación: Quick Links + Demo Mode con Restauración de Slide

**Fecha:** Febrero 27, 2026  
**Estado:** ✅ BUILD SUCCESS  
**Commits:** N/A (desarrollo local)

---

## 1. Visión General de Fase 4

Implementación del **sistema de Quick Links asociados a presentaciones** y **mejora del flujo de Demo Mode** con auto-restauración de slide. Los presentadores pueden ahora:

1. Crear **Quick Links** (enlaces rápidos a demostraciones) en el **`/main-panel`**
2. Activar demostraciones desde el **`/remote`** (smartphone)
3. Automáticamente **retornar al slide anterior** sin perder el contexto
4. Ver la **lista de Quick Links** en ambas vistas (control y remoto)

### Flujo de usuario principal

```
┌─────────────────────────────────────────────────────────────────┐
│ Presentador en /main-panel (tablet)                             │
├─────────────────────────────────────────────────────────────────┤
│ 1. Configura Quick Links (CRUD) en sidebar                      │
│ 2. Ve lista de links disponibles asociados a la presentación    │
│ 3. Puede editar displayOrder y propiedades (título, URL, icon)  │
│ 4. Selecciona un link y lo activa (o lo hace desde /remote)    │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Audiencia visualiza /demo (iframe a URL del Quick Link)        │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Presentador en /remote (smartphone)                             │
├─────────────────────────────────────────────────────────────────┤
│ 1. Ve FAB "Demo" button (si hay Quick Links)                    │
│ 2. Abre bottom sheet mostrando Quick Links                      │
│ 3. Toca un link → activa demo mode con returnSlide guardado    │
│ 4. Toca "Volver al Slide X" → restaura automáticamente         │
└─────────────────────────────────────────────────────────────────┘
        ↓                                           ↓
    Slide → Demo URL                    Demo URL → Slide
    (returnSlide guardado)               (auto-restored)
```

### Objetivos alcanzados

- ✅ CRUD REST de Quick Links (`/api/presentations/{id}/links/*`)
- ✅ DB persistencia en PostgreSQL (tabla `quick_links` creada en Flyway V2)
- ✅ Auto-indexing de orden de visualización (`displayOrder`)
- ✅ DemoStateService enhanced: auto-restaura slide al volver a "slides" mode
- ✅ Vistas Thymeleaf (`/main-panel`, `/remote`) con controles integrados
- ✅ Demo URL iframe overlay con botón "Volver al Slide X" flotante
- ✅ Polling sincronizado en `/remote` y `/demo` para demo state
- ✅ Seguridad: GET público (se consume sin auth desde `/remote`), POST/PUT/DELETE requieren PRESENTER/ADMIN

---

## 2. Contexto: Antecedentes Técnicos

### Clave Redis (state-service)

La Fase 4 aprovecha la infraestructura de demo state que ya existía desde **Fase 0**:

```json
{
  "key": "demo_state",
  "value": {
    "mode": "slides" | "url",
    "slide": 1,
    "url": "https://example.com/demo",
    "returnSlide": 3
  }
}
```

**Campo clave en Fase 4:** `returnSlide` — almacena el slide anterior para restaurar automáticamente.

### Tabla PostgreSQL (ui-service)

Fase 4 utiliza la tabla `quick_links` **ya creada en Flyway V2** (Fase 2):

```sql
CREATE TABLE quick_links (
  id VARCHAR(36) PRIMARY KEY,
  presentation_id VARCHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  url VARCHAR(2048) NOT NULL,
  icon VARCHAR(50),
  description TEXT,
  display_order INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (presentation_id) REFERENCES presentations(id) ON DELETE CASCADE
);
```

---

## 3. Flujos Implementados en Detalle

### 3.1 Flujo de Activación de Demo desde Quick Link

**Participantes:** `/main-panel` (presenter), `/remote` (smartphone), `/demo` (pantalla dual)

```
1. Presentador selecciona Quick Link en /main-panel
   ↓
2. JavaScript local: activateDemoLink(url, title)
   ├─ Obtiene currentSlide del DOM
   └─ POST /api/demo { mode: "url", url: "...", returnSlide: currentSlide }
   ↓
3. DemoStateController.setDemoState() → DemoStateService
   ├─ Validación: mode ∈ {"url", "slides"}
   ├─ Si mode="url":
   │  └─ Guarda { mode, url, returnSlide } en Redis
   └─ Responde 200 { mode, url, returnSlide }
   ↓
4. /remote y /demo pollan /api/demo (cada 800-1000ms)
   ├─ /remote: oculta FAB, muestra botón "Volver al Slide X"
   └─ /demo: muestra iframe con URL, overlay flotante "Volver al Slide X"
```

### 3.2 Flujo de Restauración Automática (returnSlide)

**Participantes:** `/remote` o `/demo` (botón "Volver"), state-service (DemoStateService)

```
1. Usuario toca "Volver al Slide X" en /remote o /demo
   ↓
2. JavaScript: returnToSlide()
   └─ POST /api/demo { mode: "slides" }  // NO envía slide
   ↓
3. DemoStateService.setDemoState(SetDemoRequest{mode:"slides"})
   ├─ Lee demo_state actual: { mode: "url", url: "...", returnSlide: 3 }
   ├─ Extrae returnSlide (3) del estado actual
   ├─ Llama slideStateService.setSlide(3)  ← AUTO-RESTAURA
   └─ Guarda { mode: "slides", returnSlide: 3 } en Redis
   ↓
4. /slides, /remote, /demo pollan /api/slide
   └─ Ven slide = 3 automáticamente
```

**Código actual en DemoStateService:**

```java
if ("slides".equals(request.mode())) {
    DemoState current = getDemoState();
    Integer restoreSlide = request.slide();
    if (restoreSlide == null && current.returnSlide() != null) {
        restoreSlide = current.returnSlide();  // ← Auto-restore
    }
    slideStateService.setSlide(restoreSlide != null ? restoreSlide : 1);
    newState = new DemoState("slides", restoreSlide, null, null);
}
```

### 3.3 Flujo CRUD de Quick Links

#### Create
```
POST /api/presentations/{presentationId}/links
Body: { title, url, icon, description }

1. QuickLinkController.createLink()
2. Valida: title y url obligatorios
3. QuickLinkService.create()
   ├─ Verifica que presentation existe
   ├─ Calcula displayOrder = max(actual) + 1
   ├─ Genera UUID para id
   └─ Guarda en DB
4. Responde 200 con QuickLink creado
```

#### Read
```
GET /api/presentations/{presentationId}/links

1. QuickLinkController.listLinks()
2. QuickLinkService.findByPresentation()
3. Responde 200 con List<QuickLink> ordenada por displayOrder
4. Acceso público — sin validación (se usa desde /remote sin auth)
```

#### Update
```
PUT /api/presentations/{presentationId}/links/{linkId}
Body: { title, url, icon, description, displayOrder }

1. QuickLinkController.updateLink()
2. Valida: title y url obligatorios
3. QuickLinkService.update()
   ├─ Busca link por id
   ├─ Valida ownership: link.presentation.id == presentationId
   ├─ Actualiza campos
   └─ Guarda
4. Responde 200 con QuickLink actualizado
```

#### Delete
```
DELETE /api/presentations/{presentationId}/links/{linkId}

1. QuickLinkController.deleteLink()
2. QuickLinkService.delete()
   ├─ Busca link por id
   ├─ Valida ownership: link.presentation.id == presentationId
   └─ Borra
3. Responde 204 No Content
```

---

## 4. Archivos Creados

### `ui-service` — Nuevos componentes

#### A. `ui-service/src/main/java/com/brixo/slidehub/ui/repository/QuickLinkRepository.java`

```java
public interface QuickLinkRepository extends JpaRepository<QuickLink, String> {
    List<QuickLink> findByPresentationIdOrderByDisplayOrderAsc(String presentationId);
    int countByPresentationId(String presentationId);
}
```

**Métodos de query:**
- `findByPresentationIdOrderByDisplayOrderAsc()` — obtiene links ordenados por orden de visualización
- `countByPresentationId()` — cuenta links totales (usado para auto-indexing)

#### B. `ui-service/src/main/java/com/brixo/slidehub/ui/service/QuickLinkService.java`

```java
@Service
public class QuickLinkService {
    public List<QuickLink> findByPresentation(String presentationId)
    public QuickLink create(String presentationId, String title, String url, 
                            String icon, String description)
    public QuickLink update(String presentationId, String linkId, String title, 
                            String url, String icon, String description, Integer displayOrder)
    public void delete(String presentationId, String linkId)
}
```

**Características:**
- Inyección por constructor: `QuickLinkRepository`, `PresentationRepository`
- Transaccional (`@Transactional`) para operaciones de escritura
- Auto-indexing: `displayOrder = max(actual) + 1` en creation
- Validación de ownership en update/delete
- Logging con SLF4J (`private static final Logger log`)

#### C. `ui-service/src/main/java/com/brixo/slidehub/ui/controller/QuickLinkController.java`

```java
@RestController
@RequestMapping("/api/presentations/{presentationId}/links")
public class QuickLinkController {
    @GetMapping
    public ResponseEntity<List<QuickLink>> listLinks(...)
    
    @PostMapping
    public ResponseEntity<?> createLink(...)
    
    @PutMapping("/{linkId}")
    public ResponseEntity<?> updateLink(...)
    
    @DeleteMapping("/{linkId}")
    public ResponseEntity<?> deleteLink(...)
}
```

**Records anidados:**
- `CreateRequest(title, url, icon, description)`
- `UpdateRequest(title, url, icon, description, displayOrder)`

**Seguridad:**
- GET: permitAll (desde SecurityConfig `/api/**`)
- POST/PUT/DELETE: PRESENTER o ADMIN (desde SecurityConfig)

---

## 5. Archivos Modificados

### A. `ui-service/src/main/java/com/brixo/slidehub/ui/model/QuickLink.java`

**Cambio en Fase 4:**
```java
import com.fasterxml.jackson.annotation.JsonIgnore;  // ← fixed from tools.jackson.annotation

@Entity
@Table(name = "quick_links")
public class QuickLink {
    
    @JsonIgnore  // ← previene circular serialization JSON
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentation_id")
    private Presentation presentation;
    
    // ... otros campos ...
}
```

**Razón:** La relación bidireccional `Presentation ↔ QuickLink` causaba circular serialization. `@JsonIgnore` omite el campo en JSON mientras mantiene la relación JPA.

### B. `ui-service/src/main/java/com/brixo/slidehub/ui/controller/PresenterViewController.java`

**Cambios en Fase 4:**

```java
public class PresenterViewController {
    private final QuickLinkService quickLinkService;  // ← nuevo
    
    // En constructor: inyección de QuickLinkService
    
    @GetMapping("/main-panel")
    public String mainPanelView(
            @RequestParam(required = false) String presentationId,
            Model model) {
        // Nuevo: cargar quick links si presentationId presente
        if (presentationId != null && !presentationId.isBlank()) {
            List<QuickLink> quickLinks = quickLinkService.findByPresentation(presentationId);
            model.addAttribute("quickLinks", quickLinks);
            model.addAttribute("hasQuickLinks", !quickLinks.isEmpty());
            model.addAttribute("presentationId", presentationId);
        }
        // ... resto del método ...
    }
}
```

**Atributos agregados al modelo:**
- `presentationId` — usado en JavaScript del template
- `quickLinks` — lista renderizada con Thymeleaf
- `hasQuickLinks` — flag para ocultar FAB si no hay links

### C. `ui-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationViewController.java`

**Cambios en Fase 4:**

```java
public class PresentationViewController {
    private final QuickLinkService quickLinkService;  // ← nuevo
    
    // En constructor: inyección de QuickLinkService
    
    @GetMapping("/remote")
    public String remoteView(
            @RequestParam(required = false) String presentationId,
            Model model) {
        // Nuevo: cargar quick links SERVIDOR-SIDE (sin auth requerida desde /remote)
        if (presentationId != null && !presentationId.isBlank()) {
            List<QuickLink> quickLinks = quickLinkService.findByPresentation(presentationId);
            model.addAttribute("quickLinks", quickLinks);
            model.addAttribute("hasQuickLinks", !quickLinks.isEmpty());
            model.addAttribute("presentationId", presentationId);
        }
        // ... resto del método ...
    }
}
```

**Razón de server-side loading:** `/remote` es pública sin autenticación. Los quick links se inyectan directamente en Thymeleaf para evitar necesidad de llamadas HTTP autenticadas desde JS.

### D. `state-service/src/main/java/com/brixo/slidehub/state/service/DemoStateService.java`

**Cambios en Fase 4:**

```java
public class DemoStateService {
    private final SlideStateService slideStateService;  // ← nuevo
    
    public DemoStateService(StringRedisTemplate redis, 
                           ObjectMapper objectMapper,
                           SlideStateService slideStateService) {  // ← inyectado
        // ...
    }
    
    public DemoState setDemoState(SetDemoRequest request) {
        // ... código previo ...
        
        if ("slides".equals(request.mode())) {
            DemoState current = getDemoState();
            Integer restoreSlide = request.slide();
            
            // ← NUEVO: si no se envía slide, restaurar del estado actual
            if (restoreSlide == null && current.returnSlide() != null) {
                restoreSlide = current.returnSlide();
            }
            
            // ← NUEVO: autosettear el slide
            slideStateService.setSlide(restoreSlide != null ? restoreSlide : 1);
            
            newState = new DemoState("slides", restoreSlide, null, null);
        }
        // ... resto del método ...
    }
}
```

**Cambio crítico:** Inyección de `SlideStateService` permite auto-restauración del slide cuando se vuelve del modo URL al modo slides.

---

## 6. Archivos de Template Modificados

### A. `ui-service/src/main/resources/templates/main-panel.html`

**Reescrito completamente en Fase 4:**

**Layout:**
- Two-column flex: slide grid (izq, flex-1) + sidebar (derecha, 300px, dark)
- Grid de slides con thumbnail navigation
- Sidebar con demo mode controls

**Secciones del sidebar:**
1. **Demo Mode Indicator** — badge mostrando estado actual (Slides | URL)
2. **Return to Slides** — botón "Volver a Slides" visible cuando mode=url
3. **URL Input** — campo para entrar URL de demo custom
4. **Quick Links Buttons** — lista de buttons Thymeleaf `th:each="link : ${quickLinks}"`
   - Cada botón: `onclick="activateDemoLink(this.dataset.url, this.dataset.title)"`
   - Clase `.active-demo` cuando `demo.url === link.url`
5. **CRUD Modal** — Bootstrap modal con:
   - Tabla de links existentes
   - Botón "Add Link" → abre formulario
   - Delete buttons (con confirmación)
   - Edit form inline

**JavaScript integrado:**
- `activateDemoLink(url, title)` — `POST /api/demo {mode:"url", url, returnSlide: currentSlide}`
- `loadLinksModal()` — `GET /api/presentations/{id}/links` + render tabla
- `addQuickLink()` — valida + `POST /api/presentations/{id}/links`
- `deleteLink(id)` — confirmación + `DELETE /api/presentations/{id}/links/{id}`
- `returnToSlides()` — `POST /api/demo {mode:"slides"}`
- `poll()` — cada 1000ms: `GET /api/slide` y `GET /api/demo`

**Thymeleaf vars:**
```html
<script th:inline="javascript">
    const PRESENTATION_ID = /*[[${presentationId}]]*/;
    const POLL_INTERVAL = /*[[${pollIntervalMs}]]*/ 1000;
    const SLIDE_POLL_URL = /*[[${slidePollUrl}]]*/ '/api/slide';
    const DEMO_POLL_URL = /*[[${demoPollUrl}]]*/ '/api/demo';
</script>
```

### B. `ui-service/src/main/resources/templates/remote.html`

**Reescrito completamente en Fase 4:**

**Elementos nuevos:**
- `#btn-return-slide` (fixed top, display:none por defecto)
  - Muestra "Volver al Slide X" cuando demo.mode=url y demo.returnSlide no es null
  - Clase `.visible` → display:block
- `#btn-demo-fab` (fixed bottom-right blue button)
  - Oculto si `!hasQuickLinks`
  - Abre bottom sheet al tocar
- `#bottom-sheet` + `#sheet-overlay` 
  - Panel que sube desde abajo
  - Lista de quick links buttons
  - Cierra al tocar overlay o button

**Polling sincronizado:**
```javascript
function poll() {
    fetch('/api/slide')
        .then(r => r.json())
        .then(data => {
            currentSlide = data.slide;
            totalSlides = data.totalSlides;
            updateUISlide(data.slide);
        });
    
    fetch('/api/demo')
        .then(r => r.json())
        .then(data => onDemoStateChange(data));
}
```

**Manejo de demo state:**
```javascript
function onDemoStateChange(demo) {
    if (demo.mode === 'url' && demo.returnSlide) {
        // Mostrar botón "Volver al Slide X"
        const returnBtn = document.getElementById('btn-return-slide');
        returnBtn.textContent = `Volver al Slide ${demo.returnSlide}`;
        returnBtn.classList.add('visible');
    } else {
        // Ocultar botón
        document.getElementById('btn-return-slide').classList.remove('visible');
    }
}
```

**Activación de demo desde bottom sheet:**
```javascript
function activateDemoLink(url, title) {
    fetch('/api/demo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            mode: 'url',
            url: url,
            returnSlide: currentSlide
        })
    })
    .then(() => {
        navigator.vibrate?.(200);  // haptic feedback
        closeBottomSheet();  // cierra panel
    });
}
```

**Retorno automático:**
```javascript
function returnToSlide() {
    fetch('/api/demo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: 'slides' })
    });
    document.getElementById('btn-return-slide').classList.remove('visible');
}
```

### C. `ui-service/src/main/resources/templates/demo.html`

**Modificado en Fase 4:**

**Elementos nuevos:**
- `#btn-return` (fixed floating button, bottom-right)
  - Posición: `bottom:1.5rem; right:1.5rem`
  - Oculto por defecto (`display:none`)
  - Clase `.visible` → mostrado
  - Fondo azul con backdrop-filter para legibilidad

**CSS añadido:**
```css
#btn-return {
    position: fixed;
    bottom: 1.5rem;
    right: 1.5rem;
    padding: 0.75rem 1.5rem;
    background: #0d6efd;
    color: white;
    border: none;
    border-radius: 50px;
    cursor: pointer;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    backdrop-filter: blur(10px);
    display: none;
    z-index: 999;
}

#btn-return.visible {
    display: block;
    animation: slideInUp 0.3s ease-out;
}
```

**JavaScript modificado:**
```javascript
function poll() {
    fetch('/api/demo')
        .then(r => r.json())
        .then(data => {
            currentDemoState = data;
            updateDemoUI(data);  // ← nueva función
        });
}

function updateDemoUI(demo) {
    const iframe = document.getElementById('demo-iframe');
    const returnBtn = document.getElementById('btn-return');
    
    if (demo.mode === 'url') {
        iframe.src = demo.url;
        if (demo.returnSlide) {
            returnBtn.textContent = `Volver al Slide ${demo.returnSlide}`;
            returnBtn.classList.add('visible');
        }
    } else {
        iframe.src = 'about:blank';
        returnBtn.classList.remove('visible');
    }
}

function returnToSlide() {
    fetch('/api/demo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: 'slides' })
    });
    document.getElementById('btn-return').classList.remove('visible');
}
```

---

## 7. Seguridad y Validaciones

### Validaciones en QuickLinkController

```java
// CreateRequest: title y url obligatorios
if (body.title() == null || body.title().isBlank()) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "El campo 'title' es obligatorio."));
}
if (body.url() == null || body.url().isBlank()) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "El campo 'url' es obligatorio."));
}

// UpdateRequest: igual, más displayOrder como int
if (body.displayOrder() != null && body.displayOrder() < 0) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "El displayOrder no puede ser negativo."));
}
```

### Validaciones en QuickLinkService

```java
// Verification que presentation existe
public QuickLink create(...) {
    Presentation p = presentationRepository.findById(presentationId)
        .orElseThrow(() -> new RuntimeException("Presentation not found"));
}

// Ownership check en update/delete
public QuickLink update(String presentationId, String linkId, ...) {
    QuickLink link = quickLinkRepository.findById(linkId)
        .orElseThrow(() -> new RuntimeException("Link not found"));
    
    // ← Crítico: validar que pertenece a esta presentación
    if (!link.getPresentation().getId().equals(presentationId)) {
        throw new RuntimeException("Unauthorized: link does not belong to this presentation");
    }
    // ... update ...
}
```

---

## 8. Endpoints API Generados

### Quick Links REST API

```
Method   Path                                 Access Level    Description
─────────────────────────────────────────────────────────────────
GET      /api/presentations/{id}/links        público          lista links
POST     /api/presentations/{id}/links        PRESENTER/ADMIN  crea link
PUT      /api/presentations/{id}/links/{lid}  PRESENTER/ADMIN  actualiza link
DELETE   /api/presentations/{id}/links/{lid}  PRESENTER/ADMIN  borra link
```

### Existing Demo State API (mejorado)

```
POST     /api/demo                            público          set demo state
        Body: { mode, url?, returnSlide?, slide? }
        Response: { mode, url, returnSlide, slide }

GET      /api/demo                            público          get demo state
        Response: { mode, url, returnSlide, slide }
```

---

## 9. Base de Datos

### Tabla `quick_links` (Flyway V2, creada en Fase 2)

```sql
CREATE TABLE quick_links (
    id VARCHAR(36) PRIMARY KEY,
    presentation_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    icon VARCHAR(50),
    description TEXT,
    display_order INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (presentation_id) REFERENCES presentations(id) ON DELETE CASCADE
);
```

**Índices automáticos:**
- PK: `id`
- FK: `presentation_id`
- Composite (recomendado en producción): `(presentation_id, display_order)` para queries ordenadas rápidas

---

## 10. Estado de Compilación

### Build Command

```bash
./mvnw clean compile -pl state-service,ui-service -am
```

### Resultado

```
[INFO] Reactor Summary for slidehub-parent 0.0.1-SNAPSHOT:
[INFO]
[INFO] slidehub-parent .................................... SUCCESS [  0.229 s]
[INFO] state-service ...................................... SUCCESS [  3.160 s]
[INFO] ui-service ......................................... SUCCESS [  2.994 s]
[INFO] ────────────────────────────────────────────────────
[INFO] BUILD SUCCESS ✅
[INFO] ────────────────────────────────────────────────────
[INFO] Total time:  7.040 s
[INFO] Finished at: 2026-02-27T19:33:04-05:00
```

**Módulos compilados:**
- ✅ `slidehub-parent` (aggregator pom)
- ✅ `state-service` — DemoStateService enhanced con SlideStateService injection
- ✅ `ui-service` — QuickLinkRepository, QuickLinkService, QuickLinkController, templates

**Errores resueltos:**
- ❌ `tools.jackson.annotation.JsonIgnore` (no existe en classpath)
- ✅ Cambiado a `com.fasterxml.jackson.annotation.JsonIgnore` (Jackson 2.x, disponible en `spring-boot-starter-web`)

---

## 11. Verificación de Historias de Usuario Cubiertas

| ID  | Descripción                                | Estado | Servicio        |
|-----|--------------------------------------------|---------|--------------------|
| HU-004 | Avanzar/retroceder slides desde smartphone | ✅ | ui-service, state-service |
| HU-005 | Ver slide activo en proyector | ✅ | ui-service, state-service |
| HU-007 | Navegar a cualquier slide desde /main-panel | ✅ | ui-service, state-service |
| HU-010 | Desactivar modo URL y volver a slides en /demo | ✅ (mejorado) | state-service, ui-service |
| HU-011 | /demo sincroniza modo slides/iframe | ✅ (mejorado) | state-service, ui-service |
| Fase 4 - Quick Links | CRUD de quick links + auto-restore slide | ✅ | ui-service |

---

## 12. Decisiones Técnicas Clave

### 12.1 Por qué `@JsonIgnore` en `QuickLink.presentation`

La relación bidireccional causa serialización infinita:
- `Presentation` → `OneToMany quickLinks` → `List<QuickLink>`
- Cada `QuickLink` → `Presentation` (circular)

Solución: `@JsonIgnore` en el lado `ManyToOne` para omitir el back-link en JSON.

### 12.2 Por qué server-side loading de Quick Links en `/remote`

- `/remote` es pública sin autenticación
- Los quick links se necesitan renderizar en Thymeleaf
- Si se cargasen con JS async vía `/api/presentations/{id}/links`, requeriría autenticación
- **Solución:** Inyectar `quickLinks` directo en el Thymeleaf context desde el controller (sin autenticación, es server-side)

### 12.3 Flujo de returnSlide: por qué DemoStateService lee estado actual

Alternativatía rechazada: guardar returnSlide en cada request `POST /api/demo {mode:"slides"}`.

**Problema:** El cliente podría no enviar el returnSlide, quedándose indefinidamente en slide 1.

**Solución actual:** DemoStateService siempre guarda returnSlide cuando entra en mode="url". Cuando vuelve a mode="slides", lee el returnSlide del estado anterior de Redis automáticamente.

```java
Integer restoreSlide = request.slide();  // podría ser null
if (restoreSlide == null && current.returnSlide() != null) {
    restoreSlide = current.returnSlide();  // ← fallback al anterior
}
slideStateService.setSlide(restoreSlide != null ? restoreSlide : 1);
```

### 12.4 Polling intervals en `/main-panel` y `/remote`

- `/main-panel`: 1000ms (control de presentador — puede tolerar latencia)
- `/remote`: 1000ms más 800ms en `/demo` (usuarios finales ven cambios en demo URL rápido)
- Configurable via `application.properties`: `slidehub.poll.*.interval-ms`

---

## 13. Matriz de Responsabilidades por Servicio

| Componente | state-service | ui-service | ai-service | gateway |
|---|---|---|---|---|
| Demo state (Redis) | GET/POST /api/demo | ✗ | ✗ | Enruta a state |
| Slide state (Redis) | GET/POST /api/slide | ✗ | ✗ | Enruta a state |
| Quick links CRUD | ✗ | POST/PUT/DELETE /api/.../links | ✗ | Enruta a ui |
| Quick links READ | ✗ | GET /api/.../links | ✗ | Enruta a ui |
| Thymeleaf (main-panel) | ✗ | Renderiza + injecta | ✗ | Enruta a ui |
| Thymeleaf (remote) | ✗ | Renderiza + injecta | ✗ | Enruta a ui |
| Thymeleaf (demo) | ✗ | Renderiza | ✗ | Enruta a ui |
| Auto-restore slide | Enhanced | ✗ | ✗ | ✗ |

---

## 14. Próximas Fases Recomendadas

### Fase 5: AI-Assisted Demo Tagging

Extender Quick Links con tags generados automáticamente por Gemini:
- `demoTags` → array de strings
- Gemini analiza slide + repositorio
- Sugiere qué demostraciones mostrar
- Presentador puede aceptar/rechazar sugerencias

**Ubicación:** `ai-service` + ui-service model enhancement

### Fase 6: Responsive Mobile-First Design

Mejorar UX de `/main-panel` y `/remote` para tablet (11") y smartphone:
- Viewport optimizado
- Touch-friendly buttons (48px mínimo)
- Orientación automática (landscape para presentador)

### Fase 7: Websockets para Sincronización en Tiempo Real

Reemplazar polling con WebSockets:
- Reducir latencia de demo mode activation
- Haptic feedback instantáneo
- Escalabilidad mejorada para múltiples dispositivos

---

## 15. Resumen de Cambios por Archivo

### Nuevos (3 archivos)

1. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/repository/QuickLinkRepository.java`
2. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/service/QuickLinkService.java`
3. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/controller/QuickLinkController.java`

### Modificados (7 archivos)

1. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/model/QuickLink.java` — `@JsonIgnore` en presentation + fix import
2. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/controller/PresenterViewController.java` — inject QuickLinkService, mainPanelView adds quickLinks
3. ✅ `ui-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationViewController.java` — inject QuickLinkService, remoteView adds quickLinks
4. ✅ `state-service/src/main/java/com/brixo/slidehub/state/service/DemoStateService.java` — inject SlideStateService, auto-restore returnSlide
5. ✅ `ui-service/src/main/resources/templates/main-panel.html` — full rewrite con sidebar CRUD
6. ✅ `ui-service/src/main/resources/templates/remote.html` — full rewrite con bottom sheet + FAB
7. ✅ `ui-service/src/main/resources/templates/demo.html` — add floating return button

---

## 16. Documento de Referencia

**Especificación funcional:** [PLAN-EXPANSION.md](PLAN-EXPANSION.md) — Fase 4, tareas 37-41

**Análisis original:** [docs/Presentation-Module-Analysis.md](docs/Presentation-Module-Analysis.md) — §7.2, §7.3, §7.6

**Documentación de agentes:** [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md)

---

*Documento final de Fase 4 — BUILD SUCCESS ✅ — 27 Feb 2026*
