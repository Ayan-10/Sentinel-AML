# ===========================================================================
# Sentinel AML API — multi-stage build.
# The build runs INSIDE Docker, so the host needs nothing but Docker itself
# (no local JDK, no local Gradle).
#
# Base images are the Ubuntu-based (jammy) Temurin variants rather than the
# alpine ones: the alpine tags publish no arm64 manifest, so they fail outright
# on Apple Silicon and on ARM CI runners. Jammy is multi-architecture.
#
# The build stage invokes the Gradle *wrapper*, not a Gradle bundled in the
# image, so the container compiles with exactly the version declared in
# gradle/wrapper/gradle-wrapper.properties. A bundled Gradle would silently
# build with a different version than a developer runs locally.
# ===========================================================================

# --- Stage 1: compile ------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build

# Wrapper + build scripts first, so the dependency layer is cached separately
# and a source-only change does not re-resolve the whole dependency graph.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Stage 2: runtime ------------------------------------------------------
# JRE only (no compiler, no Gradle) and a non-root user: smaller image,
# smaller attack surface.
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system sentinel \
    && useradd --system --gid sentinel --create-home sentinel

WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar
RUN chown -R sentinel:sentinel /app
USER sentinel

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=15 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
