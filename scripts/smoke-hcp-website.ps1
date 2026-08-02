<#
.SYNOPSIS
  Smoke-test HCP School Website Platform APIs (local or remote gateway).

.EXAMPLE
  .\scripts\smoke-hcp-website.ps1
  .\scripts\smoke-hcp-website.ps1 -BaseUrl http://localhost:9090 -HostName hcp.localhost
  .\scripts\smoke-hcp-website.ps1 -BaseUrl https://api.sugamflow.com -HostName hcpschool.com
#>
param(
  [string]$BaseUrl = "http://localhost:9090",
  [string]$HostName = "hcp.localhost",
  [string]$OrganizationId = "HCP-01"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$failed = 0

function Assert-Ok([string]$name, [scriptblock]$block) {
  try {
    $result = & $block
    Write-Host "PASS  $name" -ForegroundColor Green
    return $result
  } catch {
    $script:failed++
    Write-Host "FAIL  $name - $($_.Exception.Message)" -ForegroundColor Red
    return $null
  }
}

Write-Host "HCP website smoke -> $BaseUrl (host=$HostName, org=$OrganizationId)" -ForegroundColor Cyan

$resolve = Assert-Ok "resolve host" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/website/public/resolve?host=$HostName" -Method GET
  if (-not $r.data) { throw "missing data" }
  if ($r.data.organizationId -ne $OrganizationId) {
    throw "expected org $OrganizationId, got $($r.data.organizationId)"
  }
  if ($r.data.status -eq "SUSPENDED") { throw "site suspended" }
  $r.data
}

Assert-Ok "sitemap" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/website/public/sitemap?host=$HostName" -Method GET
  if (-not $r.data.urls) { throw "no urls" }
  $r.data
}

Assert-Ok "cms about page" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/cms/public/pages/about?organizationId=$OrganizationId" -Method GET
  if (-not $r.data.slug) { throw "missing page" }
  $r.data
}

Assert-Ok "cms news list" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/cms/public/news?organizationId=$OrganizationId" -Method GET
  if ($null -eq $r.data) { throw "missing data" }
  $r.data
}

Assert-Ok "cms blog list" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/cms/public/blog?organizationId=$OrganizationId" -Method GET
  if ($null -eq $r.data) { throw "missing data" }
  $r.data
}

Assert-Ok "cms alumni list" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/cms/public/alumni?organizationId=$OrganizationId" -Method GET
  if ($null -eq $r.data) { throw "missing data" }
  $r.data
}

Assert-Ok "marketplace templates" {
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/website/public/marketplace/templates" -Method GET
  if (-not $r.data -or $r.data.Count -lt 1) { throw "no templates" }
  $r.data
}

Assert-Ok "prerender html" {
  $r = Invoke-WebRequest -Uri "$BaseUrl/api/website/public/prerender?host=$HostName&path=/" -Method GET -UseBasicParsing
  if ($r.StatusCode -ne 200) { throw "status $($r.StatusCode)" }
  if ($r.Content -notmatch "<title>") { throw "missing title" }
  $r.StatusCode
}

Assert-Ok "analytics track" {
  $body = @{
    host = $HostName
    eventType = "page_view"
    path = "/smoke"
  } | ConvertTo-Json
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/website/public/track" -Method POST -Body $body -ContentType "application/json"
  if (-not $r.data.accepted) { throw "not accepted" }
  $r.data
}

Assert-Ok "admission apply (may 402/403 if feature off)" {
  $body = @{
    organizationId = $OrganizationId
    fullName = "HCP Smoke Tester"
    mobile = "9000000001"
    email = "smoke@hcpschool.test"
    classApplied = "1"
    message = "Automated smoke - safe to ignore"
  } | ConvertTo-Json
  try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/admission/public/apply" -Method POST -Body $body -ContentType "application/json"
    return $r.data
  } catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -in 402, 403) {
      Write-Host "WARN  admission feature gated ($code) - enable FEATURE_WEBSITE_ADMISSION" -ForegroundColor Yellow
      return $null
    }
    throw
  }
}

if ($resolve -and $resolve.cdnBaseUrl) {
  Write-Host "INFO  cdnBaseUrl=$($resolve.cdnBaseUrl)" -ForegroundColor DarkGray
} else {
  Write-Host "INFO  cdnBaseUrl empty (set WEBSITE_CDN_BASE_URL in prod)" -ForegroundColor DarkGray
}

Write-Host ""
if ($failed -gt 0) {
  Write-Host "RESULT: $failed check(s) failed" -ForegroundColor Red
  exit 1
}
Write-Host "RESULT: all critical checks passed" -ForegroundColor Green
exit 0
