# Starts school DOMAIN microservices and registers them with SugamFlow Eureka (:8761).
# Prefer: .\scripts\start-platform.ps1 first (discovery + gateway).
param(
  [switch]$Restart  # stop listeners on 8181-8199 before build/start
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\services"

if ($Restart) {
  Write-Host 'Stopping school services on :8181-8199...'
  foreach ($port in 8181..8199) {
    Get-NetTCPConnection -LocalPort $port -State Listen -EA SilentlyContinue | ForEach-Object {
      Stop-Process -Id $_.OwningProcess -Force -EA SilentlyContinue
    }
  }
  Start-Sleep -Seconds 2
}

$eurekaZone = if ($env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE) {
  $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
} else {
  'http://localhost:8761/eureka'
}

function Wait-Http([string]$Url, [int]$Seconds = 45) {
  for ($i = 1; $i -le $Seconds; $i++) {
    try {
      Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 | Out-Null
      return $true
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  return $false
}

function Test-PortOpen([int]$Port) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.ConnectAsync('127.0.0.1', $Port).Wait(500) | Out-Null
    $ok = $c.Connected
    $c.Close()
    return $ok
  } catch {
    return $false
  }
}

if (-not (Wait-Http 'http://localhost:8761/actuator/health' 5)) {
  Write-Warning "Eureka not healthy at http://localhost:8761 - run .\scripts\start-platform.ps1 first."
}

Write-Host 'Building school jars...'
mvn -q -DskipTests package

$modules = @(
  @{ Name = 'school-settings-service'; Port = 8181 },
  @{ Name = 'subscription-service'; Port = 8182 },
  @{ Name = 'form-builder-service'; Port = 8183 },
  @{ Name = 'workflow-service'; Port = 8184 },
  @{ Name = 'rule-engine-service'; Port = 8185 },
  @{ Name = 'report-builder-service'; Port = 8186 },
  @{ Name = 'school-notification-config-service'; Port = 8187 },
  @{ Name = 'audit-service'; Port = 8188 },
  @{ Name = 'admission-service'; Port = 8189 },
  @{ Name = 'fee-service'; Port = 8190 },
  @{ Name = 'student-service'; Port = 8191 },
  @{ Name = 'attendance-service'; Port = 8192 },
  @{ Name = 'exam-service'; Port = 8193 },
  @{ Name = 'library-service'; Port = 8194 },
  @{ Name = 'hostel-service'; Port = 8195 },
  @{ Name = 'transport-service'; Port = 8196 },
  @{ Name = 'payroll-service'; Port = 8197 },
  @{ Name = 'staff-service'; Port = 8198 },
  @{ Name = 'academic-structure-service'; Port = 8199 }
)

foreach ($m in $modules) {
  if (Test-PortOpen $m.Port) {
    Write-Host "Skip $($m.Name) - port $($m.Port) already in use"
    continue
  }
  $jar = Get-ChildItem "$root\services\$($m.Name)\target\$($m.Name)-*.jar" |
    Where-Object { $_.Name -notlike '*original*' } |
    Select-Object -First 1
  if (-not $jar) { throw "Missing jar for $($m.Name)" }
  Write-Host "Starting $($m.Name) on :$($m.Port)"
  Start-Process -FilePath 'java' -ArgumentList @(
    '-jar', $jar.FullName,
    "--server.port=$($m.Port)",
    "--eureka.client.service-url.defaultZone=$eurekaZone",
    '--eureka.client.register-with-eureka=true',
    '--eureka.instance.prefer-ip-address=true',
    '--eureka.instance.hostname=localhost',
    '--eureka.instance.ip-address=127.0.0.1',
    # Phase 23 operational readiness defaults (also shipped via school-ops.defaults.properties)
    '--management.endpoints.web.exposure.include=health,info,prometheus,metrics',
    '--management.endpoint.health.probes.enabled=true',
    '--management.health.livenessstate.enabled=true',
    '--management.health.readinessstate.enabled=true',
    '--management.endpoint.health.group.liveness.include=livenessState',
    '--management.endpoint.health.group.readiness.include=readinessState,db',
    '--server.shutdown=graceful',
    '--spring.lifecycle.timeout-per-shutdown-phase=30s'
  ) -WindowStyle Minimized
}

Write-Host 'Waiting for school-settings-service...'
if (Wait-Http 'http://localhost:8181/actuator/health' 90) {
  Write-Host 'school-settings-service UP'
} else {
  Write-Warning 'school-settings-service health not confirmed yet'
}

Write-Host ''
Write-Host "Eureka:  http://localhost:8761"
Write-Host "Gateway: http://localhost:9090"
Write-Host 'School UI: cd apps\school-ui; npm start'
Write-Host 'Smoke: headers X-Tenant-Id=demo-school, X-Branch-Id=main'
Write-Host '  GET http://localhost:9090/api/config/design-studio/theme'
