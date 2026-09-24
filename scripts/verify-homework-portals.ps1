# Smoke: teacher publish + parent mine/submit homework
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ExamBase = 'http://127.0.0.1:8293',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'

function Invoke-Json {
  param([string]$Method, [string]$Url, [hashtable]$Headers, $Body)
  $params = @{ Method = $Method; Uri = $Url; Headers = $Headers; ContentType = 'application/json' }
  if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
  return Invoke-RestMethod @params
}

$login = Invoke-Json -Method Post -Url "$Gateway/api/v1/auth/login" -Headers @{} -Body @{
  username = $Username; password = $Password; shopId = $ShopId
}
$h = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId; 'X-Shop-Id' = $ShopId
  'X-Branch-Id' = 'main'; 'X-Academic-Session-Id' = 'smoke-tests'
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$label = "HwPortal-$stamp"
$parentUser = "hwparent_$stamp"

Write-Host 'Enroll student + link guardian identity...'
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "HwPortal $stamp"; code = "HP$stamp".Substring(0,[Math]::Min(12,"HP$stamp".Length)); sequenceNo = 96
}
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "HPA$stamp".Substring(0,[Math]::Min(12,"HPA$stamp".Length)); studentLabel = $label
}
$sectionId = $section.data.id

$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "HwPortal Student $stamp"; age = 11
    mobile = "94$stamp".Substring(0,10); email = "hwportal.$stamp@demo-school.local"
    classApplied = $label; classSection = $label; documentsComplete = $true
    guardianFullName = 'Hw Parent'; guardianRelation = 'Mother'; guardianMobile = '9822223333'
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="hw $i" }
  $app = $upd.data
}
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo
if (-not $studentId) { throw 'missing enrolled student' }

Invoke-Json -Method Put -Url "$Gateway/api/student/students/$studentId/guardians" -Headers $h -Body @{
  guardians = @(
    @{
      fullName = 'Hw Parent'
      relation = 'Mother'
      mobile = '9822223333'
      email = "hwparent.$stamp@demo-school.local"
      authUsername = $parentUser
      isPrimary = $true
    }
  )
} | Out-Null

Write-Host 'Teacher/staff publish homework...'
$hw = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework" -Headers $h -Body @{
  title = "Portal essay $stamp"
  description = 'Write three sentences about kindness'
  subjectKey = 'english'
  sectionId = $sectionId
  classSection = $label
  dueDate = (Get-Date).AddDays(2).ToString('yyyy-MM-dd')
  status = 'PUBLISHED'
}
$hwId = $hw.data.id
if (-not $hwId) { throw 'homework create failed' }

Write-Host 'Parent mine via relationship-scoped headers...'
$parentHeaders = @{
  'X-Tenant-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
  'X-Auth-User' = $parentUser
  'X-Auth-Role' = 'PARENT'
  'X-Internal-Service' = 'true'
}
$mine = Invoke-Json -Method Get -Url "$ExamBase/api/exam/homework/mine" -Headers $parentHeaders
$rows = @($mine.data)
$hit = $rows | Where-Object { $_.id -eq $hwId -and $_.admissionNo -eq $admissionNo } | Select-Object -First 1
if (-not $hit) { throw "parent mine missing homework $hwId for $admissionNo (got $($rows.Count) rows)" }

Write-Host 'Parent submit...'
$sub = Invoke-Json -Method Post -Url "$ExamBase/api/exam/homework/$hwId/submissions/mine" -Headers $parentHeaders -Body @{
  studentId = $studentId
  admissionNo = $admissionNo
  body = 'Kindness means helping others every day.'
}
if (-not $sub.data.id) { throw 'parent submit failed' }

Write-Host 'Staff list submissions + grade...'
$subs = Invoke-Json -Method Get -Url "$Gateway/api/exam/homework/$hwId/submissions" -Headers $h
$found = @($subs.data) | Where-Object { $_.id -eq $sub.data.id } | Select-Object -First 1
if (-not $found) { throw 'staff cannot see parent submission' }
$graded = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework/submissions/$($sub.data.id)/grade" -Headers $h -Body @{
  marks = 8; feedback = 'Nice start'
}
if ($graded.data.status -ne 'GRADED') { throw 'grade failed' }

$mine2 = Invoke-Json -Method Get -Url "$ExamBase/api/exam/homework/mine" -Headers $parentHeaders
$hit2 = @($mine2.data) | Where-Object { $_.id -eq $hwId } | Select-Object -First 1
if ($hit2.submissionStatus -ne 'GRADED') { throw "expected GRADED got $($hit2.submissionStatus)" }

Write-Host ''
Write-Host 'OK - teacher/parent homework portal smoke passed.'
Write-Host "  homeworkId=$hwId admission=$admissionNo parentUser=$parentUser"
