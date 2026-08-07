# demo-school Board Compliance pilot (P6): packs → validate → approve → lock → export → disclosure.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password',
  # Soften ICSE required/BLOCKER maps temporarily so approve/lock/export can run on dirty demo masters.
  [switch]$ForceWorkflow
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$loginBody = @{
  shopId   = $ShopId
  username = $Username
  password = $Password
} | ConvertTo-Json

function Invoke-Api {
  param(
    [string]$Method = 'GET',
    [string]$Path,
    [hashtable]$Headers,
    [object]$Body = $null
  )
  $uri = "$Gateway$Path"
  $params = @{
    Method     = $Method
    Uri        = $uri
    Headers    = $Headers
    TimeoutSec = 120
  }
  if ($null -ne $Body) {
    $params.ContentType = 'application/json'
    $params.Body = if ($Body -is [string]) { $Body } else { ($Body | ConvertTo-Json -Depth 8) }
  }
  $resp = Invoke-RestMethod @params
  if ($null -ne $resp.data) { return $resp.data }
  return $resp
}

function Assert-True([bool]$Cond, [string]$Msg) {
  if (-not $Cond) { throw $Msg }
}

$pass = 0
function Ok([string]$Msg) { $script:pass++; Write-Host "OK   $Msg" -ForegroundColor Green }
function Warn([string]$Msg) { Write-Host "WARN $Msg" -ForegroundColor Yellow }

Write-Host '=== 1) Login demo-school ==='
$login = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/auth/login" `
  -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
$token = $login.accessToken
Assert-True ([bool]$token) 'No accessToken from login'
Ok ('logged in as ' + $login.username)

$headers = @{
  Authorization           = "Bearer $token"
  'X-Tenant-Id'           = $ShopId
  'X-Branch-Id'           = 'main'
  'X-Academic-Session-Id' = '2025-26'
  'X-Shop-Id'             = $ShopId
  'X-User-Id'             = $Username
  'X-Role-Code'           = 'SHOP_OWNER'
}

Write-Host ''
Write-Host '=== 2) Feature flag FEATURE_CBSE_COMPLIANCE ==='
try {
  $flag = Invoke-RestMethod -Headers $headers -Uri "$Gateway/api/subscription/feature-flags/FEATURE_CBSE_COMPLIANCE" -TimeoutSec 30
  $enabled = if ($null -ne $flag.data) { $flag.data.enabled } else { $flag.enabled }
  if ($enabled -ne $true) {
    Warn ('FEATURE_CBSE_COMPLIANCE=' + $enabled + ' - enable on demo-school enterprise/professional plan if APIs fail')
  } else {
    Ok 'FEATURE_CBSE_COMPLIANCE enabled'
  }
} catch {
  Warn ('feature-flag check failed: ' + $_.Exception.Message)
}

Write-Host ''
Write-Host '=== 3) Boards + published packs (V7) ==='
$boards = Invoke-Api -Path '/api/compliance/boards' -Headers $headers
$boardCount = @($boards).Count
Assert-True ($boardCount -ge 3) ('Expected at least 3 boards, got ' + $boardCount)
$codes = @($boards | ForEach-Object { $_.code })
Assert-True ($codes -contains 'CBSE') 'Missing CBSE board'
Assert-True ($codes -contains 'ICSE') 'Missing ICSE board'
Assert-True ($codes -contains 'STATE') 'Missing STATE board'
Ok ("boards: " + ($codes -join ', '))

$packs = Invoke-Api -Path '/api/compliance/packs' -Headers $headers
$packKeys = @($packs | ForEach-Object { $_.packKey })
Assert-True ($packKeys -contains 'CBSE-2026.1') 'Missing CBSE-2026.1'
Assert-True ($packKeys -contains 'ICSE-2026.1') 'Missing ICSE-2026.1'
Assert-True ($packKeys -contains 'STATE-2026.1') 'Missing STATE-2026.1'
Ok ("packs: " + ($packKeys -join ', '))

Write-Host ''
Write-Host '=== 4) Platform templates (admin) ==='
$templates = Invoke-Api -Path '/api/compliance/platform/templates' -Headers $headers
$tmplCount = @($templates).Count
Assert-True ($tmplCount -ge 3) ('Expected at least 3 templates, got ' + $tmplCount)
Ok ("platform templates=" + $tmplCount)

$icseMaps = Invoke-Api -Path '/api/compliance/platform/field-maps?boardCode=ICSE' -Headers $headers
$icseMapCount = @($icseMaps).Count
Assert-True ($icseMapCount -gt 0) 'ICSE field maps empty'
Ok ("ICSE field maps=" + $icseMapCount)

Write-Host ''
Write-Host '=== 5) Switch profile to ICSE / ICSE-2026.1 ==='
$profile = Invoke-Api -Path '/api/compliance/profile' -Headers $headers
$saveBody = @{
  boardCode           = 'ICSE'
  activePackKey       = 'ICSE-2026.1'
  schoolName          = if ($profile.schoolName) { $profile.schoolName } else { 'Demo School Pilot' }
  affiliationNumber   = if ($profile.affiliationNumber) { $profile.affiliationNumber } else { 'ICSE-PILOT-001' }
  schoolCode          = if ($profile.schoolCode) { $profile.schoolCode } else { 'DS-01' }
  udisePlus           = if ($profile.udisePlus) { $profile.udisePlus } else { '12345678901' }
  addressLine         = if ($profile.addressLine) { $profile.addressLine } else { 'Pilot Lane' }
  city                = if ($profile.city) { $profile.city } else { 'Demo City' }
  stateCode           = if ($profile.stateCode) { $profile.stateCode } else { 'DL' }
  pincode             = if ($profile.pincode) { $profile.pincode } else { '110001' }
  principalName       = if ($profile.principalName) { $profile.principalName } else { 'Pilot Principal' }
  principalMobile     = if ($profile.principalMobile) { $profile.principalMobile } else { '9876543210' }
  principalEmail      = if ($profile.principalEmail) { $profile.principalEmail } else { 'principal@demo-school.local' }
  schoolEmail         = if ($profile.schoolEmail) { $profile.schoolEmail } else { 'office@demo-school.local' }
  trustSocietyName    = if ($profile.trustSocietyName) { $profile.trustSocietyName } else { 'Demo Trust' }
  recognitionDetails  = $profile.recognitionDetails
  infrastructureNotes = $profile.infrastructureNotes
}
$saved = Invoke-Api -Method PUT -Path '/api/compliance/profile' -Headers $headers -Body $saveBody
Assert-True ($saved.boardCode -eq 'ICSE') ('boardCode=' + $saved.boardCode)
Assert-True ($saved.activePackKey -eq 'ICSE-2026.1') ('pack=' + $saved.activePackKey)
Ok ("profile board=" + $saved.boardCode + " pack=" + $saved.activePackKey + " completeness=" + $saved.profileCompletenessPercent + "%")

Write-Host ''
Write-Host '=== 6) Dashboard + readiness ==='
$dash = Invoke-Api -Path '/api/compliance/dashboard' -Headers $headers
Ok ("dashboard score=" + $dash.complianceScore + " pending=" + $dash.pendingCampaigns)

$ready = Invoke-Api -Path '/api/compliance/readiness' -Headers $headers
Ok ("readiness blockers=" + $ready.openBlockers + " warns=" + $ready.openWarnings + " pct=" + $ready.readinessPercent)

Write-Host ''
Write-Host '=== 7) Create ICSE campaign + validate ==='
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$campaign = Invoke-Api -Method POST -Path '/api/compliance/campaigns' -Headers $headers -Body @{
  title                     = "ICSE pilot $stamp"
  boardCode                 = 'ICSE'
  packKey                   = 'ICSE-2026.1'
  requireManagementApproval = $false
}
Assert-True ($campaign.boardCode -eq 'ICSE') ('campaign board=' + $campaign.boardCode)
Assert-True ($campaign.packKey -eq 'ICSE-2026.1') ('campaign pack=' + $campaign.packKey)
Ok ("campaign #" + $campaign.id + " status=" + $campaign.status)

$validated = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $campaign.id + "/validate") -Headers $headers -Body @{}
Ok ("validated status=" + $validated.status + " blockers=" + $validated.blockerCount + " warns=" + $validated.warnCount + " score=" + $validated.complianceScore)

$campaign = Invoke-Api -Path ("/api/compliance/campaigns/" + $campaign.id) -Headers $headers

$fieldBackup = @()
$ruleBackup = @()

function Restore-CbseProfile {
  $restore = @{
    boardCode           = 'CBSE'
    activePackKey       = 'CBSE-2026.1'
    schoolName          = $saveBody.schoolName
    affiliationNumber   = $saveBody.affiliationNumber
    schoolCode          = $saveBody.schoolCode
    udisePlus           = $saveBody.udisePlus
    addressLine         = $saveBody.addressLine
    city                = $saveBody.city
    stateCode           = $saveBody.stateCode
    pincode             = $saveBody.pincode
    principalName       = $saveBody.principalName
    principalMobile     = $saveBody.principalMobile
    principalEmail      = $saveBody.principalEmail
    schoolEmail         = $saveBody.schoolEmail
    trustSocietyName    = $saveBody.trustSocietyName
    recognitionDetails  = $saveBody.recognitionDetails
    infrastructureNotes = $saveBody.infrastructureNotes
  }
  $null = Invoke-Api -Method PUT -Path '/api/compliance/profile' -Headers $headers -Body $restore
}

function Restore-IcsePackConfig {
  foreach ($b in $script:fieldBackup) {
    $null = Invoke-Api -Method PUT -Path ('/api/compliance/platform/field-maps/' + $b.id) -Headers $headers -Body @{
      required = $b.required
      severity = $b.severity
    }
  }
  foreach ($b in $script:ruleBackup) {
    $null = Invoke-Api -Method PUT -Path ('/api/compliance/platform/rules/' + $b.id) -Headers $headers -Body @{
      severity = $b.severity
      active   = $b.active
    }
  }
}

if ($campaign.blockerCount -gt 0 -and $ForceWorkflow) {
  Warn ('ForceWorkflow: softening ICSE required/BLOCKER maps temporarily (blockers=' + $campaign.blockerCount + ')')
  $maps = Invoke-Api -Path '/api/compliance/platform/field-maps?boardCode=ICSE' -Headers $headers
  foreach ($m in @($maps)) {
    if ($m.required -or $m.severity -eq 'BLOCKER') {
      $script:fieldBackup += @{ id = $m.id; required = $m.required; severity = $m.severity }
      $null = Invoke-Api -Method PUT -Path ('/api/compliance/platform/field-maps/' + $m.id) -Headers $headers -Body @{
        required = $false
        severity = 'WARN'
      }
    }
  }
  $rules = Invoke-Api -Path '/api/compliance/platform/rules?boardCode=ICSE' -Headers $headers
  foreach ($r in @($rules)) {
    if ($r.active -and $r.severity -eq 'BLOCKER') {
      $script:ruleBackup += @{ id = $r.id; severity = $r.severity; active = $r.active }
      $null = Invoke-Api -Method PUT -Path ('/api/compliance/platform/rules/' + $r.id) -Headers $headers -Body @{
        severity = 'WARN'
      }
    }
  }
  $validated = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $campaign.id + "/validate") -Headers $headers -Body @{}
  Ok ("revalidated status=" + $validated.status + " blockers=" + $validated.blockerCount + " warns=" + $validated.warnCount)
  $campaign = Invoke-Api -Path ("/api/compliance/campaigns/" + $campaign.id) -Headers $headers
}

if ($campaign.blockerCount -gt 0) {
  Warn ("Skipping approve/lock/export (blockers=" + $campaign.blockerCount + "). Re-run with -ForceWorkflow for dirty demo masters, or fix Data Readiness.")
  Restore-CbseProfile
  Write-Host ''
  Write-Host ("PILOT PARTIAL  pass=" + $pass + "  (workflow deferred)") -ForegroundColor Yellow
  exit 0
}

Write-Host ''
Write-Host '=== 8) Submit for review → principal approve → lock → export → submit ==='
$cid = $campaign.id
$campaign = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $cid + "/submit-for-review") -Headers $headers -Body @{}
Ok ("submit-for-review status=" + $campaign.status)

$campaign = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $cid + "/approve") -Headers $headers -Body @{
  stepCode = 'PRINCIPAL'
  decision = 'APPROVED'
  comment  = 'demo-school pilot'
}
Ok ("principal approved status=" + $campaign.status)

$campaign = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $cid + "/lock") -Headers $headers -Body @{}
Ok ("locked status=" + $campaign.status)

$campaign = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $cid + "/export") -Headers $headers -Body @{
  formats = @('CSV', 'JSON')
}
$artifactCount = @($campaign.artifacts).Count
Assert-True ($artifactCount -gt 0) 'No export artifacts'
Ok ("exported artifacts=" + $artifactCount + " status=" + $campaign.status)

$campaign = Invoke-Api -Method POST -Path ("/api/compliance/campaigns/" + $cid + "/submit") -Headers $headers -Body @{
  channel = 'FILE'
  note    = 'demo-school pilot FILE adapter'
}
Ok ("submitted status=" + $campaign.status + " channel=" + $campaign.adapterChannel)

Write-Host ''
Write-Host '=== 9) Disclosure preview ==='
$disc = Invoke-Api -Path '/api/compliance/disclosure/preview' -Headers $headers
Assert-True ([bool]$disc.bodyHtml) 'disclosure bodyHtml empty'
$warnCount = @($disc.warnings).Count
Ok ("disclosure title=" + $disc.title + " warnings=" + $warnCount)

Restore-IcsePackConfig
if ($fieldBackup.Count -gt 0 -or $ruleBackup.Count -gt 0) {
  Ok 'restored ICSE field maps / rules after ForceWorkflow'
}
Restore-CbseProfile
Ok 'restored profile to CBSE-2026.1'

Write-Host ''
Write-Host ("PILOT PASS  checks=" + $pass) -ForegroundColor Green
