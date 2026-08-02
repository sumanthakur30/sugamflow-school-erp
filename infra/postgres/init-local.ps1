# Create school databases on the SAME PostgreSQL used by SugamFlow
# (host install: postgresql-x64-17 @ localhost:5432).
param(
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$AdminDb = 'postgres',
  [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
)

$ErrorActionPreference = 'Stop'
$sqlFile = Join-Path $PSScriptRoot '01-create-school-databases-simple.sql'
if (-not (Test-Path $sqlFile)) {
  throw "SQL file not found: $sqlFile"
}

function Resolve-Psql {
  param([string]$Preferred)
  if ($Preferred -and (Test-Path $Preferred)) { return $Preferred }
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $found = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1
  if ($found) { return $found.FullName }
  return $null
}

function Invoke-PsqlFile {
  param([string]$Psql, [string]$HostName, [int]$Port, [string]$User, [string]$Db, [string]$File)
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $out = & $Psql -h $HostName -p $Port -U $User -d $Db -v ON_ERROR_STOP=0 -f $File 2>&1
    $code = $LASTEXITCODE
    return [pscustomobject]@{ ExitCode = $code; Output = ($out | Out-String) }
  } finally {
    $ErrorActionPreference = $prev
  }
}

$psql = Resolve-Psql -Preferred $PsqlPath
if (-not $psql) {
  throw "psql not found. Expected C:\Program Files\PostgreSQL\17\bin\psql.exe"
}

$env:PGPASSWORD = $AdminPassword
Write-Host "Using SugamFlow host Postgres: $psql"
Write-Host "Target: ${HostName}:${Port}/${AdminDb} as $AdminUser"

$result = Invoke-PsqlFile -Psql $psql -HostName $HostName -Port $Port -User $AdminUser -Db $AdminDb -File $sqlFile
Write-Host $result.Output

# Ensure first role exists even if a BOM/prior partial run skipped it
$ensure = @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_settings') THEN
    CREATE USER school_settings WITH PASSWORD 'school_settings';
  END IF;
END
`$`$;
SELECT 'CREATE DATABASE school_settings_db OWNER school_settings'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_settings_db')\gexec
"@

$tmp = Join-Path $env:TEMP 'school-ensure-settings.sql'
# Use DO-only ensure + separate create for settings db via simple SQL without gexec
$ensureSimple = @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_settings') THEN
    CREATE USER school_settings WITH PASSWORD 'school_settings';
  END IF;
END
`$`$;
"@
Set-Content -Path $tmp -Value $ensureSimple -Encoding ascii
$ensureResult = Invoke-PsqlFile -Psql $psql -HostName $HostName -Port $Port -User $AdminUser -Db $AdminDb -File $tmp
Write-Host $ensureResult.Output

# Ensure admission + fee roles exist
$ensureDomain = @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_admission') THEN
    CREATE USER school_admission WITH PASSWORD 'school_admission';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_fee') THEN
    CREATE USER school_fee WITH PASSWORD 'school_fee';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_student') THEN
    CREATE USER school_student WITH PASSWORD 'school_student';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_attendance') THEN
    CREATE USER school_attendance WITH PASSWORD 'school_attendance';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_exam') THEN
    CREATE USER school_exam WITH PASSWORD 'school_exam';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_library') THEN
    CREATE USER school_library WITH PASSWORD 'school_library';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_hostel') THEN
    CREATE USER school_hostel WITH PASSWORD 'school_hostel';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_transport') THEN
    CREATE USER school_transport WITH PASSWORD 'school_transport';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_payroll') THEN
    CREATE USER school_payroll WITH PASSWORD 'school_payroll';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_staff') THEN
    CREATE USER school_staff WITH PASSWORD 'school_staff';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_academic') THEN
    CREATE USER school_academic WITH PASSWORD 'school_academic';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_website') THEN
    CREATE USER school_website WITH PASSWORD 'school_website';
  END IF;
END
`$`$;
"@
$tmpAdm = Join-Path $env:TEMP 'school-ensure-domain.sql'
Set-Content -Path $tmpAdm -Value $ensureDomain -Encoding ascii
Invoke-PsqlFile -Psql $psql -HostName $HostName -Port $Port -User $AdminUser -Db $AdminDb -File $tmpAdm | Out-Null

# Create any missing school_* databases
$dbs = @(
  @{ Db = 'school_settings_db'; Owner = 'school_settings' },
  @{ Db = 'school_subscription_db'; Owner = 'school_subscription' },
  @{ Db = 'school_forms_db'; Owner = 'school_forms' },
  @{ Db = 'school_workflow_db'; Owner = 'school_workflow' },
  @{ Db = 'school_rules_db'; Owner = 'school_rules' },
  @{ Db = 'school_reports_db'; Owner = 'school_reports' },
  @{ Db = 'school_notif_cfg_db'; Owner = 'school_notif_cfg' },
  @{ Db = 'school_audit_db'; Owner = 'school_audit' },
  @{ Db = 'school_admission_db'; Owner = 'school_admission' },
  @{ Db = 'school_fee_db'; Owner = 'school_fee' },
  @{ Db = 'school_attendance_db'; Owner = 'school_attendance' },
  @{ Db = 'school_exam_db'; Owner = 'school_exam' },
  @{ Db = 'school_student_db'; Owner = 'school_student' },
  @{ Db = 'school_library_db'; Owner = 'school_library' },
  @{ Db = 'school_hostel_db'; Owner = 'school_hostel' },
  @{ Db = 'school_transport_db'; Owner = 'school_transport' },
  @{ Db = 'school_payroll_db'; Owner = 'school_payroll' },
  @{ Db = 'school_staff_db'; Owner = 'school_staff' },
  @{ Db = 'school_academic_db'; Owner = 'school_academic' },
  @{ Db = 'school_website_db'; Owner = 'school_website' }
)

foreach ($d in $dbs) {
  $checkSql = "SELECT 1 FROM pg_database WHERE datname = '$($d.Db)';"
  $checkTmp = Join-Path $env:TEMP 'school-db-check.sql'
  Set-Content -Path $checkTmp -Value $checkSql -Encoding ascii
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $exists = & $psql -h $HostName -p $Port -U $AdminUser -d $AdminDb -tAc $checkSql 2>$null
  $ErrorActionPreference = $prev
  if (($exists | Out-String).Trim() -eq '1') {
    Write-Host "SKIP DB exists: $($d.Db)"
    continue
  }
  $createSql = "CREATE DATABASE $($d.Db) OWNER $($d.Owner);"
  $createTmp = Join-Path $env:TEMP 'school-db-create.sql'
  Set-Content -Path $createTmp -Value $createSql -Encoding ascii
  $createResult = Invoke-PsqlFile -Psql $psql -HostName $HostName -Port $Port -User $AdminUser -Db $AdminDb -File $createTmp
  if ($createResult.ExitCode -eq 0) {
    Write-Host "CREATED $($d.Db)"
  } else {
    Write-Host "FAIL $($d.Db): $($createResult.Output)"
  }
}

Write-Host ''
Write-Host 'School databases on SugamFlow Postgres:'
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $psql -h $HostName -p $Port -U $AdminUser -d $AdminDb -c "SELECT datname FROM pg_database WHERE datname LIKE 'school_%' ORDER BY 1;"
$ErrorActionPreference = $prev
Write-Host 'Done.'
