# Seeds 5 SCHOOL orgs (load-school-01..05) for concurrent multi-school load tests.
# Login for each: org=load-school-0N, username=admin (scoped admin_load-school-0N), password=password
param(
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$shopSql = Join-Path $root 'infra\postgres\04a-seed-multi-school-load-shops.sql'
$authSql = Join-Path $root 'infra\postgres\04b-seed-multi-school-load-auth.sql'
$demoSql = Join-Path $root 'infra\postgres\02-seed-school-demo-shop.sql'
$demoAuthSql = Join-Path $root 'infra\postgres\03-seed-school-demo-auth-account.sql'

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

Write-Host 'Ensuring demo-school exists...'
& $psql -h $HostName -p $Port -U $AdminUser -d shopdb -v ON_ERROR_STOP=1 -f $demoSql
if ($LASTEXITCODE -ne 0) { throw 'demo shop seed failed' }
& $psql -h $HostName -p $Port -U $AdminUser -d authdb -v ON_ERROR_STOP=1 -f $demoAuthSql
if ($LASTEXITCODE -ne 0) { throw 'demo auth seed failed' }

Write-Host 'Seeding load-school-01..05 shops...'
& $psql -h $HostName -p $Port -U $AdminUser -d shopdb -v ON_ERROR_STOP=1 -f $shopSql
if ($LASTEXITCODE -ne 0) { throw 'load shop seed failed' }

Write-Host 'Seeding load-school-01..05 auth accounts...'
& $psql -h $HostName -p $Port -U $AdminUser -d authdb -v ON_ERROR_STOP=1 -f $authSql
if ($LASTEXITCODE -ne 0) { throw 'load auth seed failed' }

Write-Host ''
Write-Host 'Load-test schools ready (password = password):'
Write-Host '  demo-school          / admin_demo-school'
1..5 | ForEach-Object {
  $id = 'load-school-{0:d2}' -f $_
  Write-Host ("  {0}       / admin_{0}" -f $id)
}
Write-Host ''
Write-Host 'Next: k6 run D:\school\scripts\load\multi-school-load.js -e QUICK=1'
