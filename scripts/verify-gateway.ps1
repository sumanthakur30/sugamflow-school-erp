# Verifies Phase 3/4: Eureka registration + school routes through gateway with JWT.
$ErrorActionPreference = 'Continue'

Write-Host '=== Eureka apps ==='
$eurekaUrls = @('http://localhost:8761/eureka/apps', 'http://localhost:18761/eureka/apps')
$apps = $null
$eurekaUsed = $null
foreach ($url in $eurekaUrls) {
  try {
    $apps = Invoke-RestMethod $url -Headers @{ Accept = 'application/json' } -TimeoutSec 5
    $eurekaUsed = $url
    break
  } catch {
    Write-Host "Eureka miss $url : $($_.Exception.Message)"
  }
}
if (-not $apps) {
  Write-Host 'Eureka not reachable on :8761 (or legacy :18761)'
  exit 1
}
if ($eurekaUsed -match ':18761') {
  Write-Host 'WARN Eureka on legacy :18761 — recreate discovery to publish host :8761' -ForegroundColor Yellow
}
Write-Host "OK   Eureka $eurekaUsed"
$names = @($apps.applications.application | ForEach-Object { $_.name })
$names | Sort-Object | ForEach-Object { Write-Host " - $_" }

Write-Host ''
Write-Host '=== Login (demo-school) ==='
$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

try {
  $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
    -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
} catch {
  Write-Host "Login failed: $($_.Exception.Message)"
  Write-Host 'Run .\scripts\seed-school-demo-auth.ps1 and ensure auth/shop services are up.'
  exit 1
}

if ($login.mfaRequired) {
  Write-Host 'MFA required — cannot verify gateway routes automatically.'
  exit 1
}

$headers = @{
  Authorization           = "Bearer $($login.accessToken)"
  'X-Tenant-Id'           = 'demo-school'
  'X-Branch-Id'           = 'main'
  'X-Academic-Session-Id' = '2025-26'
  'X-Shop-Id'             = 'demo-school'
}

$checks = @(
  @{ Name = 'design-studio'; Url = 'http://localhost:9090/api/config/design-studio/theme' },
  @{ Name = 'subscription-plans'; Url = 'http://localhost:9090/api/subscription/plans' },
  @{ Name = 'forms'; Url = 'http://localhost:9090/api/forms' },
  @{ Name = 'workflows'; Url = 'http://localhost:9090/api/workflows' },
  @{ Name = 'rules'; Url = 'http://localhost:9090/api/rules' },
  @{ Name = 'reports'; Url = 'http://localhost:9090/api/reports/templates' },
  @{ Name = 'notif-config'; Url = 'http://localhost:9090/api/school/notification-config/events' },
  @{ Name = 'audit'; Url = 'http://localhost:9090/api/audit/config-changes' }
)

Write-Host ''
Write-Host '=== Gateway routes (authenticated) ==='
$fail = 0
foreach ($c in $checks) {
  try {
    $r = Invoke-RestMethod -Headers $headers -Uri $c.Url -TimeoutSec 20
    $ok = $r.success -eq $true
    if ($ok) {
      Write-Host ("OK   {0}" -f $c.Name)
    } else {
      Write-Host ("WARN {0} unexpected body" -f $c.Name)
      $fail++
    }
  } catch {
    Write-Host ("FAIL {0}: {1}" -f $c.Name, $_.Exception.Message)
    $fail++
  }
}

if ($fail -gt 0) {
  Write-Host ""
  Write-Host "Failed checks: $fail"
  exit 1
}
Write-Host ''
Write-Host 'Gateway verification passed (JWT).'
