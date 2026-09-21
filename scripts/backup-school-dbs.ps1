# Backup all school PostgreSQL databases with pg_dump.
# Default output: D:\school\backups\yyyyMMdd-HHmmss\
param(
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$PgDump = 'C:\Program Files\PostgreSQL\17\bin\pg_dump.exe',
  [string]$OutRoot = ''
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $PgDump)) {
  $found = Get-Command pg_dump -ErrorAction SilentlyContinue
  if ($found) {
    $PgDump = $found.Source
  } else {
    throw "pg_dump not found. Install PostgreSQL client tools or pass -PgDump."
  }
}

if (-not $OutRoot) {
  $OutRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'backups'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $OutRoot $stamp
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$databases = @(
  'school_settings_db',
  'school_subscription_db',
  'school_forms_db',
  'school_workflow_db',
  'school_rules_db',
  'school_reports_db',
  'school_notif_cfg_db',
  'school_audit_db',
  'school_admission_db',
  'school_fee_db',
  'school_student_db',
  'school_attendance_db',
  'school_exam_db',
  'school_library_db',
  'school_hostel_db',
  'school_transport_db',
  'school_payroll_db',
  'school_staff_db',
  'school_academic_db'
)

$env:PGPASSWORD = $AdminPassword
$ok = 0
$failed = @()

Write-Host "Backing up $($databases.Count) databases to $outDir"
foreach ($db in $databases) {
  $file = Join-Path $outDir "$db.dump"
  Write-Host "  $db ..."
  try {
    & $PgDump `
      --host=$HostName `
      --port=$Port `
      --username=$AdminUser `
      --format=custom `
      --file=$file `
      --dbname=$db
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $file)) {
      throw "pg_dump exit $LASTEXITCODE"
    }
    $ok++
  } catch {
    Write-Warning "  FAIL $db : $($_.Exception.Message)"
    $failed += $db
  }
}

$manifest = [ordered]@{
  createdAt = (Get-Date).ToString('o')
  host = $HostName
  port = $Port
  ok = $ok
  failed = $failed
  files = @(Get-ChildItem $outDir -Filter *.dump | ForEach-Object {
      @{ name = $_.Name; bytes = $_.Length }
    })
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $outDir 'manifest.json') -Encoding UTF8

Write-Host ''
Write-Host "Backup complete: $ok ok, $($failed.Count) failed"
Write-Host "Folder: $outDir"
if ($failed.Count -gt 0) {
  exit 1
}
