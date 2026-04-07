# Resumen del Proyecto — SlideHub

Autor: Mike Rodriguez
Fecha: marzo 2026

## Objetivo General

Desarrollar una plataforma de presentaciones sincronizadas multi‑pantalla que permita a presentadores controlar en tiempo real diapositivas, notas generadas por IA y demos integradas, con despliegue sencillo y operación confiable en entornos cloud.

## Objetivos Específicos

- Implementar una arquitectura de microservicios (gateway, state, ui, ai) que se comuniquen vía HTTP y permitan despliegues independientes.
- Proveer vistas públicas de proyección (`/slides`, `/demo`) y paneles para presentadores (`/presenter`, `/main-panel`) con latencia baja mediante polling controlado.
- Persistir estado efímero en Redis (`state-service`) y notas/guías generadas por IA en MongoDB (`ai-service`).
- Integrar cargue y almacenamiento de assets en S3 con control de accesos y URLs públicas seguras (`ui-service`).
- Exponer APIs bien definidas (`/api/slide`, `/api/ai/notes/*`, `/api/devices`) para clientes móviles y dispositivos remotos.
- Facilitar un flujo de integración de IA (Gemini/Groq) mediante `ai-service`, manteniendo modo mock para pruebas y cache en MongoDB.
- Asegurar despliegue reproducible en Render con variables de entorno y documentación mínima para desarrolladores.

## Planteamiento del problema

Las presentaciones modernas requieren sincronización entre varias pantallas (proyector, asistentes, panel del presentador) y flujos mixtos (diapositivas + URLs/demos). Además, los presentadores demandan soporte de notas y sugerencias contextuales que reduzcan la carga de preparación. Existen soluciones puntuales (PowerPoint remotos, herramientas propietarias) pero suelen ser monolíticas, difíciles de integrar con pipelines de despliegue y no ofrecen integración nativa con servicios de IA para generar notas o guías de despliegue. 

SlideHub aborda estas carencias proponiendo un sistema modular que separa responsabilidades (estado, UI, IA, gateway) para facilitar mantenimiento, pruebas y despliegues incrementales; además aporta integración con almacenamiento en la nube y generación asistida de contenido.

## Justificación

- Productiva: Reduce tiempo de preparación del presentador al ofrecer notas y guías automáticas, y mejora la experiencia del público con sincronización fiable.
- Técnica: La arquitectura por microservicios permite escalar componentes críticos (p. ej. `ai-service`) y aislar fallos, facilitando despliegues y rollbacks en Render.
- Operativa: Centraliza configuraciones sensibles en variables de entorno, usa stores adecuados (Redis para estado, MongoDB para notas y Postgres para datos relacionales) y guarda assets en S3 para disponibilidad y CDN.
- Formativa: Proyecto ideal para un equipo junior que aprende buenas prácticas (tests, CI, infra como código mínima), apoyándose en IA para acelerar tareas repetitivas sin reemplazar la decisión humana.

## Alcance y límites (breve)

- Incluye: sincronización de slides, control remoto, panel de presentador con notas IA, upload a S3, despliegue en Render, rutas API básicas.
- Excluye (v1): WebSockets/RTC (se usa polling por simplicidad), gestión avanzada de multi‑sesión simultánea, y features de auditoría/compliance extendida.

---

Documento conciso creado para introducir el proyecto a stakeholders técnicos y operativos. Para detalles técnicos y ubicación de código, ver `docs/` y los módulos `state-service`, `ui-service`, `ai-service` y `gateway-service`.
