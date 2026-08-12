// =====================================================================
// CareQ — Day 15 Azure infrastructure (ONE-TIME provisioning)
//
// Deploys into the EXISTING resource group `careq-rg`:
//   • custom VNet (10.0.0.0/16)
//       - apps-subnet   10.0.1.0/24  (delegated to Microsoft.App/environments)
//       - mysql-subnet  10.0.3.0/24  (delegated to Microsoft.DBforMySQL/flexibleServers)
//   • private DNS zone for MySQL (private.mysql.database.azure.com)
//   • Azure Database for MySQL Flexible Server — FREE for 12 months
//     (Burstable Standard_B1ms, 32 GB, 750 hrs/month, no HA, no geo backup)
//   • Log Analytics workspace (container logs)
//   • Container Apps Environment (consumption-only, external, VNet-injected)
//   • 9 container apps:
//       careq-eureka-server        (always-on, min 1)   internal http :8761
//       careq-api-gateway          (always-on, min 1)   EXTERNAL  http :8080
//       careq-auth-service         (always-on, min 1)   internal http :8081
//       careq-user-service         (scale-to-zero)      internal http :8082
//       careq-doctor-service       (scale-to-zero)      internal http :8083
//       careq-queue-service        (scale-to-zero)      internal http :8084
//       careq-notification-service (scale-to-zero)      internal http :8085
//       careq-redis                (scale-to-zero, TCP) internal tcp  :6379
//       careq-rabbitmq             (scale-to-zero, TCP) internal tcp  :5672
//
// The React frontend is NOT here — it deploys to Vercel (free Hobby plan)
// and calls the gateway cross-origin (gateway CORS allows
// https://careq-frontend.vercel.app). See docs/10_DEPLOYMENT.md §4.
//
// COST POLICY: free-first. The MySQL Flexible Server is FREE for 12
// months, the frontend is FREE on Vercel, and EVERY container app scales
// to zero — demo-level usage stays near the Container Apps free grant
// (~$0/month; heavy daily demos can exceed it by a few dollars). Profile
// pictures go to Azure Blob Storage (5 GB free for 12 months). Redis +
// RabbitMQ use TCP scale rules so they wake on the first connection.
//
// SECRETS: this template contains ONLY placeholders. Every secret value is
// injected at deploy time by the GitHub Actions deploy job
// (az containerapp update --secrets ... sourced from GitHub secrets).
//
// Apply with infra/azure/provision.sh — or:
//   az deployment group create \
//     --resource-group careq-rg \
//     --template-file infra/azure/main.bicep \
//     --parameters mysqlPassword='...' ghcrOwner='rameshpothamsetty' \
//                  imageTag='develop-latest'
// =====================================================================

param location string = 'eastus2'
@description('Administrator password for the MySQL Flexible Server — must be added to GitHub secrets as MYSQL_PASSWORD (same value).')
@secure()
param mysqlPassword string
@description('GHCR owner (lowercase) hosting the careq-* images.')
param ghcrOwner string = 'rameshpothamsetty'
@description('Administrator login for the MySQL Flexible Server (alphanumeric only). The app connects as this user.')
param mysqlAdminUser string = 'careqadmin'
@description('Name of the MySQL Flexible Server.')
param mysqlServerName string = 'careq-mysql'
@description('MySQL SKU. Standard_B1ms = FREE 12-month tier (750 hrs/mo, 32 GB). If burstable capacity is unavailable everywhere on your subscription, set Standard_B2s (paid ≈$25-30/mo, within the trial credit).')
param mysqlSku string = 'Standard_B1ms'
@description('Name of the Azure Storage account for profile pictures (free tier: 5 GB LRS hot). Globally unique, lowercase alphanumeric.')
param storageAccountName string = 'carequploads'
@description('Image tag deployed at provisioning time (overwritten by the CD deploy job on every push).')
param imageTag string = 'develop-latest'
@description('GHCR username for image pulls. Leave EMPTY to pull the (public) ghcr.io packages anonymously. If set, you MUST also set the GHCR_PAT GitHub secret (fine-grained PAT, packages:read) — the deploy job wires it in. Set both or neither.')
param ghcrUsername string = ''

// ─────────────────────────────────────────────────────────────────────
// Virtual network
// ─────────────────────────────────────────────────────────────────────
resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: 'careq-vnet'
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [ '10.0.0.0/16' ]
    }
  }
}

// Subnet for the Container Apps environment (consumption-only environments
// inject infrastructure into this delegated subnet).
resource appsSubnet 'Microsoft.Network/virtualNetworks/subnets@2023-11-01' = {
  parent: vnet
  name: 'apps-subnet'
  properties: {
    addressPrefix: '10.0.1.0/24'
    delegations: [
      {
        name: 'app-env-delegation'
        properties: {
          serviceName: 'Microsoft.App/environments'
        }
      }
    ]
  }
}

// Subnet for the MySQL Flexible Server (private access — no public endpoint).
resource mysqlSubnet 'Microsoft.Network/virtualNetworks/subnets@2023-11-01' = {
  parent: vnet
  name: 'mysql-subnet'
  properties: {
    addressPrefix: '10.0.3.0/24'
    delegations: [
      {
        name: 'mysql-delegation'
        properties: {
          serviceName: 'Microsoft.DBforMySQL/flexibleServers'
        }
      }
    ]
  }
}

// ─────────────────────────────────────────────────────────────────────
// Private DNS zone so the apps resolve the MySQL server privately.
// Must be named private.mysql.database.azure.com (Azure requirement).
// ─────────────────────────────────────────────────────────────────────
resource privateDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'private.mysql.database.azure.com'
  location: 'global'
}

resource dnsVnetLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: privateDnsZone
  name: 'careq-vnet-link'
  location: 'global'
  properties: {
    virtualNetwork: {
      id: vnet.id
    }
    registrationEnabled: false
  }
}

// ─────────────────────────────────────────────────────────────────────
// Azure Database for MySQL Flexible Server — FREE for 12 months.
// Free-tier eligibility: Burstable Standard_B1ms, 32 GB, no high
// availability, no geo-redundant backup (set below). 750 hours/month is
// enough for 24/7 operation of a single server.
// ─────────────────────────────────────────────────────────────────────
resource mysqlServer 'Microsoft.DBforMySQL/flexibleServers@2023-12-30' = {
  name: mysqlServerName
  location: location
  // CRITICAL: Azure requires the VNet to be LINKED to the private DNS zone
  // BEFORE the server is created, or the write fails with
  // VnetNotLinkedToPrivateDnsZone. mysqlSubnet + privateDnsZone are already
  // implicit deps (referenced via .id below); dnsVnetLink is not referenced
  // anywhere, so it must be forced in with an explicit dependsOn.
  dependsOn: [
    dnsVnetLink
  ]
  sku: {
    name: mysqlSku
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: mysqlAdminUser
    administratorLoginPassword: mysqlPassword
    version: '8.0.21'
    storage: {
      storageSizeGB: 32
      autoGrow: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Disabled'
      delegatedSubnetResourceId: mysqlSubnet.id
      privateDnsZoneResourceId: privateDnsZone.id
    }
    highAvailability: {
      mode: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// Azure Blob Storage (free tier: 5 GB LRS hot) for profile pictures.
// Public-read container so <img> tags can load them directly; the app
// uploads via the account connection string (a GitHub secret injected by
// the deploy job). Free for 12 months.
// ─────────────────────────────────────────────────────────────────────
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageAccountName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    allowBlobPublicAccess: true
    accessTier: 'Hot'
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storageAccount
  name: 'default'
}

resource uploadsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: 'careq-uploads'
  properties: {
    publicAccess: 'Container'
  }
}

// ─────────────────────────────────────────────────────────────────────
// Log Analytics + Container Apps Environment (consumption-only, external)
// ─────────────────────────────────────────────────────────────────────
resource logWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: 'careq-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

resource cae 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: 'careq-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logWorkspace.properties.customerId
        sharedKey: logWorkspace.listKeys().primarySharedKey
      }
    }
    vnetConfiguration: {
      infrastructureSubnetId: appsSubnet.id
      internal: false
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// Shared placeholder secrets. The GitHub Actions deploy job replaces these
// values (az containerapp update --secrets) on every deploy — see the
// `deploy` job in .github/workflows/cd-*.yml. Never put real values here.
// ─────────────────────────────────────────────────────────────────────
var placeholderSecrets = [
  { name: 'jwt-secret', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'mysql-password', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'groq-api-key', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'rabbitmq-user', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'rabbitmq-pass', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'ghcr-pat', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
  { name: 'storage-connection-string', value: 'CHANGE_ME_DEPLOY_WILL_SET' }
]

// Private FQDN of the Flexible Server — resolved inside the VNet through
// the private DNS zone above.
var mysqlFqdn = '${mysqlServer.name}.private.mysql.database.azure.com'

// Shared env wiring for every MySQL-backed service.
// NOTE: ACA resolves services by APP NAME inside the environment
// (careq-eureka-server / careq-redis / careq-rabbitmq) — the Docker-compose
// aliases (eureka-server, redis, rabbitmq) do NOT resolve here.
var mysqlEnv = [
  { name: 'EUREKA_URI', value: 'http://careq-eureka-server/eureka/' }
  { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
  { name: 'JAVA_OPTS', value: '-Xmx384m -XX:MaxMetaspaceSize=192m' }
  { name: 'MYSQL_HOST', value: mysqlFqdn }
  { name: 'MYSQL_PORT', value: '3306' }
  { name: 'MYSQL_USER', value: mysqlAdminUser }
  { name: 'MYSQL_PASSWORD', secretRef: 'mysql-password' }
]

// ─────────────────────────────────────────────────────────────────────
// 1. eureka-server — scale-to-zero (wakes on internal traffic). Every
//    other service registers here; the HTTP scale rule wakes it on demand.
// ─────────────────────────────────────────────────────────────────────
resource eurekaApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-eureka-server'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8761
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      // Optional registry creds for pulling the ghcr.io packages. Empty by
      // default (public packages); set ghcrUsername at provision time (plus
      // the GHCR_PAT GitHub secret) to pull private packages.
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-eureka-server'
          image: 'ghcr.io/${ghcrOwner}/careq-eureka-server:${imageTag}'
          resources: {
            // ACA consumption allows only fixed CPU→memory pairs; 0.5Gi is too
            // tight for a JVM, so Java services use 0.5 vCPU / 1.0 Gi.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8761
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 2. api-gateway — the ONLY externally reachable service (scale-to-zero
//    on HTTP traffic; wakes on the first request from Vercel)
//    (Vercel → https://careq-api-gateway.<env>...azurecontainerapps.io)
// ─────────────────────────────────────────────────────────────────────
resource apiGatewayApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-api-gateway'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'http'
        allowInsecure: false
      }
      secrets: placeholderSecrets
      // Optional registry creds for pulling the ghcr.io packages. Empty by
      // default (public packages); set ghcrUsername at provision time (plus
      // the GHCR_PAT GitHub secret) to pull private packages.
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-api-gateway'
          image: 'ghcr.io/${ghcrOwner}/careq-api-gateway:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: [
            { name: 'EUREKA_URI', value: 'http://careq-eureka-server/eureka/' }
            { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
            { name: 'JAVA_OPTS', value: '-Xmx384m -XX:MaxMetaspaceSize=192m' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            // The frontend is on Vercel — the deploy job re-asserts this
            // origin on every rollout.
            { name: 'CORS_ALLOWED_ORIGINS', value: 'https://careq-frontend.vercel.app' }
          ]
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8080
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
            {
              type: 'readiness'
              httpGet: {
                path: '/actuator/health'
                port: 8080
                httpHeaders: []
              }
              initialDelaySeconds: 30
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 3. auth-service — scale-to-zero (every request path starts with login/JWT).
// ─────────────────────────────────────────────────────────────────────
resource authServiceApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-auth-service'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8081
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      // Optional registry creds for pulling the ghcr.io packages. Empty by
      // default (public packages); set ghcrUsername at provision time (plus
      // the GHCR_PAT GitHub secret) to pull private packages.
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-auth-service'
          image: 'ghcr.io/${ghcrOwner}/careq-auth-service:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
          ])
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8081
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 4-7. Business services — scale to zero, cold-start on demand.
// ─────────────────────────────────────────────────────────────────────
resource userServiceApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-user-service'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8082
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-user-service'
          image: 'ghcr.io/${ghcrOwner}/careq-user-service:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            // Profile pictures land in Azure Blob Storage on Azure; the
            // deploy job injects the account connection string as a secret.
            { name: 'AZURE_STORAGE_CONNECTION_STRING', secretRef: 'storage-connection-string' }
          ])
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8082
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

resource doctorServiceApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-doctor-service'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8083
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-doctor-service'
          image: 'ghcr.io/${ghcrOwner}/careq-doctor-service:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'REDIS_HOST', value: 'careq-redis' }
            { name: 'REDIS_PORT', value: '6379' }
          ])
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8083
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

resource queueServiceApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-queue-service'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8084
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-queue-service'
          image: 'ghcr.io/${ghcrOwner}/careq-queue-service:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'GROQ_API_KEY', secretRef: 'groq-api-key' }
            { name: 'RABBITMQ_HOST', value: 'careq-rabbitmq' }
            { name: 'RABBITMQ_PORT', value: '5672' }
            { name: 'RABBITMQ_USERNAME', secretRef: 'rabbitmq-user' }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-pass' }
          ])
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8084
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

resource notificationServiceApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-notification-service'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8085
        transport: 'http'
        allowInsecure: true
      }
      secrets: placeholderSecrets
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-notification-service'
          image: 'ghcr.io/${ghcrOwner}/careq-notification-service:${imageTag}'
          resources: {
            // ACA consumption: fixed CPU→memory pairs; 0.5 vCPU / 1.0 Gi is
            // the smallest safe pair for a Spring Boot JVM.
            cpu: '0.5'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'RABBITMQ_HOST', value: 'careq-rabbitmq' }
            { name: 'RABBITMQ_PORT', value: '5672' }
            { name: 'RABBITMQ_USERNAME', secretRef: 'rabbitmq-user' }
            { name: 'RABBITMQ_PASSWORD', secretRef: 'rabbitmq-pass' }
          ])
          probes: [
            {
              type: 'liveness'
              httpGet: {
                path: '/actuator/health'
                port: 8085
                httpHeaders: []
              }
              initialDelaySeconds: 60
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 3
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 8. redis — public image, internal TCP ingress, scale-to-zero on a TCP
//    rule. EPHEMERAL storage: data is lost on restart/scale-down — this is
//    a known limitation of the free-tier approach (doctor-service cache is
//    fail-open, so a lost cache only means a few extra DB reads).
// ─────────────────────────────────────────────────────────────────────
resource redisApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-redis'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 6379
        transport: 'tcp'
      }
      secrets: []
    }
    template: {
      containers: [
        {
          name: 'careq-redis'
          image: 'redis:7-alpine'
          resources: {
            cpu: '0.25'
            memory: '0.5Gi'
          }
          probes: [
            {
              type: 'liveness'
              tcpSocket: {
                port: 6379
              }
              initialDelaySeconds: 10
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'tcp-scale'
            tcp: {
              metadata: {
                concurrentConnections: '50'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 9. rabbitmq — public image, internal TCP ingress (:5672). The management
//    UI (:15672) is NOT exposed via ingress; reach it with
//    `az containerapp exec -n careq-rabbitmq ... --command curl -s localhost:15672/api/...`
//    if you need to inspect the broker. EPHEMERAL storage: queues and
//    undelivered events are lost on restart/scale-down — queue-service
//    publishes with a non-blocking contract (a broker outage only logs a
//    warning), so joins/calls/completes never fail because of it.
// ─────────────────────────────────────────────────────────────────────
resource rabbitmqApp 'Microsoft.App/containerApps@2025-02-02-preview' = {
  name: 'careq-rabbitmq'
  location: location
  properties: {
    managedEnvironmentId: cae.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 5672
        transport: 'tcp'
      }
      secrets: placeholderSecrets
      registries: (empty(ghcrUsername) ? [] : [
        {
          server: 'ghcr.io'
          username: ghcrUsername
          passwordSecretRef: 'ghcr-pat'
        }
      ])
    }
    template: {
      containers: [
        {
          name: 'careq-rabbitmq'
          image: 'rabbitmq:3-management'
          resources: {
            cpu: '0.25'
            memory: '0.5Gi'
          }
          env: [
            { name: 'RABBITMQ_DEFAULT_USER', secretRef: 'rabbitmq-user' }
            { name: 'RABBITMQ_DEFAULT_PASS', secretRef: 'rabbitmq-pass' }
          ]
          probes: [
            {
              type: 'liveness'
              tcpSocket: {
                port: 5672
              }
              initialDelaySeconds: 30
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'tcp-scale'
            tcp: {
              metadata: {
                concurrentConnections: '50'
              }
            }
          }
        ]
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// Outputs — captured by infra/azure/provision.sh
// ─────────────────────────────────────────────────────────────────────
output gatewayFqdn string = apiGatewayApp.properties.configuration.ingress.fqdn
output eurekaFqdn string = eurekaApp.properties.configuration.ingress.fqdn
output mysqlFqdn string = mysqlFqdn
output mysqlServerName string = mysqlServer.name
output storageAccountName string = storageAccount.name
output caeName string = cae.name
