# Experimentos Empíricos de Atributos de Calidad — CrediCasa (QA-01 a QA-04)

Suite reproducible de validación empírica (enfoque GQM / Wohlin) diseñada para validar los **Architectural Drivers** del Capítulo IV y ejecutar la **Demostración en Vivo de 3 Drivers** en la Exposición Final (TF1).

## Resumen de Resultados Reales Verificados (25/11/2026 — Stack Docker 10/10 Healthy)

| Experimento | Driver Arquitectónico | Instrumento | Estímulo Real Ejecutado | Métricas Empíricas Medias/Percentiles | Veredicto |
|---|---|---|---|---|---|
| **QA-01** | Rendimiento y Exactitud Determinista (`ASR-PERF`, `AC-02`) | `grafana/k6` (`qa01-k6/qa01-simulation-load.js`) | Warm-up `5 RPS × 15s` + carga sostenida **`20 RPS × 60s`** (`1,277` simulaciones de **360 cuotas**, gracia total + balón + seguros vía `api-gateway:8080`) | `5,108/5,108` checks (`100%`), errores `0.00%` (`0/1,201`), **`P50 = 114.27 ms`**, **`P95 = 155.88 ms < 350 ms`**, `max = 491.71 ms < 800 ms`, `100%` `finalBalance == "0.00"` | **PASS** |
| **QA-02** | Disponibilidad y Degradación Controlada (`ASR-AVAIL`, `ADR-003`) | `qa02-resilience/qa02-chaos-experiment.mjs` + Docker Chaos | Parte A: `docker stop external-bank-mock` → 12 reqs → medición OPEN → `docker start`. Parte B: `docker stop communication-notification-service` → `POST /offers` → `docker start` | Parte A: `CLOSED → OPEN` (`60%` fail rate), fallback **`P95 = 5.3 ms < 100 ms`** (`FALLBACK_CACHED`, `fallbackActive=true`), cálculo core `201 OK (76.8 ms)`, recuperación automática `OPEN → HALF_OPEN → CLOSED`. Parte B: oferta `201 (125 ms)` → entrega diferida idempotente de **1 notificación exacta** vía Outbox→RabbitMQ→Inbox | **PASS (8/8)** |
| **QA-03** | Seguridad Adversarial OWASP API1/API2/API5 (`ASR-SEC`) | `postman/newman:alpine` (`qa03-security/qa03-bola-security.postman_collection.json`) | **20 vectores adversariales** (12 BOLA/IDOR, 2 BFLA, 3 JWT/auth, 2 Gateway internal API & path traversal, 1 JWT revocado tras logout) | **`20/20` requests, `21/21` assertions PASS (`0` failed)**, tiempo total `721 ms` (media `19 ms`), `0` fugas de datos ni stacktraces | **PASS (20/20)** |
| **QA-04** | Modificabilidad y Gobernanza Hexagonal (`ASR-MOD`) | `qa04-archunit/qa04-mutation-experiment.ps1` (ArchUnit 1.3.0) | Baseline en los 5 MS (`35/35` reglas) → inyección ilegal `domain -> application.dto` en `FrenchAmortizationEngine` → restauración y re-verificación | Baseline `35/35 PASS`; mutación bloqueada determinísticamente en **`2.34 s`** (`domainMustNotDependOnApplication` + `hexagonalLayersRespectDependencies`); restauración `7/7 PASS (2.11 s)` | **PASS** |

## Comandos de Reproducción
- **QA-01 (`k6`):** `node experiments/prepare-fixtures.mjs` y luego `docker run --rm --add-host=host.docker.internal:host-gateway -e JWT=... -v "$PWD/experiments/qa01-k6:/scripts" grafana/k6 run /scripts/qa01-simulation-load.js`
- **QA-02 (Caos):** `node experiments/qa02-resilience/qa02-chaos-experiment.mjs`
- **QA-03 (Newman):** `docker run --rm --add-host=host.docker.internal:host-gateway -v "$PWD/experiments/qa03-security:/etc/newman" postman/newman:alpine run /etc/newman/qa03-bola-security.postman_collection.json -e /etc/newman/qa03-env.json`
- **QA-04 (ArchUnit):** `powershell -ExecutionPolicy Bypass -File experiments/qa04-archunit/qa04-mutation-experiment.ps1`
