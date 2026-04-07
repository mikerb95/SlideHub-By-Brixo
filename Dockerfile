# Root Dockerfile fallback for Render Docker deploys
# Builds and runs the monolith module located in slidehub-service/
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

RUN ./mvnw clean package -pl slidehub-service -am -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/slidehub-service/target/slidehub-service-*.jar app.jar

LABEL service="slidehub-service" version="1.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-Dspring.profiles.active=prod", "-jar", "app.jar"]