# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache dependencies: copy the build scripts + wrapper first
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Build the application
COPY src ./src
RUN ./gradlew --no-daemon -x test bootJar

# ---- Runtime stage (ARM64 / Graviton-friendly) ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Non-root user
RUN groupadd --system ims && useradd --system --gid ims --home /app ims
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/reports && chown -R ims:ims /app
USER ims

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
