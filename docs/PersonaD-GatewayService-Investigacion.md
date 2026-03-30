
## PersonaD-GatewayService-Investigacion.md

**Documento de investigación: Gateway Service & Config Server**

## 1. Análisis del Gateway Service

**El Gateway Service actúa como punto de entrada principal del sistema SlideHub.**
>Encargado de enrutar las solicitudes hacia los diferentes microservicios internos. Su función principal es centralizar el acceso, mejorar la seguridad y simplificar la comunicación entre cliente y servicios backend.< 

Este servicio está construido bajo el patrón de API Gateway, probablemente usando tecnologías como **Spring Cloud Gateway,** lo que permite manejar rutas dinámicas, filtros y balanceo de carga.

## 2. Rutas (Rutes)

**El gateway define rutas que redirigen las peticiones HTTP a servicios específicos según el path:**

- /state/** → redirige a State Service
- /ui/** → redirige a UI Service
- /ai/** → redirige a AI Service

>Estas rutas permiten desacoplar el cliente del backend real, ocultando la arquitectura interna.< 

**Además, el gateway puede incluir:**

- Filtros de autenticación (JWT/OAuth2)
- Logs de tráfico
- Manejo de errores centralizado

## ⚙️ 3. Config Server

**El Config Server centraliza la configuración de todos los microservicios.**

Funciones principales:

- Proveer variables de entorno y propiedades
- Manejar perfiles (dev, prod)
- Permitir cambios sin modificar código

>Generalmente funciona conectado a un repositorio (ej: Git), desde donde cada servicio obtiene su configuración al iniciar.< 

En este proyecto, variables como:

- URLs de servicios (STATE_SERVICE_URL, AI_SERVICE_URL)

- Credenciales externas
- Configuración de base de datos

**son gestionadas mediante variables de entorno en el despliegue.**

## 🚀 4. Despliegue en Render

**El sistema se despliega en Render usando contenedores Docker. Existen dos formas:**

✔️ Opción recomendada: Automática (Blueprint)
Se usa un archivo render.yaml
Render detecta servicios automáticamente
Despliega los 4 servicios:
- gateway
- state
- ui
- ai
>⚙️Opción manual:< 

Se crean servicios individuales en Render:

- Tipo: Web Service
- Entorno: Docker
- Conectado a GitHub

**Cada servicio requiere variables específicas**

>Gateway:< 
- STATE_SERVICE_URL
- UI_SERVICE_URL
- AI_SERVICE_URL
- Otros servicios:
- Bases de datos (PostgreSQL, MongoDB)
- APIs externas (Gemini, Groq)
- OAuth (Google, GitHub)

## 🌐 5. Dominio y Acceso

**El gateway expone el sistema completo a través de un dominio personalizado:**

- Dominio: slide.lat
- Configurado mediante CNAME en DNS
- Todas las peticiones pasan por el gateway

## 📊 6. Monitoreo y Salud

>Cada servicio incluye endpoints de monitoreo:<

- /actuator/health

**Permite verificar:**

1. Estado del servicio
2. Conectividad con dependencias
3. Logs disponibles en Render para depuración.

## ⚠️ 7. Problemas comunes
❌ Error de build → fallas en Maven
❌ Conexión rechazada → URLs mal configuradas
❌ Timeout DB → credenciales incorrectas
❌ MongoDB error → URI incompleta
✅ 8. Conclusión

## 8. Conclusíon

**El Gateway Service es un componente clave en la arquitectura de SlideHub, ya que centraliza el acceso, mejora la seguridad y facilita la comunicación entre microservicios. Su despliegue en Render permite escalar fácilmente el sistema y mantener una configuración flexible mediante variables de entorno.**

