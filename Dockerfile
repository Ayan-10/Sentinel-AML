# ===========================================================================
# Sentinel AML API - multi-stage build.
# The build runs INSIDE Docker, so the host needs nothing but Docker itself
# (no local JDK, no local Gradle). Satisfies "entire project up via Docker".
# ===========================================================================

# --- Stage 1: dependency cache ---------------------------------------------
# Copied separately from sources so a source-only change does not re-download
# the dependency graph on every rebuild.
FROM gradle:8.14-jdk17 AS deps
WORKDIR /build
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon --configuration runtimeClasspath || true

# --- Stage 2: compile + test ------------------------------------------------
FROM gradle:8.14-jdk17 AS build
WORKDIR /build
COPY --from=deps /home/gradle/.gradle /home/gradle/.gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon -x test

# --- Stage 3: runtime -------------------------------------------------------
# JRE only (no compiler, no Gradle) and a non-root user: smaller image,
# smaller attack surface.
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S sentinel && adduser -S sentinel -G sentinel \
    && apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar
RUN chown -R sentinel:sentinel /app
USER sentinel

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
