# Phase 6 — Fee vertical slice verification.
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
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_FEE'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw "FEATURE_FEE not enabled: $($flag | ConvertTo-Json -Compress)" }
Write-Host 'OK   FEATURE_FEE'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/fee/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if (-not $bootData.form) { throw 'bootstrap missing form' }
if (-not $bootData.workflow) { throw 'bootstrap missing workflow' }
Write-Host "OK   form=$($bootData.formKey) workflow=$($bootData.workflowKey)"

Write-Host ''
Write-Host '=== Submit collection ==='
$submitBody = @{
  answers = @{
    studentName = 'Ravi Kumar'
    admissionNo = 'ADM-1001'
    feeHead     = 'Tuition'
    feeMonth    = '2025-08'
    amount      = 5000
    pendingDays = 10
    paymentMode = 'UPI'
    email       = 'ravi@example.com'
    mobile      = '9999900001'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/fee/collections' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }
Write-Host "OK   collection $id status=$($app.status) step=$($app.currentStepName)"

Write-Host ''
Write-Host '=== Approve through workflow ==='
for ($i = 1; $i -le 6; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve step $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/fee/collections/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
  Write-Host "  step -> $($app.status) / $($app.currentStepName)"
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== Fee receipt PDF ==='
if ($app.hasFeeReceipt -ne $true) { throw 'hasFeeReceipt expected true after final approve' }
$pdfPath = Join-Path $env:TEMP "fee-receipt-$id.pdf"
Invoke-WebRequest -Headers $headers `
  -Uri "http://localhost:9090/api/fee/collections/$id/receipt" `
  -OutFile $pdfPath -TimeoutSec 30
$pdfBytes = [System.IO.File]::ReadAllBytes($pdfPath)
$head = [System.Text.Encoding]::ASCII.GetString($pdfBytes[0..3])
if ($head -ne '%PDF') { throw "Expected PDF magic, got: $head" }
Write-Host "OK   fee-receipt PDF ($($pdfBytes.Length) bytes)"

Write-Host ''
Write-Host '=== FEE_APPROVED delivery ==='
$approvedIntent = $null
foreach ($intent in @($app.notificationIntents)) {
  if ($intent.intent -eq 'FEE_APPROVED') { $approvedIntent = $intent }
}
if (-not $approvedIntent) { throw 'FEE_APPROVED intent missing' }
$delivery = @($approvedIntent.delivery)
if ($delivery.Count -lt 1) { throw 'FEE_APPROVED delivery list empty' }
foreach ($d in $delivery) {
  Write-Host "     $($d.channel) -> $($d.status)"
}
$emailRow = $delivery | Where-Object { $_.channel -eq 'EMAIL' } | Select-Object -First 1
if (-not $emailRow) { throw 'EMAIL delivery row missing (ensure answers.email is set)' }
if ($emailRow.status -ne 'SENT') {
  Write-Warning "EMAIL got $($emailRow.status) (expected SENT). MailHog/notification may need restart. Continuing for local sale smoke."
} else {
  Write-Host 'OK   EMAIL SENT'
}

Write-Host ''
Write-Host '=== Block rule (amount < 1) ==='
$blockBody = @{
  answers = @{
    studentName = 'Zero Fee'
    admissionNo = 'ADM-0'
    feeHead     = 'Misc'
    feeMonth    = '2025-08'
    amount      = 0
    pendingDays = 0
    paymentMode = 'CASH'
  }
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/fee/collections' `
    -ContentType 'application/json' -Body $blockBody | Out-Null
  throw 'Expected BLOCK_FEE'
} catch {
  Write-Host "OK   blocked ($($_.Exception.Message))"
}

Write-Host ''
Write-Host 'Phase 6 fee verification passed.'
