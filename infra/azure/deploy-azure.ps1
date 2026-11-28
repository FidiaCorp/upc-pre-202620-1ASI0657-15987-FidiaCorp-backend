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
  [string]$ImageTag = "2.0.0"
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

Write-Host "=== 3. Aprovisionando ACR inicial para subir las 6 imágenes Docker ===" -ForegroundColor Cyan
$uniqueAcr = ("${Prefix}acr" + ([guid]::NewGuid().ToString("N").Substring(0, 6))).ToLower()
az acr create --resource-group $ResourceGroup --name $uniqueAcr --sku Basic --admin-enabled true | Out-Null
$acrServer = az acr show --name $uniqueAcr --query loginServer -o tsv
az acr login --name $uniqueAcr

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

Write-Host "=== 4. Desplegando plantilla Azure Bicep (infra/azure/main.bicep) ===" -ForegroundColor Cyan
$outputs = az deployment group create `
  --resource-group $ResourceGroup `
  --template-file (Join-Path $PSScriptRoot 'main.bicep') `
  --parameters prefix=$Prefix imageTag=$ImageTag `
    postgresAdminPassword=$pgPass jwtSecret=$jwtSecret `
    twoFactorEncryptionKey=$tfaKey internalApiToken=$internalToken `
    paymentSandboxSecret=$paymentSecret rabbitMqPassword=$rabbitPass `
  --query properties.outputs -o json | ConvertFrom-Json

Write-Host "`n=== DESPLIEGUE COMPLETADO EN MICROSOFT AZURE ===" -ForegroundColor Green
Write-Host "API Gateway HTTPS : $($outputs.gatewayUrl.value)"
Write-Host "Swagger UI Público: $($outputs.swaggerUiUrl.value)"
Write-Host "Frontend SWA URL  : $($outputs.frontendUrl.value)"
Write-Host "PostgreSQL FQDN   : $($outputs.postgresFqdn.value)"
Write-Host "Key Vault         : $($outputs.keyVaultName.value)"
