# Live smoke: term report cards (section grid, single student JSON + PDF, published gating).
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
$classGrade = ("7" + $stamp.Substring($stamp.Length - 3)).Substring(0, 4)
$label = "Grade $classGrade-A"
$term = 'TERM1'

Write-Host "Create class/section + two subjects ($label)..."
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "ReportCard $stamp"; code = "RC$stamp".Substring(0,[Math]::Min(12,"RC$stamp".Length)); sequenceNo = 97
}
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "RCA$stamp".Substring(0,[Math]::Min(12,"RCA$stamp".Length)); studentLabel = $label
}
$sectionId = $section.data.id
$math = Invoke-Json -Method Post -Url "$Gateway/api/academic/subjects" -Headers $h -Body @{
  name = "Math $stamp"; code = "M$stamp".Substring(0,[Math]::Min(12,"M$stamp".Length)); subjectType = 'CORE'
}
$sci = Invoke-Json -Method Post -Url "$Gateway/api/academic/subjects" -Headers $h -Body @{
  name = "Science $stamp"; code = "S$stamp".Substring(0,[Math]::Min(12,"S$stamp".Length)); subjectType = 'CORE'
}
if (-not $sectionId -or -not $math.data.id -or -not $sci.data.id) { throw 'academic setup failed' }

Write-Host 'Enroll student...'
$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "ReportCard Student $stamp"; age = 13; dob = '2013-06-01'
    mobile = "96$stamp".Substring(0,10); email = "rc.$stamp@demo-school.local"
    classApplied = $label; classSection = $label; classGrade = $classGrade; sectionLetter = 'A'; documentsComplete = $true
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="rc $i" }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo

function New-PublishedExam($subjectId, $name, $max, $marks) {
  $def = Invoke-Json -Method Post -Url "$Gateway/api/exam/definitions" -Headers $h -Body @{
    sectionId = $sectionId; subjectId = $subjectId; name = $name; termKey = $term; maxMarks = $max
  }
  $examId = $def.data.id
  Invoke-Json -Method Put -Url "$Gateway/api/exam/gradebook/bulk" -Headers $h -Body @{
    examDefinitionId = $examId
    marks = @(@{ studentId = $studentId; admissionNo = $admissionNo; studentName = "ReportCard Student $stamp"; marksObtained = $marks })
  } | Out-Null
  return $examId
}

Write-Host 'Create + mark two subjects...'
$mathExam = New-PublishedExam $math.data.id 'Unit Test' 100 82
$sciExam = New-PublishedExam $sci.data.id 'Unit Test' 50 40

Write-Host 'Report card BEFORE publish (publishedOnly=true should be empty grid)...'
$before = Invoke-Json -Method Get -Url "$Gateway/api/exam/report-cards?sectionId=$sectionId&termKey=$term&publishedOnly=true" -Headers $h
$beforeSubjects = @($before.data.subjects).Count
if ($beforeSubjects -ne 0) { throw "Expected 0 published subjects before publish, got $beforeSubjects" }

Write-Host 'Publish both exams...'
Invoke-Json -Method Post -Url "$Gateway/api/exam/definitions/$mathExam/publish" -Headers $h -Body @{} | Out-Null
Invoke-Json -Method Post -Url "$Gateway/api/exam/definitions/$sciExam/publish" -Headers $h -Body @{} | Out-Null

Write-Host 'Section report card grid (published)...'
$grid = Invoke-Json -Method Get -Url "$Gateway/api/exam/report-cards?sectionId=$sectionId&termKey=$term&publishedOnly=true" -Headers $h
if (@($grid.data.subjects).Count -ne 2) { throw "Expected 2 subjects, got $(@($grid.data.subjects).Count)" }
$student = @($grid.data.students) | Where-Object { $_.admissionNo -eq $admissionNo } | Select-Object -First 1
if (-not $student) { throw 'student missing from report grid' }
# 122 / 150 = 81.33% -> A
if ($student.overallGrade -ne 'A') { throw "Expected grade A, got $($student.overallGrade)" }
if ($student.result -ne 'PASS') { throw "Expected PASS, got $($student.result)" }
Write-Host "  total=$($student.totalObtained)/$($student.totalMax) pct=$($student.percentage) grade=$($student.overallGrade)"

Write-Host 'Single student report card JSON...'
$one = Invoke-Json -Method Get -Url "$Gateway/api/exam/report-cards/student?sectionId=$sectionId&termKey=$term&studentId=$studentId&publishedOnly=true" -Headers $h
if (-not $one.data.student) { throw 'single student card missing student' }

Write-Host 'Single student report card PDF...'
$pdfHeaders = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
}
$pdfPath = Join-Path $env:TEMP "report-card-$stamp.pdf"
Invoke-WebRequest -Uri "$Gateway/api/exam/report-cards/student.pdf?sectionId=$sectionId&termKey=$term&studentId=$studentId&publishedOnly=true" -Headers $pdfHeaders -OutFile $pdfPath -UseBasicParsing
$bytes = [System.IO.File]::ReadAllBytes($pdfPath)
if ($bytes.Length -lt 500) { throw "PDF too small ($($bytes.Length) bytes)" }
$magic = [System.Text.Encoding]::ASCII.GetString($bytes[0..3])
if ($magic -ne '%PDF') { throw "Not a PDF (magic=$magic)" }
Write-Host "  PDF ok ($($bytes.Length) bytes) -> $pdfPath"

Write-Host ''
Write-Host 'OK - report card smoke passed.'
Write-Host "  sectionId=$sectionId term=$term admission=$admissionNo"
