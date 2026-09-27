# Register a new school owner through the shop gateway.
#
# Creates the shop as ACTIVE, invites the owner, sets the password, then checks login.
# The school login screen scopes the username: type the short name (Nikhil).
# Auth stores it as {Username}_{ShopId} (Nikhil_PPS-01).
#
# Required environment (do not put these in the script or in git):
#   $env:SUGAM_ADMIN_TOKEN        Super Admin access token from https://sugamflow.com
#   $env:SUGAM_SHOP_ADMIN_API_KEY X-Admin-Api-Key (shop-service SHOP_ADMIN_API_KEY)
#
# Example:
#   $env:SUGAM_ADMIN_TOKEN = '<paste bearer token>'
#   $env:SUGAM_SHOP_ADMIN_API_KEY = '<paste admin api key>'
#   .\register-school-owner.ps1 `
#     -TenantId 24 `
#     -ShopId PPS-02 `
#     -ShopName 'Example Public School' `
#     -OwnerName 'Owner Name' `
#     -Email owner@example.com `
#     -Username Owner `
#     -Password 'Owner@2026'
#
# Login after success: https://school.sugamflow.com/
#   Organization ID = ShopId
#   Username        = the short -Username value
#   Password        = the -Password value

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][long]$TenantId,
    [Parameter(Mandatory = $true)][string]$ShopId,
    [Parameter(Mandatory = $true)][string]$ShopName,
    [Parameter(Mandatory = $true)][string]$OwnerName,
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$Phone = '',
    [string]$City = '',
    [string]$State = '',
    [string]$Gateway = $(if ($env:SUGAM_GATEWAY) { $env:SUGAM_GATEWAY } else { 'https://sugamflow.com' })
)

$ErrorActionPreference = 'Stop'

if ($Password.Length -lt 8 -or $Password.Length -gt 72) {
    throw 'Password must be 8 to 72 characters.'
}
if ($Username -match '_') {
    throw 'Username must be the short name. The service adds _{ShopId} itself.'
}

$token = $env:SUGAM_ADMIN_TOKEN
$adminKey = $env:SUGAM_SHOP_ADMIN_API_KEY
if ([string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($adminKey)) {
    throw 'Set SUGAM_ADMIN_TOKEN and SUGAM_SHOP_ADMIN_API_KEY before running.'
}

$Gateway = $Gateway.TrimEnd('/')
$scoped = '{0}_{1}' -f $Username.Trim(), $ShopId.Trim()

$onboard = @{
    shop = @{
        shopId       = $ShopId.Trim()
        tenantId     = $TenantId
        shopName     = $ShopName.Trim()
        ownerName    = $OwnerName.Trim()
        email        = $Email.Trim()
        phone        = $Phone
        businessType = 'SCHOOL'
        status       = 'ACTIVE'
    }
    ownerUsername = $Username.Trim()
    ownerEmail    = $Email.Trim()
    ownerRole     = 'SHOP_OWNER'
} | ConvertTo-Json -Depth 6

Write-Host "Creating shop $ShopId on tenant $TenantId ..."
try {
    $created = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/admin/shops/onboard" `
        -Headers @{
            Authorization      = "Bearer $token"
            'X-Admin-Api-Key'  = $adminKey
        } `
        -ContentType 'application/json' -Body $onboard -TimeoutSec 60
} catch {
    $detail = $_.ErrorDetails.Message
    if ($detail) { Write-Host $detail }
    throw
}

$inviteToken = $created.inviteToken
if ([string]::IsNullOrWhiteSpace($inviteToken)) {
    throw 'Onboard succeeded but no inviteToken was returned. Do not retry until you confirm the shop row.'
}

Write-Host "Shop created. Setting password for $($created.inviteUsername) ..."
$accept = @{ token = $inviteToken; password = $Password } | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/auth/invitations/accept" `
        -ContentType 'application/json' -Body $accept -TimeoutSec 30 | Out-Null
} catch {
    $detail = $_.ErrorDetails.Message
    if ($detail) { Write-Host $detail }
    throw 'Password was not set. The invite expires in about 24 hours; do not create a second shop for the same ShopId.'
}

$loginBody = @{
    shopId   = $ShopId.Trim()
    username = $scoped
    password = $Password
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/auth/login" `
    -ContentType 'application/json' -Body $loginBody -TimeoutSec 30

Write-Host ''
Write-Host 'Owner login is ready.'
Write-Host ("  Organization ID : {0}" -f $login.shopId)
Write-Host ("  Username        : {0}  (typed on the school screen as {1})" -f $login.username, $Username.Trim())
Write-Host ("  Role            : {0}" -f $login.role)
Write-Host ("  Tenant          : {0}" -f $login.tenantId)
Write-Host '  School URL      : https://school.sugamflow.com/'
