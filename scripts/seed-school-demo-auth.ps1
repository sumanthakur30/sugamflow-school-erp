# Seeds demo-school org in authdb + shopdb for Phase 4 login.
param(
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$shopSql = Join-Path $root 'infra\postgres\02-seed-school-demo-shop.sql'
$authSql = Join-Path $root 'infra\postgres\03-seed-school-demo-auth-account.sql'

function Resolve-Psql {
  param([string]$Preferred)
  if ($Preferred -and (Test-Path $Preferred)) { return $Preferred }
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $found = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
  if ($found) { return $found.FullName }
  return $null
}

$psql = Resolve-Psql -Preferred $PsqlPath
if (-not $psql) { throw 'psql not found' }

$env:PGPASSWORD = $AdminPassword
Write-Host 'Seeding shopdb (demo-school)...'
& $psql -h $HostName -p $Port -U $AdminUser -d shopdb -v ON_ERROR_STOP=1 -f $shopSql
if ($LASTEXITCODE -ne 0) { throw 'shopdb seed failed' }

Write-Host 'Seeding authdb (admin_demo-school)...'
& $psql -h $HostName -p $Port -U $AdminUser -d authdb -v ON_ERROR_STOP=1 -f $authSql
if ($LASTEXITCODE -ne 0) { throw 'authdb seed failed' }

Write-Host ''
Write-Host 'Demo login:'
Write-Host '  Organization: demo-school'
Write-Host '  Username:     admin  (scoped admin_demo-school)'
Write-Host '  Password:     password'
