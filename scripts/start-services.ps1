# Starts school DOMAIN microservices and registers them with SugamFlow Eureka.
# Prefer: .\scripts\start-platform.ps1 first (discovery + gateway), OR Docker common platform.
# Canonical Eureka host port is always :8761 (Docker compose default and jar default).
#
# When the API gateway runs in Docker (start-common-platform / compose-local), jars must
# advertise host.docker.internal - not 127.0.0.1 - or the gateway gets Connection refused / 503.
param(
  [switch]$Restart,  # stop school service JVMs on 8181-8199 before build/start
  [switch]$SkipBuild, # reuse existing jars (faster / less RAM)
  # Eureka advertise address reachable FROM the gateway.
  # Host-jar gateway (start-platform.ps1): 127.0.0.1
  # Docker gateway (start-common-platform): host.docker.internal
  [string]$AdvertiseIp = ''
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\services"
$logDir = Join-Path $root 'logs\services'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

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
  @{ Name = 'academic-structure-service'; Port = 8199 },
  @{ Name = 'website-service'; Port = 8200 },
  @{ Name = 'cms-service'; Port = 8201 }
)

function Get-JavaExe {
  if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) { return $candidate }
  }
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if (-not $cmd) { throw 'java not found on PATH' }
  # Prefer real JDK over Oracle javapath shim (shim leaves an extra java.exe parent).
  if ($cmd.Source -match 'javapath') {
    $jdk = Get-ChildItem 'C:\Program Files\Java\jdk*\bin\java.exe' -ErrorAction SilentlyContinue |
      Sort-Object FullName -Descending |
      Select-Object -First 1
    if ($jdk) { return $jdk.FullName }
  }
  return $cmd.Source
}

function Get-SchoolJavaProcesses {
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object {
      $_.CommandLine -and (
        $_.CommandLine -match '\\school\\services\\' -or
        $_.CommandLine -match '/school/services/'
      )
    }
}

# Oracle javapath\java.exe is a shim that spawns jdk\bin\java.exe - count only leaf JVMs.
function Get-SchoolLeafJavaProcesses {
  Get-SchoolJavaProcesses | Where-Object {
    $_.ExecutablePath -and ($_.ExecutablePath -notmatch '\\Oracle\\Java\\javapath\\')
  }
}

function Get-ServiceNameFromCommandLine([string]$CommandLine) {
  if ($CommandLine -match '\\([a-z0-9-]+-service)\\target\\') { return $Matches[1] }
  if ($CommandLine -match '/([a-z0-9-]+-service)/target/') { return $Matches[1] }
  return $null
}

function Stop-SchoolServices {
  Write-Host 'Stopping school service JVMs...'
  $killed = @{}
  foreach ($port in 8181..8201) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
      if (-not $killed.ContainsKey($_.OwningProcess)) {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        $killed[$_.OwningProcess] = $true
      }
    }
  }
  # Kill remaining shims + leaf JVMs for school jars
  Get-SchoolJavaProcesses | ForEach-Object {
    if (-not $killed.ContainsKey($_.ProcessId)) {
      Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
      $killed[$_.ProcessId] = $true
    }
  }
  if ($killed.Count -gt 0) {
    Write-Host "Stopped $($killed.Count) process(es)"
    Start-Sleep -Seconds 2
  } else {
    Write-Host 'No school service processes found'
  }
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

function Test-ServiceAlreadyRunning([string]$Name, [int]$Port) {
  if (Test-PortOpen $Port) { return $true }
  $procs = @(Get-SchoolLeafJavaProcesses | Where-Object {
    (Get-ServiceNameFromCommandLine $_.CommandLine) -eq $Name
  })
  return ($procs.Count -gt 0)
}

function Wait-Http([string]$Url, [int]$Seconds = 45, [string]$Label = '') {
  for ($i = 1; $i -le $Seconds; $i++) {
    try {
      Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 | Out-Null
      if ($Label) { Write-Host "" }
      return $true
    } catch {
      if ($Label -and ($i % 5 -eq 0)) {
        Write-Host "  still waiting for $Label... ${i}s / ${Seconds}s"
      }
      Start-Sleep -Seconds 1
    }
  }
  if ($Label) { Write-Host "" }
  return $false
}

if ($Restart) {
  Stop-SchoolServices
}

# True duplicates = more than one leaf JVM per service jar (not Oracle javapath shims).
$dupGroups = @(Get-SchoolLeafJavaProcesses |
  ForEach-Object { Get-ServiceNameFromCommandLine $_.CommandLine } |
  Where-Object { $_ } |
  Group-Object |
  Where-Object { $_.Count -gt 1 })
if ($dupGroups.Count -gt 0) {
  $names = ($dupGroups | ForEach-Object { $_.Name }) -join ', '
  Write-Warning "Duplicate school JVMs detected ($names) - cleaning before restart"
  Stop-SchoolServices
}

$eurekaZone = if ($env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE) {
  $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
} else {
  # Canonical host Eureka is always :8761 (Docker + jar). Legacy :18761 only as fallback.
  if (Wait-Http 'http://localhost:8761/actuator/health' 2) {
    'http://localhost:8761/eureka'
  } elseif (Wait-Http 'http://localhost:18761/actuator/health' 2) {
    Write-Host 'Eureka healthy on legacy :18761 - recreate discovery to publish :8761' -ForegroundColor Yellow
    Write-Host '  cd D:\sugamFlow; docker compose up -d --force-recreate discovery-service config-service' -ForegroundColor Yellow
    'http://localhost:18761/eureka'
  } else {
    'http://localhost:8761/eureka'
  }
}
$eurekaHealthUrl = ($eurekaZone -replace '/eureka/?$', '') + '/actuator/health'

function Test-DockerGatewayRunning {
  # Docker Desktop publishes :9090 via wslrelay/com.docker.backend - CommandLine often has no "docker".
  try {
    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCmd) {
      $gw = & docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -match 'gateway-service' }
      if ($gw) { return $true }
    }
  } catch { }
  try {
    $listeners = @(Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue)
    foreach ($l in $listeners) {
      $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($l.OwningProcess)" -ErrorAction SilentlyContinue
      if (-not $proc) { continue }
      $blob = @($proc.Name, $proc.ExecutablePath, $proc.CommandLine) -join ' '
      if ($blob -match 'docker|wslrelay|vpnkit|com\.docker') { return $true }
    }
  } catch { }
  return $false
}

if (-not $AdvertiseIp) {
  if ($env:SCHOOL_EUREKA_ADVERTISE_IP) {
    $AdvertiseIp = $env:SCHOOL_EUREKA_ADVERTISE_IP
  } else {
    # Host-jar gateway (start-platform.ps1): 127.0.0.1
    # Docker gateway (start-common-platform / SEQ 00): host.docker.internal
    if (Test-DockerGatewayRunning) {
      $AdvertiseIp = 'host.docker.internal'
    } else {
      $AdvertiseIp = '127.0.0.1'
    }
  }
}

$javaExe = Get-JavaExe
Write-Host "Java: $javaExe"
Write-Host "Eureka zone: $eurekaZone"
Write-Host "Eureka advertise IP: $AdvertiseIp" -ForegroundColor Yellow
if ($AdvertiseIp -eq '127.0.0.1') {
  Write-Host '  (host-jar gateway mode - pass -AdvertiseIp host.docker.internal if gateway runs in Docker)' -ForegroundColor DarkGray
} else {
  Write-Host '  (Docker gateway mode - school jars reachable as host.docker.internal:<port>)' -ForegroundColor DarkGray
}

# Prevent a leftover platform/notification shell env from pointing every school jar at the wrong DB.
# Spring prefers SPRING_DATASOURCE_URL over per-service SCHOOL_*_DB_URL defaults.
foreach ($bad in @(
  'SPRING_DATASOURCE_URL',
  'SPRING_DATASOURCE_USERNAME',
  'SPRING_DATASOURCE_PASSWORD'
)) {
  if (Test-Path "Env:$bad") {
    Write-Host "Clearing inherited $bad (was set - would override school service DB URLs)" -ForegroundColor Yellow
    Remove-Item "Env:$bad"
  }
}

if (-not (Wait-Http $eurekaHealthUrl 8)) {
  Write-Warning "Eureka not healthy at $eurekaHealthUrl - run platform/Docker discovery first."
}

if ($SkipBuild) {
  Write-Host 'SkipBuild: reusing existing school jars'
} else {
  Write-Host 'Building school jars...'
  mvn -q -DskipTests package
}

foreach ($m in $modules) {
  if (Test-ServiceAlreadyRunning $m.Name $m.Port) {
    if (Test-PortOpen $m.Port) {
      Write-Host "Skip $($m.Name) - port $($m.Port) already in use"
    } else {
      Write-Host "Skip $($m.Name) - JVM already starting (port $($m.Port) not listening yet)"
    }
    continue
  }
  $jar = Get-ChildItem "$root\services\$($m.Name)\target\$($m.Name)-*.jar" |
    Where-Object { $_.Name -notlike '*original*' -and $_.Name -notlike '*copy*' } |
    Sort-Object Length -Descending |
    Select-Object -First 1
  if (-not $jar) { throw "Missing jar for $($m.Name)" }
  if ($jar.Length -lt 5MB) {
    throw "Jar for $($m.Name) looks incomplete ($([math]::Round($jar.Length/1KB)) KB). Re-run without -SkipBuild."
  }

  $outLog = Join-Path $logDir "$($m.Name).out.log"
  $errLog = Join-Path $logDir "$($m.Name).err.log"
  Write-Host "Starting $($m.Name) on :$($m.Port)"
  Start-Process -FilePath $javaExe -ArgumentList @(
    '-jar', $jar.FullName,
    "--server.port=$($m.Port)",
    "--eureka.client.service-url.defaultZone=$eurekaZone",
    '--eureka.client.register-with-eureka=true',
    '--eureka.instance.prefer-ip-address=true',
    "--eureka.instance.hostname=$AdvertiseIp",
    "--eureka.instance.ip-address=$AdvertiseIp",
    # Phase 23 operational readiness defaults (also shipped via school-ops.defaults.properties)
    '--management.endpoints.web.exposure.include=health,info,prometheus,metrics',
    '--management.endpoint.health.probes.enabled=true',
    '--management.health.livenessstate.enabled=true',
    '--management.health.readinessstate.enabled=true',
    '--management.endpoint.health.group.liveness.include=livenessState',
    '--management.endpoint.health.group.readiness.include=readinessState,db',
    '--server.shutdown=graceful',
    '--spring.lifecycle.timeout-per-shutdown-phase=30s'
  ) -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog

  # Small stagger so ports bind before the next launch check / re-run race.
  Start-Sleep -Milliseconds 400
}

Write-Host 'Waiting for school-settings-service (health on :8181)...'
if (Wait-Http 'http://localhost:8181/actuator/health' 180 'school-settings-service') {
  Write-Host 'school-settings-service UP' -ForegroundColor Green
} else {
  Write-Warning 'school-settings-service health not confirmed yet'
  Write-Warning "Check log: $logDir\school-settings-service.err.log"
}

$up = 0
foreach ($m in $modules) {
  if (Test-PortOpen $m.Port) { $up++ }
}
Write-Host "Listening: $up / $($modules.Count) school ports (8181-8199)"

Write-Host ''
Write-Host "Eureka:  $eurekaZone"
Write-Host "Gateway: http://localhost:9090"
Write-Host "Logs:    $logDir"
Write-Host 'School UI: cd apps\school-ui; npm start'
Write-Host 'Smoke: headers X-Tenant-Id=demo-school, X-Branch-Id=main'
Write-Host '  GET http://localhost:9090/api/config/design-studio/theme'
