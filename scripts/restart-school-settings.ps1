<#
.SYNOPSIS
  Rebuild and restart school-settings-service (:8181) after Design Studio asset changes.
#>
param(
  [switch]$StopOnly,
  [string]$AdvertiseIp = ''
)
$ErrorActionPreference = 'Stop'
$ServiceName = 'school-settings-service'
$Port = 8181
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs\services'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-JavaExe {
  if ($env:JAVA_HOME) {
    $c = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $c) { return $c }
  }
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if (-not $cmd) { throw 'java not found' }
  return $cmd.Source
}

function Stop-OnPort([int]$p) {
  Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
  }
  Start-Sleep -Seconds 1
}

Write-Host "Restarting $ServiceName (:$Port)" -ForegroundColor Cyan
Stop-OnPort $Port
if ($StopOnly) { exit 0 }

if (-not $AdvertiseIp) {
  $AdvertiseIp = if ($env:SCHOOL_EUREKA_ADVERTISE_IP) { $env:SCHOOL_EUREKA_ADVERTISE_IP } else { '127.0.0.1' }
}
$eureka = if ($env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE) {
  $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
} else {
  'http://localhost:8761/eureka'
}

Push-Location (Join-Path $root 'services')
try {
  mvn -q -pl $ServiceName -am -DskipTests package
  if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
} finally {
  Pop-Location
}

$jar = Get-ChildItem "$root\services\$ServiceName\target\$ServiceName-*.jar" |
  Where-Object { $_.Name -notlike '*original*' } |
  Select-Object -First 1
if (-not $jar) { throw 'Missing jar' }

$java = Get-JavaExe
$out = Join-Path $logDir "$ServiceName.out.log"
$err = Join-Path $logDir "$ServiceName.err.log"
Start-Process -FilePath $java -ArgumentList @(
  '-jar', $jar.FullName,
  "--server.port=$Port",
  "--eureka.client.service-url.defaultZone=$eureka",
  '--eureka.client.register-with-eureka=true',
  '--eureka.instance.prefer-ip-address=true',
  "--eureka.instance.hostname=$AdvertiseIp",
  "--eureka.instance.ip-address=$AdvertiseIp"
) -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err

for ($i = 1; $i -le 90; $i++) {
  try {
    Invoke-WebRequest "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "$ServiceName UP" -ForegroundColor Green
    exit 0
  } catch {
    Start-Sleep -Seconds 1
  }
}
Write-Warning "Health not confirmed - check $err"
exit 1
