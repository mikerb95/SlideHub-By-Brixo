# FASE-1-RESUMEN — SlideHub

> **Documento educativo sobre Fase 1**  
> Resumen de: Autenticación local + OAuth2 (GitHub, Google) + persistencia en PostgreSQL

**Fecha:** 27 de febrero de 2026  
**Duración:** Completado en una sesión  
**Estado:** ✅ BUILD SUCCESS (todos los módulos compilados)

---

## 1. ¿Qué era Fase 1?

Extiende el **estado sin autenticación** de Fase 0 con:

- **Login local seguro** — usuarios registrados con contraseña (BCrypt)
- **OAuth2 dual** — GitHub y Google coexisten sin reemplazarse
- **Persistencia de usuarios** — PostgreSQL (Aiven en producción, H2 en desarrollo)
- **Email transaccional** — Resend API para confirmación de cuenta
- **Almacenamiento de slides** — Amazon S3 (nunca en filesystem de Render)
- **Gestión de tokens OAuth** — almacenamiento seguro de access tokens para integración con APIs

### Antes (Fase 0)
```
Flujo: Visitor → [GET /slides] → Proyector (sin autenticación)
        + [GET /api/slide] → polling de estado
        + NO hay concepto de "usuario"
```

### Después (Fase 1)
```
Flujo: Visitor → [GET /auth/login] → (local OR GitHub OR Google)
        → [POST /auth/login | /oauth2/authorization/{provider}]
        → Session creada → [GET /presenter] ← panel protegido del presentador
        → [GET /auth/profile] ← gestión de cuentas vinculadas
        → [GET /auth/verify?token=...] ← confirmación de email
```

---

## 2. Stack Fase 1

### Nuevas dependencias agregadas al `pom.xml`

```xml
<!-- Base de datos relacional — Aiven PostgreSQL en prod -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
</dependency>

<!-- Migraciones de esquema — Flyway -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- OAuth2 (GitHub + Google) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Amazon S3 (AWS SDK v2 — única excepción al patrón WebClient-only) -->
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
</dependency>

<!-- H2 para testing local sin PostgreSQL levantado -->
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>runtime</scope>
</dependency>
```

### Versiones clave
- **Java 21** (sin cambios)
- **Spring Boot 4.0.3** (sin cambios)
- **AWS SDK v2:** `2.29.52` (manejado globalmente por BOM en parent `pom.xml`)
- **PostgreSQL:** driver estándar `org.postgresql:postgresql` (versión gestionada por Boot)
- **Flyway:** incluido en Boot 4.0.3

---

## 3. Modelo de datos: Tabla `users`

### Estructura (Flyway V1__create_users.sql)

```sql
CREATE TABLE users (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255),  -- NULL para cuentas OAuth2-only
  role VARCHAR(20) NOT NULL DEFAULT 'PRESENTER',
  email_verified BOOLEAN DEFAULT FALSE,
  email_verification_token VARCHAR(36),
  
  -- GitHub OAuth
  github_id VARCHAR(100) UNIQUE,
  github_username VARCHAR(100),
  github_access_token TEXT,
  
  -- Google OAuth
  google_id VARCHAR(100) UNIQUE,
  google_email VARCHAR(255),
  google_refresh_token TEXT,
  
  created_at TIMESTAMP DEFAULT NOW()
);
```

### Decisión arquitectónica: Cuentas "overlap"

Un mismo usuario puede ser creado de **múltiples formas** y coexistir:

```
Escenario 1: Local → OAuth local
  User[@email="mike@example.com"]
    ├─ password_hash="$2a$10..."
    ├─ github_id=null
    └─ google_id=null

Escenario 2: GitHub → vinculado a Google después
  User[@github_id="12345678"]
    ├─ password_hash=null (solo OAuth)
    ├─ github_username="mikedev"
    ├─ github_access_token="gho_abc123..."
    ├─ google_id="109876543210987654321"
    └─ google_email="mike@gmail.com"

Escenario 3: "Merge automático" por email
  User[@email="shared@example.com"] creado vía GitHub
    → Luego intenta login vía Google
    → `CustomOAuth2UserService` detecta email IGUAL → VINCULA ambos
    → ahora tiene github_id + google_id en el mismo registro
```

**Ventaja:** El usuario puede cambiar de proveedor, mantener la misma cuenta, elegir cómo autenticarse.

---

## 4. Flujos de autenticación

### 4.1 Login local (formulario tradicional)

```
[GET /auth/login]  (sin sesión)
    ↓
[HTML form: username + password]
    ↓
[POST /auth/login]  (procesa Spring Security)
    ↓
CustomUserDetailsService.loadUserByUsername(username)
    ↓ (busca en PostgreSQL)
SecurityConfig.authenticationProvider() → BCrypt check
    ↓
¿PasswordEncoder.matches(raw, hash) == true ?
    YES: Session creada, redirect a /presenter
    NO:  Formulario nuevamente con error genérico
        "Credenciales incorrectas. Inténtalo de nuevo."
        (NO dice "usuario no existe" ni "contraseña mala" — HU-001 §2)
```

### 4.2 OAuth2 (GitHub)

```
[GET /auth/login] → botón "Continuar con GitHub"
    ↓
[BROWSER REDIRECT] → /oauth2/authorization/github
    ↓ (Spring Security)
GitHub OAuth endpoint: https://github.com/login/oauth
    ↓ (usuario permite acceso)
[CALLBACK] → /login/oauth2/code/github?code=...&state=...
    ↓
Spring Security intercepts, intercambia code por access_token
    ↓
CustomOAuth2UserService.loadUser()
    │
    ├─ ¿existe User con github_id?
    │   YES → actualiza github_access_token, retorna User
    │
    ├─ ¿existe User con email=github.email?
    │   YES → vincula github_id a cuenta existente
    │
    └─ NO → crea User nuevo con role=PRESENTER
    
Session creada, redirect a /presenter
```

### 4.3 OAuth2 (Google)

Similar a GitHub, con diferencias:

- **Scopes:** `openid,profile,email` (acceso a Google Drive, implementado en Fase 2)
- **ID attribute:** `sub` (no `login` como GitHub)
- **Email siempre verificado** — Google lo garantiza

---

## 5. Componentes principales

### `User.java` — Entidad JPA

**No es un record** — JPA requiere:
- Mutabilidad (setters para JPA)
- Constructor no-arg (reflexión)
- `@Id` y `@Column` decoradores

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;  // UUID como string
    
    private String username;     // único, 3-50 caracteres
    private String email;        // único, verificable
    private String passwordHash; // null si OAuth2-only
    private Role role;           // PRESENTER | ADMIN
    
    // OAuth GitHub
    private String githubId;           // único
    private String githubUsername;     // @username
    private String githubAccessToken;  // token para GitHub API
    
    // OAuth Google
    private String googleId;            // único, sub claim
    private String googleEmail;         // copia por facilidad
    private String googleRefreshToken;  // para refrescar token expirado
    
    // Verificación
    private boolean emailVerified;
    private String emailVerificationToken;  // UUID temporal
    
    private LocalDateTime createdAt;
}
```

### `UserRepository.java` — JPA Repository

```java
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByGithubId(String githubId);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmailVerificationToken(String token);
}
```

Consultas **generadas automáticamente** por Spring Data. No SQL manual.

### `UserService.java` — Lógica de negocio

```java
@Service
public class UserService {
    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent())
            throw new UserAlreadyExistsException(...);
        
        String hash = passwordEncoder.encode(rawPassword);  // BCrypt
        String verifyToken = UUID.randomUUID().toString();
        
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setRole(Role.PRESENTER);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(verifyToken);
        
        User saved = userRepository.save(user);
        emailService.send(email, "Confirma tu cuenta", "...");
        return saved;
    }
    
    @Transactional
    public Optional<User> verifyEmail(String token) {
        return userRepository.findByEmailVerificationToken(token)
            .map(user -> {
                user.setEmailVerified(true);
                user.setEmailVerificationToken(null);
                return userRepository.save(user);
            });
    }
}
```

### `CustomUserDetailsService.java` — Spring Security adapter

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) 
            throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(...));
        
        String password = user.getPasswordHash() != null
            ? user.getPasswordHash()
            : "{noop}__oauth2_only__";  // Sin contraseña → no puede hacer login local
        
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            password,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}
```

**Truco:** Si `password_hash` es null (OAuth2-only), Spring Security lo marca como "{noop}" inválido, bloqueando el login local.

### `CustomOAuth2UserService.java` — OAuth2 processor

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) 
            throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(request);
        String provider = request.getClientRegistration()
            .getRegistrationId();  // "github" | "google"
        
        User user = switch (provider) {
            case "github" -> processGithubUser(request, oauth2User);
            case "google" -> processGoogleUser(oauth2User);
            default -> throw new ...;
        };
        
        return new DefaultOAuth2User(..., oauth2User.getAttributes(), nameAttr);
    }
    
    private User processGithubUser(...) {
        // 1. ¿ya existe por github_id? → actualiza token
        // 2. ¿ya existe por email? → vincula github_id
        // 3. sino → crea cuenta nueva
    }
}
```

### `EmailService.java` — Resend API (sin SDK)

```java
@Service
public class EmailService {
    public void send(String to, String subject, String html) {
        resendClient.post()
            .uri("https://api.resend.com/emails")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "from", "noreply@slidehub.app",
                "to", List.of(to),
                "subject", subject,
                "html", html
            ))
            .retrieve()
            .bodyToMono(Void.class)
            .block();
    }
}
```

**Ventaja de WebClient puro:** control total, sin dependencias de terceros, fácil mockear en tests.

### `SlideUploadService.java` — AWS S3 (SDK v2)

```java
@Service
public class SlideUploadService {
    private final S3Client s3;  // inyectado por S3Config
    
    public String upload(String key, byte[] data, String contentType) {
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(data)
        );
        return "https://%s.s3.%s.amazonaws.com/%s"
            .formatted(bucket, region, key);
    }
}
```

**Nota:** AWS SDK v2 es la **única excepción** al patrón WebClient-only, porque requiere firma **SigV4** automática (muy compleja de hacer manualmente).

### `SecurityConfig.java` — Spring Security 6.x configuración

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .authenticationProvider(authenticationProvider())  // local
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/slides", "/remote", "/demo", "/showcase")
                    .permitAll()
                .requestMatchers("/auth/**", "/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                .requestMatchers("/presenter", "/main-panel")
                    .hasAnyRole("PRESENTER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .defaultSuccessUrl("/presenter", true)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/auth/login")
                .defaultSuccessUrl("/presenter", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService)  // CustomOAuth2UserService
                )
            )
            .logout(...);
        return http.build();
    }
    
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        return new DaoAuthenticationProvider(userDetailsService);
        // Spring Boot 6.x: constructor requiere UserDetailsService
    }
}
```

---

## 6. Vistas creadas/actualizadas

### `auth/register.html` — Formulario funcional Fase 1

**Antes (Fase 0):** Formulario deshabilitado con aviso "Fase 1".

**Después:**
- Input de username, email, password, confirmación
- Validación HTML5 (minlength, required)
- Botones OAuth2: GitHub + Google
- Mensajes de error (a nivel de usuario en caso de fallo)

```html
<form method="post" th:action="@{/auth/register}">
    <input type="text" name="username" required>
    <input type="email" name="email" required>
    <input type="password" name="password" minlength="8" required>
    <input type="password" name="confirmPassword" minlength="8" required>
    <button type="submit">Crear cuenta</button>
</form>

<div class="separator">o continúa con</div>

<a href="/oauth2/authorization/github" class="btn btn-github">
    GitHub
</a>
<a href="/oauth2/authorization/google" class="btn btn-google">
    Google
</a>
```

### `auth/profile.html` — Panel de gestión de cuenta (nueva)

```
┌─────────────────────────────────┐
│ Username                 PRESENTER │
│ email@example.com                 │
├─────────────────────────────────┤
│ EMAIL                             │
│ ✓ Verificado                     │
├─────────────────────────────────┤
│ PROVEEDORES VINCULADOS            │
│ GitHub        → [✓ Vinculado]     │
│ Google        → [Vincular]        │
├─────────────────────────────────┤
│ [Volver]  [Cerrar sesión]        │
└─────────────────────────────────┘
```

---

## 7. Configuración Fase 1

### `application.properties` (desarrollo)

```properties
# Base de datos H2 en modo PostgreSQL (no requiere PostgreSQL levantado)
spring.datasource.url=jdbc:h2:mem:slidehub;MODE=PostgreSQL;...
spring.datasource.driver-class-name=org.h2.Driver

# OAuth2 GitHub
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET}
spring.security.oauth2.client.registration.github.scope=read:user,user:email

# OAuth2 Google
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email

# Resend
slidehub.resend.api-key=${RESEND_API_KEY}

# AWS S3
aws.s3.bucket=${AWS_S3_BUCKET}
aws.s3.region=${AWS_REGION}
aws.access-key-id=${AWS_ACCESS_KEY_ID}
aws.secret-access-key=${AWS_SECRET_ACCESS_KEY}
```

### `application-prod.properties` (Aiven)

```properties
# DATABASE_URL viene de Aiven con SSL incluido
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
```

---

## 8. Decisiones técnicas de Fase 1

| Decisión | Justificación |
|----------|---|
| **H2 en modo PostgreSQL para dev** | Replica el esquema exacto de Aiven sin necesidad de levantar PostgreSQL |
| **UUID como string en User.id** | Facilita generación sin auto-increment; portabilidad entre BD |
| **passwordHash nullable** | Soporta cuentas OAuth2-only (sin contraseña local) |
| **Email verificable con token temporal** | Resend envía URL, usuario hace click, verifica automáticamente |
| **AWS SDK v2 (no WebClient)** | SigV4 es complejo; SDK lo maneja, justificado por el caso de uso |
| **CustomOAuth2UserService vs FilterChain** | Centralizar lógica de provisioning en un lugar, no distribuir en Spring Security |
| **DaoAuthenticationProvider inyectado por constructor** | Spring Security 6.x cambió API; constructor es el patrón recomendado |

---

## 9. Variables de entorno requeridas (resumen)

| Variable | Ejemplo | Dónde obtener |
|----------|---------|---|
| `GITHUB_CLIENT_ID` | `Ov23liXz...` | GitHub → Settings → Developer settings → OAuth Apps |
| `GITHUB_CLIENT_SECRET` | `9fc4a6e7...` | GitHub OAuth App settings |
| `GOOGLE_CLIENT_ID` | `123456789-abc...apps.googleusercontent.com` | Google Cloud Console → Credentials |
| `GOOGLE_CLIENT_SECRET` | `GOCSPX-...` | Google OAuth configuration |
| `RESEND_API_KEY` | `re_...` | Resend dashboard → API keys |
| `AWS_ACCESS_KEY_ID` | `AKIA...` | AWS SES console |
| `AWS_SECRET_ACCESS_KEY` | `...` | AWS SES console |
| `AWS_S3_BUCKET` | `slidehub-assets` | AWS S3 → bucket name |
| `DATABASE_URL` | `jdbc:postgresql://...` | Aiven database URL (si estás en producción) |

---

## 10. Pruebas locales sin env vars configuradas

El proyecto usa valores por defecto ("changeme") — las rutas OAuth2 fallarán si no configuras las env vars, pero **el resto de la app levanta sin error**:

```bash
# Desarrollo local — sin OAuth2
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl ui-service

# Puedes:
✓ GET /slides (público)
✓ GET /auth/login (público)
✗ POST /auth/register → falla si no hay BD
✗ /oauth2/authorization/github → 401 client not configured
```

Para **tests con OAuth2**, necesitas las variables de entorno:

```bash
export GITHUB_CLIENT_ID=...
export GITHUB_CLIENT_SECRET=...
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
./mvnw spring-boot:run -pl ui-service
```

---

## 11. Flujo completo de un nuevo usuario (end-to-end)

### Escenario: Ana crea cuenta local + verifica email

```
1. Ana abre http://localhost:8082/auth/register
2. Completa:
   - Username: ana_dev
   - Email: ana@example.com
   - Password: MiContraseña123
3. Click "Crear cuenta" → POST /auth/register
4. UserService.registerUser() valida:
   - username único? SÍ
   - email único? SÍ
   - encripta password con BCrypt
   - genera token verificación: UUID
   - guarda User en PostgreSQL
5. EmailService.send() envía:
   To: ana@example.com
   Subject: Confirma tu cuenta en SlideHub
   Body: <a href="http://localhost:8082/auth/verify?token=abc123">
           Confirmar cuenta
         </a>
6. Ana abre email, hace click en enlace
7. GET /auth/verify?token=abc123
   → UserService.verifyEmail(token) marca emailVerified=true
   → redirect a /auth/login con mensaje "Email verificado ¡Bienvenida!"
8. Ana ingresa username + password
   → Spring Security → CustomUserDetailsService.loadUserByUsername()
   → BCrypt.matches() ✓
   → Session creada
   → redirect a /presenter
9. Ana ve:
   - Slide actual
   - Botón "Mi Perfil"
   - Controles de navegación
```

### Escenario: Bob usa GitHub OAuth

```
1. Bob abre http://localhost:8082/auth/register
2. Click "Continuar con GitHub"
3. Redirect a /oauth2/authorization/github
4. Spring Security intercepta, redirige a:
   https://github.com/login/oauth/authorize?client_id=...
5. Bob autoriza ("SlideHub quiere acceso a tu perfil")
6. GitHub redirige a:
   http://localhost:8082/login/oauth2/code/github?code=...
7. Spring Security intercambia code por access_token
8. CustomOAuth2UserService.loadUser() ejecuta:
   - githubId=98765432 (obtenido del perfil)
   - user = userRepository.findByGithubId(98765432)
   - NO existe → crea User nuevo:
     * username = "bob_ghc" (generado único)
     * email = "bob@github.com"
     * githubId = 98765432
     * githubUsername = "bob_dev"
     * githubAccessToken = "gho_Xabc123..." (guardado)
     * role = PRESENTER
9. Session creada → redirect a /presenter
10. Bob puede ver /auth/profile → muestra:
    - GitHub: ✓ Vinculado (@bob_dev)
    - Google: Vincular
```

---

## 12. Cómo continuar a Fase 2

Fase 2 agregará:

1. **Google Drive Import** — usar `googleAccessToken` almacenado para listar y descargar slides
2. **Modelo Presentation** — tabla JPA con slides y metadata
3. **Uploads a S3** — usar `SlideUploadService` para almacenar files de usuario
4. **Endpoint `/api/presentations/create-from-drive`** — orquestar el flujo completo

Todos los componentes base están listos:
- ✅ `UserService` — el usuario está autenticado y verificado
- ✅ `SlideUploadService` — S3 está configurado y funcional
- ✅ `CustomOAuth2UserService` — tokens de OAuth2 almacenados

---

## 13. Cambios en la tabla de vistas del gateway

| Ruta | Vista | Acceso | Para |
|------|-------|--------|------|
| `/auth/login` | login.html | Público | Formulario de login (local + OAuth2) |
| `/auth/register` | register.html | Público | Registro de nuevas cuentas |
| `/auth/verify` | (redirect) | Público | Verificar email con token |
| `/auth/profile` | profile.html | PRESENTER | Gestión de cuentas vinculadas |
| `/auth/logout` | (POST) | Autenticado | Cerrar sesión |
| `/oauth2/authorization/github` | (gateway route) | Público | Iniciar flujo GitHub OAuth2 |
| `/oauth2/authorization/google` | (gateway route) | Público | Iniciar flujo Google OAuth2 |
| `/login/oauth2/code/{provider}` | (gateway route) | Público | Callback de OAuth2 provider |

---

## 14. Resumen de archivos creados

### Java (11 nuevos)

```
ui-service/src/main/java/com/brixo/slidehub/ui/

model/
  Role.java ........................... enum (PRESENTER, ADMIN)
  User.java ........................... @Entity JPA con campos OAuth

repository/
  UserRepository.java ................. JpaRepository

exception/
  UserAlreadyExistsException.java ...... dominio

service/
  EmailService.java ................... Resend HTTP WebClient
  UserService.java .................... registrar + verificar email
  CustomUserDetailsService.java ........ Spring Security UserDetailsService
  CustomOAuth2UserService.java ......... OAuth2 processor (GitHub/Google)
  SlideUploadService.java ............. S3 upload/delete

config/
  S3Config.java ....................... S3Client bean (AWS SDK v2)
  SecurityConfig.java (actualizado) ... OAuth2 + form login configurados
```

### SQL (1 nuevo)

```
ui-service/src/main/resources/db/migration/
  V1__create_users.sql ................ tabla users + constraints
```

### HTML (2 nuevos, 1 actualizado)

```
ui-service/src/main/resources/templates/auth/

register.html (actualizado) .......... formulario funcional + OAuth2 buttons
profile.html (nuevo) ................. panel de gestión de cuentas
```

### Properties (2 nuevos, 1 actualizado)

```
ui-service/src/main/resources/

application.properties (actualizado) .. JPA, OAuth2, Resend, S3
application-prod.properties (nuevo) ... PostgreSQL/Aiven config
```

---

## 15. BUILD STATUS

```bash
$ mvn clean compile

[INFO] slidehub-parent ......................... SUCCESS
[INFO] state-service .......................... SUCCESS
[INFO] ui-service ............................ SUCCESS
[INFO] ai-service ............................ SUCCESS
[INFO] gateway-service ....................... SUCCESS
[INFO] BUILD SUCCESS
```

**Todos los módulos compilados exitosamente.** Fase 1 = ready para Fase 2.

---

## 16. Próximas acciones

1. **En tu local:** Configura env vars para GitHub/Google OAuth si quieres probar el flujo completo
2. **En Render:** Configura las 8 variables de entorno en cada Web Service
3. **Para Fase 2:** Empieza con modelo `Presentation` y endpoint de upload a Google Drive

---

*Completado: 27 de febrero de 2026 — Fase 1 ✅*

