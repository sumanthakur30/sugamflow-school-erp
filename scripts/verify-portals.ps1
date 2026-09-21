# Phase 13 - Parent / Teacher portal apps verification.
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

function Headers([string]$role) {
  return @{
    Authorization           = "Bearer $token"
    'X-Tenant-Id'           = 'demo-school'
    'X-Branch-Id'           = 'main'
    'X-Academic-Session-Id' = '2025-26'
    'X-Shop-Id'             = 'demo-school'
    'X-User-Id'             = 'admin_demo-school'
    'X-Role-Code'           = $role
  }
}

Write-Host ''
Write-Host '=== Feature flags ==='
foreach ($flag in @('FEATURE_PARENT_APP', 'FEATURE_TEACHER_APP')) {
  $resp = Invoke-RestMethod -Headers (Headers 'SHOP_OWNER') `
    -Uri "http://localhost:9090/api/subscription/feature-flags/$flag"
  $enabled = if ($null -ne $resp.data) { $resp.data.enabled } else { $resp.enabled }
  if ($enabled -ne $true) { throw "$flag not enabled" }
  Write-Host "OK   $flag"
}

Write-Host ''
Write-Host '=== Parent bootstrap ==='
$parent = Invoke-RestMethod -Headers (Headers 'PARENT') `
  -Uri 'http://localhost:9090/api/config/portals/parent/bootstrap'
$pd = if ($parent.data) { $parent.data } else { $parent }
if ($pd.featureEnabled -ne $true) { throw 'parent featureEnabled=false' }
if (-not $pd.nav) { throw 'parent missing nav' }
if (-not $pd.widgets) { throw 'parent missing widgets' }
if (-not $pd.sections.attendance) { throw 'parent missing attendance section' }
Write-Host "OK   parent nav=$(@($pd.nav).Count) widgets=$(@($pd.widgets).Count)"

Write-Host ''
Write-Host '=== Teacher bootstrap ==='
$teacher = Invoke-RestMethod -Headers (Headers 'TEACHER') `
  -Uri 'http://localhost:9090/api/config/portals/teacher/bootstrap'
$td = if ($teacher.data) { $teacher.data } else { $teacher }
if ($td.featureEnabled -ne $true) { throw 'teacher featureEnabled=false' }
if (-not $td.sections.students) { throw 'teacher missing students section' }
if (-not $td.sections.gradebook) { throw 'teacher missing gradebook section' }
Write-Host "OK   teacher nav=$(@($td.nav).Count) widgets=$(@($td.widgets).Count)"

Write-Host ''
Write-Host '=== Module settings present ==='
foreach ($mod in @('parent_portal', 'teacher_portal')) {
  $m = Invoke-RestMethod -Headers (Headers 'SHOP_OWNER') `
    -Uri "http://localhost:9090/api/config/modules/$mod"
  $md = if ($m.data) { $m.data } else { $m }
  $settings = if ($md.settings) { $md.settings } else { $md }
  if (-not $settings.nav -and -not $settings.title) {
    # ModuleSettings may wrap as { settings: {...} }
    if ($md.settings) { $settings = $md.settings }
  }
  $title = $null
  if ($md.settings) { $title = $md.settings.title }
  elseif ($md.title) { $title = $md.title }
  if (-not $title) { throw "$mod missing title in settings" }
  Write-Host "OK   $mod title=$title"
}

Write-Host ''
Write-Host '=== Portal list ==='
$list = Invoke-RestMethod -Headers (Headers 'SHOP_OWNER') -Uri 'http://localhost:9090/api/config/portals'
$keys = if ($list.data) { $list.data } else { $list }
if (@($keys).Count -lt 2) { throw 'expected parent+teacher portal keys' }
Write-Host "OK   portals=$($keys -join ',')"

Write-Host ''
Write-Host 'PASS  verify-portals'
