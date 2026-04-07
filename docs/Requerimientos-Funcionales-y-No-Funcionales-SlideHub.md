# SlideHub — Requerimientos funcionales y no funcionales

**Fuente de verdad funcional:** [docs/Historias de Usuario - SlideHub.csv](docs/Historias%20de%20Usuario%20-%20SlideHub.csv)  
**Referencia arquitectónica y de alcance:** [docs/Presentation-Module-Analysis.md](docs/Presentation-Module-Analysis.md)  
**Restricciones técnicas y operativas:** [AGENTS.md](../AGENTS.md) y [CLAUDE.md](../CLAUDE.md)

## 1. Propósito del documento

Este documento consolida los requerimientos de SlideHub:

1. separar **requerimientos funcionales** de **no funcionales**;
2. organizarlos por fase del **ciclo de vida del software**;
3. dejar trazabilidad hacia las historias de usuario y las decisiones de arquitectura.

## 2. Alcance del sistema

SlideHub es una plataforma multi-servicio para controlar, presentar y enriquecer diapositivas en tiempo real. Su alcance cubre:

- visualización sincronizada de slides;
- control remoto desde smartphone;
- modo demo con alternancia entre slides e iframe;
- panel maestro para navegación avanzada;
- autenticación local y OAuth2;
- notas del presentador con IA;
- importación de slides desde Google Drive;
- almacenamiento de assets en S3;
- gestión de presentaciones y dispositivos;
- despliegue asistido por IA.

## 3. Ciclo de vida de referencia

Para organizar los requerimientos se usa este ciclo de vida:

1. **Elicitación y análisis**
2. **Diseño funcional y arquitectónico**
3. **Construcción e integración**
4. **Pruebas y validación**
5. **Despliegue y operación**
6. **Mantenimiento y evolución**

---

## 4. Requerimientos funcionales

### 4.1 Elicitación y análisis

#### Funcionales

- Login local con usuario y contraseña.
- Registro de cuenta nueva.
- Cierre de sesión.
- Avanzar y retroceder diapositivas desde smartphone.
- Ver siempre la diapositiva activa en pantalla pública.
- Ver slide actual, notas y preview del siguiente en la vista del presentador.
- Navegar directamente a cualquier diapositiva desde el panel maestro.
- Consultar el estado actual de slides mediante `GET /api/slide`.
- Consultar el modo demo mediante `GET /api/demo`.
- Ver la landing pública del proyecto.
- Cargar imágenes de diapositivas desde recursos estáticos.
- Listar dispositivos registrados y buscar por token.
- Generar notas de presentador con IA.
- Listar, consultar y borrar notas por presentación.
- Verificar salud del servicio de IA.
- Analizar repositorios y generar Dockerfiles o guías de despliegue.
- Importar slides desde Google Drive.
- Subir slides manualmente a una presentación.
- Gestionar presentaciones guardadas.
- Unirse a una sesión de presentación mediante QR.
- Gestionar participantes y asignarlos por slide.
- Solicitar ayuda con vibración háptica.
- Dictado por audio con transcripción y respuesta IA.
- Generar quick slides con colores dominantes.
- Consumir el catálogo de slides por presentación.

#### No funcionales

- El sistema debe operar como plataforma multi-pantalla en tiempo real.
- La sincronización debe basarse en polling HTTP, no en WebSockets.
- Los servicios deben estar desacoplados y comunicarse por HTTP.
- La solución debe permitir despliegue en Render.
- Debe evitarse el acoplamiento con el dominio de negocio original.

### 4.2 Diseño funcional y arquitectónico

#### Funcionales

- `state-service` administra el estado activo de presentación y el modo demo.
- `ui-service` sirve las vistas HTML y la autenticación.
- `ai-service` genera notas, analiza repositorios y crea guías de despliegue.
- `gateway-service` enruta solicitudes y centraliza configuración.
- `GET /api/slide` devuelve `slide` y `totalSlides`.
- `GET /api/ai/notes/{presentationId}/{slideNumber}` devuelve nota o `204 No Content`.
- `DELETE /api/ai/notes/{presentationId}` elimina notas aunque no existan registros previos.
- El modo demo debe alternar entre `slides` y `url` y poder volver al slide de retorno.

#### No funcionales

- Arquitectura de microservicios con un módulo por responsabilidad.
- UI con Thymeleaf; APIs con REST JSON.
- Autenticación local con BCrypt y OAuth2 coexistiendo.
- Persistencia distribuida según el tipo de dato: Redis, PostgreSQL y MongoDB.
- Integración con Gemini, Groq y Google Drive únicamente por HTTP.
- Integración con S3 mediante AWS SDK v2.
- No introducir multi-sesión en la primera versión.

### 4.3 Construcción e integración

#### Funcionales

- Implementar vistas: `/slides`, `/remote`, `/demo`, `/showcase`, `/presenter`, `/main-panel`, `/deploy-tutor`, `/presentations`, `/presentations/import`, `/auth/login`, `/auth/register`, `/auth/profile`.
- Implementar endpoints de estado, demo, dispositivos, notas, deploy tutor y reuniones.
- Implementar polling desde el frontend con `fetch()`.
- Implementar almacenamiento de notas en MongoDB.
- Implementar almacenamiento de estado efímero en Redis.
- Implementar usuarios, perfiles, presentaciones y sesiones en PostgreSQL.
- Implementar subida de assets a S3.

#### No funcionales

- Inyección por constructor en todo el código.
- Evitar `@Autowired` en campos.
- Preferir `record` para DTOs inmutables.
- No añadir Lombok ni MapStruct.
- No usar SDKs oficiales de Gemini, Groq, Google Drive ni Resend.
- No hardcodear secretos ni URLs de servicios.
- Mantener la convención de paquetes por servicio.

### 4.4 Pruebas y validación

#### Funcionales

- Verificar que `GET /api/slide` siempre responda `{ slide, totalSlides }`.
- Verificar que el slide por defecto sea `1` si no existe estado previo.
- Verificar que el login incorrecto muestre un error genérico.
- Verificar que la nota inexistente responda `204 No Content`.
- Verificar que el borrado sin datos previos también responda `204 No Content`.
- Verificar que en límites superior e inferior el slide no avance ni retroceda.

#### No funcionales

- Usar pruebas unitarias e integración por servicio.
- Preferir `@WebMvcTest` para controllers.
- Validar la compilación antes de cerrar una tarea.
- Mantener nombres de test orientados a escenario y resultado esperado.
- La validación debe cubrir seguridad, rutas y contratos JSON.

### 4.5 Despliegue y operación

#### Funcionales

- Desplegar un Web Service por microservicio en Render.
- Exponer health checks operativos.
- Centralizar el enrutamiento en el gateway.
- Cargar configuración por entorno.
- Usar `DATABASE_URL` para PostgreSQL en Aiven.

#### No funcionales

- Todas las credenciales deben venir de variables de entorno.
- Redis debe usar TTL para estado efímero.
- MongoDB debe persistir notas y guías de despliegue.
- Aiven debe usarse con SSL obligatorio.
- El filesystem local no debe usarse para almacenamiento persistente.
- El gateway debe definir primero `/api/ai/**` y luego `/api/**`.

### 4.6 Mantenimiento y evolución

#### Funcionales

- Regenerar notas si cambia el contenido del slide o del repositorio.
- Refrescar guías de despliegue descartando caché previa.
- Permitir ampliaciones futuras como asistencia por audio, reuniones y quick slides.

#### No funcionales

- Mantener compatibilidad con el contrato público de APIs.
- Conservar trazabilidad entre historias, servicios y endpoints.
- Mantener separación entre lógica de negocio, infraestructura y vistas.
- No introducir sesiones múltiples simultáneas en la versión base.

---

## 5. Requerimientos no funcionales por categoría

### 5.1 Seguridad

- Autenticación local con BCrypt.
- OAuth2 con GitHub y Google coexistiendo con login local.
- Roles `PRESENTER` y `ADMIN`.
- Tokens OAuth2 no deben almacenarse en texto plano.
- El login debe responder con mensajes genéricos ante error.
- Las rutas protegidas deben respetar el acceso por rol.

### 5.2 Rendimiento y tiempo real

- La experiencia debe sentirse en tiempo real mediante polling.
- La sincronización entre dispositivos debe ser rápida y estable.
- La respuesta de `GET /api/slide` debe ser inmediata para los clientes.
- Las interfaces deben evitar trabajo pesado en el frontend.

### 5.3 Disponibilidad y resiliencia

- El servicio de IA debe exponer health check.
- El sistema debe tolerar ausencia temporal de notas o estados previos.
- Las operaciones de borrado sin contenido previo no deben fallar.
- Los límites de navegación no deben producir errores visibles.

### 5.4 Mantenibilidad

- Código explícito y legible.
- Separación clara entre controller, service, model, config y exception.
- Configuración externalizada por servicio.
- Evitar dependencias innecesarias.

### 5.5 Escalabilidad

- Cada microservicio debe poder escalar de forma independiente.
- El catálogo de slides debe poder crecer por presentación.
- La persistencia debe separarse por tipo de dato y uso.

### 5.6 Portabilidad y despliegue

- Debe poder desplegarse en Render sin cambios funcionales.
- El entorno de producción debe configurarse por variables.
- Las rutas y URLs de servicios deben resolverse por configuración.

### 5.7 Usabilidad

- El flujo de login debe ser simple y sin ambigüedad.
- La pantalla pública debe abrir sin fricción.
- El control remoto debe funcionar bien en smartphone.
- El panel maestro debe favorecer navegación rápida.

### 5.8 Observabilidad

- Deben existir health checks.
- Deben poder identificarse fallos de integración con IA.
- Deben preservarse logs útiles para diagnóstico.

---

## 6. Matriz breve de trazabilidad

| Área | Requerimiento funcional principal | Requerimiento no funcional principal |
|---|---|---|
| Autenticación | Login, registro, logout, OAuth2 | Seguridad, BCrypt, tokens protegidos |
| Presentación | Slide activo, demo, remote, main-panel | Tiempo real, polling HTTP, disponibilidad |
| IA | Notas, deploy tutor, health check | Resiliencia, integración HTTP, observabilidad |
| Datos | Redis, PostgreSQL, MongoDB, S3 | Persistencia adecuada, externalización, seguridad |
| Infraestructura | Gateway, rutas, Render | Portabilidad, escalabilidad, configuración por entorno |

## 7. Fuentes consultadas

- [docs/Historias de Usuario - SlideHub.csv](docs/Historias%20de%20Usuario%20-%20SlideHub.csv)
- [docs/Presentation-Module-Analysis.md](docs/Presentation-Module-Analysis.md)
- [AGENTS.md](../AGENTS.md)
- [CLAUDE.md](../CLAUDE.md)

## 8. Nota de criterio

Este documento prioriza la forma de documentación que suele usarse en organizaciones grandes: requisitos trazables, separaciones claras entre negocio, calidad y operación, y una estructura que permita pasar de la visión del producto a la arquitectura y a las pruebas.