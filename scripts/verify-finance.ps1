# Phase 19 - Finance depth verification.
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
foreach ($flag in @('FEATURE_FEE', 'FEATURE_MULTI_PAYMENT_GATEWAY')) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Finance bootstrap ==='
$boot = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/fee/finance/bootstrap')
if (@($boot.heads).Count -lt 1) { throw 'no fee heads' }
if (@($boot.structures).Count -lt 1) { throw 'no structures' }
Write-Host "OK   heads=$(@($boot.heads).Count) structures=$(@($boot.structures).Count) gateway=$($boot.paymentGatewayEnabled)"

Write-Host ''
Write-Host '=== Save fee head ==='
$labKey = "LAB_$(Get-Date -Format 'HHmmss')"
$headBody = @{
  definitionKey = $labKey
  code          = $labKey
  label         = 'Lab Fee'
  category      = 'ACADEMIC'
  refundable    = $true
  enabled       = $true
} | ConvertTo-Json
$savedHead = Unwrap-Data (Invoke-RestMethod -Method Put -Headers $headers `
  -Uri "http://localhost:9090/api/fee/finance/heads/$labKey" `
  -ContentType 'application/json' -Body $headBody)
if ($savedHead.definitionKey -ne $labKey) { throw 'head key mismatch' }
Write-Host "OK   head $labKey v=$($savedHead.version)"

Write-Host ''
Write-Host '=== Demand preview with concession ==='
$demandBody = @{
  structureKey  = 'grade_8_annual'
  concessionKey = 'sibling_10'
  studentRef    = 'ADM-FIN-VERIFY'
  studentName   = 'Finance Verify'
  classSection  = 'Grade 8-A'
} | ConvertTo-Json
$demand = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/fee/finance/demands/preview' `
  -ContentType 'application/json' -Body $demandBody)
if ([decimal]$demand.grossAmount -le 0) { throw 'grossAmount expected > 0' }
if ([decimal]$demand.discountAmount -le 0) { throw 'discountAmount expected > 0 for sibling_10' }
if ([decimal]$demand.netAmount -ge [decimal]$demand.grossAmount) { throw 'net should be < gross' }
Write-Host "OK   gross=$($demand.grossAmount) discount=$($demand.discountAmount) net=$($demand.netAmount)"

Write-Host ''
Write-Host '=== Payment intent + simulate capture ==='
$idem = [guid]::NewGuid().ToString()
$intentBody = @{
  amount          = $demand.netAmount
  studentRef      = 'ADM-FIN-VERIFY'
  providerKey     = 'simulated'
  mode            = 'UPI'
  idempotencyKey  = $idem
  demand          = $demand
} | ConvertTo-Json -Depth 8
$intent = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/fee/finance/payments/intents' `
  -ContentType 'application/json' -Body $intentBody)
if ($intent.status -ne 'PENDING') { throw "expected PENDING, got $($intent.status)" }
$id = $intent.id
$again = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/fee/finance/payments/intents' `
  -ContentType 'application/json' -Body $intentBody)
if ($again.id -ne $id) { throw 'idempotency failed' }
$captured = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri "http://localhost:9090/api/fee/finance/payments/intents/$id/simulate-capture" `
  -ContentType 'application/json' -Body '{}')
if ($captured.status -ne 'CAPTURED') { throw "expected CAPTURED, got $($captured.status)" }
Write-Host "OK   intent=$id CAPTURED"

Write-Host ''
Write-Host '=== Transactions list ==='
$txns = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/fee/finance/transactions')
if (@($txns).Count -lt 1) { throw 'expected transactions' }
Write-Host "OK   transactions=$(@($txns).Count)"

Write-Host ''
Write-Host 'PASS  Phase 19 finance depth'
