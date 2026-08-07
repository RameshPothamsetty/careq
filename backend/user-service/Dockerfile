# syntax=docker/dockerfile:1
# Day 11 — user-service: multi-stage build (Maven build -> slim JRE runtime).
# Build context is ./backend so the parent POM is available for the reactor.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
COPY user-service/src ./user-service/src
RUN mvn -f user-service/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r careq -g 10001 && useradd -r -g careq -u 10001 careq
WORKDIR /app
COPY --from=build /workspace/user-service/target/*.jar app.jar
# Profile pictures are persisted on a named volume mounted at /app/uploads.
# Pre-create it so Docker seeds the volume with careq ownership on first
# mount — otherwise the root-owned empty volume blocks the non-root user.
RUN mkdir -p /app/uploads && chown -R careq:careq /app/uploads
USER careq
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
