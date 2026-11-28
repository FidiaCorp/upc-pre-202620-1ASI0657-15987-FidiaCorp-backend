<#
  CrediCasa (FidiaCorp) — Despliegue automatizado en Microsoft Azure (Azure for Students / TF1 5.4.2)
  Prerrequisitos:
    1. Activar Azure for Students con correo @upc.edu.pe (US$ 100 gratis, sin tarjeta).
    2. Instalar Azure CLI (`winget install Microsoft.AzureCLI`) y ejecutar `az login`.
  Uso:
    powershell -ExecutionPolicy Bypass -File ".\infra\azure\deploy-azure.ps1" -ResourceGroup "rg-credicasa-prod" -Location "eastus2"
#>
param(
  [string]$ResourceGroup = "rg-credicasa-prod",
  [string]$Location = "eastus2",
  [string]$Prefix = "credicasa",
  [string]$ImageTag = "2.0.0",
  # Correos (separados por comas) de cuentas YA registradas que se promueven a ROLE_REALTOR al iniciar identity.
  [string]$RealtorEmails = ""
)
$ErrorActionPreference = 'Stop'
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

Write-Host "=== 1. Verificando sesión de Azure CLI ===" -ForegroundColor Cyan
az account show --query "{name:name, id:id, user:user.name}" -o table

Write-Host "=== 2. Creando Resource Group ($ResourceGroup en $Location) ===" -ForegroundColor Cyan
az group create --name $ResourceGroup --location $Location | Out-Null

# Generar secretos criptográficos aleatorios (nunca hardcodeados)
function New-RandomSecret([int]$bytes = 32) {
  $buf = New-Object byte[] $bytes
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
  return [Convert]::ToBase64String($buf)
}
$pgPass = "Cr3d!" + ([guid]::NewGuid().ToString("N").Substring(0, 14)) + "A9#"
$jwtSecret = (New-RandomSecret 48)
$tfaKey = (New-RandomSecret 32)
$internalToken = (New-RandomSecret 32)
$paymentSecret = (New-RandomSecret 32)
$rabbitPass = "RbMq!" + ([guid]::NewGuid().ToString("N").Substring(0, 14))

Write-Host "=== 3. ACR del grupo de recursos (el mismo que usará la plantilla Bicep) ===" -ForegroundColor Cyan
# Se reutiliza el ACR existente del grupo de recursos para que las re-ejecuciones no creen registros huérfanos;
# si no hay ninguno, se crea uno nuevo. El nombre se pasa a main.bicep (parámetro acrName), de modo que las
# Container Apps descargan las imágenes del mismo registro donde se publicaron.
$acrName = az acr list --resource-group $ResourceGroup --query "[0].name" -o tsv
if ([string]::IsNullOrWhiteSpace($acrName)) {
  $acrName = ("${Prefix}acr" + ([guid]::NewGuid().ToString("N").Substring(0, 6))).ToLower()
  az acr create --resource-group $ResourceGroup --name $acrName --sku Basic --admin-enabled true | Out-Null
} else {
  az acr update --name $acrName --admin-enabled true | Out-Null
}
$acrServer = az acr show --name $acrName --query loginServer -o tsv
az acr login --name $acrName

$services = @(
  'api-gateway',
  'identity-subscription-service',
  'property-catalog-service',
  'mortgage-pricing-service',
  'quotation-deal-service',
  'communication-notification-service'
)

Push-Location $backendRoot
try {
  foreach ($svc in $services) {
    Write-Host "Building & pushing $acrServer/credicasa/${svc}:${ImageTag}..." -ForegroundColor Yellow
    docker build -f "$svc/Dockerfile" -t "$acrServer/credicasa/${svc}:${ImageTag}" .
    docker push "$acrServer/credicasa/${svc}:${ImageTag}"
  }
} finally { Pop-Location }

$extraParams = @()
if (-not [string]::IsNullOrWhiteSpace($RealtorEmails)) { $extraParams += "realtorBootstrapEmails=$RealtorEmails" }

Write-Host "=== 4. Desplegando plantilla Azure Bicep (infra/azure/main.bicep) ===" -ForegroundColor Cyan
$outputs = az deployment group create `
  --resource-group $ResourceGroup `
  --template-file (Join-Path $PSScriptRoot 'main.bicep') `
  --parameters prefix=$Prefix imageTag=$ImageTag acrName=$acrName `
    postgresAdminPassword=$pgPass jwtSecret=$jwtSecret `
    twoFactorEncryptionKey=$tfaKey internalApiToken=$internalToken `
    paymentSandboxSecret=$paymentSecret rabbitMqPassword=$rabbitPass $extraParams `
  --query properties.outputs -o json | ConvertFrom-Json

Write-Host "`n=== DESPLIEGUE COMPLETADO EN MICROSOFT AZURE ===" -ForegroundColor Green
Write-Host "API Gateway HTTPS : $($outputs.gatewayUrl.value)"
Write-Host "Swagger UI Público: $($outputs.swaggerUiUrl.value)"
Write-Host "Frontend SWA URL  : $($outputs.frontendUrl.value)"
Write-Host "PostgreSQL FQDN   : $($outputs.postgresFqdn.value)"
Write-Host "ACR               : $($outputs.acrLoginServer.value)"
Write-Host "Key Vault         : $($outputs.keyVaultName.value)"
$swaName = az staticwebapp list --resource-group $ResourceGroup --query "[0].name" -o tsv
Write-Host "`nFrontend (desde FidiaCorp-FrontEnd, tras pnpm build):" -ForegroundColor Cyan
Write-Host "  `$token = az staticwebapp secrets list --resource-group $ResourceGroup --name $swaName --query properties.apiKey -o tsv"
Write-Host "  npx @azure/static-web-apps-cli deploy ./dist --deployment-token `$token --env production"
