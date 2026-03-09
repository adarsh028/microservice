# ---- Build stage -------------------------------------------------------
# Build context is the repo root (microservices-demo/) so that the shared
# proto/ directory is accessible alongside the service source.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy shared proto files first (needed by protobuf-maven-plugin)
COPY proto /proto

# Cache Maven dependencies before copying source
COPY profile-service/pom.xml .
RUN mvn dependency:go-offline -B

# Build the service
COPY profile-service/src ./src
RUN mvn package -DskipTests -B

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/profile-service-*.jar app.jar

USER appuser

EXPOSE 8082 9090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
