# Phase 7b - Parent / guardian link on student.
$ErrorActionPreference = 'Stop'

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

Write-Host '=== Login ==='
$login = $null
for ($i = 1; $i -le 5; $i++) {
  try {
    $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' `
      -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
    break
  } catch {
    Start-Sleep -Seconds 2
  }
}
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
Write-Host '=== Bootstrap parent form ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/student/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if (-not $bootData.parentForm) { throw 'bootstrap missing parentForm' }
Write-Host "OK   parentFormKey=$($bootData.parentFormKey)"

Write-Host ''
Write-Host '=== Admit with guardian + enroll ==='
$submitBody = @{
  answers = @{
    fullName           = 'Kabir Mehta'
    age                = 10
    dob                = '2016-02-01'
    mobile             = '9999900888'
    email              = 'kabir@example.com'
    classApplied       = 'V'
    classGrade         = '5'
    sectionLetter      = 'A'
    documentsComplete  = $true
    guardianFullName   = 'Anita Mehta'
    guardianRelation   = 'Mother'
    guardianMobile     = '9999900777'
    guardianEmail      = 'anita@example.com'
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/admission/applications' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }

for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/admission/applications/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
$studentId = $app.enrolledStudentId
if (-not $studentId) { throw 'enrolledStudentId missing' }
Write-Host "OK   enrolled student $studentId"

Write-Host ''
Write-Host '=== Guardians from enrollment ==='
$stuResp = Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/student/students/$studentId"
$stu = if ($stuResp.data) { $stuResp.data } else { $stuResp }
$guardians = @($stu.guardians)
if ($guardians.Count -lt 1) { throw 'Expected at least one guardian from admission mapping' }
$g0 = $guardians[0]
if ($g0.fullName -ne 'Anita Mehta') { throw "fullName mismatch: $($g0.fullName)" }
if ($g0.relation -ne 'Mother') { throw "relation mismatch: $($g0.relation)" }
if ($g0.mobile -ne '9999900777') { throw "mobile mismatch: $($g0.mobile)" }
Write-Host "OK   guardian $($g0.fullName) ($($g0.relation))"

Write-Host ''
Write-Host '=== PUT add second guardian ==='
$putBody = @{
  guardians = @(
    @{
      fullName  = 'Anita Mehta'
      relation  = 'Mother'
      mobile    = '9999900777'
      email     = 'anita@example.com'
      isPrimary = $true
    },
    @{
      fullName  = 'Raj Mehta'
      relation  = 'Father'
      mobile    = '9999900666'
      email     = 'raj@example.com'
      isPrimary = $false
    }
  )
} | ConvertTo-Json -Depth 5

$updatedStu = Invoke-RestMethod -Method Put -Headers $headers `
  -Uri "http://localhost:9090/api/student/students/$studentId/guardians" `
  -ContentType 'application/json' -Body $putBody
$stu2 = if ($updatedStu.data) { $updatedStu.data } else { $updatedStu }
if (@($stu2.guardians).Count -ne 2) { throw "Expected 2 guardians, got $($stu2.guardianCount)" }
Write-Host "OK   guardianCount=$($stu2.guardianCount)"

Write-Host ''
Write-Host 'Phase 7b student guardians verification passed.'
