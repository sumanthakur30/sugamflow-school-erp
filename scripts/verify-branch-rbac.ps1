# Multi-campus RBAC smoke: elevated can manage; non-elevated cannot create/update.
$ErrorActionPreference = 'Stop'

function Login([string]$user) {
  $body = @{ shopId = 'demo-school'; username = $user; password = 'password' } | ConvertTo-Json
  $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
    -ContentType 'application/json' -Body $body -TimeoutSec 30
  if (-not $login.accessToken) { throw "Login failed for $user" }
  return $login
}

function Headers($login, [string]$branch, [string]$role) {
  return @{
    Authorization           = "Bearer $($login.accessToken)"
    'X-Tenant-Id'           = 'demo-school'
    'X-Branch-Id'           = $branch
    'X-Academic-Session-Id' = '2025-26'
    'X-Shop-Id'             = 'demo-school'
    'X-User-Id'             = $login.username
    'X-Role-Code'           = $role
  }
}

Write-Host '=== Owner bootstrap (elevated) ==='
$owner = Login 'admin_demo-school'
$boot = Invoke-RestMethod -Headers (Headers $owner 'main' 'SHOP_OWNER') `
  -Uri 'http://localhost:9090/api/config/branches/bootstrap'
$b = if ($boot.data) { $boot.data } else { $boot }
if ($b.canManage -ne $true) { throw "Owner canManage expected true, got $($b.canManage)" }
Write-Host "OK   owner canManage=$($b.canManage) canAdd=$($b.canAdd) branches=$($b.branchCount)"

Write-Host '=== Teacher create denied (JWT role via portal override is not available; use PARENT claim simulation by calling settings directly with gateway-verified headers) ==='
# Gateway overwrites X-Role-Code from JWT. Call settings-service directly with TEACHER role.
$denied = $false
try {
  $headers = @{
    'X-Gateway-Verified'    = 'true'
    'X-Tenant-Id'           = 'demo-school'
    'X-Branch-Id'           = 'main'
    'X-Academic-Session-Id' = '2025-26'
    'X-User-Id'             = 'teacher1'
    'X-Role-Code'           = 'TEACHER'
  }
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:8281/api/config/branches' `
    -ContentType 'application/json' `
    -Body (@{ branchKey = 'rbac-denied'; name = 'Should Fail' } | ConvertTo-Json) | Out-Null
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -ne 403 -and $code -ne 400) {
    throw "Expected 403 for teacher create, got $code $($_.ErrorDetails.Message)"
  }
  $denied = $true
  Write-Host "OK   teacher create blocked ($code)"
}
if (-not $denied) { throw 'Teacher create unexpectedly succeeded' }

Write-Host '=== Teacher update denied ==='
$denied = $false
try {
  $headers = @{
    'X-Gateway-Verified'    = 'true'
    'X-Tenant-Id'           = 'demo-school'
    'X-Branch-Id'           = 'main'
    'X-Academic-Session-Id' = '2025-26'
    'X-User-Id'             = 'teacher1'
    'X-Role-Code'           = 'TEACHER'
  }
  Invoke-RestMethod -Method Put -Headers $headers `
    -Uri 'http://localhost:8281/api/config/branches/main' `
    -ContentType 'application/json' `
    -Body (@{ city = 'hack' } | ConvertTo-Json) | Out-Null
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -ne 403) { throw "Expected 403 for teacher update, got $code" }
  $denied = $true
  Write-Host "OK   teacher update blocked ($code)"
}
if (-not $denied) { throw 'Teacher update unexpectedly succeeded' }

Write-Host '=== Owner update allowed ==='
$marker = "rbac-$(Get-Date -Format 'HHmmss')"
$upd = Invoke-RestMethod -Method Put -Headers (Headers $owner 'main' 'SHOP_OWNER') `
  -Uri 'http://localhost:9090/api/config/branches/main' `
  -ContentType 'application/json' `
  -Body (@{ city = $marker } | ConvertTo-Json)
$u = if ($upd.data) { $upd.data } else { $upd }
if ([string]$u.city -ne $marker) { throw "Owner update failed city=$($u.city)" }
Write-Host "OK   owner update city=$marker"

Write-Host ''
Write-Host 'PASS multi-campus RBAC smoke'
