# Live smoke: parent alerts on roster attendance submit (ABSENT/LATE -> guardian SMS/EMAIL).
# Prerequisites: gateway + academic + student + attendance + notification services; demo admin login.
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
if (-not $login.accessToken) { throw 'Login failed - no accessToken' }

$h = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
}

$attendanceModule = Invoke-Json -Method Get -Url "$Gateway/api/config/modules/attendance" -Headers $h
$attendanceSettings = $attendanceModule.data.settings
if (-not $attendanceSettings) { $attendanceSettings = @{} }
$channels = @($attendanceSettings.rosterNotificationChannels)
if ($channels -notcontains 'IN_APP') {
  $attendanceSettings.rosterNotificationChannels = @($channels + 'IN_APP' | Select-Object -Unique)
  Invoke-Json -Method Put -Url "$Gateway/api/config/modules/attendance" -Headers $h -Body @{
    settings = $attendanceSettings
  } | Out-Null
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$label = "Alert-$stamp"

Write-Host "Create class/section ($label)..."
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "Alert $stamp"; code = "AL$stamp".Substring(0,[Math]::Min(12,"AL$stamp".Length)); sequenceNo = 96
}
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "ALA$stamp".Substring(0,[Math]::Min(12,"ALA$stamp".Length)); studentLabel = $label
}
$sectionId = $section.data.id
if (-not $sectionId) { throw 'section create failed' }

Write-Host 'Enroll student...'
$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "Alert Student $stamp"; age = 12
    mobile = "95$stamp".Substring(0,10); email = "alert.$stamp@demo-school.local"
    classApplied = $label; classSection = $label; documentsComplete = $true
    guardianFullName = 'Mrs Alert Parent'; guardianRelation = 'Mother'
    guardianMobile = '9811112222'
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="alert $i" }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo

Write-Host 'Attach guardian with contact details...'
Invoke-Json -Method Put -Url "$Gateway/api/student/students/$studentId/guardians" -Headers $h -Body @{
  guardians = @(
    @{
      fullName = 'Mrs Alert Parent'
      relation = 'Mother'
      mobile = '9811112222'
      email = "alert.parent.$stamp@demo-school.local"
      authUsername = $Username
      isPrimary = $true
    }
  )
} | Out-Null

$date = (Get-Date).ToString('yyyy-MM-dd')

Write-Host 'Bulk mark ABSENT + submit in one call...'
$bulk = Invoke-Json -Method Put -Url "$Gateway/api/attendance/sessions/bulk" -Headers $h -Body @{
  sectionId = $sectionId
  date = $date
  submit = 'SUBMITTED'
  marks = @(
    @{
      studentId = $studentId
      admissionNo = $admissionNo
      studentName = "Alert Student $stamp"
      status = 'ABSENT'
      remark = 'smoke test'
    }
  )
}
$sessionId = $bulk.data.session.id
$alerts = $bulk.data.parentAlerts
if (-not $alerts) { throw 'submit response missing parentAlerts' }
Write-Host "  notified=$($alerts.notified) skipped=$($alerts.skipped)"
$alertRows = @($alerts.alerts)
if ($alertRows.Count -lt 1) { throw 'no alert rows for ABSENT mark' }
$row = $alertRows[0]
if ($row.intent -ne 'ATTENDANCE_ABSENT') { throw "expected intent ATTENDANCE_ABSENT, got $($row.intent)" }
$deliveries = @($row.delivery)
Write-Host "  guardian=$($row.guardianName) channels=$(@($deliveries | ForEach-Object { "$($_.channel):$($_.status)" }) -join ', ')"
$inApp = @($deliveries | Where-Object { $_.channel -eq 'IN_APP' })
if ($inApp.Count -ne 1 -or $inApp[0].status -ne 'SENT') {
  throw 'expected one SENT IN_APP delivery for linked parent account'
}
if ([int]$alerts.notified -lt 1) {
  Write-Warning 'No delivery succeeded — is notification-service (:8087) running? Alert plumbing still exercised.'
} else {
  Write-Host 'Re-submit session (idempotency)...'
  $resub = Invoke-Json -Method Post -Url "$Gateway/api/attendance/sessions/$sessionId/submit" -Headers $h -Body @{}
  $alerts2 = $resub.parentAlerts
  if (-not $alerts2) { $alerts2 = $resub.data.parentAlerts }
  if ([int]$alerts2.notified -ne 0) { throw "resubmit should notify 0, got $($alerts2.notified)" }
  if ([int]$alerts2.skipped -lt 1) { throw 'resubmit should skip the already-notified mark' }
  Write-Host "  resubmit notified=$($alerts2.notified) skipped=$($alerts2.skipped) (idempotent)"
}

Write-Host 'Check durable outbox delivery history...'
$hist = Invoke-Json -Method Get -Url "$Gateway/api/attendance/sessions/$sessionId/alerts" -Headers $h
$histRows = @($hist.data)
if ($histRows.Count -lt 1) { throw 'no outbox rows recorded for session' }
foreach ($r in $histRows) {
  Write-Host "  $($r.channel) -> $($r.recipient): $($r.status) attempts=$($r.attempts) notificationId=$($r.notificationId)"
}
$dups = $histRows | Group-Object { "$($_.markId)|$($_.markStatus)|$($_.channel)|$($_.recipient)" } | Where-Object { $_.Count -gt 1 }
if ($dups) { throw 'duplicate outbox rows for the same mark/channel/recipient' }
$overSent = @($histRows | Where-Object { $_.status -eq 'SENT' -and [int]$_.attempts -gt 1 })
if ($overSent.Count -gt 0) { throw 'resubmit re-dispatched an already SENT outbox row' }

Write-Host 'Check authenticated in-app inbox + read transition...'
$inboxJson = (Invoke-WebRequest -UseBasicParsing -Uri "$Gateway/api/v1/notifications/in-app" -Headers $h).Content
$inbox = $inboxJson | ConvertFrom-Json
$inboxAlert = $null
foreach ($candidate in $inbox) {
  if ([long]$candidate.id -eq [long]$inApp[0].notificationId) {
    $inboxAlert = $candidate
    break
  }
}
if (-not $inboxAlert) { throw 'attendance IN_APP alert missing from authenticated inbox' }
if (-not [string]::IsNullOrWhiteSpace([string]$inboxAlert.readAt)) {
  throw 'new IN_APP alert should be unread'
}
$read = Invoke-Json -Method Post -Url "$Gateway/api/v1/notifications/in-app/$($inboxAlert.id)/read" -Headers $h -Body @{}
if (-not $read.readAt) { throw 'mark-read did not set readAt' }
Write-Host "  inbox notificationId=$($inboxAlert.id) readAt=$($read.readAt)"

Write-Host ''
Write-Host 'OK - attendance parent alert smoke passed.'
Write-Host "  sectionId=$sectionId sessionId=$sessionId admission=$admissionNo"
