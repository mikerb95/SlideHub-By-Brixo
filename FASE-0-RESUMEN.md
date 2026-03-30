# Fase 0 — Resumen Educativo

> **Para estudiantes que recién empiezan en desarrollo de software**

---

## ¿Qué fue la Fase 0?

La Fase 0 fue la **transformación del proyecto de una estructura simple a una arquitectura profesional de microservicios**. Imagínalo como redecorar una casa pequeña para que tenga varios apartamentos independientes que se comunican entre sí.

### Antes (Día 0):
```
SlideHub/
├── src/main/java/
│   └── com/brixo/SlideHub/  ← Todo mezclado en UN paquete
│       └── SlideHubApplication.java
├── pom.xml  ← Un solo archivo de dependencias muy complejo
```

### Después (Fase 0):
```
SlideHub/  ← Monorepo con 4 servicios independientes
├── state-service/       (Puerto 8081) - Gestiona dónde estamos en la presentación
├── ui-service/          (Puerto 8082) - Dibuja las vistas HTML
├── ai-service/          (Puerto 8083) - Se comunicará con Gemini y Groq
├── gateway-service/     (Puerto 8080) - Punto de entrada único, como un recepcionista
└── pom.xml             ← Parent POM que coordina todo
```

---

## ¿Por qué hacer esto?

**En desarrollo profesional, los equipos grandes necesitan:**

| Beneficio | Explicación |
|-----------|-------------|
| **Separación de responsabilidades** | Cada servicio hace UNA cosa bien. Sin mezcla. |
| **Escalabilidad independiente** | Si `/slides` se vuelve lento, mejoramos solo `ui-service`, no todo el proyecto |
| **Desarrollo en paralelo** | Un equipo trabaja en `ai-service` mientras otro hace `ui-service` — sin conflictos |
| **Reutilización** | El `state-service` puede usarse desde múltiples apps (web, móvil, teclado, etc.) |
| **Fácil de testear** | Cada servicio se prueba en aislamiento |

---

## Los 4 Microservicios - Explicados Fácil

### 1️⃣ **`state-service`** (El Cerebro)
```
Puerto: 8081
Responsabilidad: Recordar en qué slide estamos
```

**¿Qué hace?**
- Guarda en Redis: "El slide actual es el 5"
- Dice cuántos slides hay en total (lee del directorio de archivos)
- Permite cambiar de slide: `/api/slide` → `POST { "slide": 5 }`
- Maneja el modo demo (slides vs iframe)
- Lleva un registro de qué dispositivos están conectados

**Analogía:** Es como el árbitro de un partido que ve a todos los jugadores y grita el marcador. Todos le preguntan a él, no se comunican directamente.

---

### 2️⃣ **`ui-service`** (La Cara Bonita)
```
Puerto: 8082
Responsabilidad: Dibujar pantallas y formularios HTML
```

**¿Qué hace?**
- Muestra 6 vistas HTML (Thymeleaf):
  - `/slides` — proyector en TV (fullscreen)
  - `/remote` — control remoto para celular (con botones y swipe)
  - `/demo` — pantalla dual (slides + iframe)
  - `/showcase` — landing page bonita
  - `/presenter` — vista del presentador con notas
  - `/main-panel` — panel maestro para tablet
- Maneja login/logout (Spring Security + BCrypt)
- **Hace polling** (cada 1-1.5s, un JavaScript pregunta a `state-service`): "¿en qué slide estamos ahora?"
- Actualiza las imágenes automáticamente sin refrescar

**Analogía:** Es el portal de una app web. Bonita, responsiva, habladora — pero no toma decisiones propias, solo pregunta al cerebro (state-service).

**Conceptos clave:**
- **Thymeleaf:** Motor que convierte archivos `.html` + datos Java → HTML final que ves en el navegador
- **Spring Security:** Dice quién puede ver qué (públicas vs protegidas por rol PRESENTER/ADMIN)
- **Polling:** En vez de WebSockets (más complicado), JavaScript hace muchas preguntas pequeñas muy seguido

---

### 3️⃣ **`ai-service`** (El Cerebro Inteligente)
```
Puerto: 8083
Responsabilidad: Hablar con IAs externas (Gemini, Groq) y guardar notas
```

**¿Qué hace?**
- Endpoints para leer/escribir/borrar notas del presentador en MongoDB
- En Fase 2 añadirá:
  - Gemini API: lee tu repositorio GitHub, extrae contexto del slide
  - Groq API: genera notas bonitas estructuradas
- Por ahora (Fase 0) solo dice "no implementado todavía" con HTTP 501

**Analogía:** Es como un asistente brillante que tienes en el despacho. En Fase 0 está de vacaciones, pero ya tiene su escritorio listo.

---

### 4️⃣ **`gateway-service`** (El Recepcionista)
```
Puerto: 8080
Responsabilidad: Enrutar requests al servicio correcto
```

**¿Qué hace?**
- Recibes una petición en `http://localhost:8080/api/slide`
- El gateway dice: "Ah, eso es para `state-service`, te conecto"
- Te enruta a `http://localhost:8081/api/slide` sin que lo sepas

**¿Por qué?** Así los clientes no necesitan saber dónde vive cada servicio. Solo hablan con el gateway.

**Analogía:** Es como la recepción de un hospital:
- Pregunta: "¿Dónde está cardiología?" 
- Recepcionista: "Tercera planta, sala 301"
- Te acompaña / te indica

**Orden importante:**
```
Orden ↓
1. /api/ai/**        → ai-service
2. /api/**           → state-service  (NO si es /api/ai/**, porque ya lo atrapó 1)
3. /auth/**, /slides → ui-service
4. /presentation/**  → ui-service
```

Sin este orden, si alguien pide `/api/ai/notes`, el paso 2 lo atrapaba antes que el paso 1. ❌

---

## Archivos Importantes Creados - Con Ejemplos

### `state-service` — El corazón

#### `SlideStateService.java`
```java
@Service
public class SlideStateService {
    // Método: ¿En qué slide estamos?
    public SlideStateResponse getCurrentSlide() {
        // Lee de Redis: "current_slide" = 5
        // Cuenta archivos de slides (Slide1.PNG, Slide2.PNG, ...)
        // Devuelve: { slide: 5, totalSlides: 11 }
    }
    
    // Método: Avanza al slide siguiente
    public void setSlide(int slide) {
        // Verifica que esté en rango [1..11]
        // Guarda en Redis
        // Si pides slide 100 en una presentación de 11 slides → ERROR
    }
}
```

---

### `ui-service` — Las pantallas

#### `SecurityConfig.java`
```java
@Configuration
public class SecurityConfig {
    // Define: ¿Quién puede ver qué?
    .requestMatchers("/slides", "/remote").permitAll()  // Público
    .requestMatchers("/presenter").hasRole("PRESENTER")  // Solo si estás logeado
}
```

#### `PresentationViewController.java`
```java
@Controller
public class PresentationViewController {
    @GetMapping("/slides")
    public String slidesView(Model model) {
        // Añade datos al modelo para que Thymeleaf los use
        model.addAttribute("pollIntervalMs", 1000);
        return "slides";  // Renderiza templates/slides.html
    }
}
```

#### `slides.html` — Ejemplo de Template
```html
<div id="slide-wrapper">
    <!-- Las imágenes se cargan dinámicamente por JavaScript -->
</div>

<script>
// Cada 1000ms, pregunta a /api/slide
setInterval(async () => {
    const data = await fetch('/api/slide').then(r => r.json());
    // data = { slide: 5, totalSlides: 11 }
    // Muestra la imagen Slide5.PNG
}, 1000);
</script>
```

**¿Por qué JavaScript aquí y no Java?** 
Java genera el HTML en el servidor. JavaScript lo actualiza DESPUÉS en el navegador (sin recargar la página). Así es fluido. ✨

---

### `ai-service` — Preparado para el futuro

#### `NotesController.java`
```java
@RestController
public class NotesController {
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, Object> request) {
        // En Fase 0: devuelve "no implementado"
        return ResponseEntity.status(501)
            .body(Map.of("errorMessage", "Disponible en Fase 2"));
    }
    
    @GetMapping("/{presentationId}/{slideNumber}")
    public ResponseEntity<PresenterNote> getNote(...) {
        // Si la nota no existe → 204 No Content (no 404)
        // Porque 204 dice "éxito, pero sin contenido"
        // 404 diría "no existe" (drama innecesario)
    }
}
```

---

## Conceptos de Desarrollo - Explicados para Principiantes

### Redis - Base de datos ultra-rápida
```
Es como un escritorio en vez de un archivador:
- Archivador (PostgreSQL): buscar un documento toma tiempo
- Escritorio (Redis): alcanzar una hoja está en tu mano → INSTANT

Se usa para estado que cambia frecuentemente:
- UUID actual en la presentación: ✅ Redis
- Datos de usuarios (nombre, email): ❌ Base de datos SQL
```

### MongoDB - Base de datos flexible
```
Guarda documentos JSON, no tablas:
- SQL: columnas fijas, rígidas
- MongoDB: cada documento puede ser diferente

Usamos aquí para notas del presentador (documentos complejos).
```

### Spring Security - Control de acceso
```
Define: ¿Quién puede ir a dónde?

1. /slides → pública (sin login)
2. /presenter → requiere @WithRole("PRESENTER")

Si intentas entrar a /presenter sin login:
→ Spring te redirecciona a /auth/login automáticamente
```

### Spring Boot - Framework manejable
```
Sin Spring Boot, esto tomaría 500 líneas de configuración XML.
Spring Boot lo trae pre-configurado:
- Servidor HTTP integrado (Tomcat)
- Seguridad lista
- Tests listos
- Inyección de dependencias automática
```

### Maven & Multi-módulo
```
pom.xml raíz = Parent POM (coordinador)
    ↓
    ├─ state-service/pom.xml     (hereda del parent)
    ├─ ui-service/pom.xml        (hereda del parent)
    ├─ ai-service/pom.xml        (hereda del parent)
    └─ gateway-service/pom.xml   (hereda del parent)

Beneficio: declara una dependencia una sola vez en parent,
todos los hijos la heredan.
```

---

## Errores Encontrados y Solucionados

### Problema 1: Jackson 3.x vs 2.x
```
Error: "package com.fasterxml.jackson.databind does not exist"

¿Por qué? Spring Boot 4.0.3 usa Jackson 3.x (nuevo groupId):
- Viejo: com.fasterxml.jackson.databind
- Nuevo: tools.jackson.databind

Solución: Cambiar los imports en SlideStateService y DemoStateService
```

### Problema 2: Gateway API de Spring Cloud 2025.1.0 cambió
```
Error: "method http in HandlerFunctions cannot be applied to given types"

¿Por qué? La API cambió:
- Viejo: http(baseUrlString)  ← pasabas la URL como parámetro
- Nuevo: http() → route(...) → uri(baseUrl)

Es más clara y sigue patrones funcionales.
```

---

## Archivos Principales Creados en Fase 0

```
✅ = Funcional en Fase 0
🚧 = Stub para Fase 2

state-service/
  ├── pom.xml ✅
  ├── SlideStateService.java ✅
  ├── DemoStateService.java ✅
  ├── DeviceRegistryService.java ✅
  ├── SlideController.java ✅
  ├── DemoController.java ✅
  └── DeviceController.java ✅

ui-service/
  ├── pom.xml ✅
  ├── SecurityConfig.java ✅
  ├── PresentationViewController.java ✅
  ├── AuthController.java ✅
  ├── PresenterViewController.java ✅
  ├── slides.html ✅
  ├── remote.html ✅
  ├── demo.html ✅
  ├── showcase.html ✅
  ├── presenter.html ✅
  ├── main-panel.html ✅
  ├── auth/login.html ✅
  └── auth/register.html ✅

ai-service/
  ├── pom.xml ✅
  ├── PresenterNote.java 🚧
  ├── PresenterNoteRepository.java 🚧
  ├── NotesController.java 🚧 (devuelve 501 en generate())
  └── test/ ✅

gateway-service/
  ├── pom.xml ✅
  ├── GatewayServiceApplication.java ✅
  ├── RoutesConfig.java ✅ (Spring Cloud 2025.1.0)
  └── test/ ✅
```

---

## ¿Qué Funciona Ahora? (Dentro de Fase 0)

| Feature | Funciona | Detalles |
|---------|----------|----------|
| Ver slide en proyector | ✅ | `/slides` → polling cada 1s → actualiza imagen |
| Control remoto swipe | ✅ | `/remote` → prev/next buttons + swipe en móvil |
| Pantalla demo dual | ✅ | `/demo` → alterna slides vs iframe |
| Landing page | ✅ | `/showcase` → info del proyecto |
| Presenter view | ✅ | `/presenter` → ve slides + notas panel (panel vacío por ahora) |
| Panel maestro | ✅ | `/main-panel` → grid de slides, controla demo URL |
| Sync slides ↔ todos | ✅ | Redis + polling |
| Dispositivos conectados | ✅ | `/api/devices` (memoria, no persistido) |
| Login/Logout | ✅ | Spring Security + BCrypt |
| Notas IA generate | ❌ | Devuelve 501, pendiente Fase 2 |
| Notas IA read/delete | ✅ | Funciona con MongoDB |

---

## Próximas Fases (Preview)

### Fase 1: Usuarios persistentes + OAuth2
- PostgreSQL para usuarios
- GitHub OAuth2 + Google OAuth2
- Múltiples presentaciones por usuario

### Fase 2: Integración IA
- Gemini API: leer repositorio GitHub
- Groq API: generar notas estructuradas
- Notas persistidas en MongoDB

### Fase 3: Extras
- Google Drive: importar slides
- Gemini Vision: analizar imágenes
- WebSockets: update en tiempo real (sin polling)

---

## Para Estudiantes: Puntos Clave de Aprendizaje

### 1. Arquitectura de Microservicios
✅ Aprendiste a **separar responsabilidades**: no mezclamos HTML, APIs y estado en un paquete monolítico.

### 2. REST APIs
✅ Aprendiste que `/api/slide` no es "magia" — un cliente HTTP pide datos, un servidor devuelve JSON.

### 3. Templating (Thymeleaf)
✅ Aprendiste que HTML no es solo texto — puede tener variables, loops, condicionales.

### 4. Frontend Dinámico (JavaScript)
✅ Aprendiste que JavaScript en el navegador puede hacer polling: preguntar cada N segundos "¿hay algo nuevo?".

### 5. Spring Framework
✅ Aprendiste que `@Controller`, `@Service`, inyección de dependencias, etc., son **abstracciones que simplifican**.

### 6. Gestión de Datos (Redis vs MongoDB)
✅ Aprendiste que **hay base de datos para cada caso de uso**: ultra-rápidas (Redis) y flexibles (MongoDB).

### 7. Seguridad (Spring Security)
✅ Aprendiste que el login no es "por costumbre" — es control de acceso real.

### 8. Maven Multi-módulo
✅ Aprendiste a **organizar código en múltiples proyectos** que comparten dependencias.

---

## Compilar y Probar (Para Ustedes)

```bash
# Ir al directorio
cd /home/mike/dev/learning/SlideHub

# Ver la estructura
ls -la state-service/ ui-service/ ai-service/ gateway-service/

# Compilar TODO
./mvnw clean compile

# Si hay errores, compilar un servicio específico
./mvnw clean compile -pl state-service -am

# Ver dependencias de un servicio
./mvnw dependency:tree -pl ui-service
```

---

## Resumen en Una Frase

**Convertimos un proyecto simple monolítico en una arquitectura profesional de 4 microservicios independientes, cada uno especializado, comunicándose vía HTTP, gestionados por un único gateway.**

---

*Fase 0 completada: 27 de febrero de 2026* 
