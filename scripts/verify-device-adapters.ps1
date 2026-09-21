# Phase 15 - AI attendance / device adapters verification.
$ErrorActionPreference = 'Stop'

$loginBody = @{
  shopId   = 'demo-school'
  username = 'admin_demo-school'
  password = 'password'
} | ConvertTo-Json

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
foreach ($flag in @('FEATURE_ATTENDANCE', 'FEATURE_AI', 'FEATURE_BIOMETRIC', 'FEATURE_FACE_RECOGNITION', 'FEATURE_GPS')) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Ensure attendance AI knobs ==='
$modResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/modules/attendance'
$mod = if ($modResp.data) { $modResp.data } else { $modResp }
$settings = if ($mod.settings) { $mod.settings } else { $mod }
# Convert to hashtable-like for PUT
$settingsObj = @{}
foreach ($p in $settings.PSObject.Properties) { $settingsObj[$p.Name] = $p.Value }
$settingsObj['aiAttendanceEnabled'] = $true
$settingsObj['autoSubmitFromDevice'] = $true
$settingsObj['minConfidence'] = 0.8
$settingsObj['acceptedAdapterTypes'] = @('BIOMETRIC', 'FACE', 'GPS', 'AI_CAMERA')
$putBody = @{
  moduleKey = 'attendance'
  settings  = $settingsObj
} | ConvertTo-Json -Depth 8
Invoke-RestMethod -Method Put -Headers $headers `
  -Uri 'http://localhost:9090/api/config/modules/attendance' `
  -ContentType 'application/json' -Body $putBody | Out-Null
Write-Host 'OK   attendance module AI knobs'

Write-Host ''
Write-Host '=== Devices bootstrap ==='
$bootResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/attendance/devices/bootstrap'
$boot = if ($bootResp.data) { $bootResp.data } else { $bootResp }
if ($boot.aiAttendanceEnabled -ne $true) { throw 'aiAttendanceEnabled=false' }
$types = @($boot.adapterTypes)
if ($types.Count -lt 4) { throw 'expected 4 adapter types' }
Write-Host "OK   types=$($types.Count) devices=$(@($boot.devices).Count)"

Write-Host ''
Write-Host '=== Register AI_CAMERA device ==='
$deviceKey = "ai-cam-$(Get-Date -Format 'HHmmss')"
$regBody = @{
  deviceKey   = $deviceKey
  name        = 'Verify AI Camera'
  adapterType = 'AI_CAMERA'
  config      = @{ vendor = 'Simulated'; location = 'Lab' }
} | ConvertTo-Json -Depth 5
$regResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/attendance/devices' `
  -ContentType 'application/json' -Body $regBody
$device = if ($regResp.data) { $regResp.data } else { $regResp }
$id = $device.id
if (-not $id) { throw 'register returned no id' }
Write-Host "OK   device $id ($deviceKey)"

Write-Host ''
Write-Host '=== Simulate punch (auto-submit) ==='
$simResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/attendance/devices/$id/simulate" `
  -ContentType 'application/json' -Body '{}'
$ev = if ($simResp.data) { $simResp.data } else { $simResp }
if ($ev.status -ne 'SUBMITTED') { throw "expected SUBMITTED, got $($ev.status) payload=$($ev | ConvertTo-Json -Compress)" }
if (-not $ev.attendanceRecordId) { throw 'attendanceRecordId missing' }
Write-Host "OK   SUBMITTED record=$($ev.attendanceRecordId)"

Write-Host ''
Write-Host '=== Low confidence rejected ==='
$lowBody = @{
  studentName         = 'Test Low'
  admissionNo         = 'ADM-LOW'
  classSection        = 'Grade 8-A'
  status              = 'PRESENT'
  attendancePercent   = 90
  confidence          = 0.2
} | ConvertTo-Json
$lowResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/attendance/devices/$id/events" `
  -ContentType 'application/json' -Body $lowBody
$low = if ($lowResp.data) { $lowResp.data } else { $lowResp }
if ($low.status -ne 'REJECTED_LOW_CONFIDENCE') {
  throw "expected REJECTED_LOW_CONFIDENCE, got $($low.status)"
}
Write-Host 'OK   low confidence rejected'

Write-Host ''
Write-Host '=== Events list ==='
$listResp = Invoke-RestMethod -Headers $headers `
  -Uri "http://localhost:9090/api/attendance/devices/events?deviceId=$id"
$list = if ($listResp.data) { $listResp.data } else { $listResp }
if (@($list).Count -lt 2) { throw 'expected at least 2 events' }
Write-Host "OK   events=$(@($list).Count)"

Write-Host ''
Write-Host 'PASS  verify-device-adapters'
