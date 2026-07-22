# Smoke: native LMS homework create + submit + grade
param(
  [string]$Gateway = 'http://localhost:9090',
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
Write-Host 'Create homework...'
$hw = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework" -Headers $h -Body @{
  title = "Essay $stamp"
  description = 'Write one paragraph on school values'
  subjectKey = 'english'
  classSection = 'Grade 6-A'
  dueDate = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
  status = 'PUBLISHED'
}
$hwId = $hw.data.id
if (-not $hwId) { throw 'homework create failed' }

Write-Host 'Submit...'
$sub = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework/$hwId/submissions" -Headers $h -Body @{
  admissionNo = "ADM-HW-$stamp"
  studentName = "Homework Student $stamp"
  body = 'Respect, kindness, and hard work.'
}
$subId = $sub.data.id
if (-not $subId) { throw 'submit failed' }

Write-Host 'Grade...'
$graded = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework/submissions/$subId/grade" -Headers $h -Body @{
  marks = 9; feedback = 'Clear and concise'
}
if ($graded.data.status -ne 'GRADED') { throw "expected GRADED got $($graded.data.status)" }

$detail = Invoke-Json -Method Get -Url "$Gateway/api/exam/homework/$hwId" -Headers $h
$subs = @($detail.data.submissions)
if ($subs.Count -lt 1) { throw 'submission missing from homework detail' }

Write-Host ''
Write-Host 'OK - LMS homework smoke passed.'
Write-Host "  homeworkId=$hwId submissionId=$subId marks=$($graded.data.gradedMarks)"
