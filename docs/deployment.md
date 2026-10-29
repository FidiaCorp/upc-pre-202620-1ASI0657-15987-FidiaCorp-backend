# Despliegue y adopción de PostgreSQL

Requiere Java 21 y PostgreSQL 16, o Docker con Compose. La API es DEMO: no conecta bancos ni aprueba créditos. El servicio Compose `credicasa-backend` escucha en 8080; otro contenedor puede usar `http://credicasa-backend:8080/api/v1`. El puerto publicado queda en loopback; un proxy HTTPS debe gestionar el acceso exterior.

## Base nueva

Copiar `.env.example` a `.env`, generar cada secreto independientemente y completar los valores vacíos. `openssl rand -hex 32` sirve para contraseña/JWT y `openssl rand -base64 32` para AES. Conservar AES entre reinicios; cambiarla impide descifrar TOTP existente. No guardar `.env` en Git ni registros. La aplicación fuera de Compose exige también `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`.

```
docker compose config --quiet
docker compose up --build -d
curl --fail http://127.0.0.1:8080/actuator/health
docker compose logs credicasa-backend
docker compose down
```

`down` conserva el volumen. No usar `down -v` sobre datos que deban preservarse. PostgreSQL no publica su puerto. Flyway aplica V1–V7 antes de Hibernate `validate`; no se ejecuta `update`, ni scripts de inicialización duplicados. Migraciones aplicadas son inmutables: cambios posteriores requieren una nueva versión SQL. Salud comprueba conectividad con la base y solo publica estado, sin componentes ni credenciales.

`CORS_ALLOWED_ORIGINS` es una lista separada por comas de orígenes completos, sin rutas. Predeterminados documentados: `http://localhost:5173,http://127.0.0.1:5173` para desarrollo. Configurar los dominios HTTPS reales al desplegar; en proxy same-origin puede dejarse vacía. No se permiten comodines generales ni cookies. Los patrones de puerto variable exclusivamente loopback se reservan al fixture local de pruebas, que los declara explícitamente. `Authorization` está permitido y `Retry-After` expuesto.

`JWT_SECRET` debe tener al menos 32 bytes UTF-8; no hay secreto de respaldo. `JWT_EXPIRATION_MS` debe estar entre 1 y 31536000000; predeterminado 86400000. `JAVA_TOOL_OPTIONS` permite ajustar memoria sin shell intermediario. Java recibe SIGTERM directamente. Swagger continúa en `/swagger-ui.html`.

## Base existente creada por Hibernate

La aplicación rechaza esquemas no vacíos sin historial Flyway. **No activar baseline automático**. Antes de adoptar:

1. Detener escritores, realizar backup verificable y ensayar restauración en una copia PostgreSQL 16.
2. Inventariar tablas, columnas, tipos, restricciones e índices; comparar con entidades actuales y V1–V7. Este procedimiento solo cubre el esquema Hibernate de esta aplicación. Un esquema desconocido requiere revisión/migración específica.
3. Conservar datos históricos: no deducir `owner_id`, `client_id`, `property_id` ni `discount_rate_used`. Los valores desconocidos quedan NULL; las simulaciones sin propietario no se exponen por REST.
4. Ejecutar **una vez y explícitamente**, sobre la copia revisada primero, el comando `baseline` de Flyway 10.10.0 con `baselineVersion=0`, URL y usuario correctos. Proporcionar credenciales mediante su entorno seguro, no argumentos visibles ni scripts versionados. Ejemplo con CLI instalada: `flyway -baselineVersion=0 baseline`, usando `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD`. No usar versión predeterminada1: omitiría V1.
5. Arrancar la aplicación: aplica V1–V7 y valida Hibernate. Comparar recuentos/datos, estado de migraciones y flujos propios. Ante fallo, detener y restaurar el backup; no editar checksums ni ejecutar `repair` sin diagnóstico.

Un volumen Compose anterior conserva usuarios/contraseñas originales: cambiar `POSTGRES_USER` en YAML no crea el rol `credicasa` en un volumen existente. Tras backup y revisión, conservar el rol/base existentes mediante `DB_USER`/`DB_NAME` y la contraseña real en `.env`, o crear/configurar explícitamente el nuevo rol y permisos mediante administración PostgreSQL. No borrar el volumen para resolver credenciales. Ensayar también permisos DDL necesarios para Flyway.

## Verificación reproducible

```
mvn verify
mvn -Ppostgres-migrations verify
```

El primer comando ejecuta suite H2/BDD y desactiva Flyway solo en recursos test. El segundo ejecuta únicamente `PostgresMigrationIT`, con servidor PostgreSQL **16.14** aislado y binarios Zonky test-only seleccionados por SO/arquitectura. Requiere usuario sin privilegios root, descarga Maven inicial y ejecución de procesos locales; escucha solo loopback y se cierra al terminar. Informes separados en `target/postgres-migration-reports`. En plataformas no cubiertas se puede seleccionar explícitamente `-Dpostgres.binary.artifact=embedded-postgres-binaries-<plataforma>` compatible, sin añadir binarios al artefacto productivo.

La prueba PostgreSQL verifica base nueva/reinicio, adopción explícita de esquema Hibernate preservando históricos y rechazo de esquema desconocido. El fixture del navegador es test-only, usa H2 y declara Flyway desactivado y CORS loopback explícitos. Docker no está disponible en el entorno de validación actual: los archivos se revisan, pero no se afirma haber ejecutado imagen/Compose; ejecutar los comandos anteriores en un host con Docker antes de desplegar.
