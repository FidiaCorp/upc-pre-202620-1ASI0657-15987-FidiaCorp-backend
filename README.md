# CrediCasa — API del Sprint 2

Backend Java 21/Spring Boot 3.3.4 con arquitectura hexagonal: `domain` contiene modelos,
reglas y puertos; `application` coordina los casos de uso; `infrastructure` implementa
persistencia y seguridad; `interfaces/rest` publica la API.

El incremento incluye cuentas persistentes, perfil, TOTP, revocación de JWT, catálogo,
ofertas por destinatario, avisos y cronogramas. **Las ofertas y referencias de BCP,
Interbank y SBS son DEMO/simuladas**: no constituyen aprobaciones bancarias ni consultas
o tarifas oficiales en tiempo real. No se ofrece una garantía de latencia.

## Ejecutar y consultar el contrato

Requiere Java 21, Maven 3.9.x y PostgreSQL accesible para ejecutar la aplicación normal.
Las pruebas usan H2 y no requieren PostgreSQL ni Docker.

```bash
mvn verify
mvn spring-boot:run
```

Antes del segundo comando configure la conexión y claves mediante el entorno:

| Variable | Uso actual |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC PostgreSQL; por defecto usa `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=credicasa_db`. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Credenciales de la base. La configuración de desarrollo tiene valores predeterminados; reemplazarlos en cada despliegue. |
| `JWT_SECRET` | Clave propia UTF-8 de al menos 32 bytes; existe un fallback de desarrollo que no debe usarse para un despliegue. |
| `JWT_EXPIRATION_MS` | Vigencia del JWT en milisegundos; predeterminado 86400000. |
| `TWO_FACTOR_ENCRYPTION_KEY` | Base64 de 32 bytes aleatorios para AES-256-GCM; sin clave válida, las operaciones que necesitan cifrar/descifrar devuelven 503. |
| `SERVER_PORT` | Puerto HTTP, predeterminado 8080. |

Puede generar claves con `openssl rand -base64 32`. Conserve la clave AES entre
reinicios: cambiarla impide descifrar los secretos TOTP existentes. No publique claves,
contraseñas, tokens ni manifiestos del fixture de pruebas.

Base local: `http://localhost:8080/api/v1`. Swagger UI: `/swagger-ui.html`.
Contrato generado: `/v3/api-docs`. `ApiContractDocumentation` describe la representación
real de los DTO y las respuestas; `OpenApiContractTest` consulta el documento mediante
HTTP real y exporta una copia a `target/openapi/generated.json`.

Actualmente el esquema normal se administra con Hibernate `ddl-auto=update`. Los SQL
`src/main/resources/db/migration/V1…V7` son DDL explícito para administración manual:
**Flyway todavía no está integrado**. Compose monta únicamente V1 y la aplicación
completa el esquema mediante Hibernate. Los archivos Docker existentes no se han
validado ejecutándolos en este entorno, donde Docker no está instalado.

## Operaciones y permisos

Todas las rutas siguientes usan el prefijo `/api/v1`. Solo login, registro y benchmarks
son públicos. Las privadas requieren `Authorization: Bearer <token>`.

| Método y ruta | Permiso / comportamiento | Éxito |
| --- | --- | --- |
| `POST /auth/register` | Público; crea únicamente `ROLE_CLIENT`. | 201 perfil |
| `POST /auth/login` | Público; contraseña y OTP si está activo. | 200 token |
| `POST /auth/logout` | Revoca exclusivamente el Bearer presentado. | 204 sin cuerpo |
| `GET /users/me` | Perfil del principal. | 200 perfil |
| `PUT /users/me` | Actualiza nombre, apellido y correo propios. | 200 perfil |
| `GET /users/me/two-factor` | Estado propio, sin secreto. | 200 estado |
| `POST /users/me/two-factor/setup` | Contraseña; genera configuración pendiente. | 200 configuración |
| `POST /users/me/two-factor/confirm` | OTP pendiente válido. | 200 estado |
| `POST /users/me/two-factor/disable` | Contraseña y OTP nuevo. | 200 estado |
| `POST /properties` | CLIENT/REALTOR; propietario derivado del JWT. | 201 + Location |
| `GET /properties` | CLIENT/REALTOR; catálogo activo con filtros. | 200 página |
| `GET /properties/{id}` | CLIENT/REALTOR; catálogo compartido. | 200 inmueble |
| `DELETE /properties/{id}` | Solo propietario; borrado lógico. | 204 sin cuerpo |
| `POST /offers` | REALTOR, propiedad propia y destinatario CLIENT. | 201 + Location |
| `GET /offers` | Solo ofertas recibidas por el principal. | 200 página |
| `GET /offers/{offerId}` | Solo destinatario, incluso frente al emisor. | 200 oferta |
| `POST /offers/{offerId}/schedule` | Solo destinatario; generación idempotente, sin cuerpo. | 200 cronograma |
| `GET /offers/{offerId}/schedule` | Solo destinatario; requiere generación previa. | 200 cronograma |
| `GET /notifications` | Solo avisos propios y contador pendiente. | 200 página |
| `PATCH /notifications/{notificationId}/read` | Solo destinatario; lectura idempotente, sin cuerpo. | 200 aviso |
| `POST /simulations/calculate` | CLIENT/REALTOR; guarda simulación propia. | 201 cronograma |
| `GET /simulations/{id}` | Solo propietario; heredadas sin owner no son accesibles. | 200 cronograma |
| `GET /banking/benchmarks` | Público; referencias simuladas en caché. | 200 lista |
| `GET /banking/banks/{bankCode}/offer` | CLIENT/REALTOR; cotización simulada, no persistida. | 200 referencia |

Registro y cálculo directo no emiten `Location`. Un UUID mal formado devuelve 400.
Un inmueble eliminado deja de aparecer y un segundo DELETE devuelve 404; cancelar en
la UI no envía DELETE. Ofertas, avisos y cronogramas ajenos responden 404 sin revelar
su existencia. No se expone edición PUT/PATCH de inmuebles.

## Formatos y errores

Importes y tasas de **catálogo, ofertas y simulaciones** se devuelven como cadenas
exactas sin notación científica: `"9999999999999999.99"`, `"0.00000001"`, `"0.00"`.
VAN puede ser negativo. Contadores, plazos, páginas y números de cuota siguen siendo
enteros JSON. Las entradas decimales aceptan números o cadenas interpretables como
BigDecimal; use cadenas para evitar pérdida de precisión en JavaScript.

El DTO heredado `BankRateResponseDto` conserva `minTea`, `maxTea`, `averageTea` y
`maxLtv` como números JSON. No confundir esa consulta simulada con `POST /offers`.
Las fechas de cuotas y desembolso son ISO `YYYY-MM-DD`, sin conversión de zona horaria.

La validación REST usa `{status,error,message,timestamp,validationErrors}`; los errores
TOTP agregan `code`. Los filtros de seguridad pueden responder 401/403 sin un cuerpo
JSON garantizado: el cliente debe comprobar el estado antes de interpretar el cuerpo.
OpenAPI no promete un esquema de contenido para esos errores de filtro.

| Estado | Casos relevantes |
| --- | --- |
| 400 | Campos, UUID o consultas inválidos; combinación financiera incoherente; `TWO_FACTOR_SETUP_EXPIRED`. |
| 401 | Bearer ausente/expirado/revocado; credenciales incorrectas; `TWO_FACTOR_REQUIRED`, `TWO_FACTOR_INVALID` o `INVALID_CREDENTIALS` según el flujo. |
| 403 | Rol insuficiente o intento de borrar/emitir sobre un inmueble ajeno. |
| 404 | Recurso no disponible para el principal, eliminado o cronograma no generado. |
| 409 | Correo ocupado/concurrencia de perfil; `TWO_FACTOR_ALREADY_ENABLED` o `TWO_FACTOR_NOT_ENABLED`. |
| 429 | `TWO_FACTOR_LOCKED`, con `Retry-After` en segundos. |
| 503 | `TWO_FACTOR_UNAVAILABLE`: configuración criptográfica ausente, incorrecta o secreto no descifrable. |

CORS expone `Retry-After`. Los DTO privados de ofertas/avisos/cronogramas y el setup
TOTP envían `Cache-Control: no-store`. El header `X-Response-Time-Ms` de benchmarks
mide únicamente esa llamada y no representa un SLA.

## Cuentas, perfil y sesión

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

## Catálogo, ofertas y avisos

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

## Cronogramas y precisión

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

## Pruebas e informes

`mvn verify` ejecuta JUnit, integración H2, nueve referencias financieras independientes,
siete escenarios Gherkin y el contrato OpenAPI servido por HTTP real. No requiere
Python, Docker, PostgreSQL ni bancos reales. Las fuentes del oráculo reproducible están
en `src/test/resources/finance_reference.py`; regeneración opcional con Python Decimal 300.

| Evidencia generada, ignorada por Git | Ruta |
| --- | --- |
| JUnit / integración / contrato | `target/surefire-reports/` |
| Gherkin por las siete historias | `target/cucumber/index.html`, `cucumber.json`, `results.xml` |
| JaCoCo sin umbral arbitrario | `target/site/jacoco/index.html`, `jacoco.xml` |
| Contrato OpenAPI del test HTTP | `target/openapi/generated.json` |

Gherkin traza US02, US03, US04, US08, US10, US11 y US14; US07 es dependencia del catálogo.
El repositorio frontend contiene pruebas Playwright contra API real.

`BrowserFixtureServer` vive solo en `src/test/java` y no se incluye en el jar productivo:
H2 forzado, loopback, puerto aleatorio, claves/cuentas aleatorias y oferta DEMO. Arranque:

```bash
mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/browser-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/browser-classpath.txt)" \
  com.fidiacorp.credicasa.testing.BrowserFixtureServer /ruta/privada/fixture.json
```

El destino debe ser nuevo. El manifiesto se publica atómicamente con permisos 0600:
`apiBaseUrl`, `client`, `otherClient`, `realtor` (id/email/password), `propertyId`, `offerId`,
`pid`. No incluye tokens ni claves AES/JWT. No hay endpoints productivos de fixture.
Ctrl-C o SIGTERM cierra Spring/H2 y elimina el manifiesto; SIGKILL requiere eliminar el
archivo residual manualmente. El reloj es real y hay que esperar al siguiente TOTP
tras consumir uno. No publicar ese archivo ni sus credenciales.
