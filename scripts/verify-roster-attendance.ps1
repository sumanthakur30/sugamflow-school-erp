# Live smoke: roster attendance (section roll + bulk mark + submit).
# Prerequisites: gateway + academic + student + attendance services; demo admin login.
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

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$label = "Roster-$stamp"

Write-Host "Create academic class/section ($label)..."
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "Roster $stamp"
  code = "R$stamp".Substring(0, [Math]::Min(12, "R$stamp".Length))
  sequenceNo = 99
}
$classId = $class.data.id
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $classId
  name = 'A'
  code = "RA$stamp".Substring(0, [Math]::Min(12, "RA$stamp".Length))
  studentLabel = $label
}
$sectionId = $section.data.id
if (-not $sectionId) { throw 'Section create failed' }

Write-Host "Enroll student into section label ($label)..."
$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "Roster Student $stamp"
    age = 12
    mobile = "98$stamp".Substring(0, 10)
    email = "roster.$stamp@demo-school.local"
    # Must match section.studentLabel so roster + teacher RBAC resolve the student.
    classApplied = $label
    classSection = $label
    documentsComplete = $true
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{
    action = 'APPROVE'
    comment = "roster smoke $i"
  }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo
if (-not $studentId) { throw 'enrolledStudentId missing' }
Write-Host "  student=$studentId admission=$admissionNo"

$date = (Get-Date).ToString('yyyy-MM-dd')
Write-Host "Load roster for $date..."
$roster = Invoke-Json -Method Get -Url "$Gateway/api/attendance/roster?sectionId=$sectionId&date=$date" -Headers $h
$students = @($roster.data.students)
if ($students.Count -lt 1) { throw 'Roster returned 0 students (label mismatch?)' }
Write-Host "  rosterCount=$($students.Count) sectionLabel=$($roster.data.sectionLabel)"

Write-Host 'Bulk mark PRESENT...'
$bulk = Invoke-Json -Method Put -Url "$Gateway/api/attendance/sessions/bulk" -Headers $h -Body @{
  sectionId = $sectionId
  date = $date
  marks = @(
    @{
      studentId = $studentId
      admissionNo = $admissionNo
      studentName = "Roster Student $stamp"
      status = 'PRESENT'
    }
  )
}
$sessionId = $bulk.data.session.id
if (-not $sessionId) { throw 'bulk mark missing session id' }
if ([int]$bulk.data.markCount -lt 1) { throw 'markCount expected >= 1' }

Write-Host 'Submit session...'
$sub = Invoke-Json -Method Post -Url "$Gateway/api/attendance/sessions/$sessionId/submit" -Headers $h -Body @{}
if ($sub.data.status -ne 'SUBMITTED') { throw "Expected SUBMITTED, got $($sub.data.status)" }

Write-Host 'Reload roster...'
$roster2 = Invoke-Json -Method Get -Url "$Gateway/api/attendance/roster?sectionId=$sectionId&date=$date" -Headers $h
$hit = @($roster2.data.students) | Where-Object { $_.admissionNo -eq $admissionNo } | Select-Object -First 1
if ($hit.markStatus -ne 'PRESENT') { throw "Expected PRESENT, got $($hit.markStatus)" }

Write-Host ''
Write-Host 'OK - roster attendance smoke passed.'
Write-Host "  sectionId=$sectionId sessionId=$sessionId"
