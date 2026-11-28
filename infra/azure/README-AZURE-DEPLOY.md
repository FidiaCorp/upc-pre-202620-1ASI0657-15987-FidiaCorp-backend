# Despliegue Cloud de CrediCasa en Microsoft Azure (Sección 5.4.1 y 5.4.2)

## Servicios Azure Seleccionados y Presupuesto Académico (Azure for Students — US\$ 100 gratis)

| Capa | Servicio Microsoft Azure | Configuración / SKU | Rol en CrediCasa | Costo Estimado Mensual |
|---|---|---|---|---:|
| **Frontend SPA** | **Azure Static Web Apps** | Free Tier (`TLS 1.3`, CDN global) | Aloja la SPA Vue 3.5 + SPA fallback | US\$ 0.00 |
| **Edge / Gateway** | **Azure Container Apps** (`api-gateway`) | `0.5 vCPU / 1 GiB`, Ingress HTTPS externo | Único punto de entrada `/api/v1/**`, CORS limitado al hostname exacto de la Static Web App, rate limit, Swagger agregado | ~US\$ 6.00 |
| **5 Microservicios** | **Azure Container Apps** (5 apps internas) | `0.5 vCPU / 1 GiB` c/u, Ingress interno, probes Startup/Liveness/Readiness | `identity-subscription`, `property-catalog`, `mortgage-pricing`, `quotation-deal`, `communication-notification` | ~US\$ 22.00 |
| **Base de Datos (PA-05)** | **Azure Database for PostgreSQL Flexible Server** | `Standard_B1ms` (Burstable, 32 GB, SSL) | 5 bases lógicas aisladas (`identity_db`, `property_db`, `pricing_db`, `quotation_db`, `communication_db`) | US\$ 0.00 (12 meses gratis Students) / ~US\$ 12.40 |
| **Mensajería Asíncrona** | **RabbitMQ** (Container App interna TCP :5672) | `0.25 vCPU / 0.5 GiB` | Broker AMQP para eventos Outbox (`credit.offer.issued.v1`) + DLQ | ~US\$ 3.00 |
| **Registro Imágenes** | **Azure Container Registry (ACR)** | Basic | Almacena las 6 imágenes Docker multi-stage | ~US\$ 5.00 |
| **Red** | **Azure Virtual Network** | `10.40.0.0/16`, subred `aca-infra` `/23` delegada a `Microsoft.App/environments` | Entorno de Container Apps con VNet propia (requisito del ingreso TCP de RabbitMQ) | US\$ 0.00 |
| **Secretos** | **Azure Key Vault** | Standard (RBAC + Soft Delete) | Aprovisionado; en esta versión los secretos se inyectan como secretos de Container Apps (parámetros `@secure()`) y aún no se leen desde Key Vault | < US\$ 0.10 |
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
4. En `FidiaCorp-FrontEnd`, configura `VITE_API_BASE_URL=<gatewayUrl>/api/v1`, ejecuta `pnpm build` y despliega `dist/` a la Static Web App creada. `public/staticwebapp.config.json` se copia a `dist/` y define el fallback SPA a `index.html` y las cabeceras de seguridad:
   ```powershell
   $token = az staticwebapp secrets list --resource-group rg-credicasa-prod --name <nombre-swa> --query properties.apiKey -o tsv
   npx @azure/static-web-apps-cli deploy ./dist --deployment-token $token --env production
   ```
5. Verifica `GET <gatewayUrl>/actuator/health` (`200 UP`) y abre `<gatewayUrl>/swagger-ui.html`.

## Health probes
Cada contenedor Java declara tres probes HTTP sobre Spring Boot Actuator (`management.endpoint.health.probes.enabled=true`):
- **Startup** `GET /actuator/health/liveness` (espera inicial 20 s, cada 15 s, hasta 10 fallos: hasta 170 s) para dar tiempo a Flyway y al arranque de Spring con 0.5 vCPU.
- **Liveness** `GET /actuator/health/liveness` (cada 30 s, 3 fallos) reinicia la réplica si la JVM queda bloqueada.
- **Readiness** `GET /actuator/health/readiness` (cada 10 s, 3 fallos) saca la réplica del balanceo mientras no esté lista.

## Alta de asesores (ROLE_REALTOR) en la nube
El rol `ROLE_REALTOR` no se asigna por la API pública y el fixture E2E (`APP_E2E_FIXTURE_TOKEN`) no se habilita en Azure. Para crear un asesor:
1. Registrar la cuenta desde la aplicación web (queda como `ROLE_CLIENT`).
2. Indicar su correo en la variable `REALTOR_BOOTSTRAP_EMAILS` de `identity-subscription-service` (varios correos separados por comas), ya sea en el despliegue inicial:
   ```powershell
   powershell -ExecutionPolicy Bypass -File ".\infra\azure\deploy-azure.ps1" -ResourceGroup "rg-credicasa-prod" -RealtorEmails "asesor@ejemplo.pe"
   ```
   o sobre un entorno ya desplegado (crea una nueva revisión y la reinicia):
   ```powershell
   az containerapp update --resource-group rg-credicasa-prod --name identity-subscription-service --set-env-vars REALTOR_BOOTSTRAP_EMAILS=asesor@ejemplo.pe
   ```
3. Al arrancar, `RealtorBootstrapRunner` promueve cada cuenta existente a `ROLE_REALTOR` y deja la traza `[SECURITY-AUDIT]` en Log Analytics. Es idempotente; los correos sin cuenta solo generan un aviso. El usuario debe volver a iniciar sesión para recibir un JWT con el nuevo rol.
