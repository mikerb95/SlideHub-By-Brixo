# Multi-stage Dockerfile para state-service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

# Compilar state-service (skip test compilation — tests run in CI, not Docker)
RUN ./mvnw clean package -pl state-service -am -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/state-service/target/state-service-*.jar app.jar

LABEL service="state-service" version="1.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
