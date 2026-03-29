# Multi-stage Dockerfile para ai-service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

# Compilar ai-service (skip test compilation — tests run in CI, not Docker)
RUN ./mvnw clean package -pl ai-service -am -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/ai-service/target/ai-service-*.jar app.jar

LABEL service="ai-service" version="1.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8083/actuator/health || exit 1

EXPOSE 8083

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
