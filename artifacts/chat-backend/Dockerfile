# =============================================================
#  Multi-stage Dockerfile for Spring Boot Chat Backend
#  Stage 1: Build the JAR using Maven
#  Stage 2: Run the JAR in a minimal JRE image
#
#  Multi-stage builds minimize the final image size by excluding
#  Maven, source code, and build tools from the production image.
# =============================================================

# ---- Stage 1: Build ----
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first to leverage Docker layer caching.
# Dependencies are downloaded only when pom.xml changes.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies (cached layer if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build the JAR
# -DskipTests: tests run in CI pipeline, not in Docker build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user for security (never run as root in production)
RUN addgroup -S chatapp && adduser -S chatapp -G chatapp
USER chatapp

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Health check for Docker/K8s liveness probes
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers:
# - UseContainerSupport: respect Docker CPU/memory limits (JDK 10+)
# - MaxRAMPercentage: use 75% of the container's RAM for the heap
# - ExitOnOutOfMemoryError: crash fast instead of limping (K8s will restart)
# - PrintCommandLineFlags: log JVM flags at startup for debugging
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
