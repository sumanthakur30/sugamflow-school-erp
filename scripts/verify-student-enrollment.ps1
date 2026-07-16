# Phase 7 — Student enrollment after admission approve.
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
foreach ($flag in @('FEATURE_ADMISSION', 'FEATURE_STUDENT_MASTER')) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Submit + approve admission ==='
$submitBody = @{
  answers = @{
    fullName          = 'Neha Sharma'
    age               = 9
    mobile            = '9999900099'
    email             = 'neha@example.com'
    classApplied      = 'IV'
    documentsComplete = $true
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/admission/applications' `
  -ContentType 'application/json' -Body $submitBody
$app = if ($created.data) { $created.data } else { $created }
$id = $app.id
if (-not $id) { throw 'submit returned no id' }
Write-Host "OK   application $id"

for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $actBody = @{ action = 'APPROVE'; comment = "Auto-approve $i" } | ConvertTo-Json
  $updated = Invoke-RestMethod -Method Post -Headers $headers `
    -Uri "http://localhost:9090/api/admission/applications/$id/actions" `
    -ContentType 'application/json' -Body $actBody
  $app = if ($updated.data) { $updated.data } else { $updated }
}
if ($app.status -ne 'APPROVED') { throw "Expected APPROVED, got $($app.status)" }
Write-Host 'OK   approved'

Write-Host ''
Write-Host '=== Enrollment on application ==='
if ($app.hasEnrollment -ne $true) { throw 'hasEnrollment expected true' }
$studentId = $app.enrolledStudentId
if (-not $studentId) { throw 'enrolledStudentId missing' }
Write-Host "OK   studentId=$studentId admissionNo=$($app.enrolledAdmissionNo)"

Write-Host ''
Write-Host '=== Student record ==='
$stuResp = Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/student/students/$studentId"
$stu = if ($stuResp.data) { $stuResp.data } else { $stuResp }
if ($stu.answers.fullName -ne 'Neha Sharma') { throw "fullName mismatch: $($stu.answers.fullName)" }
if ($stu.answers.classApplied -ne 'IV') { throw "classApplied mismatch: $($stu.answers.classApplied)" }
if ($stu.sourceApplicationId -ne $id) { throw 'sourceApplicationId mismatch' }
Write-Host "OK   answers mapped status=$($stu.status)"

Write-Host ''
Write-Host '=== Idempotent re-enroll ==='
$enrollBody = @{
  applicationId = $id
  answers       = @{
    fullName     = 'Neha Sharma'
    age          = 9
    mobile       = '9999900099'
    email        = 'neha@example.com'
    classApplied = 'IV'
  }
} | ConvertTo-Json -Depth 5
$again = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/student/enroll-from-admission' `
  -ContentType 'application/json' -Body $enrollBody
$againData = if ($again.data) { $again.data } else { $again }
if ($againData.id -ne $studentId) { throw "Expected same student id, got $($againData.id)" }
Write-Host 'OK   same student returned'

Write-Host ''
Write-Host 'Phase 7 student enrollment verification passed.'
