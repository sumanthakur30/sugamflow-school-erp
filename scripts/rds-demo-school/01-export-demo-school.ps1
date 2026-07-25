<#
.SYNOPSIS
  Export all local Postgres rows for organization/shop demo-school for RDS import.

.DESCRIPTION
  Writes CSV (+ column lists) under D:\school\backups\demo-school-<stamp>\ for:
    - shopdb.shops (shop_id)
    - authdb.auth_account (shop_id)
    - school_subscription_db.subscription_plan (global)
    - every school_* table that has organization_id = OrganizationId

.EXAMPLE
  .\scripts\rds-demo-school\01-export-demo-school.ps1
  .\scripts\rds-demo-school\01-export-demo-school.ps1 -PhaseAOnly
#>
param(
  [string]$OrganizationId = 'demo-school',
  [string]$HostName = 'localhost',
  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [string]$AdminPassword = 'postgres',
  [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe',
  [string]$OutRoot = '',
  [switch]$PhaseAOnly
)

$ErrorActionPreference = 'Stop'

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

function Invoke-Psql {
  param(
    [string]$Database,
    [string]$Sql,
    [string]$OutFile = ''
  )
  $args = @(
    '-h', $HostName,
    '-p', "$Port",
    '-U', $AdminUser,
    '-d', $Database,
    '-v', 'ON_ERROR_STOP=1',
    '-t', '-A'
  )
  if ($OutFile) {
    $args += @('-o', $OutFile, '-c', $Sql)
  } else {
    $args += @('-c', $Sql)
  }
  $out = & $script:psql @args 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "psql failed on $Database : $out"
  }
  return $out
}

function Get-OrgTables {
  param([string]$Database)
  $sql = @"
SELECT table_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND column_name = 'organization_id'
  AND table_name NOT LIKE 'pg_%'
ORDER BY table_name;
"@
  $raw = Invoke-Psql -Database $Database -Sql $sql
  return @($raw | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() })
}

function Get-TableColumns {
  param([string]$Database, [string]$Table)
  $sql = @"
SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = '$Table'
ORDER BY ordinal_position;
"@
  $raw = Invoke-Psql -Database $Database -Sql $sql
  return @($raw | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() })
}

function Export-FilteredCsv {
  param(
    [string]$Database,
    [string]$Table,
    [string]$WhereSql,
    [string]$DestDir
  )
  New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
  $cols = Get-TableColumns -Database $Database -Table $Table
  if ($cols.Count -eq 0) {
    Write-Warning "  skip $Database.$Table (no columns)"
    return $null
  }
  $colFile = Join-Path $DestDir "$Table.columns.txt"
  $cols -join "`n" | Set-Content -Path $colFile -Encoding UTF8

  $csvFile = Join-Path $DestDir "$Table.csv"
  # Use absolute path with forward slashes for psql \copy
  $csvPg = ($csvFile -replace '\\', '/')
  $colList = ($cols | ForEach-Object { '"' + $_ + '"' }) -join ', '
  $copySql = "\copy (SELECT $colList FROM `"$Table`" WHERE $WhereSql) TO '$csvPg' WITH (FORMAT csv, HEADER true, NULL '')"
  $null = Invoke-Psql -Database $Database -Sql $copySql

  $countSql = "SELECT COUNT(*) FROM `"$Table`" WHERE $WhereSql;"
  $count = [int]((Invoke-Psql -Database $Database -Sql $countSql | Select-Object -First 1).Trim())
  Write-Host ("  {0}.{1}: {2} row(s)" -f $Database, $Table, $count)
  return [ordered]@{
    table = $Table
    columns = $cols
    rows = $count
    csv = "$Table.csv"
    columnsFile = "$Table.columns.txt"
  }
}

$script:psql = Resolve-Psql -Preferred $PsqlPath
if (-not $script:psql) { throw 'psql not found. Install PostgreSQL client tools or pass -PsqlPath.' }

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutRoot) {
  $OutRoot = Join-Path $repoRoot 'backups'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $OutRoot ("demo-school-{0}" -f $stamp)
if ($OrganizationId -ne 'demo-school') {
  $outDir = Join-Path $OutRoot ("{0}-{1}" -f $OrganizationId, $stamp)
}
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$env:PGPASSWORD = $AdminPassword
$orgEsc = $OrganizationId.Replace("'", "''")

$phaseA = @(
  'school_settings_db',
  'school_subscription_db',
  'school_admission_db',
  'school_fee_db',
  'school_student_db',
  'school_staff_db',
  'school_academic_db'
)

$allSchool = @(
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

$schoolDbs = if ($PhaseAOnly) { $phaseA } else { $allSchool }

# Preferred child-after-parent order (remaining tables appended alphabetically).
$tableOrder = @{
  'school_subscription_db' = @('tenant_subscription')
  'school_academic_db'     = @('academic_class', 'class_section', 'subject', 'timetable_period', 'timetable_room', 'teaching_assignment', 'timetable_slot')
  'school_student_db'      = @('student_record', 'lifecycle_definition', 'lifecycle_event', 'import_job', 'import_job_row', 'student_document', 'student_attachment', 'student_field_audit')
  'school_settings_db'     = @('design_theme', 'module_settings', 'menu_config', 'localization_settings', 'ui_screen_config', 'ai_settings', 'role_dashboard_config', 'org_branch', 'offline_sync_batch', 'offline_sync_item')
  'school_attendance_db'   = @('attendance_record', 'attendance_device', 'attendance_session', 'attendance_device_event', 'attendance_mark', 'attendance_alert_outbox')
  'school_exam_db'         = @('exam_definition', 'exam_record', 'lms_homework', 'exam_mark', 'lms_homework_submission')
  'school_fee_db'          = @('finance_definition', 'fee_collection', 'finance_transaction', 'expense_record', 'income_record', 'fee_due_reminder_outbox')
  'school_library_db'      = @('ops_definition', 'library_record', 'library_book', 'library_circulation')
  'school_hostel_db'       = @('ops_definition', 'hostel_record', 'hostel_bed', 'hostel_occupancy')
  'school_transport_db'    = @('ops_definition', 'transport_record', 'transport_route', 'transport_assignment')
  'school_payroll_db'      = @('ops_definition', 'payroll_record')
  'school_notif_cfg_db'    = @('notification_template', 'comms_announcement', 'comms_alert_outbox')
}

$manifest = [ordered]@{
  createdAt = (Get-Date).ToString('o')
  organizationId = $OrganizationId
  sourceHost = $HostName
  sourcePort = $Port
  phaseAOnly = [bool]$PhaseAOnly
  shop = $null
  auth = $null
  subscriptionPlans = $null
  databases = [ordered]@{}
}

Write-Host "Exporting organization/shop '$OrganizationId' -> $outDir"
Write-Host ''

# --- shopdb ---
Write-Host '=== shopdb ==='
$shopDir = Join-Path $outDir 'shopdb'
$shopMeta = Export-FilteredCsv -Database 'shopdb' -Table 'shops' -WhereSql "shop_id = '$orgEsc'" -DestDir $shopDir
$manifest.shop = $shopMeta

# --- authdb ---
Write-Host '=== authdb ==='
$authDir = Join-Path $outDir 'authdb'
$authMeta = Export-FilteredCsv -Database 'authdb' -Table 'auth_account' -WhereSql "shop_id = '$orgEsc'" -DestDir $authDir
$manifest.auth = $authMeta

# --- global plans (needed before tenant_subscription) ---
if ($schoolDbs -contains 'school_subscription_db') {
  Write-Host '=== school_subscription_db (subscription_plan global) ==='
  $subDir = Join-Path $outDir 'school_subscription_db'
  $planMeta = Export-FilteredCsv -Database 'school_subscription_db' -Table 'subscription_plan' -WhereSql 'TRUE' -DestDir $subDir
  $manifest.subscriptionPlans = $planMeta
}

# --- school_* tenant tables ---
foreach ($db in $schoolDbs) {
  Write-Host "=== $db ==="
  $dbDir = Join-Path $outDir $db
  New-Item -ItemType Directory -Force -Path $dbDir | Out-Null

  $discovered = @(Get-OrgTables -Database $db)
  $preferred = @()
  if ($tableOrder.ContainsKey($db)) {
    foreach ($t in $tableOrder[$db]) {
      if ($discovered -contains $t) { $preferred += $t }
    }
  }
  $rest = @($discovered | Where-Object { $preferred -notcontains $_ } | Sort-Object)
  $ordered = @($preferred + $rest)

  $tableMetas = @()
  foreach ($table in $ordered) {
    $meta = Export-FilteredCsv -Database $db -Table $table -WhereSql "organization_id = '$orgEsc'" -DestDir $dbDir
    if ($meta) { $tableMetas += $meta }
  }
  $manifest.databases[$db] = @{
    tables = $tableMetas
  }
}

$manifestPath = Join-Path $outDir 'manifest.json'
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host ''
Write-Host "Export complete."
Write-Host "Folder: $outDir"
Write-Host "Next: .\scripts\rds-demo-school\02-import-demo-school-to-rds.ps1 -ExportDir '$outDir' -RdsHost <endpoint> -AdminPassword <pwd> -Replace"
