# Smoke-test payment intents: create + simulate-capture + optional webhook.
# Requires: gateway :9090, fee-service with FEATURE_MULTI_PAYMENT_GATEWAY, demo-school login.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'

function Invoke-Json {
  param([string]$Method, [string]$Url, [hashtable]$Headers, $Body)
  $params = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = 'application/json'
  }
  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  return Invoke-RestMethod @params
}

Write-Host "Logging in as $Username @ $ShopId..."
$login = Invoke-Json -Method Post -Url "$Gateway/api/v1/auth/login" -Headers @{} -Body @{
  username = $Username
  password = $Password
  shopId = $ShopId
}
if (-not $login.accessToken) { throw 'Login failed - no accessToken' }

$h = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = '2025-26'
}

Write-Host 'Finance bootstrap...'
$boot = Invoke-Json -Method Get -Url "$Gateway/api/fee/finance/bootstrap" -Headers $h
if (-not $boot.data.paymentGatewayEnabled) {
  throw 'FEATURE_MULTI_PAYMENT_GATEWAY is off - enable it on the plan first'
}
Write-Host "  mode=$($boot.data.paymentMode) adapters=$($boot.data.availableAdapters -join ',')"

Write-Host 'Create simulated payment intent...'
$idem = [guid]::NewGuid().ToString()
$intent = Invoke-Json -Method Post -Url "$Gateway/api/fee/finance/payments/intents" -Headers $h -Body @{
  amount = 250
  studentRef = 'ADM-PAY-SMOKE'
  providerKey = 'simulated'
  mode = 'UPI'
  idempotencyKey = $idem
}
$intentId = $intent.data.id
$orderId = $intent.data.gatewayOrderId
if (-not $intentId) { throw 'Intent create failed' }
if ($intent.data.adapter -ne 'SIMULATED') { throw "Expected SIMULATED adapter, got $($intent.data.adapter)" }
Write-Host "  intent=$intentId order=$orderId status=$($intent.data.status)"

Write-Host 'Idempotent create...'
$again = Invoke-Json -Method Post -Url "$Gateway/api/fee/finance/payments/intents" -Headers $h -Body @{
  amount = 250
  studentRef = 'ADM-PAY-SMOKE'
  providerKey = 'simulated'
  idempotencyKey = $idem
}
if ($again.data.id -ne $intentId) { throw 'Idempotency failed - different intent id' }

Write-Host 'Simulate capture...'
$cap = Invoke-Json -Method Post -Url "$Gateway/api/fee/finance/payments/intents/$intentId/simulate-capture" -Headers $h -Body @{}
if ($cap.data.status -ne 'CAPTURED') { throw "Expected CAPTURED, got $($cap.data.status)" }

Write-Host 'Idempotent simulate capture...'
$cap2 = Invoke-Json -Method Post -Url "$Gateway/api/fee/finance/payments/intents/$intentId/simulate-capture" -Headers $h -Body @{}
if ($cap2.data.status -ne 'CAPTURED') { throw 'Second capture should stay CAPTURED' }

Write-Host 'Simulated webhook (second intent)...'
$intent2 = Invoke-Json -Method Post -Url "$Gateway/api/fee/finance/payments/intents" -Headers $h -Body @{
  amount = 100
  studentRef = 'ADM-PAY-WH'
  providerKey = 'simulated'
  idempotencyKey = [guid]::NewGuid().ToString()
}
$whBody = @{
  gatewayOrderId = $intent2.data.gatewayOrderId
  gatewayTxnId = 'SIM-WH-1'
} | ConvertTo-Json -Compress
$wh = Invoke-RestMethod -Method Post -Uri "$Gateway/api/fee/finance/payments/webhooks/simulated" `
  -ContentType 'application/json' `
  -Headers @{ 'X-Sim-Signature' = 'sim:test' } `
  -Body $whBody
if (-not $wh.success) { throw "Webhook failed: $($wh | ConvertTo-Json -Compress)" }
if ($wh.data.status -ne 'CAPTURED') { throw "Webhook capture expected CAPTURED, got $($wh.data.status)" }

Write-Host ''
Write-Host 'OK - payment gateway smoke passed (simulate + webhook).'
Write-Host "  intent=$intentId webhookIntent=$($intent2.data.id)"
