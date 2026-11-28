# Despliegue (Etapa 2 — microservicios)

El stack local completo se describe en `docker-compose.yml` (10 servicios con healthcheck real): `postgres`
(5 bases creadas por `docker/postgres/init-databases.sql`, un usuario por contexto), `rabbitmq`,
`external-bank-mock` (WireMock con `docker/wiremock`), los 5 microservicios, `api-gateway` y `credicasa-frontend`.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose logs -f api-gateway
docker compose down          # conserva volúmenes; down -v los elimina
```

- Cada `Dockerfile` de módulo compila con `maven:3.9.11-eclipse-temurin-21` usando la raíz como contexto (POM
  agregador + shared kernel) y ejecuta en `eclipse-temurin:21-jre-alpine` con usuario no root y `HEALTHCHECK`.
- Solo `api-gateway` (8080), el FrontEnd (80) y WireMock (8090) se publican en todas las interfaces; microservicios,
  PostgreSQL y RabbitMQ quedan en loopback. Un proxy HTTPS debe gestionar el acceso exterior.
- Flyway aplica las migraciones de cada servicio antes de Hibernate `validate`. Las migraciones aplicadas son
  inmutables; los cambios requieren una versión nueva. Un esquema no vacío sin historial Flyway es rechazado.
- Secretos: `JWT_SECRET` (≥32 bytes) e `INTERNAL_API_TOKEN` son compartidos por todos los servicios;
  `TWO_FACTOR_ENCRYPTION_KEY` (Base64 de 32 bytes) y `PAYMENT_SANDBOX_SECRET` solo por identity. Sobrescriba los
  valores de desarrollo mediante `.env` (ver `.env.example`); no los suba a Git.
- `CORS_ALLOWED_ORIGIN_PATTERNS` se aplica solo en el gateway (los microservicios no emiten cabeceras CORS).
- CI: `.github/workflows/ci.yml` ejecuta `mvn -B -ntp clean verify`, publica los informes y valida/compila las
  imágenes de Compose.
