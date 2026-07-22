# Static responsive CSS audit + optional live portal bootstrap checks for mobile/tablet readiness.
# Verifies expected breakpoints exist in school-ui portal styles and that portal APIs still serve nav for teacher/parent.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'
$uiRoot = 'D:\school\apps\school-ui\src'
$failed = 0

function Assert-Match([string]$file, [string]$pattern, [string]$label) {
  $path = Join-Path $uiRoot $file
  if (-not (Test-Path $path)) { Write-Host "FAIL missing file $file"; $script:failed++; return }
  $text = Get-Content -Raw $path
  if ($text -notmatch $pattern) {
    Write-Host "FAIL $label ($file)"
    $script:failed++
  } else {
    Write-Host "OK   $label"
  }
}

Write-Host '=== Responsive CSS breakpoints ==='
Assert-Match 'app\layout\shell.component.scss' '@media \(max-width:\s*1024px\)' 'shell tablet (1024)'
Assert-Match 'app\layout\shell.component.scss' '@media \(max-width:\s*900px\)' 'shell stack (900)'
Assert-Match 'app\layout\shell.component.scss' 'overflow-x:\s*auto' 'shell nav horizontal scroll'
Assert-Match 'app\layout\shell.component.scss' '@media \(max-width:\s*480px\)' 'shell phone (480)'
Assert-Match 'app\shared\admin-page.scss' '@media \(max-width:\s*768px\)' 'admin tablet (768)'
Assert-Match 'app\shared\admin-page.scss' '@media \(max-width:\s*640px\)' 'admin phone (640)'
Assert-Match 'app\shared\admin-page.scss' 'font-size:\s*16px' 'admin iOS zoom prevention'
Assert-Match 'app\features\portals\teacher-attendance.component.scss' '@media \(max-width:\s*640px\)' 'attendance phone stack'
Assert-Match 'app\features\portals\teacher-gradebook.component.scss' '@media \(max-width:\s*640px\)' 'gradebook phone stack'
Assert-Match 'app\features\portals\teacher-report-cards.component.scss' '@media \(max-width:\s*768px\)' 'report cards tablet card layout'
Assert-Match 'app\features\portals\teacher-report-cards.component.scss' 'mobile-cards' 'report cards mobile card class'
Assert-Match 'app\features\portals\parent-report-cards.component.scss' '@media \(max-width:\s*640px\)' 'parent report cards phone'
Assert-Match 'app\features\portals\portal-home.component.scss' '@media \(max-width:\s*1024px\)' 'portal home tablet 2-col'
Assert-Match 'app\features\portals\portal-section.component.scss' '@media \(max-width:\s*640px\)' 'portal section phone stack'
Assert-Match 'index.html' 'name="viewport" content="width=device-width, initial-scale=1"' 'viewport meta tag'

Write-Host ''
Write-Host '=== Portal bootstrap (teacher/parent feature surfaces) ==='
$login = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/auth/login" -ContentType 'application/json' -Body (@{
  username = $Username; password = $Password; shopId = $ShopId
} | ConvertTo-Json)
$token = $login.accessToken
if (-not $token) { throw 'login failed' }

function Bootstrap([string]$portal, [string]$role) {
  $h = @{
    Authorization = "Bearer $token"
    'X-Tenant-Id' = $ShopId
    'X-Auth-Role' = $role
    'X-Branch-Id' = 'main'
    'X-Academic-Session-Id' = '2025-26'
  }
  $r = Invoke-RestMethod -Headers $h -Uri "$Gateway/api/config/portals/$portal/bootstrap"
  $d = if ($r.data) { $r.data } else { $r }
  return $d
}

$teacher = Bootstrap 'teacher' 'TEACHER'
$parent = Bootstrap 'parent' 'PARENT'

$teacherNeed = @('attendance','gradebook','report-cards')
foreach ($k in $teacherNeed) {
  if (-not $teacher.sections.$k) {
    Write-Host "FAIL teacher missing section $k"; $failed++
  } else {
    Write-Host "OK   teacher section $k -> $($teacher.sections.$k.apiPath)"
  }
}
$parentNeed = @('attendance','exams','report-cards')
foreach ($k in $parentNeed) {
  if (-not $parent.sections.$k) {
    Write-Host "FAIL parent missing section $k"; $failed++
  } else {
    Write-Host "OK   parent section $k -> $($parent.sections.$k.apiPath)"
  }
}

# Prefer dedicated screens / published APIs over legacy workflow record paths.
$expected = @{
  'teacher.attendance' = '/api/attendance/roster'
  'teacher.gradebook' = '/api/exam/definitions'
  'teacher.report-cards' = '/api/exam/report-cards'
  'parent.attendance' = '/api/attendance/marks/mine'
  'parent.exams' = '/api/exam/marks/published'
  'parent.report-cards' = '/api/exam/report-cards/mine'
}
foreach ($entry in $expected.GetEnumerator()) {
  $parts = $entry.Key.Split('.')
  $portal = if ($parts[0] -eq 'teacher') { $teacher } else { $parent }
  $actual = $portal.sections.($parts[1]).apiPath
  if ($actual -ne $entry.Value) {
    Write-Host "FAIL $($entry.Key) apiPath expected $($entry.Value) got $actual"; $failed++
  } else {
    Write-Host "OK   $($entry.Key) apiPath"
  }
}

$tNav = @($teacher.nav | ForEach-Object { $_.id })
$pNav = @($parent.nav | ForEach-Object { $_.id })
if ($tNav -notcontains 'report-cards') { Write-Host 'FAIL teacher nav missing report-cards'; $failed++ } else { Write-Host 'OK   teacher nav includes report-cards' }
if ($pNav -notcontains 'report-cards') { Write-Host 'FAIL parent nav missing report-cards'; $failed++ } else { Write-Host 'OK   parent nav includes report-cards' }

Write-Host ''
Write-Host '=== Breakpoint matrix (design intent) ==='
@(
  @{ Name='phone'; W=375; Expect='single column, sticky actions, card report rows, scroll nav' },
  @{ Name='phone-lg'; W=480; Expect='same as phone with slightly larger type' },
  @{ Name='tablet'; W=768; Expect='2-col filters, report cards as cards (<=768), shell stacked' },
  @{ Name='tablet-lg'; W=1024; Expect='narrow rail or stacked, portal home 2-col widgets' },
  @{ Name='desktop'; W=1280; Expect='side rail + table report grid' }
) | ForEach-Object {
  Write-Host ("  {0,-10} {1,4}px  {2}" -f $_.Name, $_.W, $_.Expect)
}

if ($failed -gt 0) {
  Write-Host ''
  Write-Host "FAILED checks: $failed"
  exit 1
}
Write-Host ''
Write-Host 'OK - responsive audit + portal feature surfaces passed.'
