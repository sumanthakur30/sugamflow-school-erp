# Live smoke: teacher gradebook (definition + marks + publish).
# Prerequisites: gateway + academic + student + exam services; demo admin login.
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
$label = "Gradebook-$stamp"

Write-Host "Create class/section/subject ($label)..."
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "Gradebook $stamp"
  code = "G$stamp".Substring(0, [Math]::Min(12, "G$stamp".Length))
  sequenceNo = 98
}
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id
  name = 'A'
  code = "GA$stamp".Substring(0, [Math]::Min(12, "GA$stamp".Length))
  studentLabel = $label
}
$sectionId = $section.data.id
$subject = Invoke-Json -Method Post -Url "$Gateway/api/academic/subjects" -Headers $h -Body @{
  name = "Math $stamp"
  code = "M$stamp".Substring(0, [Math]::Min(12, "M$stamp".Length))
  subjectType = 'CORE'
}
$subjectId = $subject.data.id
if (-not $sectionId -or -not $subjectId) { throw 'section/subject create failed' }

Write-Host 'Enroll student...'
$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "Gradebook Student $stamp"
    age = 13
    mobile = "97$stamp".Substring(0, 10)
    email = "gradebook.$stamp@demo-school.local"
    # Must match section.studentLabel for roster/gradebook scoping.
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
    comment = "gradebook smoke $i"
  }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo

Write-Host 'Create exam definition...'
$def = Invoke-Json -Method Post -Url "$Gateway/api/exam/definitions" -Headers $h -Body @{
  sectionId = $sectionId
  subjectId = $subjectId
  name = "UT $stamp"
  termKey = 'TERM1'
  maxMarks = 50
}
$examId = $def.data.id
if (-not $examId) { throw 'exam definition create failed' }

Write-Host 'Load gradebook...'
$gb = Invoke-Json -Method Get -Url "$Gateway/api/exam/gradebook?examDefinitionId=$examId" -Headers $h
if (@($gb.data.students).Count -lt 1) { throw 'Gradebook roster empty' }

Write-Host 'Bulk save marks...'
$saved = Invoke-Json -Method Put -Url "$Gateway/api/exam/gradebook/bulk" -Headers $h -Body @{
  examDefinitionId = $examId
  marks = @(
    @{
      studentId = $studentId
      admissionNo = $admissionNo
      studentName = "Gradebook Student $stamp"
      marksObtained = 44
      grade = 'A'
    }
  )
}
$row = @($saved.data.students) | Where-Object { $_.admissionNo -eq $admissionNo } | Select-Object -First 1
if ([decimal]$row.marksObtained -ne 44) { throw "Expected marks 44, got $($row.marksObtained)" }

Write-Host 'Publish...'
$pub = Invoke-Json -Method Post -Url "$Gateway/api/exam/definitions/$examId/publish" -Headers $h -Body @{}
if ($pub.data.status -ne 'PUBLISHED') { throw "Expected PUBLISHED, got $($pub.data.status)" }

Write-Host 'Published marks list...'
$marks = Invoke-Json -Method Get -Url "$Gateway/api/exam/marks/published" -Headers $h
$hit = @($marks.data) | Where-Object { $_.examDefinitionId -eq $examId -and $_.admissionNo -eq $admissionNo } | Select-Object -First 1
if (-not $hit) { throw 'Published mark not found for admin listing' }

Write-Host ''
Write-Host 'OK - gradebook smoke passed.'
Write-Host "  examDefinitionId=$examId sectionId=$sectionId"
