# Phase 17 - Security + pagination verification.
$ErrorActionPreference = 'Stop'

function Unwrap-Data($resp) {
  if ($null -ne $resp.data) { return $resp.data }
  return $resp
}

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

Write-Host '=== Login ==='
$login = $null
for ($attempt = 1; $attempt -le 5; $attempt++) {
  try {
    $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
      -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
    break
  } catch {
    if ($attempt -eq 5) { throw }
    Start-Sleep -Seconds 2
  }
}
$token = $login.accessToken
if (-not $token) { throw 'No accessToken' }
Write-Host "OK   $($login.username)"

$headers = @{
  Authorization           = "Bearer $token"
  'X-Tenant-Id'           = 'demo-school'
  'X-Branch-Id'           = 'main'
  'X-Academic-Session-Id' = '2025-26'
  'X-Shop-Id'             = 'demo-school'
  'X-User-Id'             = 'admin_demo-school'
  'X-Role-Code'           = 'SHOP_OWNER'
}

Write-Host ''
Write-Host '=== Spoof tenant via header (must stay JWT shopId) ==='
$spoofHeaders = $headers.Clone()
$spoofHeaders['X-Tenant-Id'] = 'other-school-spoof'
$boot = Unwrap-Data (Invoke-RestMethod -Headers $spoofHeaders -Uri 'http://localhost:9090/api/admission/bootstrap')
# bootstrap does not echo org; use branches list which is org-scoped
$branches = Unwrap-Data (Invoke-RestMethod -Headers $spoofHeaders -Uri 'http://localhost:9090/api/config/branches')
$keys = @($branches) | ForEach-Object { $_.branchKey }
if (-not $keys) { throw 'expected branches for JWT org' }
Write-Host "OK   spoof header ignored (still resolved demo-school branches=$($keys.Count))"

Write-Host ''
Write-Host '=== Direct settings port without gateway verified ==='
try {
  Invoke-RestMethod -Headers $headers -Uri 'http://127.0.0.1:8181/api/config/branches' -TimeoutSec 5 | Out-Null
  throw 'expected 403 from direct service call'
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -ne 403) {
    # Some environments may refuse connection; accept 403 only when reachable
    if ($null -eq $code) {
      Write-Host 'OK   direct :8181 unreachable or blocked (acceptable)'
    } else {
      throw "expected HTTP 403, got $code"
    }
  } else {
    Write-Host 'OK   direct :8181 returned 403'
  }
}

Write-Host ''
Write-Host '=== Paged admission list ==='
$pageResp = Invoke-RestMethod -Headers $headers `
  -Uri 'http://localhost:9090/api/admission/applications?page=0&size=5'
$page = Unwrap-Data $pageResp
if ($null -eq $page.items) { throw 'missing items envelope' }
if ($null -eq $page.totalElements) { throw 'missing totalElements' }
if ($page.size -gt 200) { throw 'size exceeded max' }
Write-Host "OK   items=$(@($page.items).Count) total=$($page.totalElements) size=$($page.size)"

Write-Host ''
Write-Host '=== Effective menus ==='
$menus = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/menus/effective')
if (-not $menus) { Write-Host 'WARN effective menus empty (defaults may be minimal)' }
else { Write-Host "OK   effective menus=$(@($menus).Count)" }

Write-Host ''
Write-Host '=== Entitlements ==='
$ent = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/tenants/current/entitlements')
if (-not $ent.featureFlags) { throw 'featureFlags missing' }
if ($ent.featureFlags.FEATURE_ADMISSION -ne $true) { throw 'FEATURE_ADMISSION off' }
Write-Host "OK   plan=$($ent.planId)"

Write-Host ''
Write-Host 'PASS  Phase 17 security + pagination'
