# Smoke: Student 360 ops cards (library loans / hostel bed / transport route)
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
$label = "Ops360-$stamp"

Write-Host 'Enroll student...'
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "Ops360 $stamp"; code = "O3$stamp".Substring(0,[Math]::Min(12,"O3$stamp".Length)); sequenceNo = 97
}
Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "O3A$stamp".Substring(0,[Math]::Min(12,"O3A$stamp".Length)); studentLabel = $label
} | Out-Null

$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "Ops360 Student $stamp"; age = 12
    mobile = "96$stamp".Substring(0,10); email = "ops360.$stamp@demo-school.local"
    classApplied = $label; classSection = $label; documentsComplete = $true
    guardianFullName = 'Ops Parent'; guardianRelation = 'Father'; guardianMobile = '9811112222'
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="ops $i" }
  $app = $upd.data
}
$studentId = $app.enrolledStudentId
$admissionNo = $app.enrolledAdmissionNo
if (-not $studentId) { throw 'missing enrolled student' }

Write-Host 'Seed library / hostel / transport...'
$book = Invoke-Json -Method Post -Url "$Gateway/api/library/circulation/books" -Headers $h -Body @{
  title = "Ops Book $stamp"; author = 'Smoke'; copiesTotal = 2
}
Invoke-Json -Method Post -Url "$Gateway/api/library/circulation/issue" -Headers $h -Body @{
  bookId = $book.data.id; admissionNo = $admissionNo; studentId = $studentId; studentName = "Ops360 Student $stamp"
} | Out-Null

$bed = Invoke-Json -Method Post -Url "$Gateway/api/hostel/beds" -Headers $h -Body @{
  blockKey = 'A'; roomNo = "R$stamp".Substring(0,8); bedNo = 1
}
Invoke-Json -Method Post -Url "$Gateway/api/hostel/beds/allocate" -Headers $h -Body @{
  bedId = $bed.data.id; admissionNo = $admissionNo; studentId = $studentId; studentName = "Ops360 Student $stamp"
} | Out-Null

$route = Invoke-Json -Method Post -Url "$Gateway/api/transport/routes" -Headers $h -Body @{
  routeKey = "rk$stamp".Substring(0,12); routeName = "Route $stamp"; vehicleNo = 'BUS-1'; capacity = 40
}
Invoke-Json -Method Post -Url "$Gateway/api/transport/routes/assign" -Headers $h -Body @{
  routeId = $route.data.id; admissionNo = $admissionNo; studentId = $studentId
  studentName = "Ops360 Student $stamp"; stopName = 'Gate 1'; pickupTime = '07:30'
} | Out-Null

Write-Host 'Fetch Student 360...'
$profile = Invoke-Json -Method Get -Url "$Gateway/api/student/students/$studentId/360" -Headers $h
$ops = $profile.data.ops
if (-not $ops) { throw 'ops block missing from Student 360' }
if ([int]$ops.openLoanCount -lt 1) { throw "expected open loans, got $($ops.openLoanCount)" }
if (-not $ops.bed.id) { throw 'expected active bed' }
if (-not $ops.route.routeName) { throw 'expected active route' }

Write-Host ''
Write-Host 'OK - Student 360 ops cards smoke passed.'
Write-Host "  admission=$admissionNo loans=$($ops.openLoanCount) bed=$($ops.bed.label) route=$($ops.route.routeName)"
