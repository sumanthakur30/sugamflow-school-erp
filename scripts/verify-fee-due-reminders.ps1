# Live smoke: fee due reminders with durable outbox + IN_APP inbox.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'

function Invoke-Json {
  param([string]$Method, [string]$Url, [hashtable]$Headers, $Body)
  $params = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = 'application/json'
  }
  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  return Invoke-RestMethod @params
}

Write-Host "Logging in as $Username @ $ShopId..."
$login = Invoke-Json -Method Post -Url "$Gateway/api/v1/auth/login" -Headers @{} -Body @{
  username = $Username
  password = $Password
  shopId = $ShopId
}
if (-not $login.accessToken) { throw 'Login failed' }

$h = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId
  'X-Shop-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$label = "FeeDue-$stamp"

Write-Host 'Create section + enroll + link guardian...'
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "FeeDue $stamp"; code = "FD$stamp".Substring(0,[Math]::Min(12,"FD$stamp".Length)); sequenceNo = 98
}
Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "FDA$stamp".Substring(0,[Math]::Min(12,"FDA$stamp".Length)); studentLabel = $label
} | Out-Null

$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "FeeDue Student $stamp"; age = 12
    mobile = "97$stamp".Substring(0,10); email = "feedue.$stamp@demo-school.local"
    classApplied = $label; classSection = $label; documentsComplete = $true
    guardianFullName = 'Mrs Fee Parent'; guardianRelation = 'Mother'
    guardianMobile = '9833334444'
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="feedue $i" }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo

Invoke-Json -Method Put -Url "$Gateway/api/student/students/$studentId/guardians" -Headers $h -Body @{
  guardians = @(
    @{
      fullName = 'Mrs Fee Parent'
      relation = 'Mother'
      mobile = '9833334444'
      email = "feedue.parent.$stamp@demo-school.local"
      authUsername = $Username
      isPrimary = $true
    }
  )
} | Out-Null

Write-Host 'Create open fee collection with pendingDays...'
$fee = Invoke-Json -Method Post -Url "$Gateway/api/fee/collections" -Headers $h -Body @{
  answers = @{
    admissionNo = $admissionNo
    studentName = "FeeDue Student $stamp"
    amount = 1500
    feeHead = "TUITION-$stamp"
    pendingDays = 5
    dueDate = (Get-Date).ToString('yyyy-MM-dd')
    paymentMode = 'UPI'
    email = "feedue.$stamp@demo-school.local"
    mobile = "97$stamp".Substring(0,10)
  }
}
$collectionId = $fee.data.id
if (-not $collectionId) { throw 'fee collection create failed' }

Write-Host 'Run due reminders...'
$run1 = Invoke-Json -Method Post -Url "$Gateway/api/fee/due-reminders/run" -Headers $h -Body @{
  admissionNo = $admissionNo
}
$d1 = $run1.data
if (-not $d1) { $d1 = $run1 }
Write-Host "  reminded=$($d1.reminded) sent=$($d1.sent) skipped=$($d1.skipped)"
if ([int]$d1.reminded -lt 1 -or [int]$d1.sent -lt 1) { throw 'expected at least one reminder delivery' }

Write-Host 'Re-run (idempotent for same period)...'
$run2 = Invoke-Json -Method Post -Url "$Gateway/api/fee/due-reminders/run" -Headers $h -Body @{
  admissionNo = $admissionNo
}
$d2 = $run2.data
if (-not $d2) { $d2 = $run2 }
$history = Invoke-Json -Method Get -Url "$Gateway/api/fee/due-reminders/history?admissionNo=$admissionNo" -Headers $h
$rows = @($history.data)
$sentRows = @($rows | Where-Object { $_.status -eq 'SENT' })
$over = @($sentRows | Where-Object { [int]$_.attempts -gt 1 })
if ($over.Count -gt 0) { throw 'resubmit re-dispatched SENT fee reminder rows' }
Write-Host "  history SENT rows=$($sentRows.Count)"

Write-Host 'Check in-app inbox...'
$inboxJson = (Invoke-WebRequest -UseBasicParsing -Uri "$Gateway/api/v1/notifications/in-app" -Headers $h).Content
$inbox = $inboxJson | ConvertFrom-Json
$hit = $inbox | Where-Object { $_.subject -like "Fee due reminder*$admissionNo*" } | Select-Object -First 1
if (-not $hit) { throw 'fee due reminder missing from inbox' }

Write-Host ''
Write-Host 'OK - fee due reminder smoke passed.'
Write-Host "  admission=$admissionNo collectionId=$collectionId inboxId=$($hit.id)"
