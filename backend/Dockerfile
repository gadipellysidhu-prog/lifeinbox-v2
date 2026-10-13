# ==============================================================================
# LifeInbox V2 Backend - Multi-Stage Production Dockerfile
# Runtime: Eclipse Temurin OpenJDK 21 (Alpine)
# ==============================================================================

# Stage 1: Build JAR using Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S lifeinbox && adduser -S -G lifeinbox lifeinbox
USER lifeinbox

COPY --from=builder --chown=lifeinbox:lifeinbox /app/target/backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
