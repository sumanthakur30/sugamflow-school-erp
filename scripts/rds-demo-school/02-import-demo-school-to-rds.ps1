<#
.SYNOPSIS
  Import a demo-school export folder into RDS (tenant-scoped merge).

.DESCRIPTION
  Reads CSV produced by 01-export-demo-school.ps1 and loads into:
    shopdb, authdb, and school_* databases on the RDS host.
  With -Replace, deletes existing rows for that organization/shop first.

.EXAMPLE
  .\scripts\rds-demo-school\02-import-demo-school-to-rds.ps1 `
    -ExportDir 'D:\school\backups\demo-school-20260725-091500' `
    -RdsHost 'sugamflow-postgres.xxxxx.eu-north-1.rds.amazonaws.com' `
    -AdminUser 'postgres' `
    -AdminPassword '***' `
    -Replace
#>
param(
  [Parameter(Mandatory = $true)]
  [string]$ExportDir,

  [Parameter(Mandatory = $true)]
  [string]$RdsHost,

  [int]$Port = 5432,
  [string]$AdminUser = 'postgres',
  [Parameter(Mandatory = $true)]
  [string]$AdminPassword,
  [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe',
  [string]$SslMode = 'require',
  [string]$OrganizationId = '',
  [switch]$Replace,
  [switch]$SkipShopAuth,
  [switch]$WhatIf
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
    [string]$File = ''
  )
  $args = @(
    '-h', $RdsHost,
    '-p', "$Port",
    '-U', $AdminUser,
    '-d', $Database,
    '-v', 'ON_ERROR_STOP=1'
  )
  if ($File) {
    $args += @('-f', $File)
  } else {
    $args += @('-c', $Sql)
  }
  $out = & $script:psql @args 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "psql failed on $Database : $out"
  }
  return $out
}

function Read-Columns {
  param([string]$ColumnsFile)
  return @(
    Get-Content -Path $ColumnsFile |
      Where-Object { $_ -and $_.Trim() } |
      ForEach-Object { $_.Trim() }
  )
}

function Import-CsvTable {
  param(
    [string]$Database,
    [string]$Table,
    [string]$CsvPath,
    [string]$ColumnsFile,
    [string]$DeleteWhere = '',
    [switch]$UpsertByUsername
  )

  if (-not (Test-Path $CsvPath)) {
    Write-Warning "  missing $CsvPath"
    return
  }
  $cols = Read-Columns -ColumnsFile $ColumnsFile
  if ($cols.Count -eq 0) { throw "No columns for $Database.$Table" }

  $csvPg = ($CsvPath -replace '\\', '/')
  $colList = ($cols | ForEach-Object { '"' + $_ + '"' }) -join ', '
  $staging = ("_stg_{0}_{1}" -f $Table, ([guid]::NewGuid().ToString('N').Substring(0, 8)))

  $sql = New-Object System.Text.StringBuilder
  [void]$sql.AppendLine('BEGIN;')
  [void]$sql.AppendLine("CREATE TEMP TABLE `"$staging`" (LIKE `"$Table`" INCLUDING DEFAULTS);")
  [void]$sql.AppendLine("\copy `"$staging`" ($colList) FROM '$csvPg' WITH (FORMAT csv, HEADER true, NULL '')")

  if ($Replace -and $DeleteWhere) {
    [void]$sql.AppendLine("DELETE FROM `"$Table`" WHERE $DeleteWhere;")
  }

  if ($UpsertByUsername) {
    # auth_account: keep target identity ids; refresh credentials/role by username
    $updateCols = @($cols | Where-Object { $_ -notin @('id', 'username') })
    $setList = ($updateCols | ForEach-Object { "`"$_`" = EXCLUDED.`"$_`"" }) -join ",`n      "
    $insertCols = ($cols | Where-Object { $_ -ne 'id' })
    $insertList = ($insertCols | ForEach-Object { '"' + $_ + '"' }) -join ', '
    $selectList = ($insertCols | ForEach-Object { 's."' + $_ + '"' }) -join ', '
    [void]$sql.AppendLine(@"
INSERT INTO `"$Table`" ($insertList)
SELECT $selectList FROM `"$staging`" s
ON CONFLICT (username) DO UPDATE SET
      $setList;
"@)
  } else {
    # Keep exported PKs. Use OVERRIDING SYSTEM VALUE only when table has identity columns.
    [void]$sql.AppendLine(@"
DO `$`$
DECLARE
  has_identity boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = '$Table' AND is_identity = 'YES'
  ) INTO has_identity;

  IF has_identity THEN
    EXECUTE 'INSERT INTO `"$Table`" ($colList) OVERRIDING SYSTEM VALUE SELECT $colList FROM `"$staging`" ON CONFLICT DO NOTHING';
  ELSE
    EXECUTE 'INSERT INTO `"$Table`" ($colList) SELECT $colList FROM `"$staging`" ON CONFLICT DO NOTHING';
  END IF;
END`$`$;
"@)
  }

  # Bump identity sequences when an id column exists
  if ($cols -contains 'id') {
    [void]$sql.AppendLine(@"
DO `$`$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = '$Table' AND column_name = 'id'
      AND is_identity = 'YES'
  ) THEN
    EXECUTE format(
      'SELECT setval(pg_get_serial_sequence(%L, ''id''), COALESCE((SELECT MAX(id) FROM %I), 1), true)',
      '$Table', '$Table'
    );
  END IF;
END`$`$;
"@)
  }

  [void]$sql.AppendLine('COMMIT;')

  $tmpSql = Join-Path $env:TEMP ("import-{0}-{1}.sql" -f $Database, $Table)
  # psql -f needs meta-commands like \copy on their own lines; write UTF8 no BOM
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  [System.IO.File]::WriteAllText($tmpSql, $sql.ToString(), $utf8NoBom)

  if ($WhatIf) {
    Write-Host "  WHATIF $Database.$Table <- $CsvPath"
    return
  }

  Write-Host "  $Database.$Table <- $(Split-Path $CsvPath -Leaf)"
  $null = Invoke-Psql -Database $Database -File $tmpSql
}

$script:psql = Resolve-Psql -Preferred $PsqlPath
if (-not $script:psql) { throw 'psql not found. Install PostgreSQL client tools or pass -PsqlPath.' }

if (-not (Test-Path $ExportDir)) {
  throw "ExportDir not found: $ExportDir"
}

$manifestPath = Join-Path $ExportDir 'manifest.json'
if (-not (Test-Path $manifestPath)) {
  throw "manifest.json missing in $ExportDir - run 01-export-demo-school.ps1 first"
}
$manifest = Get-Content -Raw -Path $manifestPath | ConvertFrom-Json
if (-not $OrganizationId) {
  $OrganizationId = [string]$manifest.organizationId
}
if (-not $OrganizationId) { $OrganizationId = 'demo-school' }
$orgEsc = $OrganizationId.Replace("'", "''")

$env:PGPASSWORD = $AdminPassword
$env:PGSSLMODE = $SslMode

Write-Host "Importing organization/shop '$OrganizationId'"
Write-Host "  from: $ExportDir"
Write-Host "  into: ${RdsHost}:${Port} (sslmode=$SslMode)"
Write-Host "  replace existing tenant rows: $Replace"
Write-Host ''

# Connectivity probe
$null = Invoke-Psql -Database 'postgres' -Sql 'SELECT 1;'
Write-Host 'RDS connection OK'
Write-Host ''

# Preferred import order inside each DB (parents first). Unknown tables go last A–Z.
$tableOrder = @{
  'school_subscription_db' = @('subscription_plan', 'tenant_subscription')
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

# --- shop + auth (shared SugamFlow DBs) ---
if (-not $SkipShopAuth) {
  Write-Host '=== shopdb.shops ==='
  $shopCsv = Join-Path $ExportDir 'shopdb\shops.csv'
  $shopCols = Join-Path $ExportDir 'shopdb\shops.columns.txt'
  Import-CsvTable -Database 'shopdb' -Table 'shops' -CsvPath $shopCsv -ColumnsFile $shopCols `
    -DeleteWhere "shop_id = '$orgEsc'"

  Write-Host '=== authdb.auth_account ==='
  $authCsv = Join-Path $ExportDir 'authdb\auth_account.csv'
  $authCols = Join-Path $ExportDir 'authdb\auth_account.columns.txt'
  Import-CsvTable -Database 'authdb' -Table 'auth_account' -CsvPath $authCsv -ColumnsFile $authCols `
    -DeleteWhere "shop_id = '$orgEsc'" -UpsertByUsername
}

# --- subscription plans first (global) ---
$planCsv = Join-Path $ExportDir 'school_subscription_db\subscription_plan.csv'
$planCols = Join-Path $ExportDir 'school_subscription_db\subscription_plan.columns.txt'
if (Test-Path $planCsv) {
  Write-Host '=== school_subscription_db.subscription_plan ==='
  Import-CsvTable -Database 'school_subscription_db' -Table 'subscription_plan' `
    -CsvPath $planCsv -ColumnsFile $planCols
}

# --- each school DB folder ---
$dbDirs = Get-ChildItem -Path $ExportDir -Directory | Where-Object {
  $_.Name -like 'school_*_db'
} | Sort-Object Name

foreach ($dir in $dbDirs) {
  $db = $dir.Name
  Write-Host "=== $db ==="

  $csvFiles = @(Get-ChildItem -Path $dir.FullName -Filter '*.csv')
  $tables = @($csvFiles | ForEach-Object { $_.BaseName })
  # subscription_plan already handled
  $tables = @($tables | Where-Object { $_ -ne 'subscription_plan' })

  $preferred = @()
  if ($tableOrder.ContainsKey($db)) {
    foreach ($t in $tableOrder[$db]) {
      if ($tables -contains $t) { $preferred += $t }
    }
  }
  $rest = @($tables | Where-Object { $preferred -notcontains $_ } | Sort-Object)
  $ordered = @($preferred + $rest)

  foreach ($table in $ordered) {
    $csv = Join-Path $dir.FullName "$table.csv"
    $cols = Join-Path $dir.FullName "$table.columns.txt"
    if (-not (Test-Path $cols)) {
      Write-Warning "  skip $table (missing columns file)"
      continue
    }
    Import-CsvTable -Database $db -Table $table -CsvPath $csv -ColumnsFile $cols `
      -DeleteWhere "organization_id = '$orgEsc'"
  }
}

Write-Host ''
Write-Host 'Import complete.'
Write-Host "Login: Organization=$OrganizationId  username=admin  (scoped admin_$OrganizationId)"
Write-Host 'Password: same hash as local export (typically password for seeded demo).'
