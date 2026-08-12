# syntax=docker/dockerfile:1
# Day 13 — notification-service: multi-stage build (Maven build -> slim JRE runtime).
# Build context is ./backend so the parent POM is available for the reactor.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY notification-service/src ./notification-service/src
RUN mvn -f notification-service/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r careq -g 10001 && useradd -r -g careq -u 10001 careq
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar app.jar
USER careq
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8085/actuator/health || exit 1
# Day 15: JAVA_OPTS (e.g. -Xmx/-XX:MaxMetaspaceSize) can be injected via env —
# used on Azure Container Apps to cap the JVM heap on small consumption profiles.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
