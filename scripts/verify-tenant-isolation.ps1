# Live tenant-isolation smoke test: two orgs must never see each other's data.
# Requires: gateway :9090, auth, school services up, and valid passwords for both orgs.
param(
  [string]$OrgA = 'NAT-01',
  [string]$UserA = 'demo_NAT-01',
  [string]$PassA = 'Test@1234',
  [string]$OrgB = 'demo-school',
  [string]$UserB = 'admin_demo-school',
  [string]$PassB = 'password',
  [string]$Gateway = 'http://localhost:9090'
)

$ErrorActionPreference = 'Stop'
$failures = @()

function Login([string]$Org, [string]$User, [string]$Pass) {
  $r = Invoke-RestMethod -Uri "$Gateway/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (@{
    shopId = $Org; username = $User; password = $Pass
  } | ConvertTo-Json)
  if (-not $r.accessToken) { throw "Login failed for $Org/$User" }
  return $r.accessToken
}

function Headers([string]$Token, [string]$Org) {
  return @{
    Authorization = "Bearer $Token"
    'X-Tenant-Id' = $Org
    'X-Branch-Id' = 'main'
    'X-Academic-Session-Id' = '2025-26'
  }
}

Write-Host "Login $OrgA and $OrgB..."
$tokenA = Login $OrgA $UserA $PassA
$tokenB = Login $OrgB $UserB $PassB

# 1) Each org sees only its own students.
$stuA = Invoke-RestMethod -Uri "$Gateway/api/student/students?page=0&size=5" -Headers (Headers $tokenA $OrgA)
$stuB = Invoke-RestMethod -Uri "$Gateway/api/student/students?page=0&size=5" -Headers (Headers $tokenB $OrgB)
Write-Host "1) $OrgA students=$($stuA.data.totalElements)  $OrgB students=$($stuB.data.totalElements)"
foreach ($s in $stuA.data.items) {
  if ($s.organizationId -ne $OrgA) { $failures += "student row from '$($s.organizationId)' leaked into $OrgA list" }
}
foreach ($s in $stuB.data.items) {
  if ($s.organizationId -ne $OrgB) { $failures += "student row from '$($s.organizationId)' leaked into $OrgB list" }
}

# 2) Spoofed X-Tenant-Id: token A + tenant header B must NOT return B's data.
Write-Host "2) Spoof check: token $OrgA with X-Tenant-Id $OrgB..."
try {
  $spoof = Invoke-RestMethod -Uri "$Gateway/api/student/students?page=0&size=5" -Headers (Headers $tokenA $OrgB)
  foreach ($s in $spoof.data.items) {
    if ($s.organizationId -eq $OrgB) { $failures += "SPOOF LEAK: token $OrgA read $OrgB student data" }
  }
  Write-Host "   spoof request returned $($spoof.data.totalElements) rows (must contain no $OrgB rows)"
} catch {
  Write-Host "   spoof request rejected (status $($_.Exception.Response.StatusCode.value__)) - OK"
}

# 3) Theme isolation: A's theme must not show B's school name.
$themeA = Invoke-RestMethod -Uri "$Gateway/api/config/design-studio/theme" -Headers (Headers $tokenA $OrgA)
$themeB = Invoke-RestMethod -Uri "$Gateway/api/config/design-studio/theme" -Headers (Headers $tokenB $OrgB)
Write-Host "3) themeA=$($themeA.data.branding.schoolName)  themeB=$($themeB.data.branding.schoolName)"
if ($themeA.data.organizationId -ne $OrgA) { $failures += "theme org mismatch for $OrgA" }
if ($themeB.data.organizationId -ne $OrgB) { $failures += "theme org mismatch for $OrgB" }

# 4) Direct service call bypassing gateway must be rejected (403).
Write-Host "4) Direct call to :8181 without gateway headers..."
try {
  Invoke-RestMethod -Uri 'http://localhost:8181/api/config/branches' -Headers @{ 'X-Tenant-Id' = $OrgA } | Out-Null
  $failures += 'direct service call without X-Gateway-Verified was NOT rejected'
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 403) { Write-Host "   rejected with 403 - OK" } else { Write-Host "   rejected with $code" }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}
Write-Host 'PASS - no cross-tenant leakage detected.'
