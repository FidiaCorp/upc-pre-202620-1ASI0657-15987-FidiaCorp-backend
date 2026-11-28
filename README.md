# CrediCasa — Backend v2.0.0 (Etapa 2: microservicios + Sprint 4)

Backend Java 21 / Spring Boot 3.3.4 dividido en un **build Maven multi-módulo** con 7 módulos desplegables
de forma independiente. Cada microservicio sigue arquitectura hexagonal (`domain` → `application` →
`infrastructure` / `interfaces`), verificada en cada build con ArchUnit (`HexagonalArchitectureTest`), tiene su
propia base PostgreSQL con migraciones Flyway y **0 claves foráneas entre contextos** (PA-05 / ADR-001).
**Las ofertas, tasas bancarias, SBS, SUNARP y pasarelas son DEMO/simuladas**: no constituyen aprobaciones
bancarias, cobros reales ni consultas oficiales.

| Módulo | Puerto | Base de datos | Historias | Responsabilidad |
| --- | --- | --- | --- | --- |
| `credicasa-shared-kernel` | — | — | — | Biblioteca propia (5.1.3): `Money`, `FinancialMath`, JWT, `CorrelationId`, ProblemDetail, guard de API interna, eventos de integración. |
| `api-gateway` | 8080 | — | — | Spring Cloud Gateway: rutas `/api/v1/**`, CORS central, `X-Correlation-Id`, Swagger agregado, rate limiting de login/registro, revocación de JWT, bloqueo de `/api/v1/internal/**`. |
| `identity-subscription-service` | 8081 | `identity_db` | US01–US06 | Cuentas, perfil, JWT/logout, TOTP, suscripciones y pasarelas sandbox. |
| `property-catalog-service` | 8082 | `catalog_db` | US07–US09, US13–US15 | Inmuebles, filtros, verificación SUNARP sellada con SHA-256 (+ Resilience4j), asesor hipotecario. |
| `mortgage-pricing-service` | 8083 | `pricing_db` | US11, US12, US19, US20 | `AmortizationStrategy` (francés), TEA/TNA, historial y comparador de escenarios, XLSX, tasas bancarias con CircuitBreaker + TimeLimiter (QA-02). |
| `quotation-deal-service` | 8084 | `quotation_db` | US10–US12, US21, US22 | Ofertas, versiones inmutables con SHA-256, exportación PDF (OpenPDF) / XLSX desde el snapshot, Transactional Outbox → RabbitMQ. |
| `communication-notification-service` | 8085 | `communication_db` | US10, US16–US18 | Bandeja de avisos (Idempotent Inbox + DLQ), conversaciones, bloqueos y exportación. |

Comunicación entre contextos: **síncrona** por `RestClient` a la API interna `/api/v1/internal/**` (protegida con
`X-Internal-Token`, con `@CircuitBreaker` y propagación de `Authorization` y `X-Correlation-Id`); **asíncrona** por
RabbitMQ (exchange topic `credicasa.events`, colas `credicasa.notifications.queue` / `credicasa.notifications.dlq`,
routing keys `credit.offer.issued.v1` y `quotation.version.issued.v1`).

## Levantar el stack completo

Requiere Docker Desktop (Compose v2). Desde esta carpeta, con el FrontEnd clonado al lado
(`../upc-pre-202620-1ASI0657-15987-FidiaCorp-FrontEnd`, configurable con `FRONTEND_CONTEXT`):

```bash
docker compose up --build -d
docker compose ps          # los 10 servicios deben quedar "Up (healthy)"
```

| Servicio | URL local |
| --- | --- |
| FrontEnd (Nginx → api-gateway) | http://localhost (`FRONTEND_PORT`, por defecto 80) |
| API Gateway | http://localhost:8080/api/v1 · Swagger agregado: http://localhost:8080/swagger-ui.html |
| Microservicios (solo loopback) | http://127.0.0.1:8081 … :8085 (`/actuator/health`, `/v3/api-docs`) |
| Circuit breakers (QA-02) | http://127.0.0.1:8083/actuator/circuitbreakers |
| external-bank-mock (WireMock) | http://localhost:8090/__admin |
| RabbitMQ management | http://127.0.0.1:15672 |
| PostgreSQL 16 (5 bases) | 127.0.0.1:5433 |

Los secretos de `docker-compose.yml` tienen valores **solo de desarrollo local**; sobrescríbalos copiando
`.env.example` a `.env` (`openssl rand -hex 32`; la clave TOTP con `openssl rand -base64 32`). `E2E_FIXTURE_TOKEN`
vacío deja deshabilitado el endpoint interno de fixture E2E; defínalo únicamente para la corrida de aceptación.
`docker compose down` conserva los volúmenes; `down -v` borra los datos.

## Construir y probar

```bash
mvn clean verify                       # 7 módulos: unitarias, integración, Cucumber, ArchUnit, PostgreSQL 16 embebido y JaCoCo
mvn -pl mortgage-pricing-service -am verify   # un solo microservicio con sus dependencias
node scripts/qa-verify.mjs             # QA-01/QA-02/QA-03 contra el stack levantado (requiere E2E_FIXTURE_TOKEN)
```

Cada microservicio incluye `HexagonalArchitectureTest` (7 reglas, incluida la prohibición de depender de otro
contexto), pruebas unitarias y de integración, un `.feature` Cucumber (en conjunto cubren US01–US22), una prueba
adversarial de BOLA/BFLA (QA-03), `OpenApiContractTest` (exporta `target/openapi/<módulo>.json`) y
`PostgresMigrationIT` (PostgreSQL 16.14 embebido Zonky: migración limpia, validación, reinicio, ausencia de FKs
hacia tablas ajenas y rechazo de esquemas desconocidos). JaCoCo exige ≥80 % de líneas por módulo y ≥90 % de líneas y
ramas en el shared kernel. Los informes quedan en `<módulo>/target/surefire-reports`, `target/cucumber` y
`target/site/jacoco`.

## Resiliencia (QA-02)

`ResilientMarketRatesProvider` envuelve el `RestClient` hacia `external-bank-mock` (connect 500 ms, read 1000 ms) con
`@CircuitBreaker(name = "bankingProvider", fallbackMethod = "fallbackToSbs")` y `@TimeLimiter` de 1000 ms
(ventana 10, mínimo 10 llamadas, umbral 50 %, 15 s abierto, 3 llamadas en HALF_OPEN). `GET /api/v1/banking/benchmarks`
devuelve `X-Data-Source: LIVE | FALLBACK_CACHED` y los campos `dataSource`, `fallbackActive` y `asOfDate`. Los
mappings de WireMock (`docker/wiremock`) incluyen los escenarios `bank-outage` (`OUTAGE` → 503, `SLOW` → 3 s):

```bash
docker compose stop external-bank-mock     # o: curl -X PUT localhost:8090/__admin/scenarios/bank-outage/state -d '{"state":"OUTAGE"}'
curl -i "http://localhost:8080/api/v1/banking/benchmarks?currency=PEN"   # repetir ≥10 veces → FALLBACK_CACHED
curl -s http://127.0.0.1:8083/actuator/circuitbreakers                   # bankingProvider: OPEN
docker compose start external-bank-mock    # tras 15 s: HALF_OPEN → CLOSED y de nuevo LIVE
```

## Endpoints públicos por microservicio (vía gateway `/api/v1`)

Generados desde el OpenAPI real de cada servicio (`OpenApiContractTest`).

### identity-subscription-service (:8081)

| Método | Ruta | Resumen |
| --- | --- | --- |
| GET | `/api/v1/users/me` | Consultar el perfil propio |
| PUT | `/api/v1/users/me` | Actualizar el perfil propio |
| POST | `/api/v1/users/me/two-factor/setup` | Generar una configuración TOTP pendiente |
| POST | `/api/v1/users/me/two-factor/disable` | Desactivar TOTP propio |
| POST | `/api/v1/users/me/two-factor/confirm` | Confirmar el TOTP pendiente |
| POST | `/api/v1/payments/verify` | Verificar la referencia de pago y activar la suscripción |
| POST | `/api/v1/payments/checkout` | Iniciar un cobro sandbox con la pasarela elegida |
| POST | `/api/v1/auth/register` | Registrar una cuenta CLIENT |
| POST | `/api/v1/auth/logout` | Revocar únicamente el token actual |
| POST | `/api/v1/auth/login` | Ingresar con contraseña y, si corresponde, TOTP |
| GET | `/api/v1/users/me/two-factor` | Consultar el estado TOTP propio |
| GET | `/api/v1/subscriptions/status` | Consultar estado de suscripción del usuario |
| GET | `/api/v1/payments/gateways` | Listar pasarelas de pago sandbox disponibles |

### property-catalog-service (:8082)

| Método | Ruta | Resumen |
| --- | --- | --- |
| GET | `/api/v1/properties` | Buscar inmuebles activos |
| POST | `/api/v1/properties` | Registrar un inmueble propio |
| POST | `/api/v1/properties/{id}/verify-registry` | Verificar partida registral del inmueble propio (SUNARP DEMO) |
| POST | `/api/v1/ai/recommendations` | Recomendar inmuebles según capacidad de pago y preferencias |
| GET | `/api/v1/properties/{id}` | Consultar un inmueble activo |
| DELETE | `/api/v1/properties/{id}` | Eliminar un inmueble propio |
| GET | `/api/v1/properties/{id}/legitimacy` | Consultar certificado registral de legitimidad |

### mortgage-pricing-service (:8083)

| Método | Ruta | Resumen |
| --- | --- | --- |
| POST | `/api/v1/simulations/compare` | US20: comparar 2–3 escenarios lado a lado |
| POST | `/api/v1/simulations/calculate` | Calcular y guardar una simulación propia |
| GET | `/api/v1/simulations` | US20: listar mis simulaciones |
| GET | `/api/v1/simulations/{id}` | Recuperar una simulación propia |
| GET | `/api/v1/simulations/{id}/export/xlsx` | Descargar cronograma de una simulación propia en .xlsx |
| GET | `/api/v1/banking/benchmarks` | Consultar referencias de tasas simuladas |
| GET | `/api/v1/banking/banks/{bankCode}/offer` | Consultar una cotización bancaria simulada |

### quotation-deal-service (:8084)

| Método | Ruta | Resumen |
| --- | --- | --- |
| GET | `/api/v1/offers` | Listar ofertas recibidas propias |
| POST | `/api/v1/offers` | Emitir una oferta DEMO y su aviso de forma atómica |
| GET | `/api/v1/offers/{offerId}/versions` | Listar el historial de versiones |
| POST | `/api/v1/offers/{offerId}/versions` | Emitir una nueva versión inmutable |
| GET | `/api/v1/offers/{offerId}/schedule` | Leer el cronograma ya generado de una oferta |
| POST | `/api/v1/offers/{offerId}/schedule` | Generar u obtener el cronograma persistido de una oferta |
| GET | `/api/v1/quotations/versions/{versionId}/verify` | Verificar la integridad de una versión |
| GET | `/api/v1/offers/{offerId}` | Consultar una oferta recibida |
| GET | `/api/v1/offers/{offerId}/versions/{versionNumber}` | Consultar una versión con su snapshot |
| GET | `/api/v1/offers/{offerId}/schedule/export/xlsx` | Descargar la cotización versionada en .xlsx |
| GET | `/api/v1/offers/{offerId}/schedule/export/pdf` | Descargar la cotización versionada en PDF |
| GET | `/api/v1/offers/{offerId}/exports` | Historial de exportaciones de una oferta |

### communication-notification-service (:8085)

| Método | Ruta | Resumen |
| --- | --- | --- |
| POST | `/api/v1/messages` | Enviar un mensaje privado en una conversación propia |
| GET | `/api/v1/messages/conversations` | Listar las conversaciones propias |
| POST | `/api/v1/messages/conversations` | Crear u obtener una conversación con otra cuenta |
| POST | `/api/v1/messages/blocks/{targetUserId}` | Bloquear a otro usuario en la mensajería |
| DELETE | `/api/v1/messages/blocks/{targetUserId}` | Desbloquear a un usuario previamente bloqueado |
| PATCH | `/api/v1/notifications/{notificationId}/read` | Marcar un aviso propio como leído |
| GET | `/api/v1/notifications` | Consultar avisos propios y contador sin leer |
| GET | `/api/v1/messages/conversations/{conversationId}/messages` | Leer los mensajes de una conversación |
| GET | `/api/v1/messages/conversations/{conversationId}/export` | Descargar historial de conversación en archivo .txt |
| GET | `/api/v1/messages/conversations/{conversationId}/export/json` | Obtener historial de conversación estructurado en JSON |
| GET | `/api/v1/messages/blocks` | Listar usuarios bloqueados por la cuenta actual |

### API interna (no expuesta por el gateway; exige `X-Internal-Token`)

| Servicio | Método | Ruta | Consumidor |
| --- | --- | --- | --- |
| identity | GET | `/api/v1/internal/accounts/{id}`, `/{id}/exists`, `/lookup?email=` | quotation, communication |
| identity | GET | `/api/v1/internal/tokens/status` | api-gateway (revocación) |
| identity | POST | `/api/v1/internal/e2e/accounts/{id}/realtor` (+ `X-Fixture-Token`; solo si `APP_E2E_FIXTURE_TOKEN` está definido) | pruebas E2E |
| catalog | GET | `/api/v1/internal/properties/{id}` | quotation, communication |
| pricing | POST/GET | `/api/v1/simulations/calculate`, `/api/v1/simulations/{id}` (con el JWT del usuario) | quotation |

## Reglas de negocio y contrato (heredadas de v1.3.0, vigentes en cada microservicio)

### Formatos y errores

Importes y tasas de **catálogo, ofertas y simulaciones** se devuelven como cadenas
exactas sin notación científica: `"9999999999999999.99"`, `"0.00000001"`, `"0.00"`.
VAN puede ser negativo. Contadores, plazos, páginas y números de cuota siguen siendo
enteros JSON. Las entradas decimales aceptan números o cadenas interpretables como
BigDecimal; use cadenas para evitar pérdida de precisión en JavaScript.

El DTO heredado `BankRateResponseDto` conserva `minTea`, `maxTea`, `averageTea` y
`maxLtv` como números JSON. No confundir esa consulta simulada con `POST /offers`.
Las fechas de cuotas y desembolso son ISO `YYYY-MM-DD`, sin conversión de zona horaria.

Los errores siguen **RFC 7807** (`ProblemDetail`, `application/problem+json`):
`{type,title,status,detail,instance}` más las extensiones de compatibilidad
`error,message,timestamp,validationErrors` (los errores TOTP agregan `code`). Los filtros de seguridad pueden responder 401/403 sin un cuerpo
JSON garantizado: el cliente debe comprobar el estado antes de interpretar el cuerpo.
OpenAPI no promete un esquema de contenido para esos errores de filtro.

| Estado | Casos relevantes |
| --- | --- |
| 400 | Campos, UUID o consultas inválidos; combinación financiera incoherente; `TWO_FACTOR_SETUP_EXPIRED`. |
| 401 | Bearer ausente/expirado/revocado; credenciales incorrectas; `TWO_FACTOR_REQUIRED`, `TWO_FACTOR_INVALID` o `INVALID_CREDENTIALS` según el flujo. |
| 402 | Cobro sandbox rechazado por la pasarela (`status=REJECTED`, `declineCode`, `retryAllowed`). |
| 403 | Rol insuficiente, inmueble ajeno (borrar/verificar registro) o conversación de la que no se participa. |
| 404 | Recurso no disponible para el principal, eliminado o cronograma no generado. |
| 409 | Correo ocupado/concurrencia; bloqueo activo en mensajería; referencia de pago de otra cuenta; `TWO_FACTOR_ALREADY_ENABLED` o `TWO_FACTOR_NOT_ENABLED`. |
| 422 | Pago con importe distinto a la tarifa oficial, moneda distinta de PEN o referencia no emitida por la pasarela. |
| 429 | `TWO_FACTOR_LOCKED`, con `Retry-After` en segundos. |
| 503 | `TWO_FACTOR_UNAVAILABLE`: configuración criptográfica ausente, incorrecta o secreto no descifrable. |

CORS expone `Retry-After`. Los DTO privados de ofertas/avisos/cronogramas y el setup
TOTP envían `Cache-Control: no-store`. El header `X-Response-Time-Ms` de benchmarks
mide únicamente esa llamada y no representa un SLA.

### Cuentas, perfil y sesión

Registro: `{firstName,lastName,email,password}`. Nombres obligatorios de hasta 100
caracteres, correo normalizado/único de hasta 254 y contraseña de 12–72 caracteres,
máximo 72 bytes UTF-8. Se almacena BCrypt y no se crean usuarios de demostración.
Perfil: `{id,firstName,lastName,email,roles}`; PUT exige los tres campos editables.
El UUID del JWT permanece estable al cambiar el correo; los próximos logins usan el
correo nuevo. Enviar roles en el cuerpo no concede privilegios.

Login: `{email,password,otp?}` → `{token,type,email,roles,expiresIn}`; `expiresIn`
está en milisegundos. Logout no lleva cuerpo, almacena SHA-256 del JWT y su expiración,
y no afecta otras sesiones. La revocación persiste entre reinicios de la aplicación
cuando se conserva la base; su limpieza elimina solo registros expirados. Un JWT nuevo
incluye `jti` aleatorio. La comprobación posterior a logout rechaza el token, aunque
una solicitud que ya había superado la validación puede terminar.

TOTP usa HMAC-SHA1, seis dígitos y paso actual de 30 segundos, sin tolerancia para
pasos expirados/futuros. Setup `{password}` devuelve `{secret,otpauthUri,expiresIn:600}`;
solo se muestra ese secreto pendiente. Confirm `{otp}` activa la configuración antes
de los diez minutos y consume el código. Disable `{password,otp}` requiere otro código
nuevo. No se reutiliza el código de confirmación para login inmediato.

Cinco fallos de OTP o contraseña de gestión bloquean las operaciones y nuevos ingresos
durante 15 minutos. Regenerar setup no reinicia el bloqueo. El contador es persistente;
los errores de contraseña primaria de login no forman parte de ese contador específico.
Con 2FA activo no se emite JWT sin OTP válido; las sesiones emitidas previamente no se
revocan automáticamente. Un 401 de gestión con `INVALID_CREDENTIALS`/`TWO_FACTOR_INVALID`
no significa que el Bearer haya expirado; un 401 de filtro sin esos códigos sí invalida
la sesión en la interfaz.

### Catálogo, ofertas y avisos

Crear inmueble: `{title,address,description?,price,currency,tags?}`. Título 120,
dirección 250, descripción 2000; precio positivo con 16 enteros/2 decimales, PEN/USD.
Hasta 20 etiquetas de 40 caracteres, no vacías: NFKC, minúsculas, recorte, espacios
internos únicos, sin duplicados y ordenadas. OwnerId se deriva del principal.

Buscar: `q` literal sobre título/dirección/descripción, sin distinguir mayúsculas,
hasta 120 caracteres; `%`, `_` y barras no son comodines SQL. `tags=terraza&tags=garaje`
exige **todas** las etiquetas. `mine=true` filtra por el principal. `page=0`, `size=20`,
rango 1–100; orden `createdAt DESC,id DESC`. Respuesta común:
`{items,page,size,totalElements,totalPages}`.

Emitir oferta: `{recipientEmail,propertyId,bankCode,loanAmount,currency,annualInterestRate,termMonths,startDate}`.
Banco BCP/INTERBANK, título y nombre derivados; `origin:"DEMO"` no es editable.
Destinatario CLIENT resuelto por correo, propiedad propia activa, préstamo≤precio y
misma moneda. TEA decimal `(0,1]` de hasta 8 decimales, plazo 1–360 y fecha segura.
La oferta conserva `{id,title,address,price,currency}` del inmueble aunque se elimine.
Oferta y aviso se guardan en la misma transacción; no queda uno sin el otro.

La bandeja `/notifications?unreadOnly=false&page=0&size=20` agrega `unreadCount`, total
propio sin leer independiente de la página/filtro. Aviso:
`{id,offerId,type:"CREDIT_OFFER",origin:"DEMO",title,createdAt,readAt}`.
Marcarlo leído conserva el primer `readAt`, incluso con concurrencia. La UI consulta
cada 5 segundos mientras esté visible y autenticada; no hay SSE ni integración push.

No hay alta pública de REALTOR. Un administrador puede asignarlo a una cuenta de
prueba ya registrada mediante acceso autorizado a su base, por ejemplo:

```sql
-- Solo en la base de demostración administrada; sustituya el correo propio.
UPDATE accounts SET roles = 'ROLE_REALTOR' WHERE email = 'asesor-demo@example.com';
```

Después se inicia sesión de nuevo; no se crean contraseñas ni usuarios productivos.

### Cronogramas y precisión

Cálculo directo requiere `loanAmount`, `currency` y `annualInterestRate`; admite
`termMonths?`, `startDate?`, `propertyValue?`,
`termYears?`, `gracePeriodType?`, `gracePeriodMonths?`, `balloonPaymentAmount?`,
`balloonPaymentMonth?`, `lifeInsuranceRate?`, `propertyInsuranceRate?`, `monthlyAdminFee?`,
`discountRateAnnual?`, `propertyAddress?`. `clientProfile` es heredado y su identidad
se ignora: UUID, nombre y correo se toman de la cuenta autenticada.

- Préstamo/valor positivos 16+2 y préstamo≤valor; omitir valor utiliza el préstamo.
- TEA decimal `(0,1]`, máximo 8 decimales; `8.5` se rechaza, `0.085` significa 8.5%.
- Plazo 1–360 meses o 1–30 años. Si se envían ambos deben coincidir; omitirlos usa 240 meses.
- NONE exige 0 meses; TOTAL/PARTIAL requieren 1–36 y menos que el plazo total.
- Sin balón se omiten los campos o son 0; positivo≤préstamo, exclusivamente al vencimiento.
- Seguros y COK `[0,1]` con 8 decimales; gastos≥0 con 16+2. Seguros/gastos omitidos usan 0;
  COK omitido usa TEA, pero COK 0 se conserva como 0.
- Fecha inicial 1900–9990 y última cuota con año≤9999. Fechas históricas válidas;
  omitirla usa el día UTC actual. Dirección opcional hasta 250 caracteres.

Respuesta: `{quotationId,currency,loanAmount,propertyValue,termMonths,estimatedMonthlyInstallment,metrics,status,schedule,createdAt}`.
La cuota estimada es el primer pago regular, no el mes de gracia. Cada fila contiene
fecha, saldo inicial, interés, amortización, cuota pura, seguros, gasto, total y saldo
final. `/offers/{id}/schedule` usa exclusivamente los términos guardados de la oferta:
**sin gracia, balón, seguros ni gastos; COK=TEA**. POST genera una sola vez con bloqueo
y devuelve 200 también al repetir; GET recupera lo guardado. Es DEMO, no una TCEA
regulatoria de un producto bancario real.

TEM se obtiene por raíz duodécima, cuota por anualidad BigDecimal/DECIMAL128 y redondeo
HALF_EVEN a centavos por período. La última cuota liquida 0.00. Sin gracia total las
amortizaciones suman el préstamo; con TOTAL incluyen intereses capitalizados.
VAN usa `+loanAmount` inicial y pagos negativos: con COK 0 es préstamo−suma de pagos.
TIR se obtiene por bisección adaptativa sobre flujos normalizados. Dinero persistido
usa NUMERIC(38,2), TIR(60,24) y TCEA(280,24); se conservan owner, IDs y COK reales.
No se inventan datos faltantes de registros heredados.
