# Análisis de Abstracción — Módulo de Presentación BrixoCI4

**Fecha:** 22 de febrero de 2026  
**Objetivo:** Documentar la funcionalidad de presentación para su extracción como proyecto independiente

---

## 1. Resumen Ejecutivo

El módulo de presentación es un **sistema de control de diapositivas en tiempo real** multi-pantalla, completamente **desacoplado** del dominio de negocio de Brixo (marketplace de construcción). No tiene dependencia alguna de modelos, base de datos, ni filtros de autenticación. Su única dependencia de infraestructura es el **cache de CI4** para sincronizar el estado entre pantallas vía polling HTTP.

Es un candidato ideal para extracción a un proyecto independiente.

---

## 2. Inventario de Endpoints

| Método | Ruta | Controller::Method | Propósito |
|--------|------|--------------------|-----------|
| `GET` | `/slides` | `Presentation::slides` | Pantalla de proyección (audiencia/proyector) — muestra la diapositiva activa en fullscreen |
| `GET` | `/remote` | `Presentation::remote` | Control remoto táctil — botones prev/next con feedback háptico y swipe |
| `GET` | `/presenter` | `Presentation::presenter` | Vista del presentador — slide actual, notas, preview del siguiente, timer |
| `GET` | `/main-panel` | `Presentation::mainPanel` | Panel de control maestro (tablet 11") — thumbnails, navegación, proyección de URLs en iframe |
| `GET` | `/demo` | `Presentation::demo` | Pantalla dual: muestra slides O una URL del proyecto en iframe, controlada desde `/main-panel` |
| `GET/POST` | `/api/slide` | `Presentation::apiSlide` | API REST — GET: obtener slide actual; POST: cambiar slide |
| `GET/POST` | `/api/demo` | `Presentation::apiDemo` | API REST — GET: obtener estado demo (slides/url); POST: cambiar modo (slides o URL) |
| `GET` | `/showcase` | `Showcase::index` | Landing page del proyecto con stack tecnológico, galería de slides y arquitectura |

---

## 3. Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────┐
│                    Cache (in-memory)                     │
│            current_slide (int), demo_state (json)        │
└──────────┬──────────────────────────────┬────────────────┘
           │ write                        │ read (polling)
           │                              │
  ┌────────▼────────┐          ┌──────────▼──────────────┐
  │  Control Layer   │          │   Display Layer          │
  │                  │          │                          │
  │  /remote         │          │  /slides  (proyector)    │
  │  /presenter      │          │  /demo    (dual mode)    │
  │  /main-panel     │          │                          │
  └──────────────────┘          └──────────────────────────┘
           │                              │
           └──────── /api/slide ──────────┘
           └──────── /api/demo  ──────────┘
```

**Mecanismo de sincronización:** Polling HTTP cada 800ms–1500ms (varía por vista). No usa WebSockets.

---

## 4. Inventario de Archivos

### 4.1 Backend (PHP)

| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `app/Controllers/Presentation.php` | 107 | Controller principal. 7 métodos públicos. Sin dependencias externas salvo Cache |
| `app/Controllers/Showcase.php` | 12 | Controller de showcase. Renderiza una sola vista |
| `app/Config/Routes.php` (líneas 77-87) | 11 líneas | Bloque de rutas de presentación + showcase |

### 4.2 Vistas (PHP/HTML/JS)

| Archivo | Líneas | Rol | Dependencias externas |
|---------|--------|-----|----------------------|
| `app/Views/slides.php` | 100 | Proyector fullscreen | Bootstrap 5 CSS |
| `app/Views/remote.php` | 237 | Control remoto móvil | Bootstrap 5 CSS, Font Awesome 6 |
| `app/Views/presenter.php` | 637 | Notas del presentador | Bootstrap 5 CSS, Font Awesome 6.5 |
| `app/Views/main_panel.php` | 933 | Panel maestro (tablet) | Bootstrap 5, Font Awesome 6.5, Google Fonts (Inter) |
| `app/Views/demo.php` | 219 | Pantalla dual slides/iframe | Ninguna (CSS vanilla) |
| `app/Views/showcase.php` | 513 | Landing/showcase | Tailwind CSS CDN, Font Awesome 6.5, Google Fonts (Inter) |

### 4.3 Assets estáticos

| Ruta | Contenido |
|------|-----------|
| `public/presentation/` | 11 imágenes PNG: `Slide1.PNG` a `Slide11.PNG` |

**Lista completa de slides:**
- `Slide1.PNG` - Portada
- `Slide2.PNG` - Problema & Oportunidad
- `Slide3.PNG` - Solución — ¿Qué es Brixo?
- `Slide4.PNG` - Arquitectura del Sistema
- `Slide5.PNG` - Funcionalidades Principales
- `Slide6.PNG` - Base de Datos & Modelos
- `Slide7.PNG` - Seguridad & Filtros
- `Slide8.PNG` - CI/CD Pipeline
- `Slide9.PNG` - Demo en Vivo
- `Slide10.PNG` - Resultados & Aprendizajes
- `Slide11.PNG` - Cierre & Preguntas

### 4.4 Documentación relacionada

| Archivo | Descripción |
|---------|-------------|
| `docs/CI-CD_Presentation.md` | Guion de la presentación (151 líneas), notas del speaker por slide |

---

## 5. Dependencias y Acoplamiento

| Aspecto | Estado | Detalle |
|---------|--------|---------|
| Modelos / DB | **Ninguno** | No hay queries, models ni migraciones |
| Autenticación | **Ninguna** | Rutas no están protegidas por `AuthFilter` |
| Sesiones | **Ninguna** | Usa solo cache, no sesiones de usuario |
| Servicios CI4 | **Solo Cache** | `\Config\Services::cache()` para almacenar `current_slide` y `demo_state` |
| Assets del proyecto | **Indirecto** | `main_panel.php` tiene links hardcoded a rutas de Brixo (`/`, `/map`, `/panel`, etc.) para proyectarlos en `/demo` |
| CSS/JS externo | **CDN only** | Bootstrap, Tailwind, Font Awesome, Google Fonts — todo vía CDN |

**Nivel de acoplamiento: BAJO.** El único acoplamiento real es:

1. El cache driver de CI4 (`\Config\Services::cache()`)
2. Los links de navegación en `main_panel.php` que apuntan a rutas del proyecto Brixo (configurable)

---

## 6. Funcionalidades Clave (Valor como Producto)

| Feature | Descripción |
|---------|-------------|
| **Multi-pantalla sincronizada** | N pantallas se sincronizan vía API REST + polling |
| **Presenter Notes** | Notas por slide con tiempo sugerido, key phrases destacadas, y tags de demo |
| **Control remoto móvil** | Interfaz táctil con swipe, vibración, y fullscreen — ideal para smartphone |
| **Panel maestro para tablet** | Grid layout optimizado 11", thumbnails, navegación completa |
| **Modo dual (slides/URL)** | `/demo` puede alternar entre diapositivas y cualquier URL del proyecto en iframe |
| **Timer de presentación** | Cronómetro que inicia al primer interaction |
| **Auto-detección de slides** | Escanea el directorio `presentation/` con glob para determinar `totalSlides` |
| **Fullscreen nativo** | Todas las vistas soportan Fullscreen API del navegador |
| **Responsive** | Cada vista optimizada: `slides` para proyector, `remote` para móvil, `main-panel` para tablet |

---

## 7. Detalle de Componentes

### 7.1 Controller: `Presentation.php`

**Ruta:** `app/Controllers/Presentation.php`

**Métodos públicos:**

```php
private function getTotalSlides() // Escanea public/presentation/ para contar slides
public function slides()          // Vista de proyección
public function remote()          // Vista de control remoto
public function presenter()       // Vista del presentador con notas
public function mainPanel()       // Panel maestro de control
public function demo()            // Vista dual (slides/iframe)
public function apiSlide()        // API GET/POST para slide actual
public function apiDemo()         // API GET/POST para estado de demo
```

**Dependencias:**
- `CodeIgniter\Controller`
- `\Config\Services::cache()`

**Lógica de negocio:**
- `getTotalSlides()`: Usa `glob(FCPATH . 'presentation/Slide*.{png,PNG,jpg,jpeg,gif}', GLOB_BRACE)` para detectar slides automáticamente
- `apiSlide()`: Almacena/recupera `current_slide` del cache (TTL: 3600s)
- `apiDemo()`: Almacena/recupera `demo_state` (JSON) del cache con dos modos:
  - `{ "mode": "slides", "slide": 1 }`
  - `{ "mode": "url", "url": "/path" }`

### 7.2 Controller: `Showcase.php`

**Ruta:** `app/Controllers/Showcase.php`

**Métodos públicos:**

```php
public function index(): string  // Renderiza la vista showcase
```

Simple wrapper que retorna `view('showcase')`.

### 7.3 Vista: `slides.php` (100 líneas)

**Propósito:** Pantalla de proyección fullscreen para el proyector/TV.

**Características:**
- Muestra la diapositiva actual en `100vw x 100vh`, `object-fit: cover`
- Loop PHP genera un `<div>` por cada slide con clase `.slide.active`
- Polling cada 1 segundo (`setInterval(updateSlide, 1000)`)
- Botón de fullscreen (esquina superior derecha)
- Fallback de imagen: `.PNG` → `.png` vía `onerror`

**Dependencias:**
- Bootstrap 5 CSS (CDN)

### 7.4 Vista: `remote.php` (237 líneas)

**Propósito:** Control remoto móvil con interfaz táctil.

**Características:**
- Diseño responsive optimizado para smartphones
- Botones circulares grandes (120px × 120px en desktop, 30vh × 30vh en móvil)
- Contador de slide actual con indicadores de progreso (dots)
- **Swipe gestures** (left/right) para cambiar slides
- **Vibración háptica** (50ms) al cambiar slide con `navigator.vibrate()`
- Gradient background animado
- Polling cada 1 segundo para sincronizar con otras pantallas

**Dependencias:**
- Bootstrap 5 CSS (CDN)
- Font Awesome 6 (CDN)

### 7.5 Vista: `presenter.php` (637 líneas)

**Propósito:** Vista del presentador con notas, timer y preview.

**Características:**
- **Sticky header** con badge de slide actual y timer
- **Slide actual** en formato 16:9 con número overlay
- **Notas del presentador** hardcoded en objeto JS `presenterNotes` (11 slides)
  - Cada nota incluye: `title`, `points[]`, `time` sugerido
  - HTML renderizado dinámicamente con `key-phrase` y `demo-tag` destacados
- **Preview del siguiente slide** con thumbnail
- **Progress dots** en barra inferior (navegación visual)
- **Timer** formato `MM:SS`, inicia al primer click/touch
- **Swipe gestures** para navegación
- **Bottom nav bar** fija con botones Ant/Sig
- Polling cada 1.5 segundos

**Dependencias:**
- Bootstrap 5 CSS (CDN)
- Font Awesome 6.5 (CDN)

**Fragmento de notas (ejemplo):**

```javascript
const presenterNotes = {
    1: {
        title: "Portada — Bienvenida",
        points: [
            "Saludar al público y presentarse con nombre completo",
            "<span class='key-phrase'>Brixo</span> es una plataforma...",
            "Mencionar que es un proyecto académico...",
            "Agradecer al profesor y compañeros..."
        ],
        time: "~1 min"
    },
    // ... hasta slide 11
}
```

### 7.6 Vista: `main_panel.php` (933 líneas)

**Propósito:** Panel de control maestro optimizado para tablet 11".

**Características:**
- **Grid layout** 2 columnas: slide preview + sidebar de links
- **Top bar** con logo, timer, modo actual (slides/URL), botón fullscreen
- **Columna izquierda:**
  - Preview del slide actual (grande)
  - Controles de navegación (first, prev, counter, next, last)
  - **Thumbnail strip** con grid auto-fill de miniaturas clickeables
- **Columna derecha:**
  - **Quick links** para proyectar en `/demo`:
    - Demo en vivo (10+ rutas del proyecto Brixo)
    - Paneles de usuario
    - Presentación & Docs
    - Recursos externos (GitHub, Render, Aiven)
  - Botón "Volver a Diapositivas" (visible solo en modo URL)
- **Proyección de URLs:** Al hacer click en un link, envía POST a `/api/demo` con `{ mode: "url", url: "/path" }`
- **Keyboard shortcuts:** flechas, Home, End, Escape (volver a slides)
- Polling dual cada 1.2 segundos (`/api/slide` + `/api/demo`)

**Dependencias:**
- Bootstrap 5 CSS (CDN)
- Font Awesome 6.5 (CDN)
- Google Fonts: Inter (CDN)

**Links hardcoded (configurables):**
```javascript
// Demo en vivo
'/', '/map', '/especialidades', '/cotizador', '/login'

// Paneles
'/panel', '/solicitudes', '/mensajes', '/perfil'

// Presentación
'/showcase', '/reportes/contratistas'

// Externos (target="_blank")
'https://github.com/mikerb95/BrixoCI4'
'https://dashboard.render.com'
'https://console.aiven.io'
```

### 7.7 Vista: `demo.php` (219 líneas)

**Propósito:** Pantalla dual que alterna entre slides e iframe según `/api/demo`.

**Características:**
- **Dos layers superpuestos:**
  1. `#slide-layer`: Muestra la diapositiva actual (fullscreen)
  2. `#iframe-layer`: Iframe fullscreen con `sandbox` y permisos
- **Transiciones suaves** (opacity 0.35s) al cambiar de modo
- **Polling cada 800ms** (`/api/slide` + `/api/demo` en paralelo con `Promise.all`)
- **Indicador de transición** temporal (2s) al cambiar de modo
- **Fullscreen trigger** invisible en esquina superior derecha
- **Keyboard:** `F` para fullscreen, `Escape` para salir

**Lógica de estados:**

```javascript
if (demoState.mode === 'url' && demoState.url) {
    setUrlMode(url);  // Muestra iframe, oculta slide
} else {
    setSlideMode(slide); // Muestra slide, oculta iframe
}
```

**Dependencias:**
- Ninguna (CSS vanilla)

**Sandbox del iframe:**
```html
sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-modals"
```

### 7.8 Vista: `showcase.php` (513 líneas)

**Propósito:** Landing page de documentación del proyecto con diseño premium.

**Características:**
- **Hero section** con gradient mesh animado de fondo
- **BadgeAnimatedÈ "Proyecto Activo" con dot pulsante
- **CTAs:**
  - Ver Presentación PPTX (link a `Slide1.PNG`)
  - Ver en Vivo (`/slides`)
  - Exploración Profunda
- **Stack Tecnológico:** 5 cards con gradientes (Aiven, Render, AWS, GitHub, CodeIgniter 4)
- **Galería de slides:** Grid 2/3/4 columnas responsive con hover effects
- **Arquitectura:** Diagrama de flujo visual (GitHub → Render → Docker → Aiven → AWS)
- **Footer** con links de navegación

**Dependencias:**
- Tailwind CSS 3 (CDN con config inline)
- Font Awesome 6.5 (CDN)
- Google Fonts: Inter (CDN)

**Efectos visuales:**
- Animated gradient mesh (keyframe `meshFloat` 18s)
- Card glow borders con gradientes en hover
- Noise texture overlay (SVG inline)
- Button shine effect (pseudo-element `::after`)
- Custom scrollbar

---

## 8. API REST

### 8.1 `GET /api/slide`

**Respuesta:**
```json
{
  "slide": 1
}
```

**Fuente:** Lee `current_slide` del cache (default: 1).

---

### 8.2 `POST /api/slide`

**Request body:**
```json
{
  "slide": 3
}
```

**Respuesta:**
```json
{
  "slide": 3
}
```

**Lógica:**
1. Valida que `slide` esté entre 1 y `totalSlides`
2. Guarda en cache con `$cache->save('current_slide', $slide, 3600)`
3. Retorna el slide guardado

---

### 8.3 `GET /api/demo`

**Respuesta (modo slides):**
```json
{
  "mode": "slides",
  "slide": 1
}
```

**Respuesta (modo URL):**
```json
{
  "mode": "url",
  "url": "/map"
}
```

**Fuente:** Lee `demo_state` del cache (default: `{ "mode": "slides" }`).

---

### 8.4 `POST /api/demo`

**Request body (modo slides):**
```json
{
  "mode": "slides"
}
```

**Request body (modo URL):**
```json
{
  "mode": "url",
  "url": "/cotizador"
}
```

**Respuesta:** Mismo formato que el request.

**Lógica:**
1. Valida `mode` (solo acepta `"slides"` o `"url"`)
2. Si modo URL, almacena la URL proporcionada
3. Serializa a JSON y guarda en cache: `demo_state`
4. Retorna el estado guardado

---

## 9. Consideraciones para la Extracción

### 9.1 Decisiones Técnicas

| Decisión | Recomendación |
|----------|---------------|
| **Framework** | Puede mantenerse en CI4 (ligero) o migrar a vanilla PHP / Express.js / cualquier micro-framework. El backend son ~60 líneas de lógica real |
| **Cache** | Reemplazable por file cache, Redis, o incluso un simple archivo JSON. Solo almacena 2 keys |
| **Notas del presentador** | Están hardcoded en JS dentro de `presenter.php`. **Extraer a un archivo JSON configurable** |
| **Links de main_panel** | Actualmente hardcoded (rutas de Brixo). **Deben parametrizarse como configuración JSON** |
| **Imágenes de slides** | Deben ser cargables/reemplazables por el usuario (upload o directorio configurable) |
| **Showcase** | Es específico de Brixo (stack, arquitectura). **Puede convertirse en un template genérico o excluirse** |

### 9.2 Configuración Propuesta

**Archivo:** `config/presentation.json`

```json
{
  "slides": {
    "directory": "public/presentation",
    "pattern": "Slide*.{png,PNG,jpg,jpeg,gif}",
    "defaultSlide": 1
  },
  "cache": {
    "driver": "file",
    "ttl": 3600
  },
  "presenter": {
    "notes": "config/presenter-notes.json"
  },
  "demo": {
    "allowedOrigins": ["*"],
    "defaultMode": "slides"
  },
  "mainPanel": {
    "links": "config/demo-links.json"
  }
}
```

**Archivo:** `config/presenter-notes.json`

```json
{
  "1": {
    "title": "Portada — Bienvenida",
    "points": [
      "Saludar al público y presentarse con nombre completo",
      "<span class='key-phrase'>Proyecto</span> es una plataforma...",
      "Mencionar contexto del proyecto"
    ],
    "time": "~1 min"
  }
}
```

**Archivo:** `config/demo-links.json`

```json
{
  "groups": [
    {
      "title": "🖥️ Demo en vivo",
      "links": [
        {
          "url": "/",
          "icon": "fas fa-home",
          "iconColor": "emerald",
          "title": "Página Principal",
          "description": "Landing page del proyecto"
        }
      ]
    }
  ]
}
```

### 9.3 Mejoras Propuestas

| Mejora | Prioridad | Esfuerzo |
|--------|-----------|----------|
| Extraer notas a JSON configurable | Alta | Bajo |
| Parametrizar links de main-panel | Alta | Bajo |
| Subida/gestión de slides vía UI | Media | Alto |
| WebSockets en vez de polling | Media | Alto |
| Multi-presentación (varias sesiones paralelas) | Baja | Alto |
| Persistencia de slides en DB | Baja | Medio |
| Autenticación opcional para control | Baja | Medio |
| Temas/estilos configurables | Baja | Medio |

---

## 10. Superficie de Código a Extraer

| Categoría | Archivos | Líneas aprox. |
|-----------|----------|---------------|
| Controllers | 2 | ~120 |
| Vistas | 6 | ~2,639 |
| Assets | 11 PNGs | — |
| Rutas | 1 bloque | ~11 |
| Documentación | 1 | ~151 |
| **Total** | **21 archivos** | **~2,921 líneas** |

### Detalle de extracción:

**Archivos backend:**
```
app/Controllers/Presentation.php
app/Controllers/Showcase.php
```

**Archivos vistas:**
```
app/Views/slides.php
app/Views/remote.php
app/Views/presenter.php
app/Views/main_panel.php
app/Views/demo.php
app/Views/showcase.php
```

**Assets:**
```
public/presentation/Slide1.PNG
public/presentation/Slide2.PNG
public/presentation/Slide3.PNG
public/presentation/Slide4.PNG
public/presentation/Slide5.PNG
public/presentation/Slide6.PNG
public/presentation/Slide7.PNG
public/presentation/Slide8.PNG
public/presentation/Slide9.PNG
public/presentation/Slide10.PNG
public/presentation/Slide11.PNG
```

**Documentación:**
```
docs/CI-CD_Presentation.md
```

**Rutas a extraer de `app/Config/Routes.php`:**
```php
// Presentation routes
$routes->get('/slides', 'Presentation::slides');
$routes->get('/remote', 'Presentation::remote');
$routes->get('/presenter', 'Presentation::presenter');
$routes->get('/main-panel', 'Presentation::mainPanel');
$routes->get('/demo', 'Presentation::demo');
$routes->match(['get', 'post'], '/api/slide', 'Presentation::apiSlide');
$routes->match(['get', 'post'], '/api/demo', 'Presentation::apiDemo');

// Showcase
$routes->get('/showcase', 'Showcase::index');
```

---

## 11. Flujos de Uso Típicos

### Escenario 1: Presentación en aula con proyector

1. **Setup:**
   - Proyector: abrir `/slides` en fullscreen
   - Presentador: abrir `/presenter` en laptop
   - Control: abrir `/remote` en smartphone

2. **Durante la presentación:**
   - Presentador navega con botones en `/presenter` o con gestos en smartphone `/remote`
   - Todas las pantallas se sincronizan automáticamente vía polling
   - Timer en `/presenter` muestra tiempo transcurrido
   - Notas del presentador visibles solo en laptop

3. **Demo en vivo:**
   - Presentador cambia a `/main-panel` en tablet
   - Click en "Mapa Interactivo" → proyector muestra `/map` en iframe
   - Click "Volver a Diapositivas" → proyector regresa a slides

### Escenario 2: Presentación remota con Zoom

1. **Setup:**
   - Compartir pantalla de `/slides` en Zoom
   - Tener `/presenter` abierto en monitor secundario
   - Controlar con `/remote` desde el móvil o con `/main-panel` en tablet

2. **Ventajas:**
   - Audiencia solo ve slides limpias
   - Presenter notes privadas
   - Control desde cualquier dispositivo

### Escenario 3: Presentación sin conexión

1. **Limitación:** Las pantallas NO se sincronizan sin servidor
2. **Workaround:** Usar una sola pantalla (`/presenter` tiene todo: slide, notas, controles)

### Escenario 4: Auto-presentación en stand/booth

1. **Setup:**
   - Pantalla en `/slides` con auto-avance (requiere modificación: añadir `setInterval(changeSlide(1), 30000)`)
   - O tablet en `/showcase` para que los visitantes exploren

---

## 12. Análisis de Valor del Producto

### ¿Por qué merece ser un proyecto independiente?

1. **Completamente funcional:** No es un prototipo, es un sistema completo y probado
2. **Multi-dispositivo:** Sincronización en tiempo real sin WebSockets (bajo overhead)
3. **UX profesional:** Diseño responsive cuidado para cada tipo de pantalla
4. **Zero-config:** Detecta slides automáticamente, no requiere instalación compleja
5. **Modo dual único:** Alternar entre slides y demo en vivo del proyecto (feature diferenciadora)
6. **Bajo acoplamiento:** <100 líneas de lógica core, fácil de portar a cualquier stack

### Comparación con alternativas

| Feature | Esta solución | reveal.js | Google Slides | PowerPoint Online |
|---------|--------------|-----------|---------------|-------------------|
| Multi-pantalla sync | ✅ (polling) | ✅ (multiplex) | ❌ | ❌ |
| Presenter notes | ✅ Configurables | ✅ | ✅ | ✅ |
| Control remoto táctil | ✅ Dedicado | ⚠️ Plugin | ❌ | ❌ |
| Modo demo (iframe) | ✅ Único | ❌ | ❌ | ❌ |
| Offline-capable | ⚠️ Con modificaciones | ✅ | ❌ | ❌ |
| Zero-install | ✅ (CDN only) | ⚠️ Requiere build | ✅ Web | ✅ Web |
| Autodeploy CI/CD | ✅ Integrado | ⚠️ Manual | N/A | N/A |

**Ventaja competitiva:** El modo dual (slides + demo en vivo) es único. Ideal para presentaciones técnicas donde se necesita mostrar código/app en vivo dentro del flujo de slides.

---

## 13. Roadmap de Extracción

### Fase 1: Extracción limpia (1-2 días)
- [ ] Crear nuevo repo `presentation-system`
- [ ] Copiar controllers, vistas, assets
- [ ] Adaptar rutas a CodeIgniter 4 limpio
- [ ] Probar en local con slides de ejemplo
- [ ] Documentar en README.md

### Fase 2: Desacoplamiento (2-3 días)
- [ ] Extraer presenter notes a JSON
- [ ] Parametrizar links de main-panel
- [ ] Crear archivo de configuración `config/presentation.json`
- [ ] Reemplazar cache CI4 por file-based cache o Redis
- [ ] Hacer showcase.php genérico (template con variables)

### Fase 3: Empaquetado (1 día)
- [ ] Crear script de instalación (`setup.sh` o `composer install`)
- [ ] Generar Dockerfile standalone
- [ ] Configurar CI/CD (GitHub Actions) para el nuevo repo
- [ ] Deploy de demo en Render/Vercel

### Fase 4: Documentación (1-2 días)
- [ ] README.md completo con screenshots
- [ ] Quick start guide
- [ ] Configuration reference
- [ ] Deployment guides (Docker, Render, VPS)
- [ ] Tutorial video (opcional)

### Fase 5: Mejoras opcionales (ongoing)
- [ ] WebSockets para sincronización real-time
- [ ] Multi-sesión (varias presentaciones en paralelo)
- [ ] Temas/skins configurables
- [ ] Upload de slides vía UI
- [ ] Estadísticas de presentación (tiempo por slide, etc.)

**Esfuerzo total estimado:** 5-8 días de desarrollo + documentación

---

## 14. Licencia y Distribución

### Opciones:

1. **Open Source (MIT):** Máxima adopción, permite uso comercial
2. **GPL v3:** Requiere que derivados sean open source
3. **Dual license:** Open source para uso personal, comercial para empresas
4. **Proprietary:** Cerrado, solo binarios/SaaS

**Recomendación:** MIT o Apache 2.0 para facilitar adopción.

---

## 15. Stack Tecnológico del Módulo

### Backend
- **PHP:** 8.2+
- **Framework:** CodeIgniter 4.5+
- **Cache:** File / Redis / Memcached (adaptable)

### Frontend
- **HTML5/CSS3:** Vanilla + CDN libraries
- **JavaScript:** ES6+, sin frameworks (fetch API, DOM manipulation)
- **CSS Frameworks:**
  - Bootstrap 5.3 (slides, remote, presenter, main-panel)
  - Tailwind CSS 3 (showcase)
- **Iconografía:** Font Awesome 6.5
- **Fuentes:** Google Fonts (Inter)

### APIs
- **REST:** JSON sobre HTTP
- **Polling:** `setInterval()` con `fetch()`

### Infraestructura
- **Servidor web:** Apache / Nginx
- **Despliegue:** Docker-ready, compatible con Render/VPS/shared hosting

---

## 16. Conclusión

El **módulo de presentación de BrixoCI4** es un sistema completo, profesional y modular que resuelve un problema real de presentaciones técnicas multi-pantalla. Su bajo acoplamiento, funcionalidad única (modo dual slides/demo) y diseño cuidado lo convierten en un candidato ideal para ser extraído como producto independiente.

La extracción es técnicamente sencilla (~2,900 líneas de código en 21 archivos) y el valor aportado justifica el esfuerzo de empaquetado y documentación para convertirlo en una solución reutilizable.

**Próximos pasos recomendados:**
1. Crear nuevo repositorio dedicado
2. Implementar configuración basada en JSON
3. Generar demo público en línea
4. Documentar y promocionar como herramienta open source

---

**Fin del análisis**
