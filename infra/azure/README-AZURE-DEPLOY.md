# Despliegue Cloud de CrediCasa en Microsoft Azure (Sección 5.4.1 y 5.4.2)

## Servicios Azure Seleccionados y Presupuesto Académico (Azure for Students — US\$ 100 gratis)

| Capa | Servicio Microsoft Azure | Configuración / SKU | Rol en CrediCasa | Costo Estimado Mensual |
|---|---|---|---|---:|
| **Frontend SPA** | **Azure Static Web Apps** | Free Tier (`TLS 1.3`, CDN global) | Aloja la SPA Vue 3.5 + SPA fallback | US\$ 0.00 |
| **Edge / Gateway** | **Azure Container Apps** (`api-gateway`) | `0.5 vCPU / 1 GiB`, Ingress HTTPS externo | Único punto de entrada `/api/v1/**`, CORS, rate limit, Swagger agregado | ~US\$ 6.00 |
| **5 Microservicios** | **Azure Container Apps** (5 apps internas) | `0.5 vCPU / 1 GiB` c/u, Ingress interno VNet | `identity-subscription`, `property-catalog`, `mortgage-pricing`, `quotation-deal`, `communication-notification` | ~US\$ 22.00 |
| **Base de Datos (PA-05)** | **Azure Database for PostgreSQL Flexible Server** | `Standard_B1ms` (Burstable, 32 GB, SSL) | 5 bases lógicas aisladas (`identity_db`, `property_db`, `pricing_db`, `quotation_db`, `communication_db`) | US\$ 0.00 (12 meses gratis Students) / ~US\$ 12.40 |
| **Mensajería Asíncrona** | **RabbitMQ** (Container App interna TCP :5672) | `0.25 vCPU / 0.5 GiB` | Broker AMQP para eventos Outbox (`credit.offer.issued.v1`) + DLQ | ~US\$ 3.00 |
| **Registro Imágenes** | **Azure Container Registry (ACR)** | Basic | Almacena las 6 imágenes Docker multi-stage | ~US\$ 5.00 |
| **Secretos** | **Azure Key Vault** | Standard (RBAC + Soft Delete) | `JWT_SECRET`, `TWO_FACTOR_ENCRYPTION_KEY`, credenciales DB/AMQP | < US\$ 0.10 |
| **Observabilidad** | **Azure Monitor + Log Analytics** | PerGB2018 (5 GB/mes gratis) | Logs JSON correlacionados por `X-Correlation-Id` y alertas | US\$ 0.00 |
| **TOTAL ESTIMADO** | — | — | **Cubierto >2.5 meses con Azure for Students (US\$ 100 gratis)** | **~US\$ 36.10 / mes** |

## Pasos de Despliegue Reproducible (1 Comando)
1. Activa tu suscripción gratuita con correo institucional `@upc.edu.pe` en `https://azure.microsoft.com/free/students/`.
2. Inicia sesión en tu terminal local: `az login`.
3. Ejecuta desde la raíz de `FidiaCorp-backend`:
   ```powershell
   powershell -ExecutionPolicy Bypass -File ".\infra\azure\deploy-azure.ps1" -ResourceGroup "rg-credicasa-prod" -Location "eastus2"
   ```
   El script reutiliza el ACR que ya exista en el grupo de recursos (o crea uno), publica allí las 6 imágenes y pasa ese mismo nombre a `main.bicep` (`acrName`), de modo que las Container Apps descargan las imágenes del registro correcto.
4. En `FidiaCorp-FrontEnd`, configura `VITE_API_BASE_URL=<gatewayUrl>/api/v1` y despliega `dist/` al Static Web App creado (`swa deploy ./dist`).
5. Verifica `GET <gatewayUrl>/actuator/health` (`200 UP`) y abre `<gatewayUrl>/swagger-ui.html`.
