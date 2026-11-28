// CrediCasa (FidiaCorp, UPC 1ASI0657) — Infraestructura como Código (IaC) para Microsoft Azure
// Arquitectura Cloud Native (Sección 5.4.1 y 5.4.2):
// - Azure Static Web Apps (Free Tier) para Vue 3 SPA
// - Azure Container Registry (Basic) para imágenes Docker de los 6 contenedores Java
// - Azure Container Apps Environment con VNet propia (subred delegada, perfil Consumption) + Log Analytics
// - Ingress HTTPS TLS 1.3 externo únicamente en api-gateway (:8080)
// - 5 Microservicios internos (sin exposición pública directa):
//     identity-subscription-service, property-catalog-service, mortgage-pricing-service,
//     quotation-deal-service, communication-notification-service
// - RabbitMQ Container App interno (AMQP :5672, ingreso TCP: requiere la VNet del entorno)
// - Health probes Startup/Liveness/Readiness sobre /actuator/health/{liveness,readiness} en los 6 contenedores Java
// - Azure Database for PostgreSQL Flexible Server (Standard_B1ms) con 5 bases lógicas aisladas (PA-05)
// - Azure Key Vault aprovisionado; en esta versión los secretos se inyectan como secretos de Container Apps
//   (parámetros @secure()) y aún no se referencian desde Key Vault

targetScope = 'resourceGroup'

@description('Ubicación de Azure (por defecto eastus2 para Azure for Students)')
param location string = resourceGroup().location

@description('Prefijo único de despliegue (solo minúsculas y números, 3-10 caracteres)')
@minLength(3)
@maxLength(10)
param prefix string = 'credicasa'

@description('Etiqueta de imagen Docker publicada en ACR')
param imageTag string = '2.0.0'

@secure()
@description('Contraseña del administrador de PostgreSQL Flexible Server')
param postgresAdminPassword string

@secure()
@description('Clave JWT HMAC-SHA256 (>= 32 bytes)')
param jwtSecret string

@secure()
@description('Clave AES-256-GCM Base64 de 32 bytes para secretos TOTP 2FA')
param twoFactorEncryptionKey string

@secure()
@description('Token interno de autenticación entre microservicios')
param internalApiToken string

@secure()
@description('Secreto HMAC para referencias de pasarela sandbox')
param paymentSandboxSecret string

@secure()
@description('Contraseña del usuario AMQP en RabbitMQ')
param rabbitMqPassword string

@description('Nombre del Azure Container Registry. deploy-azure.ps1 pasa el mismo ACR donde publica las imágenes; si se omite, se usa el nombre determinista del grupo de recursos.')
@minLength(5)
@maxLength(50)
param acrName string = toLower('${prefix}acr${take(uniqueString(resourceGroup().id), 6)}')

var uniqueSuffix = take(uniqueString(resourceGroup().id), 6)
var kvName = toLower('${prefix}kv${uniqueSuffix}')
var pgServerName = toLower('${prefix}-pg-${uniqueSuffix}')
var logAnalyticsName = '${prefix}-logs-${uniqueSuffix}'
var acaEnvName = '${prefix}-aca-env-${uniqueSuffix}'
var swaName = '${prefix}-web-${uniqueSuffix}'

var databaseNames = [
  'identity_db'
  'property_db'
  'pricing_db'
  'quotation_db'
  'communication_db'
]

// 1. Observabilidad centralizada (Azure Monitor / Log Analytics)
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: logAnalyticsName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// 2. Registro de Contenedores (ACR)
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: true
  }
}

// 3. Gestión de Secretos (Azure Key Vault)
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: kvName
  location: location
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

// 4. Azure Database for PostgreSQL Flexible Server + 5 Bases Aisladas (PA-05)
resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' = {
  name: pgServerName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: 'credicasa_admin'
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
  }
}

resource pgFirewallAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-12-01-preview' = {
  parent: postgresServer
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource databases 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-01-preview' = [for dbName in databaseNames: {
  parent: postgresServer
  name: dbName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}]

// 5. Red virtual del entorno: el ingreso TCP interno de RabbitMQ (AMQP :5672) requiere un entorno con VNet propia
resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: '${prefix}-vnet-${uniqueSuffix}'
  location: location
  properties: {
    addressSpace: { addressPrefixes: ['10.40.0.0/16'] }
    subnets: [
      {
        name: 'aca-infra'
        properties: {
          addressPrefix: '10.40.0.0/23'
          delegations: [
            { name: 'aca-delegation', properties: { serviceName: 'Microsoft.App/environments' } }
          ]
        }
      }
    ]
  }
}

// 6. Entorno Azure Container Apps (VNet propia + perfil Consumption + Log Analytics)
resource acaEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: acaEnvName
  location: location
  properties: {
    vnetConfiguration: {
      infrastructureSubnetId: vnet.properties.subnets[0].id
      internal: false
    }
    workloadProfiles: [
      { name: 'Consumption', workloadProfileType: 'Consumption' }
    ]
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

// 7. Broker RabbitMQ interno (AMQP)
resource rabbitmqApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'rabbitmq'
  location: location
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      ingress: {
        external: false
        targetPort: 5672
        transport: 'tcp'
        exposedPort: 5672
      }
      secrets: [
        { name: 'rabbitmq-password', value: rabbitMqPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'rabbitmq'
          image: 'rabbitmq:3.13-management-alpine'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'RABBITMQ_DEFAULT_USER', value: 'credicasa' }
            { name: 'RABBITMQ_DEFAULT_PASS', secretRef: 'rabbitmq-password' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 1 }
    }
  }
}

var pgHost = postgresServer.properties.fullyQualifiedDomainName
var acrServer = acr.properties.loginServer
var acrCreds = acr.listCredentials()

// 8. MS-1: identity-subscription-service (Interno :8081)
resource identityApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'identity-subscription-service'
  location: location
  dependsOn: [databases]
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: false, targetPort: 8081, transport: 'http' }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'pg-pass', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'two-factor-key', value: twoFactorEncryptionKey }
        { name: 'internal-token', value: internalApiToken }
        { name: 'payment-secret', value: paymentSandboxSecret }
      ]
    }
    template: {
      containers: [
        {
          name: 'identity-subscription-service'
          image: '${acrServer}/credicasa/identity-subscription-service:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8081 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8081 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8081 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'SPRING_DATASOURCE_URL', value: 'jdbc:postgresql://${pgHost}:5432/identity_db?sslmode=require' }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'credicasa_admin' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'pg-pass' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'TWO_FACTOR_ENCRYPTION_KEY', secretRef: 'two-factor-key' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'PAYMENT_SANDBOX_SECRET', secretRef: 'payment-secret' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'true' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 3 }
    }
  }
}

// 9. MS-2: property-catalog-service (Interno :8082)
resource catalogApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'property-catalog-service'
  location: location
  dependsOn: [databases]
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: false, targetPort: 8082, transport: 'http' }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'pg-pass', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'internal-token', value: internalApiToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'property-catalog-service'
          image: '${acrServer}/credicasa/property-catalog-service:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8082 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8082 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8082 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'SPRING_DATASOURCE_URL', value: 'jdbc:postgresql://${pgHost}:5432/property_db?sslmode=require' }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'credicasa_admin' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'pg-pass' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'true' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 3 }
    }
  }
}

// 10. MS-3: mortgage-pricing-service (Interno :8083)
resource pricingApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'mortgage-pricing-service'
  location: location
  dependsOn: [databases]
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: false, targetPort: 8083, transport: 'http' }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'pg-pass', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'internal-token', value: internalApiToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'mortgage-pricing-service'
          image: '${acrServer}/credicasa/mortgage-pricing-service:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8083 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8083 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8083 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'SPRING_DATASOURCE_URL', value: 'jdbc:postgresql://${pgHost}:5432/pricing_db?sslmode=require' }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'credicasa_admin' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'pg-pass' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'true' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 4 }
    }
  }
}

// 11. MS-4: quotation-deal-service (Interno :8084)
resource quotationApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'quotation-deal-service'
  location: location
  dependsOn: [databases, rabbitmqApp]
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: false, targetPort: 8084, transport: 'http' }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'pg-pass', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'internal-token', value: internalApiToken }
        { name: 'rabbitmq-pass', value: rabbitMqPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'quotation-deal-service'
          image: '${acrServer}/credicasa/quotation-deal-service:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8084 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8084 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8084 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'SPRING_DATASOURCE_URL', value: 'jdbc:postgresql://${pgHost}:5432/quotation_db?sslmode=require' }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'credicasa_admin' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'pg-pass' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'RABBITMQ_HOST', value: 'rabbitmq' }
            { name: 'RABBITMQ_USERNAME', value: 'credicasa' }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-pass' }
            { name: 'IDENTITY_SERVICE_URL', value: 'https://${identityApp.properties.configuration.ingress.fqdn}' }
            { name: 'CATALOG_SERVICE_URL', value: 'https://${catalogApp.properties.configuration.ingress.fqdn}' }
            { name: 'PRICING_SERVICE_URL', value: 'https://${pricingApp.properties.configuration.ingress.fqdn}' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'true' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 3 }
    }
  }
}

// 12. MS-5: communication-notification-service (Interno :8085)
resource communicationApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'communication-notification-service'
  location: location
  dependsOn: [databases, rabbitmqApp]
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: false, targetPort: 8085, transport: 'http' }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'pg-pass', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'internal-token', value: internalApiToken }
        { name: 'rabbitmq-pass', value: rabbitMqPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'communication-notification-service'
          image: '${acrServer}/credicasa/communication-notification-service:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8085 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8085 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8085 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'SPRING_DATASOURCE_URL', value: 'jdbc:postgresql://${pgHost}:5432/communication_db?sslmode=require' }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'credicasa_admin' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'pg-pass' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'RABBITMQ_HOST', value: 'rabbitmq' }
            { name: 'RABBITMQ_USERNAME', value: 'credicasa' }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-pass' }
            { name: 'IDENTITY_SERVICE_URL', value: 'https://${identityApp.properties.configuration.ingress.fqdn}' }
            { name: 'CATALOG_SERVICE_URL', value: 'https://${catalogApp.properties.configuration.ingress.fqdn}' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'true' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 3 }
    }
  }
}

// 13. Edge: api-gateway (Único ingreso público HTTPS TLS 1.3 :8080)
resource gatewayApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'api-gateway'
  location: location
  properties: {
    managedEnvironmentId: acaEnv.id
    workloadProfileName: 'Consumption'
    configuration: {
      registries: [{ server: acrServer, username: acrCreds.username, passwordSecretRef: 'acr-pass' }]
      ingress: { external: true, targetPort: 8080, transport: 'auto', allowInsecure: false }
      secrets: [
        { name: 'acr-pass', value: acrCreds.passwords[0].value }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'internal-token', value: internalApiToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'api-gateway'
          image: '${acrServer}/credicasa/api-gateway:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8080 }, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 10 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8080 }, periodSeconds: 30, timeoutSeconds: 5, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8080 }, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
          ]
          env: [
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'INTERNAL_API_TOKEN', secretRef: 'internal-token' }
            { name: 'IDENTITY_SERVICE_URL', value: 'https://${identityApp.properties.configuration.ingress.fqdn}' }
            { name: 'CATALOG_SERVICE_URL', value: 'https://${catalogApp.properties.configuration.ingress.fqdn}' }
            { name: 'PRICING_SERVICE_URL', value: 'https://${pricingApp.properties.configuration.ingress.fqdn}' }
            { name: 'QUOTATION_SERVICE_URL', value: 'https://${quotationApp.properties.configuration.ingress.fqdn}' }
            { name: 'COMMUNICATION_SERVICE_URL', value: 'https://${communicationApp.properties.configuration.ingress.fqdn}' }
            // Solo el hostname exacto de la Static Web App de este despliegue (no cualquier *.azurestaticapps.net)
            { name: 'CORS_ALLOWED_ORIGIN_PATTERNS', value: 'https://${staticWebApp.properties.defaultHostname}' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 4 }
    }
  }
}

// 14. Frontend SPA: Azure Static Web Apps (Free Tier)
resource staticWebApp 'Microsoft.Web/staticSites@2023-12-01' = {
  name: swaName
  location: location
  sku: {
    name: 'Free'
    tier: 'Free'
  }
  properties: {}
}

output gatewayUrl string = 'https://${gatewayApp.properties.configuration.ingress.fqdn}'
output swaggerUiUrl string = 'https://${gatewayApp.properties.configuration.ingress.fqdn}/swagger-ui.html'
output frontendUrl string = 'https://${staticWebApp.properties.defaultHostname}'
output acrLoginServer string = acrServer
output postgresFqdn string = pgHost
output keyVaultName string = keyVault.name
