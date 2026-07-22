# Smoke-test School Provisioner for an org (default NAT-01).
# Requires: gateway :9090, school-settings-service with provision API, valid password.
param(
  [string]$Org = 'NAT-01',
  [string]$Username = 'demo_NAT-01',
  [string]$Password = 'Test@1234',
  [string]$Gateway = 'http://localhost:9090'
)

$ErrorActionPreference = 'Stop'

Write-Host "1) Public shop branding for $Org..."
$shop = Invoke-RestMethod -Uri "$Gateway/api/v1/public/shops/$Org" -Method Get
Write-Host "   shopName=$($shop.shopName)"

Write-Host "2) Login..."
$login = Invoke-RestMethod -Uri "$Gateway/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (@{
  shopId = $Org
  username = $Username
  password = $Password
} | ConvertTo-Json)
if (-not $login.accessToken) { throw 'Login failed - no accessToken' }
$token = $login.accessToken
$headers = @{
  Authorization = "Bearer $token"
  'X-Tenant-Id' = $Org
  'X-Branch-Id' = 'main'
  'X-Session-Id' = '2025-26'
}

Write-Host "3) POST /api/config/provision..."
$prov = Invoke-RestMethod -Uri "$Gateway/api/config/provision" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
  schoolName = $shop.shopName
} | ConvertTo-Json)
Write-Host "   schoolName=$($prov.data.schoolName) alreadyProvisioned=$($prov.data.alreadyProvisioned) planId=$($prov.data.planId)"

Write-Host "4) Theme + branches..."
$theme = Invoke-RestMethod -Uri "$Gateway/api/config/design-studio/theme" -Headers $headers
$branches = Invoke-RestMethod -Uri "$Gateway/api/config/branches" -Headers $headers
Write-Host "   theme.schoolName=$($theme.data.branding.schoolName) provisionedAt=$($theme.data.branding.provisionedAt)"
Write-Host "   branches=$($branches.data.Count) first=$($branches.data[0].branchKey)/$($branches.data[0].name)"

Write-Host "5) Idempotent second provision..."
$prov2 = Invoke-RestMethod -Uri "$Gateway/api/config/provision" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
  schoolName = $shop.shopName
} | ConvertTo-Json)
Write-Host "   alreadyProvisioned=$($prov2.data.alreadyProvisioned) schoolName=$($prov2.data.schoolName)"

if ($theme.data.branding.schoolName -ne $shop.shopName) {
  Write-Warning "Expected theme schoolName='$($shop.shopName)' but got '$($theme.data.branding.schoolName)'"
} else {
  Write-Host "OK - provisioner wired school name from shop registry."
}
