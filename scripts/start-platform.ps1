# Phase 3/4/5b: start SugamFlow discovery (:8761), auth (:8085), shop (:8080),
# MailHog SMTP (:1025 / UI :8025), notification delivery (:8087), gateway (:9090).
# School services register with Eureka; school-ui calls http://localhost:9090
param(
  [string]$SugamFlowRoot = 'D:\sugamFlow',
  [switch]$SkipAuthServices,
  [switch]$SkipMailHog
)
$ErrorActionPreference = 'Stop'

$schoolRoot = Split-Path -Parent $PSScriptRoot
$platformLogDir = Join-Path $schoolRoot 'logs\platform'
New-Item -ItemType Directory -Force -Path $platformLogDir | Out-Null

# auth-service application.properties ends with security.jwt.secret=${SECURITY_JWT_SECRET:}
# (empty default), which crashes JwtService with WeakKeyException unless set.
# Match docker-compose / gateway local default so login JWTs verify through :9090.
$JwtSecretDefault = '01234567890123456789012345678901'
if (-not $env:SECURITY_JWT_SECRET -or [string]::IsNullOrWhiteSpace($env:SECURITY_JWT_SECRET)) {
  $env:SECURITY_JWT_SECRET = $JwtSecretDefault
  Write-Host "SECURITY_JWT_SECRET not set - using local docker-compose default for jar startup"
}

function Test-PortOpen([int]$Port) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.ConnectAsync('127.0.0.1', $Port).Wait(800) | Out-Null
    $ok = $c.Connected
    $c.Close()
    return $ok
  } catch {
    return $false
  }
}

function Get-FatJar([string]$Dir, [string]$Prefix) {
  Get-ChildItem "$Dir\target\$Prefix*.jar" |
    Where-Object { $_.Name -notlike '*original*' -and $_.Name -notlike '*-sources*' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
}

function Wait-Http([string]$Url, [int]$Seconds = 60) {
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

function Show-LogTail([string]$Path, [int]$Lines = 25) {
  if (Test-Path -LiteralPath $Path) {
    Write-Host "--- last lines: $Path ---" -ForegroundColor DarkYellow
    Get-Content -LiteralPath $Path -Tail $Lines | ForEach-Object { Write-Host $_ }
  }
}

$discoveryJar = Get-FatJar (Join-Path $SugamFlowRoot 'discovery-service') 'discovery-service'
$gatewayJar = Get-FatJar (Join-Path $SugamFlowRoot 'gateway-service') 'gateway-service'
if (-not $discoveryJar) { throw "discovery jar not found under $SugamFlowRoot\discovery-service\target" }
if (-not $gatewayJar) { throw "gateway jar not found under $SugamFlowRoot\gateway-service\target" }

if (-not (Test-PortOpen 8761)) {
  Write-Host "Starting discovery-service from $($discoveryJar.FullName)"
  Start-Process -FilePath 'java' -ArgumentList @(
    '-jar', $discoveryJar.FullName,
    '--spring.profiles.active=local',
    '--server.port=8761'
  ) -WindowStyle Minimized
} else {
  Write-Host 'discovery-service already listening on :8761'
}

if (-not (Wait-Http 'http://localhost:8761/actuator/health' 90)) {
  throw 'discovery-service did not become healthy on :8761'
}
Write-Host 'Eureka UP: http://localhost:8761'

function Start-PlatformServiceIfNeeded {
  param(
    [int]$Port,
    [string]$Name,
    [string]$JarDir,
    [string]$JarPrefix,
    [string[]]$ExtraArgs = @(),
    [switch]$Required
  )
  if (Test-PortOpen $Port) {
    Write-Host "$Name already listening on :$Port"
    return
  }
  $jar = Get-FatJar (Join-Path $SugamFlowRoot $JarDir) $JarPrefix
  if (-not $jar) { throw "$Name jar not found under $JarDir\target" }
  $outLog = Join-Path $platformLogDir "$Name.out.log"
  $errLog = Join-Path $platformLogDir "$Name.err.log"
  Write-Host "Starting $Name from $($jar.FullName)"
  Write-Host "  logs: $outLog"
  $args = @(
    '-jar', $jar.FullName,
    '--spring.profiles.active=local',
    "--server.port=$Port",
    "--security.jwt.secret=$($env:SECURITY_JWT_SECRET)"
  ) + $ExtraArgs
  $proc = Start-Process -FilePath 'java' -ArgumentList $args `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden
  $healthy = $false
  for ($i = 1; $i -le 120; $i++) {
    if ($proc.HasExited) {
      Write-Warning "$Name exited early (pid $($proc.Id), code $($proc.ExitCode))"
      Show-LogTail $errLog
      Show-LogTail $outLog
      if ($Required) {
        throw "$Name failed to start - see $errLog (common: missing SECURITY_JWT_SECRET / authdb)"
      }
      return
    }
    try {
      Invoke-WebRequest -Uri "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 2 | Out-Null
      $healthy = $true
      break
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  if (-not $healthy) {
    Write-Warning "$Name health not confirmed on :$Port after 120s"
    Show-LogTail $errLog
    Show-LogTail $outLog
    if ($Required) {
      throw "$Name did not become healthy on :$Port - login will fail"
    }
  } else {
    Write-Host "$Name UP: http://localhost:$Port"
  }
}

if (-not $SkipAuthServices) {
  Start-PlatformServiceIfNeeded -Port 8085 -Name 'auth-service' -JarDir 'auth-service' -JarPrefix 'auth-service' -Required -ExtraArgs @(
    '--eureka.client.service-url.defaultZone=http://localhost:8761/eureka',
    '--eureka.instance.prefer-ip-address=true',
    '--eureka.instance.hostname=localhost',
    '--auth.integration.shop-base-url=http://localhost:8080',
    '--spring.cloud.config.enabled=false',
    '--spring.config.import=optional:configserver:http://localhost:8888'
  )
  Start-PlatformServiceIfNeeded -Port 8080 -Name 'shop-service' -JarDir 'shop-service' -JarPrefix 'shop-service' -Required -ExtraArgs @(
    '--eureka.client.service-url.defaultZone=http://localhost:8761/eureka',
    '--eureka.instance.prefer-ip-address=true',
    '--eureka.instance.hostname=localhost',
    '--spring.cloud.config.enabled=false',
    '--spring.config.import=optional:configserver:http://localhost:8888'
  )
}

# SMTP sink for real EMAIL SENT (notification-service defaults: localhost:1025).
# UI: http://localhost:8025
if (-not $SkipMailHog) {
  if (Test-PortOpen 1025) {
    Write-Host 'SMTP already listening on :1025 (MailHog/Mailpit)'
  } else {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
      Write-Warning 'Docker not found - EMAIL will FAIL until SMTP is available on localhost:1025'
      Write-Warning 'Install Docker or run: docker run -d --name school-mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog:v1.0.1'
    } else {
      try {
        $existing = docker ps -a --filter 'name=^school-mailhog$' --format '{{.Names}}' 2>$null
        if ($existing -eq 'school-mailhog') {
          Write-Host 'Starting existing school-mailhog container...'
          docker start school-mailhog | Out-Null
        } else {
          Write-Host 'Starting MailHog (SMTP :1025, UI :8025)...'
          docker run -d --name school-mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog:v1.0.1 | Out-Null
        }
        $smtpUp = $false
        for ($i = 1; $i -le 30; $i++) {
          if (Test-PortOpen 1025) { $smtpUp = $true; break }
          Start-Sleep -Seconds 1
        }
        if ($smtpUp) {
          Write-Host 'MailHog UP: SMTP http://localhost:1025 | UI http://localhost:8025'
        } else {
          Write-Warning 'MailHog did not open :1025 - EMAIL delivery will FAIL'
        }
      } catch {
        Write-Warning "Docker unavailable (MailHog skipped): $($_.Exception.Message)"
        Write-Warning 'Start Docker Desktop later, or re-run with -SkipMailHog. Login does not need MailHog.'
      }
    }
  }
} else {
  Write-Host 'SkipMailHog set - using existing SMTP or EMAIL may FAIL'
}

# Delivery bus for admission offer-letter emails / SMS (Phase 5b).
# Actuator may report DOWN (mail health) while the API still accepts posts - wait on TCP.
if (Test-PortOpen 8087) {
  Write-Host 'notification-service already listening on :8087'
  if (-not (Test-PortOpen 1025)) {
    Write-Warning 'SMTP :1025 is down while notification-service is up - restart notification after starting MailHog for EMAIL SENT'
  }
} else {
  $notifJar = Get-FatJar (Join-Path $SugamFlowRoot 'notification-service') 'notification-service'
  if (-not $notifJar) {
    Write-Warning 'notification-service jar not found - offer-letter EMAIL delivery will fail until started'
  } else {
    Write-Host "Starting notification-service from $($notifJar.FullName)"
    Start-Process -FilePath 'java' -ArgumentList @(
      '-jar', $notifJar.FullName,
      '--spring.profiles.active=local',
      '--server.port=8087',
      "--security.jwt.secret=$($env:SECURITY_JWT_SECRET)",
      '--eureka.client.service-url.defaultZone=http://localhost:8761/eureka',
      '--eureka.instance.prefer-ip-address=true',
      '--eureka.instance.ip-address=127.0.0.1',
      '--eureka.instance.hostname=localhost',
      '--spring.cloud.config.enabled=false',
      '--spring.config.import=optional:configserver:http://localhost:8888',
      '--spring.mail.host=localhost',
      '--spring.mail.port=1025',
      '--spring.mail.properties.mail.smtp.auth=false',
      '--spring.mail.properties.mail.smtp.starttls.enable=false'
    ) -WindowStyle Minimized
    $up = $false
    for ($i = 1; $i -le 60; $i++) {
      if (Test-PortOpen 8087) { $up = $true; break }
      Start-Sleep -Seconds 1
    }
    if ($up) { Write-Host 'notification-service listening on :8087 (SMTP localhost:1025)' }
    else { Write-Warning 'notification-service did not open :8087 in time' }
  }
}

# Rebuild gateway if school routes / JWT filter sources are newer than the fat jar.
$gatewaySrc = Join-Path $SugamFlowRoot 'gateway-service\src'
$newestSrc = Get-ChildItem $gatewaySrc -Recurse -File -EA SilentlyContinue |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($newestSrc -and $gatewayJar -and $newestSrc.LastWriteTime -gt $gatewayJar.LastWriteTime) {
  Write-Host 'Gateway sources newer than jar - rebuilding gateway-service...'
  Push-Location (Join-Path $SugamFlowRoot 'gateway-service')
  try {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'gateway mvn package failed' }
  } finally {
    Pop-Location
  }
  $gatewayJar = Get-FatJar (Join-Path $SugamFlowRoot 'gateway-service') 'gateway-service'
}

if (Test-PortOpen 9090) {
  Write-Host 'Stopping existing gateway on :9090 so school routes load from rebuilt jar'
  Get-NetTCPConnection -LocalPort 9090 -State Listen -EA SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -EA SilentlyContinue
  }
  Start-Sleep -Seconds 2
}

Write-Host "Starting gateway-service from $($gatewayJar.FullName)"
$gwOut = Join-Path $platformLogDir 'gateway-service.out.log'
$gwErr = Join-Path $platformLogDir 'gateway-service.err.log'
Start-Process -FilePath 'java' -ArgumentList @(
  '-jar', $gatewayJar.FullName,
  '--spring.profiles.active=local',
  '--server.port=9090',
  "--security.jwt.secret=$($env:SECURITY_JWT_SECRET)",
  '--eureka.client.service-url.defaultZone=http://localhost:8761/eureka',
  '--spring.cloud.config.enabled=false',
  '--spring.config.import=optional:configserver:http://localhost:8888'
) -RedirectStandardOutput $gwOut -RedirectStandardError $gwErr -WindowStyle Hidden

if (-not (Wait-Http 'http://localhost:9090/actuator/health' 90)) {
  Write-Warning 'gateway health endpoint not ready yet - it may still be starting; continue and retry school routes'
  Show-LogTail $gwErr
} else {
  Write-Host 'Gateway UP: http://localhost:9090'
}

Write-Host ''
Write-Host 'Platform ready. Next:'
Write-Host '  .\scripts\seed-school-demo-auth.ps1   # first time / if login fails'
Write-Host '  .\scripts\start-services.ps1 -AdvertiseIp 127.0.0.1'
Write-Host '  School UI (if not already): cd apps\school-ui; npx ng serve --port 4300'
Write-Host '  Verify: .\scripts\verify-auth.ps1'
Write-Host "  Platform logs: $platformLogDir"
Write-Host '  SMTP UI: http://localhost:8025   | smoke: .\scripts\verify-smtp-email.ps1'
