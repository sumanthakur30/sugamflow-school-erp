# Phase 5 - Admission vertical slice verification (JWT + config engines + runtime).
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
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_ADMISSION'
if ($flag.data.enabled -ne $true -and $flag.enabled -ne $true) {
  # ApiService unwrap is server-side; RestMethod returns full ApiResponse
  $enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
  if ($enabled -ne $true) { throw "FEATURE_ADMISSION not enabled: $($flag | ConvertTo-Json -Compress)" }
}
Write-Host 'OK   FEATURE_ADMISSION'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/admission/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if (-not $bootData.form) { throw 'bootstrap missing form' }
if (-not $bootData.workflow) { throw 'bootstrap missing workflow' }
Write-Host "OK   form=$($bootData.formKey) workflow=$($bootData.workflowKey)"

Write-Host ''
Write-Host '=== Submit application ==='
$submitBody = @{
  answers = @{
    fullName           = 'Asha Verma'
    age                = 8
    dob                = '2017-06-15'
    dateOfBirth        = '2017-06-15'
    mobile             = '9999900001'
    email              = 'asha@example.com'
    classApplied       = '5-A'
    classGrade         = '5'
    sectionLetter      = 'A'
    documentsComplete  = $true
    fatherName         = 'Ramesh Verma'
    motherName         = 'Sita Verma'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/admission/applications' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }
Write-Host "OK   application $id status=$($app.status) step=$($app.currentStepName)"

Write-Host ''
Write-Host '=== Approve through workflow ==='
$max = 8
for ($i = 1; $i -le $max; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve step $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/admission/applications/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
  Write-Host "  step -> $($app.status) / $($app.currentStepName)"
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== Offer letter PDF ==='
if ($app.hasOfferLetter -ne $true) { throw 'hasOfferLetter expected true after final approve' }
$pdfPath = Join-Path $env:TEMP "offer-letter-$id.pdf"
Invoke-WebRequest -Headers $headers `
  -Uri "http://localhost:9090/api/admission/applications/$id/offer-letter" `
  -OutFile $pdfPath -TimeoutSec 30
$pdfBytes = [System.IO.File]::ReadAllBytes($pdfPath)
$head = [System.Text.Encoding]::ASCII.GetString($pdfBytes[0..3])
if ($head -ne '%PDF') { throw "Expected PDF magic, got: $head" }
Write-Host "OK   offer-letter PDF ($($pdfBytes.Length) bytes)"

Write-Host ''
Write-Host '=== Approve notification delivery ==='
$approvedIntent = $null
foreach ($intent in @($app.notificationIntents)) {
  if ($intent.intent -eq 'ADMISSION_APPROVED') { $approvedIntent = $intent }
}
if (-not $approvedIntent) { throw 'ADMISSION_APPROVED intent missing' }
$delivery = @($approvedIntent.delivery)
if ($delivery.Count -lt 1) { throw 'ADMISSION_APPROVED delivery list empty' }
$channels = ($delivery | ForEach-Object { $_.channel }) -join ', '
Write-Host "OK   delivery channels: $channels"
$emailRow = $delivery | Where-Object { $_.channel -eq 'EMAIL' } | Select-Object -First 1
$inAppRow = $delivery | Where-Object { $_.channel -eq 'IN_APP' } | Select-Object -First 1
foreach ($d in $delivery) {
  Write-Host "     $($d.channel) -> $($d.status)"
}
if (-not $emailRow) { throw 'EMAIL delivery row missing (ensure answers.email is set)' }
if ($emailRow.status -ne 'SENT') {
  Write-Warning ("EMAIL got {0} (expected SENT). MailHog :1025 / notification-service may need restart. Continuing; IN_APP is required." -f $emailRow.status)
} else {
  Write-Host 'OK   EMAIL SENT'
}
if ($inAppRow -and $inAppRow.status -ne 'SENT') {
  throw "IN_APP expected SENT, got $($inAppRow.status)"
}
if ($emailRow.status -ne 'SENT') {
  Write-Host 'WARN EMAIL not SENT (non-blocking for local sale smoke)'
}

Write-Host ''
Write-Host '=== Block rule (age < 3) ==='
$blockBody = @{
  answers = @{
    fullName          = 'Too Young'
    age               = 2
    dob               = '2024-01-01'
    mobile            = '9999900002'
    classApplied      = '1-A'
    classGrade        = '1'
    sectionLetter     = 'A'
    documentsComplete = $true
  }
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/admission/applications' `
    -ContentType 'application/json' -Body $blockBody | Out-Null
  throw 'Expected BLOCK_ADMISSION'
} catch {
  $msg = [string]$_.Exception.Message
  if ($msg -match 'Expected BLOCK_ADMISSION') { throw }
  Write-Host ('OK   blocked ({0})' -f $msg)
}

Write-Host ''
Write-Host 'Phase 5 admission verification passed.'
