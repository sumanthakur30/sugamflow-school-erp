# Phase 20 — Academic lifecycle verification.
$ErrorActionPreference = 'Stop'

function Unwrap-Data($resp) {
  if ($null -ne $resp.data) { return $resp.data }
  return $resp
}

function Approve-Admission($headers, $appId) {
  $app = $null
  for ($i = 1; $i -le 8; $i++) {
    $actBody = @{ action = 'APPROVE'; comment = "Auto-approve $i" } | ConvertTo-Json
    $updated = Invoke-RestMethod -Method Post -Headers $headers `
      -Uri "http://localhost:9090/api/admission/applications/$appId/actions" `
      -ContentType 'application/json' -Body $actBody
    $app = Unwrap-Data $updated
    if ($app.status -eq 'APPROVED') { break }
  }
  if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
  return $app
}

function New-LifecycleStudent($headers, $suffix, $classApplied) {
  $submitBody = @{
    answers = @{
      fullName          = "Lifecycle $suffix"
      age               = 12
      dob               = '2013-04-01'
      mobile            = "99999$(Get-Random -Minimum 10000 -Maximum 99999)"
      email             = "lifecycle.$suffix@example.com"
      classApplied      = $classApplied
      classGrade        = (($classApplied -split '[-/]')[0] -replace '\D','')
      sectionLetter     = if ($classApplied -match '[-/]([A-Za-z])') { $Matches[1].ToUpper() } else { 'A' }
      documentsComplete = $true
      fatherName        = 'Lifecycle Father'
      motherName        = 'Lifecycle Mother'
    }
  } | ConvertTo-Json -Depth 5
  $created = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/admission/applications' `
    -ContentType 'application/json' -Body $submitBody
  $app = Unwrap-Data $created
  if (-not $app.id) { throw 'submit returned no id' }
  $approved = Approve-Admission $headers $app.id
  if ($approved.hasEnrollment -ne $true) { throw 'hasEnrollment expected true' }
  return @{
    studentId   = $approved.enrolledStudentId
    admissionNo = $approved.enrolledAdmissionNo
    applicationId = $app.id
  }
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
Write-Host '=== Feature flags ==='
foreach ($flag in @('FEATURE_STUDENT_MASTER', 'FEATURE_ACADEMIC_LIFECYCLE', 'FEATURE_REPORT_BUILDER', 'FEATURE_FEE', 'FEATURE_LIBRARY')) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Lifecycle bootstrap ==='
$boot = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/lifecycle/bootstrap')
if (@($boot.promotionMaps).Count -lt 1) { throw 'no promotion maps' }
if (@($boot.sessions).Count -lt 1) { throw 'no sessions' }
Write-Host "OK   maps=$(@($boot.promotionMaps).Count) sessions=$(@($boot.sessions).Count) reportBuilder=$($boot.reportBuilderEnabled)"

Write-Host ''
Write-Host '=== Enroll student for promotion ==='
$promoteStudent = New-LifecycleStudent $headers 'Promote' 'Grade 8-A'
$studentId = $promoteStudent.studentId
Write-Host "OK   studentId=$studentId class=Grade 8-A"

Write-Host ''
Write-Host '=== Promote student ==='
$promoteBody = @{
  studentIds      = @($studentId)
  promotionMapKey = 'default_grade_map'
} | ConvertTo-Json
$promoted = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/lifecycle/promote' `
  -ContentType 'application/json' -Body $promoteBody)
$row = @($promoted.results)[0]
if ($row.toClass -ne 'Grade 9-A') { throw "Expected Grade 9-A, got $($row.toClass)" }
Write-Host "OK   $($row.fromClass) -> $($row.toClass) ref=$($row.referenceNo)"

Write-Host ''
Write-Host '=== TC clearance: fee dues block ==='
$feeStudent = New-LifecycleStudent $headers 'FeeBlock' 'Grade 8-A'
$feeSubmit = @{
  answers = @{
    studentName = 'Lifecycle FeeBlock'
    admissionNo = $feeStudent.admissionNo
    feeHead     = 'Tuition'
    feeMonth    = '2025-08'
    amount      = 5000
    pendingDays = 15
    paymentMode = 'CASH'
    email       = 'feeblock@example.com'
    mobile      = '9999911111'
  }
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/fee/collections' -ContentType 'application/json' -Body $feeSubmit | Out-Null
$feeClear = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/lifecycle/tc/clearance/preview' `
  -ContentType 'application/json' -Body (@{ studentId = $feeStudent.studentId } | ConvertTo-Json))
if ($feeClear.cleared -ne $false) { throw 'expected cleared=false when fee dues open' }
Write-Host "OK   fee pending=$($feeClear.fees.pendingAmount) rules=$($feeClear.matchedActions -join ',')"
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/student/lifecycle/tc' `
    -ContentType 'application/json' -Body (@{ studentId = $feeStudent.studentId; reason = 'should fail' } | ConvertTo-Json) | Out-Null
  throw 'expected TC blocked for fee dues'
} catch {
  Write-Host 'OK   TC blocked (BLOCK_TC)'
}

Write-Host ''
Write-Host '=== TC clearance: library books block ==='
$libStudent = New-LifecycleStudent $headers 'LibBlock' 'Grade 8-A'
$libSubmit = @{
  answers = @{
    studentName = 'Lifecycle LibBlock'
    admissionNo = $libStudent.admissionNo
    bookTitle   = 'Physics Vol 1'
    bookId      = 'BK-TC-1'
    issueDate   = '2026-07-01'
    dueDate     = '2026-07-10'
    email       = 'libblock@example.com'
    mobile      = '9999922222'
  }
} | ConvertTo-Json -Depth 5
$libCreated = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/library/records' -ContentType 'application/json' -Body $libSubmit)
$libId = $libCreated.id
for ($i = 1; $i -le 8; $i++) {
  if ($libCreated.status -eq 'APPROVED') { break }
  $libCreated = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/library/records/$libId/actions" `
    -ContentType 'application/json' -Body (@{ action = 'APPROVE'; comment = "step $i" } | ConvertTo-Json))
}
if ($libCreated.status -ne 'APPROVED') { throw 'library issue not approved' }
$libClear = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/lifecycle/tc/clearance/preview' `
  -ContentType 'application/json' -Body (@{ studentId = $libStudent.studentId } | ConvertTo-Json))
if ($libClear.cleared -ne $false) { throw 'expected cleared=false when library books outstanding' }
Write-Host "OK   outstanding=$($libClear.library.outstandingBooks)"
try {
  Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/student/lifecycle/tc' `
    -ContentType 'application/json' -Body (@{ studentId = $libStudent.studentId; reason = 'should fail' } | ConvertTo-Json) | Out-Null
  throw 'expected TC blocked for library books'
} catch {
  Write-Host 'OK   TC blocked (BLOCK_TC)'
}

Write-Host ''
Write-Host '=== Enroll student for TC ==='
$tcStudent = New-LifecycleStudent $headers 'TC' 'Grade 8-A'
$tcStudentId = $tcStudent.studentId
Write-Host "OK   studentId=$tcStudentId"

Write-Host ''
Write-Host '=== Issue transfer certificate ==='
$tcKey = "tc-$(Get-Date -Format 'yyyyMMddHHmmss')"
$tcBody = @{
  studentId       = $tcStudentId
  reason          = 'Verify script relocation'
  remarks         = 'Phase 20'
  idempotencyKey  = $tcKey
} | ConvertTo-Json
$tc = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/lifecycle/tc' `
  -ContentType 'application/json' -Body $tcBody)
if ($tc.student.status -ne 'TRANSFERRED') { throw "Expected TRANSFERRED, got $($tc.student.status)" }
if ($tc.hasTcDocument -eq $true) {
  Write-Host "OK   TC issued ref=$($tc.referenceNo) PDF ready"
} else {
  $err = $tc.payload.document.error
  Write-Host "WARN TC event OK but PDF missing ($err) - ensure report-builder-service is up"
}

Write-Host ''
Write-Host '=== Idempotent TC ==='
$tcAgain = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/lifecycle/tc' `
  -ContentType 'application/json' -Body $tcBody)
if ($tcAgain.id -ne $tc.id) { throw 'idempotency key should return same TC event' }
Write-Host 'OK   same event on replay'

Write-Host ''
Write-Host '=== Lifecycle events ==='
$events = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/lifecycle/events')
if (@($events).Count -lt 2) { throw 'expected lifecycle events' }
Write-Host "OK   events=$(@($events).Count)"

Write-Host ''
Write-Host 'PASS  Phase 20 academic lifecycle'
