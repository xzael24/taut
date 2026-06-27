# ==============================================================================
# Dockerfile for TAUT Backend — Multi-stage build
# Stage 1: Build with Gradle 8.11.1 + JDK 21 (openjdk:21-jdk-slim)
# Stage 2: Run with JRE 21 (openjdk:21-jre-slim)
# ==============================================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM openjdk:21-jdk-slim AS builder

WORKDIR /app

# Install dependencies needed by the Gradle wrapper
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Copy Gradle wrapper files first (for layer caching)
COPY gradlew gradlew.bat ./
COPY gradle/ ./gradle/

# Make gradlew executable
RUN chmod +x gradlew

# Copy build configuration (for dependency caching)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Pre-download Gradle wrapper and dependencies
RUN ./gradlew --no-daemon dependencies || true

# Copy source code
COPY src/ ./src/

# Build the application fat JAR
RUN ./gradlew --no-daemon clean build -x test

# ── Stage 2: Runtime ────────────────────────────────────────
FROM openjdk:21-jre-slim

WORKDIR /app

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN addgroup --system --gid 1001 taut && \
    adduser --system --uid 1001 --ingroup taut taut

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*-all.jar /app/backend-all.jar

# Set ownership and permissions
RUN chown -R taut:taut /app && \
    chmod 755 /app/backend-all.jar

USER taut

# Expose Ktor HTTP + gRPC port
EXPOSE 9000

# Environment variables (overridable at runtime)
ENV TAUT_JWT_SECRET=""
ENV TAUT_DB_PASSWORD=""
ENV TAUT_DB_URL="jdbc:postgresql://localhost:5432/taut"
ENV TAUT_DB_USER="taut"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:9000/health || exit 1

# Run the application
CMD ["java", "-jar", "/app/backend-all.jar"]