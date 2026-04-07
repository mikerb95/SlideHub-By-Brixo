# Matriz de Cumplimiento — ISO/SQuaRE, PDCA y PSP (SlideHub)

Fecha de evaluación: 2026-04-01
Alcance: repositorio completo (`gateway-service`, `state-service`, `ui-service`, `ai-service`, `docs/`, `scripts/`).

## 1) Semáforo de cumplimiento

| Dominio | Estado | Evidencia principal | Brecha principal |
|---|---|---|---|
| ISO/IEC 25000 (SQuaRE, marco general) | 🟡 Parcial | Requisitos y trazabilidad documentados en `docs/Requerimientos-Funcionales-y-No-Funcionales-SlideHub.md` | No existe modelo formal de calidad con métricas/umbrales por característica (fiabilidad, seguridad, mantenibilidad, etc.) |
| ISO/IEC 25001 (planificación/gestión de evaluación) | 🔴 Bajo | Existen checks funcionales y smoke (`docs/SMOKE-E2E.md`) | No hay Plan de Evaluación de Calidad formal, con criterio de aceptación cuantitativo y periodicidad definida |
| ISO/IEC 27001 (SGSI) | 🟡 Parcial | Seguridad de acceso (`ui-service/.../SecurityConfig.java`), secretos en env vars, rate limit en gateway | Tokens OAuth2 persistidos sin cifrado aplicativo en `users.github_access_token` y `users.google_refresh_token` |
| ISO/IEC 12207 (ciclo de vida software) | 🟡 Parcial | Ciclo por fases/sprints (`docs/Historial-Sprints.md`), arquitectura y responsabilidades (`AGENTS.md`) | Falta institucionalizar artefactos de salida por fase (DoD, checklist de cierre, control de cambios formal) |
| ISO/IEC 15504 / SPICE (capacidad de procesos) | 🔴 Bajo | Hay práctica de pruebas y operación | No existe evaluación de capacidad por procesos/niveles ni baseline de madurez |
| PDCA (Plan-Do-Check-Act) | 🟡 Parcial | Plan (requisitos/sprints), Do (implementación), Check (tests/smoke/status), Act (migraciones de reparación + fixes) | El “Check” carece de métricas de calidad objetivo y el “Act” no está estandarizado en un ciclo formal |
| PSP (disciplina individual) | 🔴 Bajo | Evidencia puntual de pruebas y bugs corregidos | No se registran estimaciones individuales, tiempo real invertido ni bitácora de defectos por desarrollador |

## 2) Evidencia concreta (repositorio)

### 2.1 Calidad y trazabilidad (SQuaRE base)
- Requisitos funcionales/no funcionales y matriz resumida:
  - `docs/Requerimientos-Funcionales-y-No-Funcionales-SlideHub.md`
- Fuente de verdad funcional y reglas de aceptación:
  - `AGENTS.md`
  - `docs/Historias de Usuario - SlideHub.csv`

### 2.2 Seguridad (27001)
- Control de acceso, roles, autenticación y OAuth2:
  - `ui-service/src/main/java/com/brixo/slidehub/ui/config/SecurityConfig.java`
- Protección anti-abuso (rate limiting):
  - `gateway-service/src/main/java/com/brixo/slidehub/gateway/ratelimit/GatewayRateLimitService.java`
  - `gateway-service/src/main/resources/application.properties`
- Persistencia de tokens OAuth2 en texto plano (brecha crítica):
  - `ui-service/src/main/resources/db/migration/V1__create_users.sql`
  - `ui-service/src/main/java/com/brixo/slidehub/ui/model/User.java`
  - `ui-service/src/main/java/com/brixo/slidehub/ui/service/CustomOAuth2UserService.java`

### 2.3 Proceso y verificación (12207/PDCA)
- Planificación y fases:
  - `docs/Historial-Sprints.md`
- Verificación operativa y smoke:
  - `docs/SMOKE-E2E.md`
  - `.github/workflows/smoke-e2e.yml`
- Observabilidad mínima de disponibilidad/dependencias:
  - `ui-service/src/main/java/com/brixo/slidehub/ui/service/StatusChecksService.java`

### 2.4 Actuar/mejora
- Corrección de defectos operativos en enrutamiento:
  - `gateway-service/src/main/java/com/brixo/slidehub/gateway/config/RoutesConfig.java`
  - `FASE-3-RESUMEN.md`
- Reparación idempotente de esquema:
  - `ui-service/src/main/resources/db/migration/V4__repair_meeting_tables.sql`

## 3) Backlog priorizado de remediación

## Prioridad P0 (esta semana)
1. **Cifrar tokens OAuth2 en base de datos (brecha 27001 crítica)**
   - Acción:
     - Implementar cifrado en reposo a nivel aplicación (JPA `AttributeConverter` o servicio de cifrado) para `github_access_token` y `google_refresh_token`.
     - Rotar credenciales y reemitir tokens activos.
   - Entregable:
     - Código + migración de datos + procedimiento de rotación.
   - Criterio de cierre:
     - No existe token legible en DB en texto claro.

2. **Eliminar inconsistencia documental de seguridad**
   - Acción:
     - Alinear lo declarado (“tokens encrypted”) con implementación real.
   - Entregable:
     - Actualización de `FASE-6-RESUMEN.md` y/o docs de seguridad.
   - Criterio de cierre:
     - Toda afirmación de control tiene evidencia ejecutable.

## Prioridad P1 (1–2 semanas)
3. **Plan de Evaluación de Calidad (ISO/IEC 25001)**
   - Acción:
     - Crear documento `docs/Plan-Evaluacion-Calidad-SlideHub.md` con:
       - características y subcaracterísticas de calidad evaluadas,
       - métricas, umbrales, método y frecuencia,
       - responsables y evidencias.
   - Entregable:
     - Plantilla operativa aprobada por el equipo.
   - Criterio de cierre:
     - Cada release tiene evaluación registrada contra umbrales.

4. **Formalizar PDCA de defectos**
   - Acción:
     - Estandarizar plantilla de incidente: causa raíz, acción correctiva, verificación y estandarización.
   - Entregable:
     - `docs/PDCA-Incidentes.md` + 1 caso real completado.
   - Criterio de cierre:
     - Incidentes críticos cierran con evidencia de “Act” aplicada.

5. **Fortalecer “Check” con KPIs de calidad**
   - Acción:
     - Definir y publicar métricas mínimas: tasa de fallos smoke, tiempo medio de recuperación, % endpoints críticos con test.
   - Entregable:
     - `docs/KPIs-Calidad.md` y registro periódico.
   - Criterio de cierre:
     - Tendencia semanal visible y usada en decisiones.

## Prioridad P2 (2–4 semanas)
6. **Autoevaluación de capacidad de procesos (alineación SPICE/15504)**
   - Acción:
     - Evaluar 3 procesos iniciales: gestión de requisitos, pruebas, gestión de cambios.
   - Entregable:
     - Matriz de capacidad inicial y plan de mejora trimestral.
   - Criterio de cierre:
     - Baseline de madurez y objetivos de avance definidos.

7. **Introducir prácticas PSP ligeras**
   - Acción:
     - Registrar por desarrollador: estimado vs real y defectos por fase (codificación, revisión, pruebas).
   - Entregable:
     - Plantilla simple (`docs/PSP-Log-Template.md`) + piloto de 2 sprints.
   - Criterio de cierre:
     - Se reduce desvío de estimación y aumenta detección temprana de defectos.

## 4) KPIs sugeridos (primer corte)

- **Seguridad**: % secretos/tokens cifrados en reposo (objetivo: 100%).
- **Confiabilidad**: éxito smoke E2E por ejecución (objetivo: ≥95%).
- **Mantenibilidad**: defectos reabiertos por sprint (objetivo: tendencia decreciente).
- **Proceso**: % historias con evidencia de prueba + criterio de aceptación verificado (objetivo: 100%).
- **PSP**: desviación estimado/real por tarea (objetivo inicial: ±25%).

## 5) Conclusión ejecutiva

SlideHub tiene buenas bases técnicas de arquitectura, seguridad de acceso y verificación operativa, pero aún no demuestra un sistema de calidad formal alineado completamente con ISO/IEC 25001, 15504/SPICE y PSP. El mayor riesgo inmediato está en la gestión de tokens OAuth2 en texto claro. Resolver P0 + P1 coloca al proyecto en un nivel de cumplimiento práctico significativamente más alto sin frenar el delivery.
