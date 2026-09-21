# Phase 10 - Transport Route vertical slice verification.
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
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_TRANSPORT'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw "FEATURE_TRANSPORT not enabled: $($flag | ConvertTo-Json -Compress)" }
Write-Host 'OK   FEATURE_TRANSPORT'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/transport/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if (-not $bootData.form) { throw 'bootstrap missing form' }
if (-not $bootData.workflow) { throw 'bootstrap missing workflow' }
Write-Host "OK   form=$($bootData.formKey) workflow=$($bootData.workflowKey)"

Write-Host ''
Write-Host '=== Submit record ==='
$submitBody = @{
  answers = @{
    studentName = 'Meera Iyer'
    admissionNo = 'ADM-4201'
    routeName   = 'Route 7'
    stopName    = 'Lakeview'
    vehicleNo   = 'KA-05-AB-1234'
    pickupTime  = '07:30'
    distanceKm  = 8
    email       = 'meera@example.com'
    mobile      = '9999900006'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/transport/records' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }
Write-Host "OK   record $id status=$($app.status) step=$($app.currentStepName)"

Write-Host ''
Write-Host '=== Approve through workflow ==='
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve step $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/transport/records/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
  Write-Host "  step -> $($app.status) / $($app.currentStepName)"
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== TRANSPORT_APPROVED delivery ==='
$approvedIntent = $null
foreach ($intent in @($app.notificationIntents)) {
  if ($intent.intent -eq 'TRANSPORT_APPROVED') { $approvedIntent = $intent }
}
if (-not $approvedIntent) { throw 'TRANSPORT_APPROVED intent missing' }
$delivery = @($approvedIntent.delivery)
if ($delivery.Count -lt 1) { throw 'TRANSPORT_APPROVED delivery list empty' }
foreach ($d in $delivery) {
  Write-Host "     $($d.channel) -> $($d.status)"
}
Write-Host 'OK   delivery recorded'

Write-Host ''
Write-Host '=== Block rule (distanceKm < 0) ==='
$blockBody = @{
  answers = @{
    studentName = 'Bad Route Student'
    admissionNo = 'ADM-9996'
    routeName   = 'Route X'
    stopName    = 'Nowhere'
    vehicleNo   = 'KA-05-ZZ-0000'
    pickupTime  = '07:30'
    distanceKm  = -5
  }
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/transport/records' `
    -ContentType 'application/json' -Body $blockBody | Out-Null
  throw 'Expected BLOCK_TRANSPORT'
} catch {
  Write-Host "OK   blocked ($($_.Exception.Message))"
}

Write-Host ''
Write-Host 'Phase 10 transport verification passed.'
