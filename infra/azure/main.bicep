// =====================================================================
// CareQ — Day 15 Azure infrastructure (ONE-TIME provisioning)
//
// Deploys into the EXISTING resource group `careq-rg`:
//   • custom VNet (10.0.0.0/16)
//       - apps-subnet  10.0.1.0/24  (delegated to Microsoft.App/environments)
//       - vms-subnet   10.0.2.0/24  (MySQL VM)
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
//   • MySQL VM (Standard_B1s, no public IP) with cloud-init MySQL 8,
//     NSG allowing 3306 ONLY from the apps subnet (10.0.1.0/24)
//   • Azure Static Web App `careq-frontend` (Free tier)
//
// COST POLICY (see docs/10_DEPLOYMENT.md §4): eureka / gateway / auth stay
// always-warm because they sit on every request path; the rest scale to zero
// and cold-start on demand. Redis + RabbitMQ use TCP scale rules so they wake
// on the first connection after idle.
//
// SECRETS: this template contains ONLY placeholders. Every secret value is
// injected at deploy time by the GitHub Actions deploy job
// (az containerapp update --secrets ... sourced from GitHub secrets).
//
// Apply with infra/azure/provision.sh — or:
//   az deployment group create \
//     --resource-group careq-rg \
//     --template-file infra/azure/main.bicep \
//     --parameters mysqlPassword='...' mysqlVmAdminPassword='...' \
//                  ghcrOwner='rameshpothamsetty' imageTag='develop-latest'
// =====================================================================

param location string = 'eastus2'
@description('Root password for the MySQL `careq` user — must be added to GitHub secrets as MYSQL_PASSWORD (same value).')
@secure()
param mysqlPassword string
@secure()
param mysqlVmAdminPassword string
@description('GHCR owner (lowercase) hosting the careq-* images.')
param ghcrOwner string = 'rameshpothamsetty'
@description('Local admin username on the MySQL VM (no public IP — only used by cloud-init).')
param mysqlVmAdminUsername string = 'careqadmin'
@description('Image tag deployed at provisioning time (overwritten by the CD deploy job on every push).')
param imageTag string = 'develop-latest'
@description('Region for the Static Web App. Decoupled from `location` because the free-tier VM size (Standard_B1s) and Static Web Apps Free tier are available in different sets of regions — the SWA always goes somewhere known-good (eastus2) regardless of where the backend lands.')
param swaLocation string = 'eastus2'
@description('MySQL VM size. Standard_B1s is the 12-months-free size but is capacity-restricted in many regions; set Standard_B2s (paid) if B1s is unavailable everywhere on your subscription.')
param vmSize string = 'Standard_B1s'
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

// Subnet for the MySQL VM (no public IP — not reachable from the internet).
resource vmsSubnet 'Microsoft.Network/virtualNetworks/subnets@2023-11-01' = {
  parent: vnet
  name: 'vms-subnet'
  properties: {
    addressPrefix: '10.0.2.0/24'
  }
}

// NSG: MySQL reachable ONLY from the Container Apps subnet. Everything else
// inbound is denied (Azure platform services — DHCP/metadata — are exempt).
resource mysqlNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: 'careq-mysql-nsg'
  location: location
  properties: {
    securityRules: [
      {
        name: 'AllowMySQLFromContainerApps'
        properties: {
          description: 'MySQL 3306 from the Container Apps apps-subnet only'
          protocol: 'Tcp'
          sourceAddressPrefix: '10.0.1.0/24'
          sourcePortRange: '*'
          destinationAddressPrefix: '10.0.2.0/24'
          destinationPortRange: '3306'
          access: 'Allow'
          priority: 1000
          direction: 'Inbound'
        }
      }
      {
        name: 'DenyAllOtherInbound'
        properties: {
          description: 'No other inbound traffic (no public IP on this VM anyway)'
          protocol: '*'
          sourceAddressPrefix: '*'
          sourcePortRange: '*'
          destinationAddressPrefix: '*'
          destinationPortRange: '*'
          access: 'Deny'
          priority: 4096
          direction: 'Inbound'
        }
      }
    ]
  }
}

// ─────────────────────────────────────────────────────────────────────
// MySQL VM (Standard_B1s — the 12-months-free size), static private IP
// 10.0.2.10, MySQL 8 installed via cloud-init.
// ─────────────────────────────────────────────────────────────────────
resource mysqlNic 'Microsoft.Network/networkInterfaces@2023-11-01' = {
  name: 'careq-mysql-vm-nic'
  location: location
  properties: {
    ipConfigurations: [
      {
        name: 'ipconfig1'
        properties: {
          subnet: {
            id: vmsSubnet.id
          }
          privateIPAllocationMethod: 'Static'
          privateIPAddress: '10.0.2.10'
          primary: true
        }
      }
    ]
    networkSecurityGroup: {
      id: mysqlNsg.id
    }
  }
}

resource mysqlVm 'Microsoft.Compute/virtualMachines@2023-09-01' = {
  name: 'careq-mysql-vm'
  location: location
  properties: {
    hardwareProfile: {
      vmSize: vmSize
    }
    osProfile: {
      computerName: 'careq-mysql-vm'
      adminUsername: mysqlVmAdminUsername
      adminPassword: mysqlVmAdminPassword
      customData: base64(replace(loadTextContent('cloud-init-mysql.sh'), '__MYSQL_PASSWORD__', mysqlPassword))
      linuxConfiguration: {
        disablePasswordAuthentication: false
      }
    }
    storageProfile: {
      imageReference: {
        publisher: 'Canonical'
        offer: 'ubuntu-24_04-lts'
        sku: 'server'
        version: 'latest'
      }
      osDisk: {
        createOption: 'FromImage'
        caching: 'ReadWrite'
        diskSizeGB: 30
        managedDisk: {
          storageAccountType: 'Standard_LRS'
        }
      }
    }
    networkProfile: {
      networkInterfaces: [
        {
          id: mysqlNic.id
        }
      ]
    }
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
]

// Shared env wiring for every MySQL-backed service.
var mysqlEnv = [
  { name: 'EUREKA_URI', value: 'http://eureka-server/eureka/' }
  { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
  { name: 'JAVA_OPTS', value: '-Xmx384m -XX:MaxMetaspaceSize=192m' }
  { name: 'MYSQL_HOST', value: '10.0.2.10' }
  { name: 'MYSQL_PORT', value: '3306' }
  { name: 'MYSQL_USER', value: 'careq' }
  { name: 'MYSQL_PASSWORD', secretRef: 'mysql-password' }
]

// ─────────────────────────────────────────────────────────────────────
// 1. eureka-server — always on (min 1). Every other service registers here.
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
            cpu: '0.25'
            memory: '0.75Gi'
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
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────
// 2. api-gateway — always on, the ONLY externally reachable service
//    (Static Web App → https://careq-api-gateway.<env>...azurecontainerapps.io)
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
            cpu: '0.25'
            memory: '1.0Gi'
          }
          env: [
            { name: 'EUREKA_URI', value: 'http://eureka-server/eureka/' }
            { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
            { name: 'JAVA_OPTS', value: '-Xmx384m -XX:MaxMetaspaceSize=192m' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            // Placeholder — the deploy job overwrites it with the live SWA origin.
            { name: 'CORS_ALLOWED_ORIGINS', value: 'http://localhost:3030' }
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
        minReplicas: 1
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
// 3. auth-service — always on (every request path starts with login/JWT).
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
            cpu: '0.25'
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
        minReplicas: 1
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
          name: 'careq-user-service'
          image: 'ghcr.io/${ghcrOwner}/careq-user-service:${imageTag}'
          resources: {
            cpu: '0.25'
            memory: '1.0Gi'
          }
          env: mysqlEnv
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
          name: 'careq-doctor-service'
          image: 'ghcr.io/${ghcrOwner}/careq-doctor-service:${imageTag}'
          resources: {
            cpu: '0.25'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'REDIS_HOST', value: 'redis' }
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
          name: 'careq-queue-service'
          image: 'ghcr.io/${ghcrOwner}/careq-queue-service:${imageTag}'
          resources: {
            cpu: '0.25'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'GROQ_API_KEY', secretRef: 'groq-api-key' }
            { name: 'RABBITMQ_HOST', value: 'rabbitmq' }
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
          name: 'careq-notification-service'
          image: 'ghcr.io/${ghcrOwner}/careq-notification-service:${imageTag}'
          resources: {
            cpu: '0.25'
            memory: '1.0Gi'
          }
          env: concat(mysqlEnv, [
            { name: 'RABBITMQ_HOST', value: 'rabbitmq' }
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
          name: 'careq-rabbitmq'
          image: 'rabbitmq:3-management'
          resources: {
            cpu: '0.25'
            memory: '0.75Gi'
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
// Static Web App for the React frontend (Free tier). The deployment token
// is NOT a secret in this template — fetch it after provisioning with:
//   az staticwebapp secrets list --name careq-frontend \
//     --resource-group careq-rg --query properties.apiKey -o tsv
// and add it as the GitHub secret AZURE_STATIC_WEB_APPS_API_TOKEN.
// ─────────────────────────────────────────────────────────────────────
resource swa 'Microsoft.Web/staticSites@2022-09-01' = {
  name: 'careq-frontend'
  location: swaLocation
  sku: {
    name: 'Free'
    tier: 'Free'
  }
  properties: {}
}

// ─────────────────────────────────────────────────────────────────────
// Outputs — captured by infra/azure/provision.sh
// ─────────────────────────────────────────────────────────────────────
output gatewayFqdn string = apiGatewayApp.properties.configuration.ingress.fqdn
output eurekaFqdn string = eurekaApp.properties.configuration.ingress.fqdn
output mysqlPrivateIp string = mysqlNic.properties.ipConfigurations[0].properties.privateIPAddress
output swaDefaultHostname string = swa.properties.defaultHostname
output swaName string = swa.name
output caeName string = cae.name
