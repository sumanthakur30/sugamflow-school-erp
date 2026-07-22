# Finish demo student cleanup:
# 1) Soft-delete remaining smoke-test students
# 2) Strip "(Grade …)" suffixes from names (name/parent only — keep class to avoid roll conflicts)
$ErrorActionPreference = 'Stop'

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

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

function Get-AllStudents {
  $all = @()
  $page = 0
  while ($true) {
    $r = Invoke-RestMethod -Method Get -Headers $headers `
      -Uri "http://localhost:9090/api/student/students?page=$page&size=100"
    $items = @($r.data.items)
    if (-not $items.Count) { break }
    $all += $items
    if ($items.Count -lt 100) { break }
    $page++
  }
  return $all
}

function Test-SmokeName([string]$name) {
  if ([string]::IsNullOrWhiteSpace($name)) { return $false }
  return $name -match '^(ReportCard|Alert|Gradebook|Roster|Lifecycle|Phase\d+)\b' `
    -or $name -match '\bSmoke\b'
}

$boyFirst = @('Aarav','Vivaan','Aditya','Kabir','Ishaan','Reyansh','Arjun','Shaurya','Rohan','Dev','Yash','Krish','Vihaan','Aryan','Om','Atharv')
$girlFirst = @('Ananya','Diya','Myra','Kiara','Saanvi','Aisha','Pari','Navya','Isha','Riya','Meera','Tara','Naina','Kavya','Zara','Sara')
$lastNames = @('Mehta','Shah','Rao','Nair','Gupta','Iyer','Desai','Pillai','Reddy','Kapoor','Joshi','Bhat','Menon','Khan','Verma','Jain','Patel','Singh','Chopra','Malhotra','Banerjee','Das','Kulkarni','Shetty')
$fatherFirst = @('Rajesh','Suresh','Amit','Vikram','Sanjay','Rakesh','Anil','Pradeep','Manoj','Naveen')

$students = Get-AllStudents
Write-Host "Loaded $($students.Count) students"

$softDeleted = 0
$updated = 0
$failed = 0
$usedNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$idx = 0

# Reserve names that are already clean
foreach ($s in $students) {
  $n = [string]$s.answers.fullName
  if ($n -and $n -notmatch '\(' -and -not (Test-SmokeName $n)) {
    [void]$usedNames.Add($n.Trim())
  }
}

foreach ($s in $students) {
  $name = [string]$s.answers.fullName
  if (Test-SmokeName $name) {
    try {
      $body = @{ reason = 'Demo cleanup: remove smoke-test student' } | ConvertTo-Json
      Invoke-RestMethod -Method Post -Headers $headers `
        -Uri "http://localhost:9090/api/student/students/$($s.id)/soft-delete" `
        -ContentType 'application/json' -Body $body | Out-Null
      $softDeleted++
      Write-Host "  soft-delete: $name"
    } catch {
      $failed++
      Write-Host "  FAIL soft-delete $name : $($_.Exception.Message)"
    }
    continue
  }

  if ($name -notmatch '\(') { continue }

  $base = ($name -replace '\s*\([^)]*\)\s*$', '').Trim()
  $finalName = $base
  if (-not $finalName -or $usedNames.Contains($finalName)) {
    do {
      $isGirl = ($idx % 2) -eq 1
      $first = if ($isGirl) { $girlFirst[$idx % $girlFirst.Count] } else { $boyFirst[$idx % $boyFirst.Count] }
      $last = $lastNames[($idx * 3) % $lastNames.Count]
      $finalName = "$first $last"
      $idx++
    } while ($usedNames.Contains($finalName))
  }
  [void]$usedNames.Add($finalName)

  $father = [string]$s.answers.fatherName
  if (-not $father -or $father -match 'Parent') {
    $father = "$($fatherFirst[$idx % $fatherFirst.Count]) $($finalName.Split(' ')[-1])"
  }

  $patch = @{
    answers = @{
      fullName   = $finalName
      fatherName = $father
      parentName = $father
    }
  } | ConvertTo-Json -Depth 5

  try {
    Invoke-RestMethod -Method Put -Headers $headers `
      -Uri "http://localhost:9090/api/student/students/$($s.id)" `
      -ContentType 'application/json' -Body $patch | Out-Null
    $updated++
    Write-Host "  $name -> $finalName"
  } catch {
    $failed++
    $msg = ''
    try {
      $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      $msg = $reader.ReadToEnd()
    } catch { $msg = $_.Exception.Message }
    Write-Host "  FAIL $name : $msg"
  }
}

Write-Host ''
Write-Host "Soft-deleted: $softDeleted"
Write-Host "Renamed:      $updated"
Write-Host "Failed:       $failed"
Write-Host ''
Write-Host '=== Top of Students list ==='
$top = Get-AllStudents | Select-Object -First 20
foreach ($s in $top) {
  Write-Host ("{0} | {1} | {2}" -f $s.admissionNo, $s.answers.fullName, $s.answers.classApplied)
}
