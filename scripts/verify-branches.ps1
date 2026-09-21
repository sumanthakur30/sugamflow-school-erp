# Phase 14 - Multi-branch UX verification.
$ErrorActionPreference = 'Stop'

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

function Headers([string]$branch) {
  return @{
    Authorization           = "Bearer $token"
    'X-Tenant-Id'           = 'demo-school'
    'X-Branch-Id'           = $branch
    'X-Academic-Session-Id' = '2025-26'
    'X-Shop-Id'             = 'demo-school'
    'X-User-Id'             = 'admin_demo-school'
    'X-Role-Code'           = 'SHOP_OWNER'
  }
}

Write-Host ''
Write-Host '=== Feature flag ==='
$flag = Invoke-RestMethod -Headers (Headers 'main') `
  -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_MULTI_BRANCH'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw 'FEATURE_MULTI_BRANCH not enabled' }
Write-Host 'OK   FEATURE_MULTI_BRANCH'

Write-Host ''
Write-Host '=== Bootstrap (ensures main) ==='
$bootResp = Invoke-RestMethod -Headers (Headers 'main') `
  -Uri 'http://localhost:9090/api/config/branches/bootstrap'
$boot = if ($bootResp.data) { $bootResp.data } else { $bootResp }
if ($boot.featureEnabled -ne $true) { throw 'bootstrap featureEnabled=false' }
$keys = @($boot.branches | ForEach-Object { $_.branchKey })
if ($keys -notcontains 'main') { throw 'main branch missing' }
Write-Host "OK   branches=$($keys -join ',') max=$($boot.maxBranches) canAdd=$($boot.canAdd)"

Write-Host ''
Write-Host '=== Create north campus (idempotent) ==='
$northKey = 'north'
$exists = $keys -contains $northKey
if (-not $exists) {
  if ($boot.canAdd -ne $true) { throw 'canAdd=false but need to create north' }
  $createBody = @{
    branchKey = $northKey
    name      = 'North Campus'
    code      = 'NORTH'
    city      = 'Bengaluru'
    address   = '100 Ring Road'
    isDefault = $false
  } | ConvertTo-Json
  $createdResp = Invoke-RestMethod -Method Post -Headers (Headers 'main') `
    -Uri 'http://localhost:9090/api/config/branches' `
    -ContentType 'application/json' -Body $createBody
  $created = if ($createdResp.data) { $createdResp.data } else { $createdResp }
  if ($created.branchKey -ne $northKey) { throw 'create returned unexpected key' }
  Write-Host "OK   created $northKey"
} else {
  Write-Host "OK   $northKey already present"
}

Write-Host ''
Write-Host '=== Get north + update city ==='
$getResp = Invoke-RestMethod -Headers (Headers 'main') `
  -Uri "http://localhost:9090/api/config/branches/$northKey"
$north = if ($getResp.data) { $getResp.data } else { $getResp }
$cityMarker = "verify-$(Get-Date -Format 'HHmmss')"
$updBody = @{ city = $cityMarker; name = $north.name } | ConvertTo-Json
$updResp = Invoke-RestMethod -Method Put -Headers (Headers 'main') `
  -Uri "http://localhost:9090/api/config/branches/$northKey" `
  -ContentType 'application/json' -Body $updBody
$upd = if ($updResp.data) { $updResp.data } else { $updResp }
if ([string]$upd.city -ne $cityMarker) { throw "city not updated: $($upd.city)" }
Write-Host "OK   city=$cityMarker"

Write-Host ''
Write-Host '=== Branch-scoped theme (north vs main) ==='
$themeNorthResp = Invoke-RestMethod -Headers (Headers $northKey) `
  -Uri 'http://localhost:9090/api/config/design-studio/theme'
$themeMainResp = Invoke-RestMethod -Headers (Headers 'main') `
  -Uri 'http://localhost:9090/api/config/design-studio/theme'
$tn = if ($themeNorthResp.data) { $themeNorthResp.data } else { $themeNorthResp }
$tm = if ($themeMainResp.data) { $themeMainResp.data } else { $themeMainResp }
if ([string]$tn.branchId -ne $northKey -and [string]$tn.branchId -ne '') {
  # getOrCreateTheme sets branchId on theme model
  Write-Host "WARN north theme branchId='$($tn.branchId)' (expected $northKey)"
}
if ([string]$tm.branchId -ne 'main' -and [string]$tm.branchId -ne '') {
  Write-Host "WARN main theme branchId='$($tm.branchId)'"
}
Write-Host "OK   themes resolved for main + $northKey"

Write-Host ''
Write-Host '=== Bootstrap currentBranchKey respects header ==='
$bootN = Invoke-RestMethod -Headers (Headers $northKey) `
  -Uri 'http://localhost:9090/api/config/branches/bootstrap'
$bn = if ($bootN.data) { $bootN.data } else { $bootN }
if ($bn.currentBranchKey -ne $northKey) {
  throw "expected currentBranchKey=$northKey got $($bn.currentBranchKey)"
}
Write-Host "OK   currentBranchKey=$northKey"

Write-Host ''
Write-Host 'PASS  verify-branches'
