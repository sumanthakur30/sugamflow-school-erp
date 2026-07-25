# Quick wrapper: export local demo-school then print import command for RDS.
param(
  [string]$OrganizationId = 'demo-school',
  [switch]$PhaseAOnly,
  [string]$RdsHost = '',
  [string]$AdminPassword = ''
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

$exportArgs = @{
  OrganizationId = $OrganizationId
}
if ($PhaseAOnly) { $exportArgs.PhaseAOnly = $true }

Write-Host '=== 1) Export from local Postgres ==='
& (Join-Path $here '01-export-demo-school.ps1') @exportArgs
if ($LASTEXITCODE -ne 0) { throw 'export failed' }

$latest = Get-ChildItem (Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'backups') -Directory |
  Where-Object { $_.Name -like "$OrganizationId-*" -or $_.Name -like 'demo-school-*' } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $latest) {
  throw 'Could not locate export folder under backups\'
}

Write-Host ''
Write-Host '=== Export folder ==='
Write-Host $latest.FullName
Write-Host ''
Write-Host '=== 2) Import to RDS (run when ready) ==='
if ($RdsHost -and $AdminPassword) {
  & (Join-Path $here '02-import-demo-school-to-rds.ps1') `
    -ExportDir $latest.FullName `
    -RdsHost $RdsHost `
    -AdminPassword $AdminPassword `
    -OrganizationId $OrganizationId `
    -Replace
} else {
  Write-Host @"
.\scripts\rds-demo-school\02-import-demo-school-to-rds.ps1 ``
  -ExportDir '$($latest.FullName)' ``
  -RdsHost 'YOUR_RDS_ENDPOINT.eu-north-1.rds.amazonaws.com' ``
  -AdminUser 'postgres' ``
  -AdminPassword 'YOUR_MASTER_PASSWORD' ``
  -Replace
"@
}
