# SlideHub

Sistema de control de diapositivas en tiempo real con múltiples pantallas sincronizadas, control remoto desde el teléfono y notas del presentador generadas automáticamente con IA.

## Licencia y uso

Este repositorio es de **código cerrado** y está publicado de forma pública solo para visualización/referencia.

- Licencia: **All Rights Reserved**
- No se permite usar, copiar, modificar, redistribuir, sublicenciar ni comercializar este software sin autorización escrita previa.

Consulta el archivo [LICENSE](LICENSE) para los términos completos.

## Estado actual de arquitectura

SlideHub se ejecuta actualmente como **monolito modular** en el módulo `slidehub-service`.

- Un solo proceso Spring Boot (puerto `8080`)
- Módulos lógicos internos: `ui`, `state`, `ai`
- Persistencia híbrida: PostgreSQL + Redis + MongoDB
- Integraciones externas por HTTP (`WebClient`): Gemini, Groq, Google Drive, Resend
- Almacenamiento de assets en S3 (AWS SDK v2)

> Nota: la estructura de microservicios (`gateway-service`, `state-service`, `ui-service`, `ai-service`) se mantiene en el repo como referencia histórica/legacy.

---

## ¿Qué hace?

- Control remoto táctil con feedback háptico
- Sincronización multi-pantalla por polling
- QR join de equipo y asignaciones por slide
- Botón de ayuda con vibración triple
- Push-to-talk con transcripción + respuesta IA contextual
- Generación de quick slides
- Modo demo (slides/iframe) con `returnSlide`
- Notas IA por slide (Gemini Vision + Gemini + Groq)
- Importación de slides desde Google Drive
- Deploy Tutor (análisis, Dockerfile, guía de despliegue)

---

## Vistas principales

| Ruta | Acceso | Uso |
|------|--------|-----|
| `/slides` | Público | Proyector / audiencia |
| `/remote` | Público | Control smartphone |
| `/demo` | Público | Pantalla dual slides/iframe |
| `/showcase` | Público | Landing |
| `/presenter` | PRESENTER | Vista presentador |
| `/main-panel` | PRESENTER | Panel maestro |
| `/deploy-tutor` | PRESENTER | Tutor de despliegue |
| `/presentations` | PRESENTER | Gestión de presentaciones |
| `/auth/login` | Público | Login local y OAuth2 |

---

## Stack

**Backend**
- Java 21
- Spring Boot 4.0.3
- Spring Security + OAuth2 (GitHub / Google)
- Spring Data JPA + Flyway + PostgreSQL
- Spring Data Redis
- Spring Data MongoDB

**Frontend**
- Thymeleaf
- Bootstrap + Font Awesome
- Vanilla JS (`fetch`)

**IA**
- Gemini Vision
- Gemini API
- Groq

---

## Correr localmente

### Prerequisitos

- Java 21
- Docker
- Maven (o `./mvnw`)

### Variables de entorno

```bash
cp .env.example .env
```

Para flujo base:

```env
SPRING_PROFILES_ACTIVE=dev
```

### Dependencias locales

```bash
docker run -d -p 6379:6379 redis:alpine
docker run -d -p 27017:27017 mongo:6
docker run -d -p 5432:5432 -e POSTGRES_DB=slidehub -e POSTGRES_USER=slidehub -e POSTGRES_PASSWORD=slidehub postgres:14
```

### Build + run monolito

```bash
./mvnw clean compile -pl slidehub-service -am
./mvnw spring-boot:run -pl slidehub-service
```

Abrir:

- `http://localhost:8080/slides`
- `http://localhost:8080/remote`
- `http://localhost:8080/presenter`

### Tests monolito

```bash
./mvnw test -pl slidehub-service
```

---

## API (resumen)

```text
GET/POST    /api/slide
GET/POST    /api/demo
GET         /api/haptics/events/next
POST        /api/haptics/events/publish

POST        /api/ai/notes/generate
POST        /api/ai/notes/generate-all
GET         /api/ai/notes/{presentationId}
GET         /api/ai/notes/{presentationId}/{slideNumber}
DELETE      /api/ai/notes/{presentationId}

POST        /api/ai/analyze-repo
POST        /api/ai/analyze-repo/refresh
POST        /api/ai/deploy/analyze
POST        /api/ai/deploy/dockerfile
POST        /api/ai/deploy/guide
POST        /api/ai/deploy/guide/refresh
POST        /api/ai/assist/audio
```

---

## Despliegue

El blueprint de Render está en `render.yaml` y despliega **un solo servicio** (`slidehub-service`).

Más detalle: [DEPLOYMENT.md](DEPLOYMENT.md)

---

## Documentación adicional

- [AGENTS.md](AGENTS.md)
- [CLAUDE.md](CLAUDE.md)
- [DEPLOYMENT.md](DEPLOYMENT.md)
- [docs/MIGRACION-MONOLITO-FASE-2.md](docs/MIGRACION-MONOLITO-FASE-2.md)