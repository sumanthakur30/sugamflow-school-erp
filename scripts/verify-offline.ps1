# Phase 16 - Offline mode verification.
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
Write-Host '=== Feature flag ==='
$flagResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_OFFLINE_MODE'
$enabled = if ($null -ne $flagResp.data) { $flagResp.data.enabled } else { $flagResp.enabled }
if ($enabled -ne $true) { throw 'FEATURE_OFFLINE_MODE not enabled' }
Write-Host 'OK   FEATURE_OFFLINE_MODE'

Write-Host ''
Write-Host '=== Offline bootstrap ==='
$bootResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/offline/bootstrap'
$boot = if ($bootResp.data) { $bootResp.data } else { $bootResp }
if ($boot.featureEnabled -ne $true) { throw 'featureEnabled=false' }
$types = @($boot.allowedEntityTypes)
if ($types.Count -lt 1) { throw 'no allowedEntityTypes' }
$att = $types | Where-Object { $_.entityType -eq 'ATTENDANCE_MARK' } | Select-Object -First 1
if (-not $att) { throw 'ATTENDANCE_MARK not allow-listed' }
Write-Host "OK   types=$($types.Count) maxQueue=$($boot.maxQueueSize) batch=$($boot.syncBatchSize)"

Write-Host ''
Write-Host '=== Manifest ==='
$manResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/offline/manifest'
$man = if ($manResp.data) { $manResp.data } else { $manResp }
$keys = @($man.cacheKeys)
if ($keys.Count -lt 1) { throw 'manifest cacheKeys empty' }
Write-Host "OK   cacheKeys=$($keys.Count)"

Write-Host ''
Write-Host '=== Sync ATTENDANCE_MARK sample ==='
$itemId = [guid]::NewGuid().ToString()
$clientId = [guid]::NewGuid().ToString()
$stamp = (Get-Date).ToString('yyyy-MM-dd')
$syncBody = @{
  clientId = $clientId
  items    = @(
    @{
      id         = $itemId
      entityType = 'ATTENDANCE_MARK'
      method     = 'POST'
      path       = '/api/attendance/records'
      createdAt  = (Get-Date).ToUniversalTime().ToString('o')
      body       = @{
        answers = @{
          classSection      = 'Grade 8-A'
          attendanceDate    = $stamp
          studentName       = 'Offline Verify'
          admissionNo       = 'ADM-OFF-VERIFY'
          status            = 'PRESENT'
          attendancePercent = 95
          email             = 'offline-verify@example.com'
          mobile            = '9999900088'
        }
      }
    }
  )
} | ConvertTo-Json -Depth 8

$syncResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/config/offline/sync' `
  -ContentType 'application/json' -Body $syncBody
$sync = if ($syncResp.data) { $syncResp.data } else { $syncResp }
if (-not $sync.batchId) { throw 'batchId missing' }
$first = @($sync.items) | Select-Object -First 1
if ($first.status -ne 'SYNCED') {
  throw "expected SYNCED, got $($first.status) error=$($first.error) payload=$($sync | ConvertTo-Json -Compress -Depth 6)"
}
Write-Host "OK   batch=$($sync.batchId) status=$($sync.status) success=$($sync.successCount)"

Write-Host ''
Write-Host '=== Batches list ==='
$batResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/offline/batches'
$batches = if ($batResp.data) { $batResp.data } else { $batResp }
$found = @($batches) | Where-Object { $_.id -eq $sync.batchId } | Select-Object -First 1
if (-not $found) { throw "batch $($sync.batchId) not in list" }
Write-Host "OK   batches=$(@($batches).Count) latest=$($found.status)"

Write-Host ''
Write-Host '=== Reject unknown entity ==='
$badBody = @{
  clientId = $clientId
  items    = @(
    @{
      id         = [guid]::NewGuid().ToString()
      entityType = 'NOT_ALLOWED'
      method     = 'POST'
      path       = '/api/hack'
      body       = @{ x = 1 }
    }
  )
} | ConvertTo-Json -Depth 5
$badResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/config/offline/sync' `
  -ContentType 'application/json' -Body $badBody
$bad = if ($badResp.data) { $badResp.data } else { $badResp }
$badItem = @($bad.items) | Select-Object -First 1
if ($badItem.status -ne 'REJECTED') { throw "expected REJECTED, got $($badItem.status)" }
Write-Host 'OK   unknown entity REJECTED'

Write-Host ''
Write-Host 'PASS  Phase 16 offline mode'
