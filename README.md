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

## 4. Credenciales de Prueba (RBAC)

El sistema provee usuarios de prueba precargados con contraseñas encriptadas con **BCrypt**:

| Rol | Correo Electrónico | Contraseña | Descripción de Acceso |
| :--- | :--- | :--- | :--- |
| **`ROLE_CLIENT`** | `cliente@credicasa.pe` | `Cliente123!` | Comprador de vivienda. Puede simular créditos hipotecarios y comparar ofertas bancarias. |
| **`ROLE_REALTOR`** | `asesor@credicasa.pe` | `Asesor123!` | Asesor inmobiliario en sala de ventas. Acceso a simulación comercial y cotizaciones. |
| **`ADMIN`** (Ambos) | `admin@credicasa.pe` | `Admin123!` | Acceso completo a todos los endpoints. |

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
1. Dirigirse al endpoint `POST /api/v1/auth/login`.
2. Autenticarse con `cliente@credicasa.pe` / `Cliente123!`.
3. Copiar el `token` devuelto.
4. Hacer clic en el botón superior **Authorize** (candado) y pegar el token.

---

## 7. Guía Rápida de Consumo de APIs (cURL)

### 1. Iniciar Sesión y Obtener Token JWT

```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "asesor@credicasa.pe",
    "password": "Asesor123!"
  }'
```

**Respuesta (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "email": "asesor@credicasa.pe",
  "roles": ["ROLE_REALTOR"],
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
