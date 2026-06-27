# ============================================================
# Multi-stage Dockerfile for TAUT Backend (Kotlin / Ktor)
# Stage 1 — Build with Gradle + JDK 17
# Stage 2 — Run on slim JRE 17
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM gradle:8.7-jdk17-alpine AS builder

WORKDIR /app

# Cache Gradle dependencies (layer is reused when source changes)
COPY backend/gradle.properties backend/settings.gradle.kts ./
COPY backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle --no-daemon dependencies || true

# Copy source code
COPY backend/src ./src

# Build the fat JAR
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle --no-daemon clean build -x test

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Health-check tool
RUN apk add --no-cache curl

# Non-root user
RUN addgroup -S taut && adduser -S taut -G taut

WORKDIR /app

# Copy the fat JAR from builder
COPY --from=builder /app/build/libs/taut-backend-*-all.jar app.jar

RUN chown -R taut:taut /app
USER taut

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
