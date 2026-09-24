<#
.SYNOPSIS
  Rebuild and restart only school payroll-service (:8197).

.DESCRIPTION
  Stops any JVM listening on 8197 / named payroll-service, packages the module,
  starts the jar with the same Eureka advertise defaults as start-services.ps1,
  and waits for actuator health. Use after Flyway / payroll code changes (e.g. V4).

.EXAMPLE
  cd D:\school
  .\scripts\restart-payroll.ps1

.EXAMPLE
  # Gateway runs in Docker (compose-local / start-common-platform)
  .\scripts\restart-payroll.ps1 -AdvertiseIp host.docker.internal

.EXAMPLE
  # Stop only (no rebuild/start)
  .\scripts\restart-payroll.ps1 -StopOnly
#>
param(
  [switch]$StopOnly,
  [switch]$SkipBuild,
  # Host-jar gateway: 127.0.0.1 | Docker gateway: host.docker.internal
  [string]$AdvertiseIp = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$ServiceName = 'payroll-service'
$Port = 8197
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs\services'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-JavaExe {
  if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) { return $candidate }
  }
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if (-not $cmd) { throw 'java not found on PATH' }
  if ($cmd.Source -match 'javapath') {
    $jdk = Get-ChildItem 'C:\Program Files\Java\jdk*\bin\java.exe' -ErrorAction SilentlyContinue |
      Sort-Object FullName -Descending |
      Select-Object -First 1
    if ($jdk) { return $jdk.FullName }
  }
  return $cmd.Source
}

function Test-PortOpen([int]$ListenPort) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.ConnectAsync('127.0.0.1', $ListenPort).Wait(500) | Out-Null
    $ok = $c.Connected
    $c.Close()
    return $ok
  } catch {
    return $false
  }
}

function Wait-Http([string]$Url, [int]$Seconds = 90, [string]$Label = '') {
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

function Test-DockerGatewayRunning {
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

function Stop-Payroll {
  Write-Host "Stopping $ServiceName (:$Port)..." -ForegroundColor Cyan
  $killed = @{}
  Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    if (-not $killed.ContainsKey($_.OwningProcess)) {
      Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
      $killed[$_.OwningProcess] = $true
    }
  }
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object {
      $_.CommandLine -and (
        $_.CommandLine -match '\\payroll-service\\target\\' -or
        $_.CommandLine -match '/payroll-service/target/' -or
        $_.CommandLine -match 'payroll-service-.*\.jar'
      )
    } |
    ForEach-Object {
      if (-not $killed.ContainsKey($_.ProcessId)) {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $killed[$_.ProcessId] = $true
      }
    }
  if ($killed.Count -gt 0) {
    Write-Host "Stopped $($killed.Count) process(es)"
    Start-Sleep -Seconds 2
  } else {
    Write-Host 'No payroll-service process found'
  }
}

Write-Host ''
Write-Host " School payroll-service restart (:$Port)" -ForegroundColor Cyan
Write-Host ''

Stop-Payroll

if ($StopOnly) {
  Write-Host 'StopOnly - done.' -ForegroundColor Green
  exit 0
}

if (-not $AdvertiseIp) {
  if ($env:SCHOOL_EUREKA_ADVERTISE_IP) {
    $AdvertiseIp = $env:SCHOOL_EUREKA_ADVERTISE_IP
  } elseif (Test-DockerGatewayRunning) {
    $AdvertiseIp = 'host.docker.internal'
  } else {
    $AdvertiseIp = '127.0.0.1'
  }
}

$eurekaZone = if ($env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE) {
  $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
} else {
  'http://localhost:8761/eureka'
}

if (-not (Wait-Http 'http://localhost:8761/actuator/health' 5)) {
  Write-Warning 'Eureka not healthy at http://localhost:8761 - run .\scripts\start-platform.ps1 first.'
}

if (-not $SkipBuild) {
  Write-Host "Building $ServiceName..." -ForegroundColor Yellow
  Push-Location (Join-Path $root 'services')
  try {
    mvn -q -pl $ServiceName -am -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed for $ServiceName (exit $LASTEXITCODE)" }
  } finally {
    Pop-Location
  }
}

$jar = Get-ChildItem "$root\services\$ServiceName\target\$ServiceName-*.jar" |
  Where-Object { $_.Name -notlike '*original*' } |
  Select-Object -First 1
if (-not $jar) { throw "Missing jar for $ServiceName - build failed or target empty" }

$javaExe = Get-JavaExe
$outLog = Join-Path $logDir "$ServiceName.out.log"
$errLog = Join-Path $logDir "$ServiceName.err.log"

Write-Host "Java: $javaExe"
Write-Host "Jar:  $($jar.FullName)"
Write-Host "Eureka advertise IP: $AdvertiseIp" -ForegroundColor Yellow
Write-Host "Starting $ServiceName on :$Port"

Start-Process -FilePath $javaExe -ArgumentList @(
  '-jar', $jar.FullName,
  "--server.port=$Port",
  "--eureka.client.service-url.defaultZone=$eurekaZone",
  '--eureka.client.register-with-eureka=true',
  '--eureka.instance.prefer-ip-address=true',
  "--eureka.instance.hostname=$AdvertiseIp",
  "--eureka.instance.ip-address=$AdvertiseIp",
  '--management.endpoints.web.exposure.include=health,info,prometheus,metrics',
  '--management.endpoint.health.probes.enabled=true',
  '--management.health.livenessstate.enabled=true',
  '--management.health.readinessstate.enabled=true',
  '--management.endpoint.health.group.liveness.include=livenessState',
  '--management.endpoint.health.group.readiness.include=readinessState,db',
  '--server.shutdown=graceful',
  '--spring.lifecycle.timeout-per-shutdown-phase=30s'
) -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog

Write-Host "Waiting for $ServiceName health..."
if (Wait-Http "http://localhost:$Port/actuator/health" 120 $ServiceName) {
  Write-Host "$ServiceName UP on :$Port" -ForegroundColor Green
} else {
  Write-Warning "$ServiceName health not confirmed"
  Write-Warning "Check logs:`n  $outLog`n  $errLog"
  exit 1
}

Write-Host ''
Write-Host "Direct:  http://localhost:$Port/actuator/health"
Write-Host "Gateway: http://localhost:9090/api/payroll/bootstrap  (needs auth + tenant headers)"
Write-Host "Logs:    $logDir\$ServiceName.*.log"
Write-Host "Verify:  .\scripts\verify-payroll.ps1"
Write-Host ''
