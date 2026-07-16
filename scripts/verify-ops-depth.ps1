# Phase 21 - Ops depth verification (library, hostel, transport, payroll masters).
$ErrorActionPreference = 'Stop'

function Unwrap-Data($resp) {
  if ($null -ne $resp.data) { return $resp.data }
  return $resp
}

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
foreach ($flag in @('FEATURE_OPS_DEPTH', 'FEATURE_LIBRARY', 'FEATURE_HOSTEL', 'FEATURE_TRANSPORT', 'FEATURE_PAYROLL')) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Library ops bootstrap + fine preview ==='
$lib = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/library/ops/bootstrap')
if (@($lib.categories).Count -lt 1) { throw 'no library categories' }
$fine = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/library/ops/fines/preview' `
  -ContentType 'application/json' -Body (@{ overdueDays = 10 } | ConvertTo-Json))
if ([decimal]$fine.fineAmount -le 0) { throw 'fineAmount expected > 0 for 10 overdue days' }
Write-Host "OK   fine=$($fine.fineAmount) chargeableDays=$($fine.chargeableDays)"

Write-Host ''
Write-Host '=== Hostel ops bootstrap + allocation preview ==='
$hostel = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/hostel/ops/bootstrap')
if (@($hostel.roomTypes).Count -lt 1) { throw 'no hostel room types' }
$alloc = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/hostel/ops/allocations/preview' `
  -ContentType 'application/json' -Body (@{ bedsRequested = 1; occupiedBeds = 5 } | ConvertTo-Json))
if ($alloc.canAllocate -ne $true) { throw 'expected canAllocate true' }
Write-Host "OK   availableBeds=$($alloc.availableBeds) monthlyFee=$($alloc.monthlyFee)"

Write-Host ''
Write-Host '=== Transport ops bootstrap + fare preview ==='
$transport = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/transport/ops/bootstrap')
if (@($transport.routes).Count -lt 1) { throw 'no transport routes' }
$fare = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/transport/ops/fares/preview' `
  -ContentType 'application/json' -Body (@{ distanceKm = 12 } | ConvertTo-Json))
if ([decimal]$fare.fareAmount -lt 500) { throw 'fareAmount too low' }
Write-Host "OK   fare=$($fare.fareAmount) distance=$($fare.distanceKm)"

Write-Host ''
Write-Host '=== Payroll ops bootstrap + payslip preview ==='
$payroll = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/payroll/ops/bootstrap')
if (@($payroll.structures).Count -lt 1) { throw 'no payroll structures' }
$payslip = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/payroll/ops/payslips/preview' `
  -ContentType 'application/json' -Body (@{ basicPay = 25000 } | ConvertTo-Json))
if ([decimal]$payslip.netPay -le 0) { throw 'netPay expected > 0' }
Write-Host "OK   gross=$($payslip.grossPay) net=$($payslip.netPay)"

Write-Host ''
Write-Host 'PASS  Phase 21 ops depth'
