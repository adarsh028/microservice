# ---- Build stage -------------------------------------------------------
# Build context is the repo root (microservices-demo/) so that the shared
# proto/ directory is accessible alongside the service source.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy shared proto files first (needed by protobuf-maven-plugin)
COPY proto /proto

# Cache Maven dependencies before copying source
COPY notification-service/pom.xml .
RUN mvn dependency:go-offline -B

# Build the service
COPY notification-service/src ./src
RUN mvn package -DskipTests -B

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/notification-service-*.jar app.jar

USER appuser

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
