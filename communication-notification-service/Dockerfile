# syntax=docker/dockerfile:1.7
# communication-notification-service — se construye con el contexto en la raíz del repositorio (necesita el POM agregador y el shared kernel):
#   docker build -f communication-notification-service/Dockerfile -t credicasa/communication-notification-service .
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY credicasa-shared-kernel credicasa-shared-kernel
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY identity-subscription-service/pom.xml identity-subscription-service/pom.xml
COPY property-catalog-service/pom.xml property-catalog-service/pom.xml
COPY mortgage-pricing-service/pom.xml mortgage-pricing-service/pom.xml
COPY quotation-deal-service/pom.xml quotation-deal-service/pom.xml
COPY communication-notification-service/pom.xml communication-notification-service/pom.xml
COPY communication-notification-service/src communication-notification-service/src
# Caché Maven compartida y bloqueada (sharing=locked): Compose construye las 6 imágenes en paralelo y el repositorio local no admite escrituras concurrentes.
RUN --mount=type=cache,id=credicasa-m2,target=/root/.m2,sharing=locked mvn -B -q -ntp -Daether.connector.http.retryHandler.count=8 -Daether.connector.http.retryHandler.interval=3000 -Daether.connector.http.retryHandler.serviceUnavailable=429,503 -pl communication-notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S credicasa && adduser -S credicasa -G credicasa
WORKDIR /app
COPY --from=build /workspace/communication-notification-service/target/communication-notification-service.jar app.jar
USER credicasa
EXPOSE 8085
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -qO- http://127.0.0.1:8085/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
