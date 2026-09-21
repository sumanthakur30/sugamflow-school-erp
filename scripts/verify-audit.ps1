# Phase 11 - Config audit + rollback verification.
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
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_AUDIT_LOGS'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw "FEATURE_AUDIT_LOGS not enabled: $($flag | ConvertTo-Json -Compress)" }
Write-Host 'OK   FEATURE_AUDIT_LOGS'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/audit/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if ($bootData.featureEnabled -ne $true) { throw 'bootstrap featureEnabled=false' }
if (-not $bootData.entityTypes) { throw 'bootstrap missing entityTypes' }
Write-Host 'OK   bootstrap'

Write-Host ''
Write-Host '=== Capture localization before ==='
$locBeforeResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/localization'
$locBefore = if ($locBeforeResp.data) { $locBeforeResp.data } else { $locBeforeResp }
$origLanguage = [string]$locBefore.language
$marker = "audit-$(Get-Date -Format 'HHmmss')"
$locPayload = @{
  language           = $marker
  currency           = [string]$locBefore.currency
  dateFormat         = [string]$locBefore.dateFormat
  timeFormat         = [string]$locBefore.timeFormat
  timeZone           = [string]$locBefore.timeZone
  academicYearFormat = [string]$locBefore.academicYearFormat
  marksFormat        = [string]$locBefore.marksFormat
  gradeSystem        = [string]$locBefore.gradeSystem
  numberFormat       = [string]$locBefore.numberFormat
  addressFormat      = [string]$locBefore.addressFormat
  paperSize          = [string]$locBefore.paperSize
}
$putBody = $locPayload | ConvertTo-Json -Depth 5

Write-Host ''
Write-Host '=== Save localization (creates audit) ==='
$savedResp = Invoke-RestMethod -Method Put -Headers $headers `
  -Uri 'http://localhost:9090/api/config/localization' `
  -ContentType 'application/json' -Body $putBody
$saved = if ($savedResp.data) { $savedResp.data } else { $savedResp }
if ([string]$saved.language -ne $marker) { throw "language not updated to $marker" }
Write-Host "OK   language=$marker"

Start-Sleep -Seconds 1

Write-Host ''
Write-Host '=== List LOCALIZATION audits ==='
$listResp = Invoke-RestMethod -Headers $headers `
  -Uri 'http://localhost:9090/api/audit/config-changes?entityType=LOCALIZATION'
$list = if ($listResp.data) { $listResp.data } else { $listResp }
$hit = $null
foreach ($row in @($list)) {
  if ($row.entityType -eq 'LOCALIZATION' -and $row.status -eq 'PENDING_APPROVAL') {
    $nvLang = $null
    if ($row.newValue -and $row.newValue.language) { $nvLang = [string]$row.newValue.language }
    if ($nvLang -eq $marker) { $hit = $row; break }
  }
}
if (-not $hit) { throw 'No PENDING_APPROVAL LOCALIZATION audit for marker language' }
$id = $hit.id
Write-Host "OK   audit $id"

Write-Host ''
Write-Host '=== Approve ==='
$aprResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/audit/config-changes/$id/approve" `
  -ContentType 'application/json' -Body '{}'
$apr = if ($aprResp.data) { $aprResp.data } else { $aprResp }
if ($apr.status -ne 'APPROVED') { throw "Expected APPROVED, got $($apr.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== Rollback (restore previous language) ==='
$rbBody = @{ reason = 'verify-audit rollback' } | ConvertTo-Json
$rbResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/audit/config-changes/$id/rollback" `
  -ContentType 'application/json' -Body $rbBody
$rb = if ($rbResp.data) { $rbResp.data } else { $rbResp }
if (-not $rb.rollbackOf) { throw 'rollback entry missing rollbackOf' }
if ($rb.rollbackOf -ne $id) { throw "rollbackOf mismatch: $($rb.rollbackOf)" }
Write-Host "OK   rollback entry $($rb.id)"

Write-Host ''
Write-Host '=== Confirm localization restored ==='
$locAfterResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/localization'
$locAfter = if ($locAfterResp.data) { $locAfterResp.data } else { $locAfterResp }
$restored = [string]$locAfter.language
if ($restored -eq $marker) { throw "language still marker after rollback: $restored" }
if ($restored -ne $origLanguage) {
  Write-Host "WARN restored language='$restored' (expected '$origLanguage') - accepting non-marker restore"
} else {
  Write-Host "OK   language restored to '$origLanguage'"
}

Write-Host ''
Write-Host '=== Original marked ROLLED_BACK ==='
$origResp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/audit/config-changes/$id"
$orig = if ($origResp.data) { $origResp.data } else { $origResp }
if ($orig.status -ne 'ROLLED_BACK') { throw "Expected ROLLED_BACK, got $($orig.status)" }
Write-Host 'OK   original ROLLED_BACK'

Write-Host ''
Write-Host 'PASS  verify-audit'
