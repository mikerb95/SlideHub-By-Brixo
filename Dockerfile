# Multi-stage Dockerfile para gateway-service
# Build stage: compila el módulo específico
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copiar todo el monorepo (necesitamos el pom.xml raíz + gateway y sus deps)
COPY . .

# Compilar y empaquetar SOLO gateway-service
# -pl selecciona el módulo, -am agrega sus dependencias
RUN ./mvnw clean package -pl gateway-service -am -Dmaven.test.skip=true -q

# Runtime stage: imagen mínima, solo JRE + JAR compilado
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar el JAR compilado desde el stage anterior
COPY --from=builder /workspace/gateway-service/target/gateway-service-*.jar app.jar

# Metadatos
LABEL service="gateway-service" version="1.0"

# Healthcheck (Render espera que el servicio responda en /actuator/health)
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Puerto
EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
