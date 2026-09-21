# Phase 12 - Drag-drop report designer verification.
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
Write-Host '=== Feature flag ==='
$flag = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/subscription/feature-flags/FEATURE_REPORT_BUILDER'
$enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
if ($enabled -ne $true) { throw "FEATURE_REPORT_BUILDER not enabled: $($flag | ConvertTo-Json -Compress)" }
Write-Host 'OK   FEATURE_REPORT_BUILDER'

Write-Host ''
Write-Host '=== Bootstrap ==='
$boot = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/reports/bootstrap'
$bootData = if ($boot.data) { $boot.data } else { $boot }
if ($bootData.featureEnabled -ne $true) { throw 'bootstrap featureEnabled=false' }
$types = @($bootData.elementTypes)
if ($types.Count -lt 4) { throw 'bootstrap missing elementTypes palette' }
Write-Host "OK   palette=$($types.Count) templates=$(@($bootData.templates).Count)"

Write-Host ''
Write-Host '=== Load offer_letter ==='
$tplResp = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/reports/templates/offer_letter'
$tpl = if ($tplResp.data) { $tplResp.data } else { $tplResp }
if (-not $tpl.elements) { throw 'offer_letter missing elements' }
$els = @($tpl.elements)
foreach ($el in $els) {
  if (-not $el.id) { throw 'element missing id after normalize' }
}
Write-Host "OK   elements=$($els.Count) (ids present)"

Write-Host ''
Write-Host '=== Save org override with extra field block ==='
$newEl = @{
  id       = [guid]::NewGuid().ToString()
  type     = 'field'
  x        = 40
  y        = 450
  width    = 300
  height   = 24
  fontSize = 12
  align    = 'left'
  bold     = $false
  bind     = 'student.admissionNo'
  text     = 'Admission: {{student.admissionNo}}'
}
$elsList = [System.Collections.Generic.List[object]]::new()
foreach ($el in $els) { $elsList.Add($el) }
$elsList.Add($newEl)
$saveBody = @{
  templateKey = 'offer_letter'
  name        = 'Offer Letter'
  layout      = $tpl.layout
  elements    = $elsList
} | ConvertTo-Json -Depth 8

$savedResp = Invoke-RestMethod -Method Put -Headers $headers `
  -Uri 'http://localhost:9090/api/reports/templates/offer_letter' `
  -ContentType 'application/json' -Body $saveBody
$saved = if ($savedResp.data) { $savedResp.data } else { $savedResp }
$savedEls = @($saved.elements)
if ($savedEls.Count -lt ($els.Count + 1)) { throw 'save did not persist new element' }
Write-Host "OK   saved elements=$($savedEls.Count)"

Write-Host ''
Write-Host '=== Preview PDF (unsaved canvas path) ==='
$previewBody = @{
  templateKey = 'offer_letter'
  name        = 'Offer Letter'
  layout      = $saved.layout
  elements    = $saved.elements
  format      = 'PDF'
} | ConvertTo-Json -Depth 8

$prevResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/reports/preview' `
  -ContentType 'application/json' -Body $previewBody
$prev = if ($prevResp.data) { $prevResp.data } else { $prevResp }
if (-not $prev.contentBase64) { throw 'preview missing contentBase64' }
if ([int]$prev.byteLength -lt 200) { throw "preview PDF too small: $($prev.byteLength)" }
Write-Host "OK   preview bytes=$($prev.byteLength)"

Write-Host ''
Write-Host '=== Render saved template ==='
$renderBody = @{ format = 'PDF' } | ConvertTo-Json
$renResp = Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/reports/templates/offer_letter/render' `
  -ContentType 'application/json' -Body $renderBody
$ren = if ($renResp.data) { $renResp.data } else { $renResp }
if (-not $ren.contentBase64) { throw 'render missing contentBase64' }
Write-Host "OK   render bytes=$($ren.byteLength)"

Write-Host ''
Write-Host 'PASS  verify-report-designer'
