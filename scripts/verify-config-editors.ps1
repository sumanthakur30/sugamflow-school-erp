# Phase 18 - Real config editors verification.
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
foreach ($flag in @(
  'FEATURE_FORM_BUILDER',
  'FEATURE_WORKFLOW_BUILDER',
  'FEATURE_RULE_ENGINE',
  'FEATURE_ADMIN_CONFIG'
)) {
  $resp = Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Form save ==='
$formKey = "verify_form_$(Get-Date -Format 'HHmmss')"
$formBody = @{
  formKey = $formKey
  title   = 'Verify Form'
  sections = @(
    @{
      id         = 'main'
      title      = 'Main'
      repeatable = $false
      fields     = @(
        @{ key = 'fullName'; label = 'Full Name'; type = 'TEXTBOX'; mandatory = $true }
      )
    }
  )
  validationRules       = @()
  conditionalVisibility = @()
} | ConvertTo-Json -Depth 8
$savedForm = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/forms' -ContentType 'application/json' -Body $formBody)
if ($savedForm.formKey -ne $formKey) { throw 'formKey mismatch' }
$gotForm = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri "http://localhost:9090/api/forms/$formKey")
if ($gotForm.title -ne 'Verify Form') { throw 'form title not persisted' }
Write-Host "OK   form $formKey"

Write-Host ''
Write-Host '=== Workflow save ==='
$wfKey = "verify_wf_$(Get-Date -Format 'HHmmss')"
$wfBody = @{
  workflowKey = $wfKey
  name        = 'Verify Workflow'
  steps       = @(
    @{ sequence = 1; name = 'Reception'; assignRole = 'RECEPTION'; slaHours = 12; autoApprove = $false }
  )
  autoApproveRules  = @()
  rejectRules       = @()
  escalationRules   = @()
  notificationRules = @()
} | ConvertTo-Json -Depth 8
$savedWf = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/workflows' -ContentType 'application/json' -Body $wfBody)
if ($savedWf.workflowKey -ne $wfKey) { throw 'workflowKey mismatch' }
Write-Host "OK   workflow $wfKey"

Write-Host ''
Write-Host '=== Rule save + evaluate ==='
$ruleId = "verify_rule_$(Get-Date -Format 'HHmmss')"
$ruleBody = @{
  id      = $ruleId
  name    = 'Verify Rule'
  enabled = $true
  when    = @{ field = 'attendance.percent'; op = 'LT'; value = 50 }
  then    = @{ action = 'BLOCK_VERIFY' }
} | ConvertTo-Json -Depth 6
$savedRule = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/rules' -ContentType 'application/json' -Body $ruleBody)
if ($savedRule.id -ne $ruleId) { throw 'rule id mismatch' }
$eval = Unwrap-Data (Invoke-RestMethod -Method Post -Headers $headers `
  -Uri 'http://localhost:9090/api/rules/evaluate' -ContentType 'application/json' `
  -Body (@{ attendance = @{ percent = 40 } } | ConvertTo-Json))
$actions = @($eval.matchedActions)
if ($actions -notcontains 'BLOCK_VERIFY') { throw "expected BLOCK_VERIFY in $actions" }
Write-Host "OK   rule $ruleId matched"

Write-Host ''
Write-Host '=== Menu save ==='
$menus = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/menus')
$menus = @($menus)
$menus += @{
  id                    = "verify_menu_$(Get-Date -Format 'HHmmss')"
  label                 = 'Verify Menu'
  icon                  = ''
  route                 = '/admin/admission'
  order                 = 99
  visible               = $true
  roles                 = @()
  requiredFeatureFlags  = @()
  branchIds             = @()
  children              = @()
}
$savedMenus = Unwrap-Data (Invoke-RestMethod -Method Put -Headers $headers `
  -Uri 'http://localhost:9090/api/config/menus' -ContentType 'application/json' `
  -Body ($menus | ConvertTo-Json -Depth 8))
if (@($savedMenus).Count -lt 1) { throw 'menus empty after save' }
Write-Host "OK   menus=$(@($savedMenus).Count)"

Write-Host ''
Write-Host '=== Module settings save ==='
$mod = Unwrap-Data (Invoke-RestMethod -Headers $headers -Uri 'http://localhost:9090/api/config/modules/admission')
$settings = @{}
foreach ($p in $mod.settings.PSObject.Properties) { $settings[$p.Name] = $p.Value }
$settings['notes'] = "verify-$(Get-Date -Format 'HHmmss')"
$putBody = @{
  moduleKey = 'admission'
  settings  = $settings
} | ConvertTo-Json -Depth 8
$savedMod = Unwrap-Data (Invoke-RestMethod -Method Put -Headers $headers `
  -Uri 'http://localhost:9090/api/config/modules/admission' -ContentType 'application/json' -Body $putBody)
if ($savedMod.settings.notes -ne $settings['notes']) { throw 'module notes not persisted' }
Write-Host "OK   admission notes=$($savedMod.settings.notes)"

Write-Host ''
Write-Host 'PASS  Phase 18 config editors'
