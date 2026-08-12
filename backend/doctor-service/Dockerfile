# syntax=docker/dockerfile:1
# Day 11 — doctor-service: multi-stage build (Maven build -> slim JRE runtime).
# Build context is ./backend so the parent POM is available for the reactor.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY doctor-service/pom.xml ./doctor-service/pom.xml
COPY doctor-service/src ./doctor-service/src
RUN mvn -f doctor-service/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r careq -g 10001 && useradd -r -g careq -u 10001 careq
WORKDIR /app
COPY --from=build /workspace/doctor-service/target/*.jar app.jar
USER careq
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8083/actuator/health || exit 1
# Day 15: JAVA_OPTS (e.g. -Xmx/-XX:MaxMetaspaceSize) can be injected via env —
# used on Azure Container Apps to cap the JVM heap on small consumption profiles.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
