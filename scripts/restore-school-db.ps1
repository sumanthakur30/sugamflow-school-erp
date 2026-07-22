# Restore one school database dump created by backup-school-dbs.ps1.
param(
  [Parameter(Mandatory = $true)][string]$DumpFile,
  [Parameter(Mandatory = $true)][string]$Database,
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$PgRestore = 'C:\Program Files\PostgreSQL\17\bin\pg_restore.exe',
  [switch]$Clean
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $DumpFile)) {
  throw "Dump file not found: $DumpFile"
}
if (-not (Test-Path $PgRestore)) {
  $found = Get-Command pg_restore -ErrorAction SilentlyContinue
  if ($found) {
    $PgRestore = $found.Source
  } else {
    throw "pg_restore not found. Install PostgreSQL client tools or pass -PgRestore."
  }
}

$env:PGPASSWORD = $AdminPassword
$args = @(
  "--host=$HostName",
  "--port=$Port",
  "--username=$AdminUser",
  "--dbname=$Database",
  '--no-owner',
  '--no-privileges'
)
if ($Clean) {
  $args += '--clean'
  $args += '--if-exists'
}
$args += $DumpFile

Write-Host "Restoring $DumpFile -> $Database"
& $PgRestore @args
if ($LASTEXITCODE -ne 0) {
  throw "pg_restore failed with exit $LASTEXITCODE"
}
Write-Host 'OK restore complete'
