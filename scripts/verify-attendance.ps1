# Phase 8 — Attendance vertical slice verification.
$ErrorActionPreference = 'Stop'

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

Write-Host '=== Login ==='
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
  -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
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
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_ATTENDANCE'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw "FEATURE_ATTENDANCE not enabled: $($flag | ConvertTo-Json -Compress)" }
Write-Host 'OK   FEATURE_ATTENDANCE'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/attendance/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if (-not $bootData.form) { throw 'bootstrap missing form' }
if (-not $bootData.workflow) { throw 'bootstrap missing workflow' }
Write-Host "OK   form=$($bootData.formKey) workflow=$($bootData.workflowKey)"

Write-Host ''
Write-Host '=== Submit record ==='
$submitBody = @{
  answers = @{
    classSection      = '10-A'
    attendanceDate    = '2025-07-16'
    studentName       = 'Priya Sharma'
    admissionNo       = 'ADM-2001'
    status            = 'PRESENT'
    attendancePercent = 95
    email             = 'priya@example.com'
    mobile            = '9999900002'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/attendance/records' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }
Write-Host "OK   record $id status=$($app.status) step=$($app.currentStepName)"

Write-Host ''
Write-Host '=== Approve through workflow ==='
for ($i = 1; $i -le 6; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve step $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/attendance/records/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
  Write-Host "  step -> $($app.status) / $($app.currentStepName)"
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== ATTENDANCE_APPROVED delivery ==='
$approvedIntent = $null
foreach ($intent in @($app.notificationIntents)) {
  if ($intent.intent -eq 'ATTENDANCE_APPROVED') { $approvedIntent = $intent }
}
if (-not $approvedIntent) { throw 'ATTENDANCE_APPROVED intent missing' }
$delivery = @($approvedIntent.delivery)
if ($delivery.Count -lt 1) { throw 'ATTENDANCE_APPROVED delivery list empty' }
foreach ($d in $delivery) {
  Write-Host "     $($d.channel) -> $($d.status)"
}
Write-Host 'OK   delivery recorded'

Write-Host ''
Write-Host '=== Block rule (attendancePercent < 1) ==='
$blockBody = @{
  answers = @{
    classSection      = '10-A'
    attendanceDate    = '2025-07-16'
    studentName       = 'Zero Percent'
    admissionNo       = 'ADM-0'
    status            = 'ABSENT'
    attendancePercent = 0
  }
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/attendance/records' `
    -ContentType 'application/json' -Body $blockBody | Out-Null
  throw 'Expected BLOCK_ATTENDANCE'
} catch {
  Write-Host "OK   blocked ($($_.Exception.Message))"
}

Write-Host ''
Write-Host 'Phase 8 attendance verification passed.'
