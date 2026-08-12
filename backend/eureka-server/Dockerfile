# syntax=docker/dockerfile:1
# Day 11 — eureka-server: multi-stage build (Maven build -> slim JRE runtime).
# Build context is ./backend so the parent POM is available for the reactor.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Parent POM + module POM first for dependency-layer caching.
COPY pom.xml ./pom.xml
COPY eureka-server/pom.xml ./eureka-server/pom.xml
COPY eureka-server/src ./eureka-server/src
RUN mvn -f eureka-server/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
# curl is required by HEALTHCHECK (the JRE image ships neither curl nor wget).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Run as a non-root user.
RUN groupadd -r careq -g 10001 && useradd -r -g careq -u 10001 careq
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/*.jar app.jar
USER careq
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8761/actuator/health || exit 1
# Day 15: JAVA_OPTS (e.g. -Xmx/-XX:MaxMetaspaceSize) can be injected via env —
# used on Azure Container Apps to cap the JVM heap on small consumption profiles.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
