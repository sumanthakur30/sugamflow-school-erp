#Requires -Version 5.1
<#
.SYNOPSIS
  Create school_cms_db + school_website_db and matching LOGIN roles.

.DESCRIPTION
  Aligns with application.properties:

    SCHOOL_CMS_DB_*     → user school_cms     / db school_cms_db
    SCHOOL_WEBSITE_DB_* → user school_website / db school_website_db

  Idempotent: skips roles/DBs that already exist.

.EXAMPLE
  # Local (defaults from connection.env / localhost)
  .\infra\postgres\create-website-cms-dbs.ps1

.EXAMPLE
  # RDS / EC2
  .\infra\postgres\create-website-cms-dbs.ps1 `
    -HostName your-rds.eu-north-1.rds.amazonaws.com `
    -Port 5432 `
    -AdminUser postgres `
    -AdminPassword 'YOUR_MASTER_PASSWORD' `
    -CmsPassword 'STRONG_CMS_PASS' `
    -WebsitePassword 'STRONG_WEBSITE_PASS' `
    -SslMode require
#>
[CmdletBinding()]
param(
  [string] $HostName = 'localhost',
  [int] $Port = 5432,
  [string] $AdminUser = 'postgres',
  [string] $AdminPassword = 'postgres',
  [string] $AdminDb = 'postgres',
  [string] $CmsUser = 'school_cms',
  [string] $CmsPassword = 'school_cms',
  [string] $CmsDb = 'school_cms_db',
  [string] $WebsiteUser = 'school_website',
  [string] $WebsitePassword = 'school_website',
  [string] $WebsiteDb = 'school_website_db',
  [ValidateSet('disable', 'allow', 'prefer', 'require', 'verify-ca', 'verify-full')]
  [string] $SslMode = 'prefer',
  [string] $PsqlPath = ''
)

$ErrorActionPreference = 'Stop'

function Find-Psql {
  param([string] $Explicit)
  if ($Explicit -and (Test-Path -LiteralPath $Explicit)) { return $Explicit }
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidates = @(
    'C:\Program Files\PostgreSQL\17\bin\psql.exe',
    'C:\Program Files\PostgreSQL\16\bin\psql.exe',
    'C:\Program Files\PostgreSQL\15\bin\psql.exe'
  )
  foreach ($c in $candidates) {
    if (Test-Path -LiteralPath $c) { return $c }
  }
  throw 'psql not found. Install PostgreSQL client or pass -PsqlPath.'
}

function Invoke-PsqlSql {
  param(
    [string] $Psql,
    [string] $Sql,
    [string] $Database = $AdminDb
  )
  $env:PGPASSWORD = $AdminPassword
  $env:PGSSLMODE = $SslMode
  $args = @(
    '-h', $HostName,
    '-p', "$Port",
    '-U', $AdminUser,
    '-d', $Database,
    '-v', 'ON_ERROR_STOP=1',
    '-c', $Sql
  )
  $out = & $Psql @args 2>&1
  $code = $LASTEXITCODE
  if ($code -ne 0) {
    throw "psql failed ($code): $out"
  }
  return $out
}

function Test-RoleExists {
  param([string] $Psql, [string] $Role)
  $env:PGPASSWORD = $AdminPassword
  $env:PGSSLMODE = $SslMode
  $q = "SELECT 1 FROM pg_roles WHERE rolname = '$Role';"
  $r = & $Psql -h $HostName -p $Port -U $AdminUser -d $AdminDb -tAc $q 2>$null
  return (($r | Out-String).Trim() -eq '1')
}

function Test-DbExists {
  param([string] $Psql, [string] $Db)
  $env:PGPASSWORD = $AdminPassword
  $env:PGSSLMODE = $SslMode
  $q = "SELECT 1 FROM pg_database WHERE datname = '$Db';"
  $r = & $Psql -h $HostName -p $Port -U $AdminUser -d $AdminDb -tAc $q 2>$null
  return (($r | Out-String).Trim() -eq '1')
}

$psql = Find-Psql -Explicit $PsqlPath
Write-Host "Using psql: $psql"
Write-Host "Target: $HostName`:$Port  admin=$AdminUser  sslmode=$SslMode"
Write-Host ''

# --- Roles ---
$roles = @(
  @{ Name = $CmsUser; Password = $CmsPassword },
  @{ Name = $WebsiteUser; Password = $WebsitePassword }
)

foreach ($role in $roles) {
  if (Test-RoleExists -Psql $psql -Role $role.Name) {
    Write-Host "SKIP role exists: $($role.Name)"
  } else {
    $pwdEsc = $role.Password.Replace("'", "''")
    Invoke-PsqlSql -Psql $psql -Sql "CREATE USER $($role.Name) WITH PASSWORD '$pwdEsc';" | Out-Null
    Write-Host "CREATED role: $($role.Name)"
  }
  # RDS: master must be granted the role to CREATE DATABASE ... OWNER
  try {
    Invoke-PsqlSql -Psql $psql -Sql "GRANT $($role.Name) TO $AdminUser;" | Out-Null
    Write-Host "GRANTED $($role.Name) TO $AdminUser"
  } catch {
    Write-Host "WARN GRANT $($role.Name) TO $AdminUser : $_"
  }
}

# --- Databases ---
$dbs = @(
  @{ Db = $CmsDb; Owner = $CmsUser },
  @{ Db = $WebsiteDb; Owner = $WebsiteUser }
)

foreach ($d in $dbs) {
  if (Test-DbExists -Psql $psql -Db $d.Db) {
    Write-Host "SKIP DB exists: $($d.Db)"
  } else {
    Invoke-PsqlSql -Psql $psql -Sql "CREATE DATABASE $($d.Db) OWNER $($d.Owner);" | Out-Null
    Write-Host "CREATED DB: $($d.Db) OWNER $($d.Owner)"
  }
}

Write-Host ''
Write-Host 'Verify (should list website + cms):'
$env:PGPASSWORD = $AdminPassword
$env:PGSSLMODE = $SslMode
& $psql -h $HostName -p $Port -U $AdminUser -d $AdminDb -c `
  "SELECT datname, pg_catalog.pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname IN ('$CmsDb','$WebsiteDb') ORDER BY 1;"

Write-Host ''
Write-Host 'Env snippet for .env.school.production / local:'
Write-Host "SCHOOL_CMS_DB_URL=jdbc:postgresql://${HostName}:${Port}/${CmsDb}$(if ($SslMode -eq 'require') { '?sslmode=require' } else { '' })"
Write-Host "SCHOOL_CMS_DB_USERNAME=$CmsUser"
Write-Host "SCHOOL_CMS_DB_PASSWORD=<set to match>"
Write-Host "SCHOOL_WEBSITE_DB_URL=jdbc:postgresql://${HostName}:${Port}/${WebsiteDb}$(if ($SslMode -eq 'require') { '?sslmode=require' } else { '' })"
Write-Host "SCHOOL_WEBSITE_DB_USERNAME=$WebsiteUser"
Write-Host "SCHOOL_WEBSITE_DB_PASSWORD=<set to match>"
Write-Host 'Done.'
