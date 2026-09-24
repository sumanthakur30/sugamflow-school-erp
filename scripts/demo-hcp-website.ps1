<#
.SYNOPSIS
  Seed HCP demo website content and open CMS + public site for testing.

.EXAMPLE
  .\scripts\demo-hcp-website.ps1
#>
param(
  [switch]$SkipServiceRestart,
  [string]$BaseUrl = 'http://localhost:9090',
  [string]$CmsUrl = 'http://localhost:4200/admin/website-cms',
  [string]$PublicUrl = 'http://localhost:4300'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$ProgressPreference = 'SilentlyContinue'

function Get-JavaExe {
  if ($env:JAVA_HOME) {
    $c = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $c) { return $c }
  }
  return (Get-Command java).Source
}

function Stop-Port([int]$Port) {
  Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
}

function Wait-Http([string]$Url, [int]$Seconds = 90) {
  for ($i = 1; $i -le $Seconds; $i++) {
    try {
      Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 | Out-Null
      return $true
    } catch { Start-Sleep -Seconds 1 }
  }
  return $false
}

Write-Host "HCP demo website" -ForegroundColor Cyan

# Prefer direct SQL when psql is available (fast); Flyway still runs on service start.
$psqlCmd = $null
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($psql) {
  $psqlCmd = $psql.Source
} else {
  $found = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
  if ($found) { $psqlCmd = $found.FullName }
}

if ($psqlCmd) {
  Write-Host "Applying demo SQL via $psqlCmd ..." -ForegroundColor Yellow
  $env:PGPASSWORD = if ($env:SCHOOL_WEBSITE_DB_PASSWORD) { $env:SCHOOL_WEBSITE_DB_PASSWORD } else { 'school_website' }
  & $psqlCmd -h localhost -U school_website -d school_website_db -v ON_ERROR_STOP=1 -f "$root\services\website-service\src\main\resources\db\migration\V11__hcp_demo_website.sql"
  $env:PGPASSWORD = if ($env:SCHOOL_CMS_DB_PASSWORD) { $env:SCHOOL_CMS_DB_PASSWORD } else { 'school_cms' }
  & $psqlCmd -h localhost -U school_cms -d school_cms_db -v ON_ERROR_STOP=1 -f "$root\services\cms-service\src\main\resources\db\migration\V4__hcp_demo_website_content.sql"
} else {
  Write-Host "psql not found - demo SQL will apply via Flyway when website/cms restart." -ForegroundColor DarkYellow
}

if (-not $SkipServiceRestart) {
  Write-Host "Building + restarting website-service / cms-service..." -ForegroundColor Yellow
  Stop-Port 8200
  Stop-Port 8201
  Push-Location "$root\services"
  try {
    mvn -q -pl website-service,cms-service -am -DskipTests package
  } finally {
    Pop-Location
  }
  $java = Get-JavaExe
  $logDir = Join-Path $root 'logs\services'
  New-Item -ItemType Directory -Force -Path $logDir | Out-Null
  $eureka = if ($env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE) { $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE } else { 'http://localhost:8761/eureka' }
  foreach ($pair in @(
    @{ Name = 'website-service'; Port = 8200 },
    @{ Name = 'cms-service'; Port = 8201 }
  )) {
    $jar = Get-ChildItem "$root\services\$($pair.Name)\target\$($pair.Name)-*.jar" |
      Where-Object { $_.Name -notlike '*original*' } | Select-Object -First 1
    if (-not $jar) { throw "Missing jar $($pair.Name)" }
    Start-Process -FilePath $java -ArgumentList @(
      '-jar', $jar.FullName,
      "--server.port=$($pair.Port)",
      "--eureka.client.service-url.defaultZone=$eureka",
      '--eureka.client.register-with-eureka=true',
      '--eureka.instance.prefer-ip-address=true',
      '--eureka.instance.hostname=127.0.0.1',
      '--eureka.instance.ip-address=127.0.0.1'
    ) -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $logDir "$($pair.Name).out.log") `
      -RedirectStandardError (Join-Path $logDir "$($pair.Name).err.log")
  }
}

Write-Host "Waiting for public APIs..." -ForegroundColor DarkGray
if (-not (Wait-Http "$BaseUrl/api/website/public/resolve?host=hcp.localhost" 120)) {
  Write-Warning "website resolve not ready - check gateway + website-service"
}
if (-not (Wait-Http "$BaseUrl/api/cms/public/pages/about?organizationId=HCP-01" 60)) {
  Write-Warning "cms about page not ready - check cms-service"
}

try {
  $r = Invoke-RestMethod "$BaseUrl/api/website/public/resolve?host=hcp.localhost"
  Write-Host ("OK  {0} | {1} | ogImage={2}" -f $r.data.displayName, $r.data.status, [bool]$r.data.seo.ogImageUrl) -ForegroundColor Green
} catch {
  Write-Warning $_.Exception.Message
}

$uiUp = $false
try { Invoke-WebRequest $PublicUrl -UseBasicParsing -TimeoutSec 2 | Out-Null; $uiUp = $true } catch {}
if (-not $uiUp) {
  Write-Host "Starting school-website-ui (:4300) in a new window..." -ForegroundColor Yellow
  Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$root\apps\school-website-ui'; npm start"
  )
  Write-Host "Wait until ng serve is ready, then refresh the public tab." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Open these to test:" -ForegroundColor Cyan
Write-Host "  Builder CMS : $CmsUrl"
Write-Host "  Public home : $PublicUrl"
Write-Host "  About       : $PublicUrl/about"
Write-Host "  Apply       : $PublicUrl/admission/apply"
Write-Host "  Or host     : http://hcp.localhost:4300"

try { Start-Process $CmsUrl } catch {}
Start-Sleep -Milliseconds 500
try { Start-Process $PublicUrl } catch {}
