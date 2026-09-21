# Seeds TEACHER/PARENT auth accounts and links one student+homework per school for portal load.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$authSql = Join-Path $root 'infra\postgres\05-seed-portal-personas-auth.sql'
$outFile = Join-Path $PSScriptRoot 'load\portal-fixture.json'

function Resolve-Psql {
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $found = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
  if ($found) { return $found.FullName }
  return $null
}

function Invoke-Json {
  param([string]$Method, [string]$Url, [hashtable]$Headers, $Body)
  $params = @{ Method = $Method; Uri = $Url; Headers = $Headers; ContentType = 'application/json' }
  if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
  return Invoke-RestMethod @params
}

$psql = Resolve-Psql
if (-not $psql) { throw 'psql not found' }
$env:PGPASSWORD = $AdminPassword

Write-Host 'Seeding teacher/parent auth accounts...'
& $psql -h $HostName -p $Port -U $AdminUser -d authdb -v ON_ERROR_STOP=1 -f $authSql
if ($LASTEXITCODE -ne 0) { throw 'portal persona auth seed failed' }

# Ensure load schools + demo exist
& (Join-Path $PSScriptRoot 'seed-multi-school-load.ps1') -HostName $HostName -Port $Port -AdminUser $AdminUser -AdminPassword $AdminPassword

$schools = @(
  @{ shopId = 'demo-school'; admin = 'admin_demo-school'; teacher = 'teacher_demo-school'; parent = 'parent_demo-school'; tenantId = 100 },
  @{ shopId = 'load-school-01'; admin = 'admin_load-school-01'; teacher = 'teacher_load-school-01'; parent = 'parent_load-school-01'; tenantId = 201 },
  @{ shopId = 'load-school-02'; admin = 'admin_load-school-02'; teacher = 'teacher_load-school-02'; parent = 'parent_load-school-02'; tenantId = 202 },
  @{ shopId = 'load-school-03'; admin = 'admin_load-school-03'; teacher = 'teacher_load-school-03'; parent = 'parent_load-school-03'; tenantId = 203 },
  @{ shopId = 'load-school-04'; admin = 'admin_load-school-04'; teacher = 'teacher_load-school-04'; parent = 'parent_load-school-04'; tenantId = 204 },
  @{ shopId = 'load-school-05'; admin = 'admin_load-school-05'; teacher = 'teacher_load-school-05'; parent = 'parent_load-school-05'; tenantId = 205 }
)

$fixtures = @()
$stamp = Get-Date -Format 'yyyyMMddHHmmss'

foreach ($s in $schools) {
  Write-Host "Provisioning portal fixture for $($s.shopId)..."
  $login = Invoke-Json -Method Post -Url "$Gateway/api/v1/auth/login" -Headers @{} -Body @{
    shopId = $s.shopId; username = $s.admin; password = 'password'
  }
  if (-not $login.accessToken) { throw "admin login failed for $($s.shopId)" }
  $h = @{
    Authorization = "Bearer $($login.accessToken)"
    'X-Tenant-Id' = $s.shopId
    'X-Shop-Id' = $s.shopId
    'X-Branch-Id' = 'main'
    'X-Academic-Session-Id' = 'load-portal'
  }

  Invoke-Json -Method Post -Url "$Gateway/api/config/provision" -Headers $h -Body @{
    schoolName = "Portal $($s.shopId)"
  } | Out-Null

  $class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
    name = "Portal $stamp $($s.shopId)"
    code = ("PC{0}" -f $stamp).Substring(0, [Math]::Min(12, ("PC{0}" -f $stamp).Length))
    sequenceNo = 80
  }
  $section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
    classId = $class.data.id
    name = 'A'
    code = ("PSA{0}" -f $stamp).Substring(0, [Math]::Min(12, ("PSA{0}" -f $stamp).Length))
    studentLabel = "Portal-$($s.shopId)"
    classTeacherUsername = $s.teacher
  }

  # Teacher assignment if API supports it
  try {
    Invoke-Json -Method Post -Url "$Gateway/api/academic/assignments" -Headers $h -Body @{
      sectionId = $section.data.id
      subjectKey = 'english'
      teacherUsername = $s.teacher
    } | Out-Null
  } catch {
    Write-Host "  assignment optional skip: $($_.Exception.Message)"
  }

  $adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
    answers = @{
      fullName = "Portal Child $($s.shopId) $stamp"
      age = 11
      mobile = ('91' + $stamp.Substring(4, 8))
      email = "child.$stamp@$($s.shopId).local"
      classApplied = "Portal-$($s.shopId)"
      classSection = "Portal-$($s.shopId)"
      documentsComplete = $true
      guardianFullName = 'Portal Parent'
      guardianRelation = 'Mother'
      guardianMobile = '9844445555'
    }
  }
  $app = $adm.data
  for ($i = 1; $i -le 8; $i++) {
    if ($app.status -eq 'APPROVED') { break }
    $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{
      action = 'APPROVE'; comment = "portal $i"
    }
    $app = $upd.data
  }
  if (-not $app.enrolledStudentId) { throw "enroll failed for $($s.shopId)" }

  Invoke-Json -Method Put -Url "$Gateway/api/student/students/$($app.enrolledStudentId)/guardians" -Headers $h -Body @{
    guardians = @(
      @{
        fullName = 'Portal Parent'
        relation = 'Mother'
        mobile = '9844445555'
        email = "parent@$($s.shopId).local"
        authUsername = $s.parent
        isPrimary = $true
      }
    )
  } | Out-Null

  $hw = Invoke-Json -Method Post -Url "$Gateway/api/exam/homework" -Headers $h -Body @{
    title = "Portal HW $($s.shopId) $stamp"
    description = 'Read chapter 1'
    subjectKey = 'english'
    sectionId = $section.data.id
    classSection = "Portal-$($s.shopId)"
    dueDate = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
    status = 'PUBLISHED'
  }

  Invoke-Json -Method Post -Url "$Gateway/api/fee/collections" -Headers $h -Body @{
    answers = @{
      admissionNo = $app.enrolledAdmissionNo
      studentName = "Portal Child $($s.shopId) $stamp"
      amount = 1200
      feeHead = "LOAD-$stamp"
      pendingDays = 3
      dueDate = (Get-Date).ToString('yyyy-MM-dd')
      paymentMode = 'UPI'
      email = "child.$stamp@$($s.shopId).local"
      mobile = ('91' + $stamp.Substring(4, 8))
    }
  } | Out-Null

  $fixtures += [ordered]@{
    shopId = $s.shopId
    admin = $s.admin
    teacher = $s.teacher
    parent = $s.parent
    password = 'password'
    studentId = $app.enrolledStudentId
    admissionNo = $app.enrolledAdmissionNo
    sectionId = $section.data.id
    homeworkId = $hw.data.id
  }
}

$payload = [ordered]@{
  generatedAt = (Get-Date).ToString('o')
  gateway = $Gateway
  schools = $fixtures
}
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($outFile, ($payload | ConvertTo-Json -Depth 6), $utf8NoBom)
Write-Host ''
Write-Host "Wrote fixture: $outFile"
Write-Host 'Next: k6 run portal-mix-load.js -e QUICK=1'
