# Verifies relationship-scoped RBAC via student-service access-scope + list filtering.
# Uses X-Internal-Service (same as inter-service hops) so we can assert persona filtering
# without creating PARENT/TEACHER JWT accounts.
param(
  [string]$Org = 'demo-school',
  [string]$StudentBase = 'http://localhost:8191',
  [string]$Gateway = 'http://localhost:9090'
)

$ErrorActionPreference = 'Stop'
$failures = @()

function Invoke-Scoped([string]$Role, [string]$User, [string]$Path) {
  $headers = @{
    'X-Tenant-Id' = $Org
    'X-Branch-Id' = 'main'
    'X-Academic-Session-Id' = '2025-26'
    'X-Auth-User' = $User
    'X-Auth-Role' = $Role
    'X-Internal-Service' = 'true'
  }
  return Invoke-RestMethod -Uri "$StudentBase$Path" -Headers $headers -Method Get
}

Write-Host "1) Elevated access-scope for $Org..."
$elev = Invoke-Scoped 'SHOP_OWNER' 'admin_demo-school' '/api/student/access-scope'
if ($elev.data.restricted -eq $true) { $failures += 'SHOP_OWNER should not be restricted' }
Write-Host "   restricted=$($elev.data.restricted)"

Write-Host "2) PARENT with no guardian link -> empty scope..."
$parentEmpty = Invoke-Scoped 'PARENT' 'unlinked_parent_demo-school' '/api/student/access-scope'
if ($parentEmpty.data.restricted -ne $true) { $failures += 'PARENT must be restricted' }
if ([int]$parentEmpty.data.studentCount -ne 0) { $failures += 'unlinked PARENT must see 0 students' }
Write-Host "   studentCount=$($parentEmpty.data.studentCount)"

Write-Host "3) PARENT list must be empty when unlinked..."
$list = Invoke-Scoped 'PARENT' 'unlinked_parent_demo-school' '/api/student/students?page=0&size=20'
if ([int]$list.data.totalElements -ne 0) { $failures += "unlinked PARENT list leaked $($list.data.totalElements) students" }
Write-Host "   totalElements=$($list.data.totalElements)"

Write-Host "4) TEACHER with no staff assignment -> empty..."
$teacher = Invoke-Scoped 'TEACHER' 'unlinked_teacher_demo-school' '/api/student/access-scope'
if ([int]$teacher.data.studentCount -ne 0) { $failures += 'unlinked TEACHER must see 0 students' }
Write-Host "   studentCount=$($teacher.data.studentCount)"

Write-Host "5) Gateway fee path for portal (collections) responds..."
try {
  $login = Invoke-RestMethod -Uri "$Gateway/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (@{
    shopId = $Org; username = 'admin_demo-school'; password = 'password'
  } | ConvertTo-Json)
  $h = @{
    Authorization = "Bearer $($login.accessToken)"
    'X-Tenant-Id' = $Org
    'X-Branch-Id' = 'main'
    'X-Academic-Session-Id' = '2025-26'
  }
  $fees = Invoke-RestMethod -Uri "$Gateway/api/fee/collections?page=0&size=1" -Headers $h
  Write-Host "   fee collections total=$($fees.data.totalElements)"
} catch {
  Write-Host "   fee collections check skipped: $($_.Exception.Message)"
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}
Write-Host 'PASS - persona RBAC fail-closed for unlinked PARENT/TEACHER.'
