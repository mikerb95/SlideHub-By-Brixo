# DEPLOYMENT.md — SlideHub Monolito en Render

## Propiedad y licencia

Este repositorio es **código cerrado** y se publica en GitHub solo para visibilidad y referencia.

- Licencia: **All Rights Reserved**
- No se permite usar, copiar, modificar, redistribuir, sublicenciar ni comercializar el código sin autorización escrita previa.
- La publicación pública del repositorio no concede permisos de uso más allá de los expresamente otorgados por el titular.

## Requisitos previos

- Dominio opcional (ej. `slide.lat`)
- Cuenta en Render
- PostgreSQL (Aiven)
- MongoDB Atlas
- Redis (Upstash/Redis Cloud)
- API keys: Gemini + Groq
- OAuth2: GitHub + Google
- S3: bucket + credenciales IAM
- Resend (si usas email)

---

## Opción recomendada: Blueprint (`render.yaml`)

### 1) Preparar repo

```bash
git add .
git commit -m "Monolith deploy blueprint"
git push origin development
```

### 2) Crear servicio desde Blueprint

1. Render Dashboard → **Blueprints**
2. **New from Blueprint**
3. URL del repo
4. Render detecta `render.yaml`
5. Configura variables `sync: false`
6. Deploy

Resultado esperado:

- 1 Web Service: `slidehub-service`
- Health check: `/actuator/health`
- Puerto: `8080`

---

## Variables de entorno requeridas

### Core

```env
SPRING_PROFILES_ACTIVE=prod
BASE_URL=https://slide.lat
```

### PostgreSQL (Aiven)

```env
DATABASE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require
DB_DRIVER=org.postgresql.Driver
JPA_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

### Redis

```env
REDIS_HOST=<host>
REDIS_PORT=6379
```

### MongoDB

```env
MONGODB_URI=mongodb+srv://<user>:<pass>@<cluster>/<db>?retryWrites=true
```

### OAuth2

```env
GITHUB_CLIENT_ID=<id>
GITHUB_CLIENT_SECRET=<secret>
GOOGLE_CLIENT_ID=<id>
GOOGLE_CLIENT_SECRET=<secret>
```

### IA

```env
GEMINI_API_KEY=<key>
GROQ_API_KEY=<key>
GROQ_MODEL=llama3-8b-8192
```

### Email

```env
RESEND_API_KEY=<key>
```

### S3

```env
AWS_S3_BUCKET=slidehub-assets
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>
```

---

## Dominio personalizado

En Render (`slidehub-service`) → Settings → Custom Domains:

- Agregar dominio `slide.lat`

Actualizar DNS (Namecheap o proveedor):

- CNAME / A según indicación de Render

Verificar:

```bash
nslookup slide.lat
```

---

## OAuth2 callbacks

### GitHub

```text
https://slide.lat/login/oauth2/code/github
```

### Google

```text
https://slide.lat/login/oauth2/code/google
```

---

## Verificación post-deploy

### Health

- `https://slide.lat/actuator/health`

### Flujos mínimos

- Login local: `/auth/login`
- Presenter: `/presenter`
- Remote: `/remote`
- Slides: `/slides`
- Notes IA: `/presentations/{id}/generate-notes`

### Logs

Render Dashboard → Service → Logs

Buscar `ERROR`, `Exception`, `Flyway`, `Mongo`, `Redis`, `OAuth2`.

---

## Troubleshooting

### Build falla en Render

```bash
./mvnw clean compile -pl slidehub-service -am
```

Si falla local, fallará en Render.

### Error DB / Flyway

- Revisar `DATABASE_URL`
- Confirmar SSL (`sslmode=require`)
- Verificar credenciales y base

### Error MongoDB timeout

- Validar `MONGODB_URI`
- Permitir IP de Render en Atlas (si aplica)

### Error Redis connection

- Validar `REDIS_HOST`/`REDIS_PORT`
- Probar conectividad del proveedor Redis

### OAuth2 redirect mismatch

- Revisar callbacks exactos en GitHub/Google
- Confirmar `BASE_URL`

---

## Rollback

Render → Deployments → seleccionar despliegue anterior → Redeploy.

---

## Nota histórica

La arquitectura previa de 4 microservicios se conserva en el repo como legado, pero el despliegue actual recomendado es **monolito único**.