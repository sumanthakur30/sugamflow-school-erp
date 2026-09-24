# Phase 24 smoke — Student 360, Import Workbench, Comms Hub,
# deep library/hostel/transport ledgers, LMS module settings.
$ErrorActionPreference = 'Stop'

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

function Unwrap($resp) {
  if ($null -eq $resp) { return $null }
  if ($null -ne $resp.data) { return $resp.data }
  return $resp
}

function Fail([string]$msg) {
  throw $msg
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$admImp = "ADM-P24-$stamp"
$admLib = "ADM-LIB-$stamp"
$admHos = "ADM-HOS-$stamp"
$admTrn = "ADM-TRN-$stamp"

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
if (-not $token) { Fail 'No accessToken' }
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
foreach ($flag in @(
    'FEATURE_STUDENT_MASTER',
    'FEATURE_LIBRARY',
    'FEATURE_HOSTEL',
    'FEATURE_TRANSPORT'
  )) {
  $f = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $f.data) { $f.data.enabled } else { $f.enabled }
  if ($enabled -ne $true) { Fail "$flag not enabled" }
  Write-Host "OK   $flag"
}
foreach ($flag in @('FEATURE_IMPORT_WORKBENCH', 'FEATURE_COMMS_HUB', 'FEATURE_LMS')) {
  try {
    $f = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
    $enabled = if ($null -ne $f.data) { $f.data.enabled } else { $f.enabled }
    if ($enabled -eq $true) {
      Write-Host "OK   $flag"
    } else {
      Write-Host "WARN $flag not enabled yet (seed/restart subscription-service); continuing via fallbacks"
    }
  } catch {
    Write-Host "WARN $flag lookup failed; continuing"
  }
}

# ---------------------------------------------------------------------------
# Import Workbench
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Import Workbench ==='
$boot = Unwrap (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/import/bootstrap')
if ($boot.featureEnabled -ne $true) { Fail 'import bootstrap featureEnabled=false' }
Write-Host "OK   bootstrap fields=$(@($boot.targetFields).Count)"

$csv = @"
fullName,admissionNo,age,mobile,email,classApplied
Phase24 Smoke,$admImp,12,98$($stamp.Substring(6,8)),p24-$stamp@demo.local,Grade 8-A
"@
$jobBody = @{
  fileName   = "phase24-$stamp.csv"
  entityType = 'student_master'
  csvText    = $csv
} | ConvertTo-Json -Depth 5

$job = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/import/jobs' `
  -ContentType 'application/json' -Body $jobBody)
$jobId = $job.id
if (-not $jobId) { Fail 'import createJob returned no id' }
$ready = $job.stats.ready
if ($ready -lt 1) { Fail "import expected ready>=1, got $($job.stats | ConvertTo-Json -Compress)" }
Write-Host "OK   job $jobId status=$($job.status) ready=$ready"

$job = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/student/import/jobs/$jobId/dry-run" `
  -ContentType 'application/json' -Body '{}')
if ($job.status -ne 'VALIDATED') { Fail "dry-run expected VALIDATED, got $($job.status)" }
Write-Host "OK   dry-run VALIDATED"

$job = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/student/import/jobs/$jobId/commit" `
  -ContentType 'application/json' -Body '{}')
if ($job.status -ne 'COMMITTED') { Fail "commit expected COMMITTED, got $($job.status)" }
$committed = $job.stats.committed
if ($committed -lt 1) { Fail "commit expected committed>=1" }
Write-Host "OK   commit COMMITTED committed=$committed"

# Resolve student id for 360 (directory search by admission)
$dir = Unwrap (Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/student/directory/students?page=0&size=20&q=$admImp")
$studentId = $null
foreach ($row in @($dir.items)) {
  if ($row.admissionNo -eq $admImp -or $row.id) {
    if ($row.admissionNo -eq $admImp -or "$($row.admissionNo)" -eq $admImp) {
      $studentId = $row.id
      break
    }
  }
}
if (-not $studentId -and @($dir.items).Count -gt 0) {
  # fallback: first match from q=
  $studentId = $dir.items[0].id
}
if (-not $studentId) {
  # fallback: student master list
  $list = Unwrap (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/students?page=0&size=50')
  $items = if ($list.items) { $list.items } else { $list }
  foreach ($row in @($items)) {
    $adm = $row.admissionNo
    if (-not $adm -and $row.answers) { $adm = $row.answers.admissionNo }
    if ($adm -eq $admImp) { $studentId = $row.id; break }
  }
}
if (-not $studentId) { Fail "could not resolve student id for $admImp after import" }
Write-Host "OK   imported studentId=$studentId admission=$admImp"

# ---------------------------------------------------------------------------
# Student 360
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Student 360 ==='
$p360 = Unwrap (Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/student/students/$studentId/360")
if (-not $p360.student) { Fail '360 missing student' }
if (-not $p360.summary) { Fail '360 missing summary' }
Write-Host "OK   360 profile keys=$([string]::Join(',', @($p360.PSObject.Properties.Name)))"

# ---------------------------------------------------------------------------
# Comms Hub
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Comms Hub ==='
$cboot = Unwrap (Invoke-RestMethod -Headers $headers `
  -Uri 'http://localhost:9090/api/school/notification-config/comms/bootstrap')
Write-Host "OK   comms bootstrap featureEnabled=$($cboot.featureEnabled)"

$annBody = @{
  title    = "Phase24 smoke $stamp"
  body     = "Automated announcement from verify-phase24.ps1"
  channel  = 'EMAIL'
  audience = 'ALL_ACTIVE'
} | ConvertTo-Json
$ann = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/school/notification-config/comms/announcements' `
  -ContentType 'application/json' -Body $annBody)
if (-not $ann.id) { Fail 'comms announcement missing id' }
Write-Host "OK   announcement $($ann.id) status=$($ann.status)"

$alist = Unwrap (Invoke-RestMethod -Headers $headers `
  -Uri 'http://localhost:9090/api/school/notification-config/comms/announcements')
if (@($alist).Count -lt 1) { Fail 'comms list empty after create' }
Write-Host "OK   announcements=$(@($alist).Count)"

# ---------------------------------------------------------------------------
# Deep library circulation
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Library circulation ==='
$bookBody = @{
  title       = "Phase24 Book $stamp"
  author      = 'Smoke Test'
  isbn        = "ISBN-$stamp"
  copiesTotal = 2
} | ConvertTo-Json
$book = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/library/circulation/books' `
  -ContentType 'application/json' -Body $bookBody)
$bookId = $book.id
if (-not $bookId) { Fail 'library book missing id' }
if ([int]$book.copiesAvailable -lt 1) { Fail 'library book copiesAvailable < 1' }
Write-Host "OK   book $bookId avail=$($book.copiesAvailable)"

$issueBody = @{
  bookId      = $bookId
  admissionNo = $admLib
  studentName = 'Lib Smoke'
  loanDays    = 7
} | ConvertTo-Json
$issue = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/library/circulation/issue' `
  -ContentType 'application/json' -Body $issueBody)
$issueId = $issue.id
if (-not $issueId) { Fail 'library issue missing id' }
if ($issue.status -ne 'ISSUED') { Fail "library issue status=$($issue.status)" }
Write-Host "OK   issued $issueId"

$ret = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/library/circulation/issues/$issueId/return" `
  -ContentType 'application/json' -Body '{}')
if ($ret.status -ne 'RETURNED') { Fail "library return status=$($ret.status)" }
Write-Host "OK   returned $issueId fine=$($ret.fineAmount)"

$clr = Unwrap (Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/library/clearance/$admLib")
if ($clr.hasOutstanding -eq $true) { Fail 'library clearance still hasOutstanding after return' }
Write-Host "OK   clearance hasOutstanding=$($clr.hasOutstanding)"

# ---------------------------------------------------------------------------
# Deep hostel beds
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Hostel beds ==='
$bedBody = @{
  blockKey = 'smoke_block'
  roomNo   = "R-$stamp"
  bedNo    = 1
} | ConvertTo-Json
$bed = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/hostel/beds' `
  -ContentType 'application/json' -Body $bedBody)
$bedId = $bed.id
if (-not $bedId) { Fail 'hostel bed missing id' }
if ($bed.status -ne 'VACANT') { Fail "hostel bed status=$($bed.status)" }
Write-Host "OK   bed $bedId"

$allocBody = @{
  bedId       = $bedId
  admissionNo = $admHos
  studentName = 'Hostel Smoke'
} | ConvertTo-Json
$occ = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/hostel/beds/allocate' `
  -ContentType 'application/json' -Body $allocBody)
$occId = $occ.id
if (-not $occId) { Fail 'hostel occupancy missing id' }
if ($occ.status -ne 'ACTIVE') { Fail "hostel occupancy status=$($occ.status)" }
Write-Host "OK   allocated $occId"

$rel = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/hostel/beds/occupancies/$occId/release" `
  -ContentType 'application/json' -Body '{}')
if ($rel.status -ne 'RELEASED') { Fail "hostel release status=$($rel.status)" }
Write-Host "OK   released $occId"

# ---------------------------------------------------------------------------
# Deep transport routes
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== Transport routes ==='
$routeBody = @{
  routeKey  = "smoke_$stamp"
  routeName = "Phase24 Route $stamp"
  vehicleNo = "VH-$stamp"
  capacity  = 20
} | ConvertTo-Json
$route = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/transport/routes' `
  -ContentType 'application/json' -Body $routeBody)
$routeId = $route.id
if (-not $routeId) { Fail 'transport route missing id' }
Write-Host "OK   route $routeId"

$asgBody = @{
  routeId     = $routeId
  admissionNo = $admTrn
  studentName = 'Transport Smoke'
  stopName    = 'Gate A'
  pickupTime  = '07:30'
} | ConvertTo-Json
$asg = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/transport/routes/assign' `
  -ContentType 'application/json' -Body $asgBody)
$asgId = $asg.id
if (-not $asgId) { Fail 'transport assignment missing id' }
if ($asg.status -ne 'ACTIVE') { Fail "transport assignment status=$($asg.status)" }
Write-Host "OK   assigned $asgId"

$ended = Unwrap (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/transport/routes/assignments/$asgId/end" `
  -ContentType 'application/json' -Body '{}')
if ($ended.status -ne 'ENDED') { Fail "transport end status=$($ended.status)" }
Write-Host "OK   ended $asgId"

# ---------------------------------------------------------------------------
# LMS module settings
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== LMS module settings ==='
$lms = Unwrap (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/modules/lms')
$settings = $lms.settings
if (-not $settings) { Fail 'lms settings missing' }
$mode = $settings.mode
$flag = $settings.requiredFeatureFlag
if ($flag -ne 'FEATURE_LMS') { Fail "lms requiredFeatureFlag=$flag" }
if (-not $mode) { Fail 'lms mode missing' }
Write-Host "OK   lms mode=$mode flag=$flag homework=$($settings.homeworkEnabled)"

Write-Host ''
Write-Host '=== Phase 24 smoke PASSED ==='
