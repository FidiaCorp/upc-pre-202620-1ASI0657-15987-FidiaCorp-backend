# CrediCasa — Backend Core Service (`mortgage-calculation-service`)
### FidiaCorp FinTech / PropTech — Plataforma de Crédito Hipotecario

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal%20%2F%20DDD-blue.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 1. Visión General del Proyecto

**CrediCasa** es la plataforma FinTech/PropTech desarrollada por **FidiaCorp** diseñada para revolucionar la adquisición y simulación de créditos hipotecarios en el mercado peruano. Conecta a compradores de vivienda con entidades financieras y salas de venta inmobiliarias, ofreciendo simulaciones exactas, transparentes y en tiempo real.

Este repositorio contiene el **Backend Core del Sistema**: el microservicio transaccional `mortgage-calculation-service`, desarrollado en **Java 21 con Spring Boot 3**, estructurado bajo los principios de **Clean Architecture**, **Arquitectura Hexagonal (Ports & Adapters)** y **Domain-Driven Design (DDD)**.

El frontend cliente está construido de forma desacoplada en **Vue 3 + Vite**, consumiendo las APIs RESTful expuestas por este backend.

---

## 2. Mapeo de los 3 Drivers Arquitectónicos en el Código

El diseño e implementación de este microservicio responde directamente a los tres Architectural Drivers seleccionados en el **Capítulo IV** del diseño arquitectónico:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MAPEO DE DRIVERS ARQUITECTÓNICOS                                │
├────────────────────────────────┬───────────────────────────────────────────────────────┤
│ Driver Arquitectónico          │ Componentes y Clases Clave en el Código               │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ DRIVER 1: SEGURIDAD            │ • SecurityConfig.java                                 │
│ (ASR-SEC)                      │ • JwtTokenProvider.java                               │
│ Autenticación y RBAC           │ • JwtAuthFilter.java                                  │
│                                │ • CustomUserDetailsService.java                       │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ DRIVER 2: RENDIMIENTO Y        │ • FrenchAmortizationEngine.java                       │
│ PRECISIÓN (ASR-PERF)           │ • FinancialMetricsCalculator.java                     │
│ Motor Francés Determinado      │ • SbsAdapter.java (@Cacheable sub-100ms)              │
│ y Métricas Financieras         │ • FrenchAmortizationEngineTest.java                   │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ DRIVER 3: MODIFICABILIDAD E    │ • BankingIntegrationPort.java (Puerto de Dominio)     │
│ INTEROPERABILIDAD (ASR-MOD)    │ • BcpBankingAdapter.java (Adaptador BCP)              │
│ Patrón Ports & Adapters        │ • InterbankBankingAdapter.java (Adaptador Interbank)  │
│ y Reglas ArchUnit              │ • BankingIntegrationCompositeAdapter.java             │
│                                │ • HexagonalArchitectureTest.java (ArchUnit)           │
└────────────────────────────────┴───────────────────────────────────────────────────────┘
```

---

### DRIVER 1: SEGURIDAD (ASR-SEC) — Autenticación y Control de Acceso

- **Sesiones sin estado (Stateless):** Configuración explícita en `SecurityConfig` utilizando `SessionCreationPolicy.STATELESS`. El servidor no retiene sesiones HTTP ni cookies, garantizando escalabilidad horizontal.
- **Generación y Validación JWT:** `JwtTokenProvider` implementa tokens firmados mediante HMAC-SHA256 con claves criptográficas seguras, codificando el identificador del usuario y sus roles asignados (`roles`).
- **Control de Acceso Basado en Roles (RBAC):**
  - `ROLE_CLIENT`: Comprador de vivienda; facultado para simular escenarios y consultar ofertas.
  - `ROLE_REALTOR`: Asesor inmobiliario en sala de ventas; facultado para gestionar propuestas y cotizaciones con clientes.
  - Endpoints protegidos mediante anotaciones de seguridad declarativa `@PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")`.
- **Encriptación con BCrypt:** Las contraseñas se gestionan encriptadas con `BCryptPasswordEncoder` (fuerza de cálculo configurable).
- **Filtro de Seguridad:** `JwtAuthFilter` intercepta cada petición entrante, verifica el encabezado `Authorization: Bearer <token>` y propaga la identidad autenticada al contexto de seguridad (`SecurityContextHolder`).

---

### DRIVER 2: RENDIMIENTO Y PRECISIÓN (ASR-PERF) — Motor Financiero Determinado

- **Método Francés Ordinario Vencido (Convención 30/360 Bancaria Peruana):**
  - Conversión estricta de Tasa Efectiva Anual (TEA) a Tasa Efectiva Mensual (TEM):
    $$\text{TEM} = (1 + \text{TEA})^{30/360} - 1 = (1 + \text{TEA})^{1/12} - 1$$
- **Aritmética de Alta Precisión con `BigDecimal`:**
  - Tasas y factores calculados con escala extendida (`MathContext.DECIMAL128` y escala 6/8).
  - Redondeo bancario monetario **`RoundingMode.HALF_EVEN`** a 2 decimales para importes de cuotas, intereses, seguros y amortizaciones.
- **Liquidación Estricta del Saldo Final a 0.00 Exacto (Architectural Concern AC-02):**
  - En la cuota final $N$, la amortización liquida exactamente el saldo inicial restante:
    $$A_N = S_{N-1}, \quad R_N = A_N + I_N, \quad S_N = 0.00$$
  - La suma de todas las amortizaciones coincide al centavo con el importe del préstamo otorgado.
- **Soporte de Variantes del Producto Financiero:**
  - **Gracia Total (`TOTAL`):** Los intereses generados se capitalizan al saldo deudor ($S_k = S_{k-1} + I_k$), amortización 0.00. Al concluir la gracia, se recalcula la cuota fija sobre el saldo capitalizado y los meses restantes.
  - **Gracia Parcial (`PARTIAL`):** Se cancelan intereses generados mensualmente, la amortización es 0.00 y el saldo permanece constante. Al concluir, se amortiza en el plazo remanente.
  - **Cuota Balón (`BalloonPayment`):** Soporte de pagos extraordinarios al vencimiento con deducción en el valor actual de la anualidad regular.
- **Métricas Financieras Completas:**
  - **TIR Mensual:** Solver numérico robusto en `FinancialMetricsCalculator` que combina el método acelerado de **Newton-Raphson** con respaldo por **Bisección** (criterio AC-04) sobre los flujos de caja del deudor.
  - **TCEA Real Anualizada:**
    $$\text{TCEA} = (1 + \text{TIR}_{\text{mensual}})^{12} - 1$$
    Incorpora cuota de amortización, intereses, seguro de desgravamen, seguro multirriesgo del bien y cargos fijos.
  - **VAN (Valor Actual Neto):** Descontado a la tasa de oportunidad de capital (COK) o TEA de referencia.
- **Caché en Memoria para Benchmarks SBS (Sub-100 ms):**
  - `SbsAdapter.fetchOfficialBenchmarks` utiliza `@Cacheable(value = "sbsRates", key = "#currency")`.
  - La primera consulta procesa y normaliza el boletín estadístico; las subsecuentes responden en menos de 5 ms desde memoria.

---

### DRIVER 3: MODIFICABILIDAD E INTEROPERABILIDAD (ASR-MOD) — Patrón Ports & Adapters

- **Arquitectura Hexagonal (DDD):**
  - **Capa de Dominio (`domain`):** Núcleo agnóstico de frameworks, JPA y Spring. Define entidades (`Quotation`, `Installment`, `BankRateBenchmark`), value objects, servicios de cálculo y los puertos (`BankingIntegrationPort`, `QuotationRepositoryPort`).
  - **Capa de Aplicación (`application`):** Orquesta los casos de uso (`CalculateCreditSimulationService`, `GetBankRatesService`) y traduce entre DTOs y el modelo de dominio.
  - **Capa de Infraestructura (`infrastructure`):** Implementa los adaptadores técnicos (`BcpBankingAdapter`, `InterbankBankingAdapter`, `SbsAdapter`, `QuotationRepositoryAdapter`, seguridad JWT).
  - **Capa de Interfaces (`interfaces`):** Controladores REST (`SimulationController`, `BankRatesController`, `AuthController`) y configuración OpenAPI Swagger.
- **Inversión de Dependencias:** El dominio define el contrato `BankingIntegrationPort`. Los adaptadores externos se encargan de desacoplar los formatos propietarios (BCP e Interbank) convirtiéndolos al modelo canónico común.
- **Garantía Automatizada con ArchUnit:** La clase de test `HexagonalArchitectureTest` valida de forma continua en el pipeline que `domain` no tenga dependencias sobre `infrastructure` ni `interfaces`.

---

## 3. Estructura del Repositorio

```
credicasa-backend/
├── pom.xml                                   # Configuración de dependencias Maven
├── Dockerfile                                # Multi-stage build (Builder + Runtime seguro)
├── docker-compose.yml                        # Orquestación de PostgreSQL 16 + Backend
├── README.md                                 # Documentación técnica del microservicio
└── src/
    ├── main/
    │   ├── java/com/fidiacorp/credicasa/
    │   │   ├── CrediCasaApplication.java    # Punto de entrada Spring Boot & IoC Beans
    │   │   │
    │   │   ├── domain/                       # NÚCLEO DE DOMINIO (Agnóstico a frameworks)
    │   │   │   ├── model/                    # Entidades y Value Objects
    │   │   │   │   ├── Quotation.java        # Aggregate Root de la simulación
    │   │   │   │   ├── Installment.java      # Detalle de cuota mensual
    │   │   │   │   ├── FinancialMetrics.java # TEA, TEM, TIR, TCEA, VAN
    │   │   │   │   ├── BankRateBenchmark.java# Benchmark bancario canónico
    │   │   │   │   ├── GracePeriod.java      # Período de gracia (NONE, PARTIAL, TOTAL)
    │   │   │   │   ├── BalloonPayment.java   # Cuota balón
    │   │   │   │   ├── PropertySnapshot.java # Inmueble evaluado
    │   │   │   │   └── ClientProfile.java    # Perfil del comprador
    │   │   │   ├── ports/in/                 # Casos de uso del dominio (Inbound Ports)
    │   │   │   │   ├── CalculateScheduleUseCase.java
    │   │   │   │   ├── CalculateScheduleCommand.java
    │   │   │   │   └── GetBankRatesUseCase.java
    │   │   │   ├── ports/out/                # Interfaces de salida (Outbound Ports)
    │   │   │   │   ├── QuotationRepositoryPort.java
    │   │   │   │   └── BankingIntegrationPort.java
    │   │   │   └── service/                  # Servicios de dominio matemáticos
    │   │   │       ├── FrenchAmortizationEngine.java
    │   │   │       └── FinancialMetricsCalculator.java
    │   │   │
    │   │   ├── application/                  # CAPA DE APLICACIÓN
    │   │   │   ├── dto/request/              # SimulationRequestDto, AuthRequestDto
    │   │   │   ├── dto/response/             # SimulationResponseDto, InstallmentDto, BankRateResponseDto
    │   │   │   └── usecase/                  # Servicios coordinadores de aplicación
    │   │   │       ├── CalculateCreditSimulationService.java
    │   │   │       └── GetBankRatesService.java
    │   │   │
    │   │   ├── infrastructure/               # CAPA DE INFRAESTRUCTURA
    │   │   │   ├── adapters/banking/         # Adaptadores bancarios e integración SBS
    │   │   │   │   ├── BcpBankingAdapter.java
    │   │   │   │   ├── InterbankBankingAdapter.java
    │   │   │   │   ├── SbsAdapter.java (@Cacheable)
    │   │   │   │   └── BankingIntegrationCompositeAdapter.java
    │   │   │   ├── persistence/              # Persistencia JPA y Adaptadores
    │   │   │   │   ├── entity/               # QuotationJpaEntity, InstallmentJpaEntity
    │   │   │   │   ├── repository/           # QuotationJpaRepository (Spring Data)
    │   │   │   │   └── QuotationRepositoryAdapter.java
    │   │   │   └── security/                 # Seguridad JWT y RBAC
    │   │   │       ├── JwtTokenProvider.java
    │   │   │       ├── JwtAuthFilter.java
    │   │   │       ├── CustomUserDetailsService.java
    │   │   │       └── SecurityConfig.java
    │   │   │
    │   │   └── interfaces/rest/              # CAPA DE INTERFACES (API REST y Swagger)
    │   │       ├── AuthController.java
    │   │       ├── SimulationController.java
    │   │       ├── BankRatesController.java
    │   │       ├── OpenApiConfig.java
    │   │       └── RestExceptionHandler.java
    │   │
    │   └── resources/
    │       ├── application.yml               # Configuración del microservicio
    │       └── db/migration/
    │           └── V1__init_schema.sql       # Script de inicialización DDL PostgreSQL
    │
    └── test/java/com/fidiacorp/credicasa/
        ├── domain/service/
        │   └── FrenchAmortizationEngineTest.java   # Tests unitarios del motor (200k a 20 años)
        └── architecture/
            └── HexagonalArchitectureTest.java      # Tests de arquitectura con ArchUnit
```

---

## 4. Cuentas persistentes y perfil (US02)

No se crean cuentas de demostración al iniciar. Registre una cuenta mediante
`POST /api/v1/auth/register` con `firstName`, `lastName`, `email` y `password`.
El registro público asigna únicamente `ROLE_CLIENT`; no permite elegir roles.
Las cuentas se conservan en PostgreSQL y las contraseñas se almacenan con BCrypt.

Los nombres son obligatorios (máximo 100 caracteres), el correo es válido, único,
normalizado a minúsculas y tiene máximo 254 caracteres. La contraseña requiere
entre 12 y 72 caracteres y no debe superar 72 bytes UTF-8.

Con el token, `GET /api/v1/users/me` obtiene el perfil y `PUT /api/v1/users/me`
actualiza `firstName`, `lastName` y `email` (todos obligatorios). La respuesta es
`{id, firstName, lastName, email, roles}`. El UUID del token permanece estable al
cambiar el correo; los próximos inicios de sesión utilizan el correo nuevo.
Los errores de validación devuelven 400 con `validationErrors`, los correos
ocupados 409 y el acceso sin sesión válida 401.

La tabla `accounts` se genera con el `ddl-auto=update` actual. El archivo
`V2__accounts.sql` documenta el DDL para instalaciones con gestión manual del
esquema; el proyecto no ejecuta Flyway automáticamente.

---

### Verificación en dos pasos (TOTP)

Configure `TWO_FACTOR_ENCRYPTION_KEY` con una clave aleatoria de **32 bytes en
Base64**, almacenada en el gestor de secretos del despliegue y conservada entre
reinicios. Puede generar una clave con `openssl rand -base64 32`; no la registre
en Git, logs ni documentación. Sin una clave válida el servicio mantiene las
cuentas normales disponibles, pero setup y validación de secretos 2FA devuelven
503 `TWO_FACTOR_UNAVAILABLE`. Cambiar o perder esta clave impide descifrar los
secretos existentes; no existe clave predeterminada ni recuperación automática.
Docker Compose admite inyectarla con la variable del mismo nombre.

Flujo con `Authorization: Bearer <token>`:

- `GET /api/v1/users/me/two-factor` devuelve `{enabled, pending}`, sin secretos.
- `POST /api/v1/users/me/two-factor/setup` con `{password}` devuelve
  `{secret, otpauthUri, expiresIn:600}` y `Cache-Control: no-store`. Introduzca
  el secreto manualmente en su autenticador o importe la URI compatible.
  Antes de activar puede regenerar la configuración: la anterior deja de servir.
  Esta operación exige contraseña correcta y no reinicia los intentos fallidos.
- `POST /api/v1/users/me/two-factor/confirm` con `{otp}` activa la configuración
  pendiente antes de los diez minutos y devuelve `{enabled:true,pending:false}`.
- `POST /api/v1/users/me/two-factor/disable` con `{password,otp}` exige contraseña
  y un código nuevo, y devuelve `{enabled:false,pending:false}`.

Para una cuenta activa, `POST /api/v1/auth/login` acepta `{email,password,otp}`.
La contraseña se valida primero; sin el segundo factor válido no se emite JWT.
La respuesta de éxito mantiene `{token,type,email,roles,expiresIn}`. Las sesiones
JWT emitidas previamente conservan su vigencia hasta expirar o cerrar la sesión
mediante `/api/v1/auth/logout`; activar 2FA no revoca otras sesiones.

Los errores 2FA incluyen `code` además de `status`, `error`, `message`, `timestamp`
y `validationErrors`. Login sin código devuelve 401 `TWO_FACTOR_REQUIRED`;
código incorrecto, expirado o reutilizado devuelve 401 `TWO_FACTOR_INVALID`.
Contraseña incorrecta en gestión devuelve 401 `INVALID_CREDENTIALS`; estos
errores de validación no implican que el JWT presentado haya expirado.
Setup ya activo devuelve 409 `TWO_FACTOR_ALREADY_ENABLED`, desactivación sin
2FA activo 409 `TWO_FACTOR_NOT_ENABLED`, y confirmación vencida 400
`TWO_FACTOR_SETUP_EXPIRED`. Cuerpos mal formados o campos inválidos devuelven 400
con el formato de validación habitual.

Cinco fallos de OTP o de contraseña de gestión bloquean las operaciones 2FA y
los nuevos ingresos durante quince minutos: 429 `TWO_FACTOR_LOCKED` con
`Retry-After` en segundos. Intentos y bloqueo son persistentes, compartidos por
cuenta y no se reinician al regenerar setup ni al abrir una sesión nueva.
Confirmar, ingresar con segundo factor o desactivar correctamente reinicia los
fallos. Los errores de contraseña del login primario mantienen la autenticación
habitual y no incrementan este contador específico del segundo factor.

El algoritmo sigue [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238.html):
HMAC-SHA1, secreto aleatorio de 20 bytes, seis dígitos y pasos de 30 segundos.
Solo se admite el paso actual, sin tolerancia para códigos expirados ni futuros;
sincronice el reloj del servidor y del autenticador. Cada código se consume una
sola vez, incluso entre solicitudes concurrentes, mediante bloqueo transaccional
y contador persistente. Después de confirmar o ingresar espere al siguiente
código antes de otra operación. Los secretos se cifran en reposo con AES-256-GCM,
nonce aleatorio y UUID de la cuenta como datos autenticados. El secreto legible
solo se devuelve al generar una configuración pendiente; no se devuelve al
consultar el estado, perfil o una configuración activa.

Pruebas `TotpSecurityTest` y `TwoFactorIntegrationTest`: vectores oficiales RFC,
contador de 64 bits, cifrado, clave ausente/incorrecta, reloj fijo, vencimientos,
consumo concurrente, bloqueo persistente y autorización antes de emitir JWT.

---

### Cierre de sesión y revocación del token actual

`POST /api/v1/auth/logout`, sin cuerpo y con `Authorization: Bearer <token>`,
revoca la sesión presentada y devuelve **204 sin contenido**. Este endpoint
requiere autenticación incluso dentro del prefijo público `/auth`.
Un token ausente, inválido, expirado o ya revocado devuelve 401.

Solo se revoca ese token: otras sesiones de la misma cuenta permanecen válidas.
Cada nuevo JWT incluye un `jti` aleatorio, por lo que ingresar inmediatamente
después del cierre genera un token diferente. Los campos de la respuesta de
login no cambian. El servidor ignora cualquier token o identificador enviado
en el cuerpo de logout; utiliza exclusivamente el Bearer autenticado.

La tabla `revoked_tokens` conserva únicamente el hash SHA-256 y la expiración
del JWT, nunca el token completo. El filtro consulta esta revocación persistente
en cada petición con un JWT válido; reiniciar el servicio no restaura tokens
revocados mientras se conserve la base de datos. Dos cierres concurrentes del
mismo token no generan registros duplicados. Una solicitud que ya superó la
validación antes del cierre puede terminar; las verificaciones posteriores
rechazan el token revocado.

La limpieza programada elimina registros expirados cada hora. Puede ajustar el
intervalo con `app.security.jwt.revocation-cleanup-ms` (milisegundos). El JWT
expirado se rechaza aunque su registro aún no se haya eliminado. El archivo
`V4__token_revocation.sql` incluye el DDL para gestión manual del esquema; la
configuración actual crea la tabla mediante Hibernate `ddl-auto=update`.

Ante un fallo de red o de servidor, el cliente no puede asumir que la revocación
se completó. Debe eliminar la sesión local y puede reintentar el cierre de ese
mismo token sin restaurar su acceso. Si el servidor responde 401, ese token ya
no permite acceso autenticado.

CORS expone `Retry-After` además de `Authorization`, permitiendo que la UI lea
la espera real del bloqueo 2FA en llamadas entre orígenes. Las pruebas cubren el
preflight autenticado y la exposición de esa cabecera en una respuesta 429.

`SessionLogoutIntegrationTest` verifica aislamiento entre sesiones, hash y
expiración persistidos, lectura desde una nueva instancia del servicio, tokens
inmediatos distintos, concurrencia, limpieza de expirados y CORS.

---

### Catálogo persistente de inmuebles

Los endpoints `/api/v1/properties` requieren JWT y rol `ROLE_CLIENT` o
`ROLE_REALTOR`. Ambos roles pueden registrar inmuebles y consultar el catálogo;
ningún rol puede eliminar un inmueble de otro propietario.

`POST /api/v1/properties` devuelve **201** con cabecera `Location`. Solicitud:

```json
{
  "title": "Departamento con terraza",
  "address": "Av. Central 120, Lima",
  "description": "Departamento luminoso cerca de servicios",
  "price": "250000.50",
  "currency": "PEN",
  "tags": ["terraza", "estacionamiento"]
}
```

`title` y `address` son obligatorios, se recortan y admiten hasta 120 y 250
caracteres respectivamente. `description` es opcional, máximo 2000 caracteres,
y se conserva como cadena vacía si no se envía. `price` debe ser al menos 0.01,
con hasta 16 dígitos enteros y 2 decimales; acepta número JSON o cadena decimal
para evitar pérdida de precisión en clientes JavaScript. `currency` solo admite
`PEN` y `USD`. `tags` es opcional, máximo 20 elementos de hasta 40 caracteres;
no admite etiquetas vacías. Se normalizan con Unicode NFKC, minúsculas, recorte
y espacios internos únicos; se eliminan duplicados y se ordenan. Por ejemplo,
`[" ＴＥＲＲＡＺＡ ","terraza","Con   Estacionamiento"]` se guarda como
`["con estacionamiento","terraza"]`.

El propietario se obtiene exclusivamente del UUID del principal autenticado;
un `ownerId` enviado en el cuerpo no cambia esa identidad. La respuesta de
registro y `GET /api/v1/properties/{id}` contienen
`{id,ownerId,title,address,description,price,currency,tags,createdAt}`.
`price` siempre se devuelve como cadena decimal exacta, incluido el máximo
`"9999999999999999.99"`; el cliente no debe convertirlo a `Number` para transportarlo.
`createdAt` utiliza ISO8601 con zona UTC. El UUID propio está disponible en
`GET /api/v1/users/me`; los clientes pueden compararlo con `ownerId`.

`GET /api/v1/properties` admite:

- `q`: búsqueda literal sin distinguir mayúsculas en título, dirección o
  descripción; máximo 120 caracteres. `%`, `_` y barras invertidas no son
  comodines SQL. Un término vacío no filtra.
- `tags`: parámetros repetidos, por ejemplo
  `?tags=terraza&tags=estacionamiento`. Se normalizan igual que al registrar y
  se exigen **todas** las etiquetas seleccionadas, con un máximo de 20.
- `mine=true`: limita el catálogo al usuario autenticado; por defecto `false`.
- `page`: página desde 0; `size`: por defecto 20 y entre 1 y 100.

Texto, etiquetas, propietario y estado activo se combinan con AND. El orden es
`createdAt DESC, id DESC`, estable entre páginas. Respuesta:

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

`DELETE /api/v1/properties/{id}` devuelve **204** solo para el propietario. Un
tercero recibe 403 y no modifica datos. La eliminación establece `deletedAt`:
el inmueble deja de aparecer en consultas, filtros y detalle. Un ID inexistente
o eliminado, incluido un segundo intento de eliminación, devuelve 404. Cancelar
la confirmación en la interfaz no debe enviar DELETE. No se expone PUT ni PATCH.

Las cotizaciones mantienen sus instantáneas independientes: no se cambian
`PropertySnapshot`, `Quotation`, cronogramas ni importes al eliminar del
catálogo. La prueba de integración compara una simulación persistida antes y
después de eliminar el inmueble.

Entradas inválidas y paginación fuera de límites devuelven 400; token ausente o
inválido 401; rol no permitido 403. `V5__properties.sql` incluye tablas e índices
para gestión manual; el despliegue actual usa Hibernate `ddl-auto=update`.

---

## 5. Instrucciones de Ejecución

### Opción A: Despliegue con Docker Compose (Recomendada)

Requiere tener Docker Desktop instalado y en ejecución:

```bash
# 1. Clonar o ubicarse en la raíz del repositorio backend
cd credicasa-backend

# 2. Levantar la base de datos PostgreSQL 16 y el microservicio
docker-compose up --build
```

El servicio estará disponible en `http://localhost:8080`.

### Opción B: Ejecución Local con Maven

Requiere Java 21 y Maven (o utilizar el wrapper):

```bash
# 1. Ejecutar las pruebas unitarias y de arquitectura
mvn test

# 2. Iniciar el servicio localmente
mvn spring-boot:run
```

---

## 6. Documentación Interactiva OpenAPI / Swagger UI

Una vez iniciado el microservicio, la documentación interactiva Swagger está accesible en:

> **URL Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
> **Especificación OpenAPI (JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Para probar endpoints protegidos desde Swagger:
1. Registrar una cuenta en `POST /api/v1/auth/register`.
2. Autenticarse en `POST /api/v1/auth/login` con el correo y contraseña registrados.
3. Copiar el `token` devuelto.
4. Hacer clic en el botón superior **Authorize** (candado) y pegar el token.

---

## 7. Guía Rápida de Consumo de APIs (cURL)

### 1. Registrar cuenta e iniciar sesión

```bash
curl -X POST "http://localhost:8080/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Lucía","lastName":"Pérez","email":"lucia@example.com","password":"MiClaveSegura2026!"}'
```

Use una contraseña propia. Después solicite el token:


```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "lucia@example.com",
    "password": "MiClaveSegura2026!"
  }'
```

**Respuesta (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "email": "lucia@example.com",
  "roles": ["ROLE_CLIENT"],
  "expiresIn": 86400000
}
```

---

### 2. Consultar Benchmarks del Mercado SBS (Caché Sub-100 ms)

```bash
curl -X GET "http://localhost:8080/api/v1/banking/benchmarks?currency=PEN"
```

*Verifique el encabezado HTTP devuelto `X-Response-Time-Ms`: en llamadas subsecuentes responderá en < 5 ms gracias a la caché `@Cacheable`.*

---

### 3. Simular Crédito Hipotecario (Escenario S/. 200,000 a 20 años)

```bash
curl -X POST "http://localhost:8080/api/v1/simulations/calculate" \
  -H "Authorization: Bearer <TU_TOKEN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "propertyValue": 250000.00,
    "loanAmount": 200000.00,
    "currency": "PEN",
    "termYears": 20,
    "annualInterestRate": 0.085,
    "gracePeriodType": "NONE",
    "gracePeriodMonths": 0,
    "lifeInsuranceRate": 0.0005,
    "propertyInsuranceRate": 0.00025,
    "monthlyAdminFee": 10.00,
    "startDate": "2026-11-01",
    "propertyAddress": "Av. Dos de Mayo 1420, San Isidro, Lima",
    "clientProfile": {
      "documentNumber": "74839201",
      "fullName": "Juan Carlos Pérez Valdivia",
      "email": "juan.perez@gmail.com",
      "monthlyIncome": 9200.00,
      "creditScore": 790
    }
  }'
```

**Respuesta destacada:**
- `loanAmount`: `200000.00`
- `termMonths`: `240`
- `metrics.tea`: `0.085000` (8.50%)
- `metrics.tem`: `0.006821` (0.68% mensual)
- `metrics.tcea`: `0.097652` (9.77% real anualizada)
- `schedule[239].finalBalance`: `0.00` **(Liquidación exacta al centavo)**
- `schedule[239].principalAmortization`: absorbe el saldo anterior residual.

---

### 4. Simulación con Período de Gracia Total (6 meses)

```bash
curl -X POST "http://localhost:8080/api/v1/simulations/calculate" \
  -H "Authorization: Bearer <TU_TOKEN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "propertyValue": 220000.00,
    "loanAmount": 180000.00,
    "currency": "PEN",
    "termMonths": 180,
    "annualInterestRate": 0.089,
    "gracePeriodType": "TOTAL",
    "gracePeriodMonths": 6,
    "lifeInsuranceRate": 0.0005,
    "propertyInsuranceRate": 0.00025,
    "monthlyAdminFee": 10.00
  }'
```

---

## 8. Batería de Pruebas Automatizadas

El proyecto incluye dos suites de pruebas automatizadas que certifican los drivers de calidad:

### 1. Pruebas Unitarias Financieras (`FrenchAmortizationEngineTest`)
- **Escenario Mandatorio:** Crédito de S/. 200,000.00 a 20 años (240 cuotas) a 8.5% TEA.
- **Validaciones:**
  - Verifica que cada una de las 240 cuotas cumpla la coherencia contable del método francés.
  - Verifica que el saldo final de la cuota 240 sea **`0.00` exacto**.
  - Verifica que la suma total de las amortizaciones mensuales equivalga a **`200000.00` exacto**.
  - Comprueba variantes con **Gracia Total** (capitalización de intereses), **Gracia Parcial** (pago de intereses sin amortización) y **Cuota Balón**.
  - Comprueba que la **TCEA** resuelta mediante el solver numérico sea estrictamente superior a la TEA pactada.

### 2. Pruebas de Arquitectura Hexagonal (`HexagonalArchitectureTest`)
- Utiliza **ArchUnit 1.3.0** para auditar el árbol de clases compilado:
  - `domainMustNotDependOnInfrastructure`: El dominio no posee referencias a clases dentro de `infrastructure`.
  - `domainMustNotDependOnApplication`: El dominio no depende de la orquestación de aplicación.
  - `domainMustNotDependOnInterfaces`: El dominio ignora la existencia de controladores REST o Swagger.
  - `applicationMustNotDependOnInfrastructure`: La aplicación no conoce implementaciones concretas de base de datos o adaptadores bancarios externos.

---

## 9. Contacto y Equipo de Desarrollo

- **Startup:** FidiaCorp S.A.C.
- **Producto:** CrediCasa Hipotecario
- **Email:** architecture@fidiacorp.pe
- **Licencia:** Apache License 2.0
