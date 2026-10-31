# =====================================================
# Build Stage - Compile Java Application with Modules
# =====================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -B

# Copy entire project
COPY src src

# Build application (skip tests for faster build)
RUN mvn clean package -DskipTests -q
RUN test -f target/classes/com/ebike/adminModule/controller/AdminController.class

# =====================================================
# Runtime Stage - Run Application
# =====================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install dependencies
RUN apk add --no-cache \
    curl \
    bash \
    git \
    python3 \
    py3-pip \
    && rm -rf /var/cache/apk/*

# Create directories for volumes
RUN mkdir -p /app/logs \
    && mkdir -p /app/ml-models \
    && mkdir -p /app/faq-data

# Copy JAR from builder
COPY --from=builder /app/target/ebike-backend-*.jar app.jar

# Copy ML models
COPY src/main/resources/ml-models/ /app/ml-models/

# Copy FAQ data
COPY src/main/resources/faq-data/ /app/faq-data/

# Create health check script
RUN echo '#!/bin/sh' > /healthcheck.sh && \
    echo 'curl -f http://localhost:8080/api/v1/health/ready || exit 1' >> /healthcheck.sh && \
    chmod +x /healthcheck.sh

# Expose ports
EXPOSE 8080 5005

# Set Java options
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+UnlockDiagnosticVMOptions -XX:G1HeapRegionSize=16M"

# Entry point
ENTRYPOINT exec java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} -jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD /healthcheck.sh
