# Seed demo Student + Staff directory data (class-wise / department-wise).
# Requires: gateway :9090, student-service, staff-service, admission-service, Postgres.
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

$headers = @{
  Authorization           = "Bearer $token"
  'X-Tenant-Id'           = 'demo-school'
  'X-Branch-Id'           = 'main'
  'X-Academic-Session-Id' = '2025-26'
  'X-Shop-Id'             = 'demo-school'
  'X-User-Id'             = 'admin_demo-school'
  'X-Role-Code'           = 'SHOP_OWNER'
}

function Approve-Admission([hashtable]$Answers) {
  $body = @{ answers = $Answers } | ConvertTo-Json -Depth 6
  $created = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri 'http://localhost:9090/api/admission/applications' `
    -ContentType 'application/json' -Body $body
  $app = if ($created.data) { $created.data } else { $created }
  for ($i = 1; $i -le 8; $i++) {
    if ($app.status -eq 'APPROVED') { break }
    $act = @{ action = 'APPROVE'; comment = "Seed approve $i" } | ConvertTo-Json
    $updated = Invoke-RestMethod -Method Post -Headers $headers `
      -Uri "http://localhost:9090/api/admission/applications/$($app.id)/actions" `
      -ContentType 'application/json' -Body $act
    $app = if ($updated.data) { $updated.data } else { $updated }
  }
  if ($app.status -ne 'APPROVED') { throw "Admission not approved for $($Answers.fullName): $($app.status)" }
  return $app
}

function Resolve-Psql {
  $preferred = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
  if (Test-Path $preferred) { return $preferred }
  $found = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -EA SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
  if ($found) { return $found.FullName }
  throw 'psql not found'
}

# --- Students (class-wise) ---
Write-Host ''
Write-Host '=== Seed students (class-wise) ==='
$classes = @(
  'Grade 6-A', 'Grade 6-B',
  'Grade 7-A', 'Grade 7-B',
  'Grade 8-A', 'Grade 8-B',
  'Grade 9-A', 'Grade 9-B'
)
$boyNames = @(
  'Aarav Mehta', 'Vivaan Shah', 'Aditya Rao', 'Kabir Nair',
  'Ishaan Gupta', 'Reyansh Iyer', 'Arjun Desai', 'Shaurya Pillai'
)
$girlNames = @(
  'Ananya Reddy', 'Diya Kapoor', 'Myra Joshi', 'Kiara Bhat',
  'Saanvi Menon', 'Aisha Khan', 'Pari Verma', 'Navya Jain'
)
$houses = @('Red', 'Blue', 'Green', 'Yellow')
$categories = @('General', 'OBC', 'SC', 'ST')

$seededStudents = @()
$mobileBase = 9800001000
$idx = 0
foreach ($cls in $classes) {
  for ($n = 0; $n -lt 3; $n++) {
    $isBoy = (($idx + $n) % 2) -eq 0
    $name = if ($isBoy) { $boyNames[($idx + $n) % $boyNames.Count] } else { $girlNames[($idx + $n) % $girlNames.Count] }
    # Make unique names per class slot
    $uniqueName = "$name ($cls-$($n+1))"
    $mobile = [string]($mobileBase + $idx)
    $age = 11
    if ($cls -match 'Grade (\d+)') { $age = 5 + [int]$Matches[1] }

    $answers = @{
      fullName          = $uniqueName
      age               = $age
      mobile            = $mobile
      email             = ("seed.student{0}@demo-school.local" -f $idx)
      classApplied      = $cls
      documentsComplete = $true
      guardianFullName  = if ($isBoy) { "Mr. Parent $idx" } else { "Mrs. Parent $idx" }
      guardianRelation  = if ($isBoy) { 'Father' } else { 'Mother' }
      guardianMobile    = [string]($mobileBase + 5000 + $idx)
    }

    $app = Approve-Admission $answers
    $seededStudents += [pscustomobject]@{
      StudentId   = $app.enrolledStudentId
      AdmissionNo = $app.enrolledAdmissionNo
      Class       = $cls
      Name        = $uniqueName
      Gender      = if ($isBoy) { 'Male' } else { 'Female' }
      House       = $houses[$idx % $houses.Count]
      Category    = $categories[$idx % $categories.Count]
      Transport   = ($idx % 3 -eq 0)
      Hostel      = ($idx % 5 -eq 0)
      Scholarship = ($idx % 7 -eq 0)
      RollNo      = '{0}{1:D2}' -f ($cls -replace '[^0-9A-Za-z]', ''), ($n + 1)
      ParentName  = $answers.guardianFullName
      Idx         = $idx
    }
    Write-Host "  OK  $($app.enrolledAdmissionNo)  $uniqueName"
    $idx++
  }
}

# Enrich answers for directory filters (gender, classSection, house, etc.)
Write-Host ''
Write-Host '=== Enrich student directory fields (Postgres) ==='
$psql = Resolve-Psql
$env:PGPASSWORD = 'school_student'
$sqlLines = @()
foreach ($s in $seededStudents) {
  if (-not $s.StudentId) { continue }
  $transport = if ($s.Transport) { 'true' } else { 'false' }
  $hostel = if ($s.Hostel) { 'true' } else { 'false' }
  $scholarship = if ($s.Scholarship) { 'true' } else { 'false' }
  $patch = @{
    gender       = $s.Gender
    classSection = $s.Class
    house        = $s.House
    category     = $s.Category
    transport    = $s.Transport
    hostel       = $s.Hostel
    scholarship  = $s.Scholarship
    rollNo       = $s.RollNo
    parentName   = $s.ParentName
  } | ConvertTo-Json -Compress
  $patchSql = $patch.Replace("'", "''")
  $sqlLines += @"
UPDATE student_record
SET answers = answers || '$patchSql'::jsonb,
    updated_at = NOW()
WHERE id = '$($s.StudentId)'::uuid
  AND organization_id = 'demo-school';
"@
}
$tmpSql = Join-Path $env:TEMP 'seed-directory-students.sql'
Set-Content -Path $tmpSql -Value ($sqlLines -join "`n") -Encoding ascii
& $psql -h localhost -p 5432 -U school_student -d school_student_db -v ON_ERROR_STOP=1 -f $tmpSql | Out-Null
Write-Host "OK   enriched $($seededStudents.Count) student records"

# --- Staff ---
Write-Host ''
Write-Host '=== Seed staff directory ==='
$staffRoster = @(
  @{ fullName = 'Anita Sharma'; department = 'Academics'; designation = 'Principal'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100001' },
  @{ fullName = 'Ramesh Kulkarni'; department = 'Academics'; designation = 'Vice Principal'; employmentType = 'Permanent'; gender = 'Male'; mobile = '9811100002' },
  @{ fullName = 'Priya Nair'; department = 'Academics'; designation = 'Teacher'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100003' },
  @{ fullName = 'Suresh Patil'; department = 'Academics'; designation = 'Teacher'; employmentType = 'Permanent'; gender = 'Male'; mobile = '9811100004' },
  @{ fullName = 'Meera Iyer'; department = 'Academics'; designation = 'Teacher'; employmentType = 'Contract'; gender = 'Female'; mobile = '9811100005' },
  @{ fullName = 'Kavita Deshmukh'; department = 'Administration'; designation = 'Reception'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100006' },
  @{ fullName = 'Vikram Singh'; department = 'HR'; designation = 'HR'; employmentType = 'Permanent'; gender = 'Male'; mobile = '9811100007' },
  @{ fullName = 'Sunita Rao'; department = 'Accounts'; designation = 'Accountant'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100008' },
  @{ fullName = 'Farhan Ali'; department = 'Library'; designation = 'Librarian'; employmentType = 'Permanent'; gender = 'Male'; mobile = '9811100009' },
  @{ fullName = 'Geeta More'; department = 'Hostel'; designation = 'Hostel Staff'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100010' },
  @{ fullName = 'Imran Sheikh'; department = 'Transport'; designation = 'Transport Staff'; employmentType = 'Contract'; gender = 'Male'; mobile = '9811100011' },
  @{ fullName = 'Balu Yadav'; department = 'Transport'; designation = 'Driver'; employmentType = 'Contract'; gender = 'Male'; mobile = '9811100012' },
  @{ fullName = 'Joseph D''Souza'; department = 'Security'; designation = 'Security'; employmentType = 'Contract'; gender = 'Male'; mobile = '9811100013' },
  @{ fullName = 'Nisha Agarwal'; department = 'Management'; designation = 'Management'; employmentType = 'Permanent'; gender = 'Female'; mobile = '9811100014' }
)

$staffCreated = 0
foreach ($row in $staffRoster) {
  $answers = @{
    fullName       = $row.fullName
    mobile         = $row.mobile
    email          = ($row.fullName.ToLower() -replace "[^a-z0-9]", '.') + '@demo-school.local'
    department     = $row.department
    designation    = $row.designation
    employmentType = $row.employmentType
    gender         = $row.gender
    joiningDate    = '2024-06-01'
    status         = 'ACTIVE'
  }
  $body = @{ answers = $answers } | ConvertTo-Json -Depth 5
  try {
    $created = Invoke-RestMethod -Method Post -Headers $headers `
      -Uri 'http://localhost:9090/api/staff/staff' `
      -ContentType 'application/json' -Body $body
    $emp = if ($created.data) { $created.data } else { $created }
    Write-Host "  OK  $($emp.employeeNo)  $($row.fullName) · $($row.designation)"
    $staffCreated++
  } catch {
    Write-Host "  SKIP $($row.fullName): $($_.Exception.Message)"
  }
}

# --- Summary ---
Write-Host ''
Write-Host '=== Directory summaries ==='
$sSumResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/directory/summary'
$sSum = if ($sSumResp.data) { $sSumResp.data } else { $sSumResp }
$stSumResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/staff/directory/summary'
$stSum = if ($stSumResp.data) { $stSumResp.data } else { $stSumResp }

Write-Host "Students total=$($sSum.total) active=$($sSum.active) boys=$($sSum.boys) girls=$($sSum.girls)"
Write-Host 'Class-wise:'
($sSum.byClass.PSObject.Properties | Sort-Object Name) | ForEach-Object {
  Write-Host ("  {0} = {1}" -f $_.Name, $_.Value)
}
Write-Host "Staff total=$($stSum.total) teachers=$($stSum.teachers) nonTeaching=$($stSum.nonTeaching)"
Write-Host ''
Write-Host "PASS  seeded students=$($seededStudents.Count) staff=$staffCreated"
Write-Host 'UI: /admin/student-directory  ·  /admin/staff-directory'
