# ==============================================================================
# Multi-stage Dockerfile for CrediCasa Mortgage Calculation Core Service
# FidiaCorp - FinTech / PropTech
# ==============================================================================

# ----------------- Stage 1: Build & Package -----------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copiar configuración del proyecto y precargar dependencias (Caché de capas Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar artefacto JAR ejecutable
COPY src ./src
RUN mvn clean package -DskipTests -B

# ----------------- Stage 2: Runtime Optimizado -----------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="FidiaCorp Architecture Team <architecture@fidiacorp.pe>"
LABEL service="credicasa-mortgage-calculation-service"

WORKDIR /app

# Crear usuario sin privilegios root por seguridad (ASR-SEC)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /build/target/credicasa-backend-*.jar app.jar

# Configuración de variables de entorno y JVM
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV SERVER_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/swagger-ui.html || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
