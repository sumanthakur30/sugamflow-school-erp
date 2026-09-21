# Phase 22 — Global Directory verification (Student + Staff).
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
Write-Host '=== Feature flags ==='
foreach ($flag in @('FEATURE_STUDENT_MASTER', 'FEATURE_STAFF_MASTER')) {
  $f = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $f.data) { $f.data.enabled } else { $f.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Student directory bootstrap + summary ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/directory/bootstrap'
$bd = if ($boot.data) { $boot.data } else { $boot }
if ($bd.featureEnabled -ne $true) { throw 'student directory featureEnabled=false' }
if (@($bd.columns).Count -lt 5) { throw 'student directory missing columns' }
Write-Host "OK   columns=$(@($bd.columns).Count) filters=$(@($bd.filters).Count)"

$sum = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/directory/summary'
$sd = if ($sum.data) { $sum.data } else { $sum }
Write-Host "OK   total=$($sd.total) active=$($sd.active)"

Write-Host ''
Write-Host '=== Student directory search ==='
$page = Invoke-RestMethod -Headers $headers `
  -Uri 'http://localhost:9090/api/student/directory/students?page=0&size=10&q='
$pd = if ($page.data) { $page.data } else { $page }
if ($null -eq $pd.items) { throw 'student directory page missing items' }
Write-Host "OK   items=$(@($pd.items).Count) totalElements=$($pd.totalElements)"
$sample = @($pd.items) | Select-Object -First 1
if ($sample) {
  if (-not $sample.id) { throw 'directory row missing id' }
  if (-not $sample.admissionNo) { throw 'directory row missing admissionNo' }
  if (-not $sample.fullName -and -not $sample.studentName) { throw 'directory row missing fullName' }
  if (-not $sample.status) { throw 'directory row missing status' }
  Write-Host "OK   sample name=$($sample.fullName) status=$($sample.status)"

  Write-Host ''
  Write-Host '=== Directory action destinations ==='
  $sid = $sample.id
  $p360 = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/student/students/$sid/360"
  $d360 = if ($p360.data) { $p360.data } else { $p360 }
  if (-not $d360.student) { throw '360 missing student' }
  Write-Host 'OK   360'

  $prof = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/student/students/$sid"
  $profData = if ($prof.data) { $prof.data } else { $prof }
  if (-not $profData.id) { throw 'profile missing' }
  Write-Host 'OK   profile'

  $life = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/lifecycle/bootstrap'
  $lifeData = if ($life.data) { $life.data } else { $life }
  if ($null -eq $lifeData) { throw 'lifecycle bootstrap empty' }
  Write-Host 'OK   lifecycle'

  $fee = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/fee/bootstrap'
  $feeData = if ($fee.data) { $fee.data } else { $fee }
  if ($null -eq $feeData) { throw 'fee bootstrap empty' }
  Write-Host 'OK   fee'
}

Write-Host ''
Write-Host '=== Student CSV export ==='
$csvPath = Join-Path $env:TEMP 'student-directory-verify.csv'
Invoke-WebRequest -Headers $headers `
  -Uri 'http://localhost:9090/api/student/directory/export.csv' `
  -OutFile $csvPath -TimeoutSec 30
$csv = Get-Content $csvPath -Raw
if ($csv -notmatch 'admissionNo') { throw 'CSV header missing admissionNo' }
Write-Host 'OK   student CSV'

Write-Host ''
Write-Host '=== Staff directory bootstrap ==='
$sboot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/staff/directory/bootstrap'
$sb = if ($sboot.data) { $sboot.data } else { $sboot }
if ($sb.featureEnabled -ne $true) { throw 'staff directory featureEnabled=false' }
Write-Host "OK   columns=$(@($sb.columns).Count)"

Write-Host ''
Write-Host '=== Create staff + search ==='
$createBody = @{
  answers = @{
    fullName        = 'Verify Teacher'
    mobile          = '9888800001'
    email           = 'verify.teacher@example.com'
    department      = 'Academics'
    designation     = 'Teacher'
    employmentType  = 'Permanent'
    gender          = 'Female'
    joiningDate     = '2026-07-16'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/staff/staff' `
  -ContentType 'application/json' -Body $createBody
$emp = if ($created.data) { $created.data } else { $created }
if (-not $emp.id) { throw 'staff create returned no id' }
if (-not $emp.employeeNo) { throw 'staff create missing employeeNo' }
Write-Host "OK   employeeNo=$($emp.employeeNo)"

$spage = Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/staff/directory/staff?page=0&size=20&q=Verify"
$spd = if ($spage.data) { $spage.data } else { $spage }
$found = @($spd.items) | Where-Object { $_.id -eq $emp.id }
if (-not $found) { throw 'created staff not found in directory search' }
Write-Host 'OK   staff search hit'

$ssum = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/staff/directory/summary'
$ssd = if ($ssum.data) { $ssum.data } else { $ssum }
Write-Host "OK   staff total=$($ssd.total) teachers=$($ssd.teachers)"

Write-Host ''
Write-Host 'PASS  Phase 22 global directory'
