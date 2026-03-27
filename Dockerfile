# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom first — cache dependency layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S caseflow && adduser -S caseflow -G caseflow

WORKDIR /app

# Copy the built jar
COPY --from=builder /build/target/caseflow-0.0.1-SNAPSHOT.jar app.jar

# Storage volume mount point
RUN mkdir -p /app/storage-data && chown caseflow:caseflow /app/storage-data

USER caseflow

EXPOSE 8080

# Respect container memory limits; MaxRAMPercentage keeps the JVM from OOMing the container
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
