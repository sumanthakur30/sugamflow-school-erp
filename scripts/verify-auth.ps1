# Phase 4: login via auth-service + verify school routes require JWT through gateway.
param(
  [string]$OrganizationId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'

Write-Host '=== Login ==='
$loginBody = @{
  shopId   = $OrganizationId
  username = $Username
  password = $Password
} | ConvertTo-Json

try {
  $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
    -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
} catch {
  Write-Host "Login failed: $($_.Exception.Message)"
  Write-Host 'Ensure auth-service (:8085), shop-service (:8080), gateway (:9090) are up.'
  Write-Host 'Run: .\scripts\seed-school-demo-auth.ps1 if demo account is missing.'
  exit 1
}

if ($login.mfaRequired) {
  Write-Host 'MFA required — use a non-MFA demo account for verify-auth.'
  exit 1
}

$token = $login.accessToken
if (-not $token) {
  Write-Host 'No accessToken in login response'
  exit 1
}
Write-Host "OK   login as $($login.username) / $($login.shopId)"

$headers = @{
  Authorization           = "Bearer $token"
  'X-Tenant-Id'           = $OrganizationId
  'X-Branch-Id'           = 'main'
  'X-Academic-Session-Id' = '2025-26'
  'X-Shop-Id'             = $OrganizationId
}

$checks = @(
  @{ Name = 'design-studio'; Url = 'http://localhost:9090/api/config/design-studio/theme' },
  @{ Name = 'subscription'; Url = 'http://localhost:9090/api/subscription/plans' },
  @{ Name = 'forms'; Url = 'http://localhost:9090/api/forms' }
)

Write-Host ''
Write-Host '=== Authenticated gateway routes ==='
$fail = 0
foreach ($c in $checks) {
  try {
    $r = Invoke-RestMethod -Headers $headers -Uri $c.Url -TimeoutSec 20
    if ($r.success -eq $true) {
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

Write-Host ''
Write-Host '=== Unauthenticated should 401 ==='
try {
  Invoke-RestMethod -Uri 'http://localhost:9090/api/config/design-studio/theme' -TimeoutSec 10 | Out-Null
  Write-Host 'FAIL no-token request succeeded (expected 401)'
  $fail++
} catch {
  if ($_.Exception.Response.StatusCode.value__ -eq 401) {
    Write-Host 'OK   no-token blocked'
  } else {
    Write-Host ("FAIL no-token: {0}" -f $_.Exception.Message)
    $fail++
  }
}

if ($fail -gt 0) {
  Write-Host "Failed checks: $fail"
  exit 1
}
Write-Host 'Phase 4 auth verification passed.'
