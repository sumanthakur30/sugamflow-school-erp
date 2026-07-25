<#
.SYNOPSIS
  Build plain .sql files from a demo-school CSV export for DBeaver / any SQL client.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\rds-demo-school\03-generate-sql-for-dbeaver.ps1 -ExportDir D:\school\backups\demo-school-20260725-101932
#>
param(
  [Parameter(Mandatory = $true)]
  [string]$ExportDir,
  [string]$OrganizationId = 'demo-school',
  [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $ExportDir)) { throw "ExportDir not found: $ExportDir" }
if (-not $OutDir) { $OutDir = Join-Path $ExportDir 'sql-dbeaver' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Escape-SqlLiteral {
  param([string]$Value)
  if ($null -eq $Value) { return 'NULL' }
  return ("'" + ($Value.Replace("'", "''")) + "'")
}

function Convert-CsvValueToSql {
  param(
    [string]$Raw,
    [string]$DataType,
    [string]$IsNullable = 'YES'
  )
  $empty = ($null -eq $Raw -or $Raw -eq '')
  $dt = $DataType.ToLowerInvariant()

  if ($empty) {
    # Empty CSV cell: use '' for text-like NOT NULL columns (export used NULL '')
    if ($dt -in @('character varying', 'varchar', 'text', 'character', 'citext')) {
      if ($IsNullable -eq 'NO') { return "''" }
      return 'NULL'
    }
    if ($dt -eq 'jsonb' -or $dt -eq 'json') {
      if ($IsNullable -eq 'NO') { return "'{}'::jsonb" }
      return 'NULL'
    }
    if ($dt -eq 'boolean') {
      if ($IsNullable -eq 'NO') { return 'FALSE' }
      return 'NULL'
    }
    return 'NULL'
  }

  if ($dt -eq 'boolean') {
    if ($Raw -eq 't' -or $Raw -eq 'true' -or $Raw -eq '1') { return 'TRUE' }
    if ($Raw -eq 'f' -or $Raw -eq 'false' -or $Raw -eq '0') { return 'FALSE' }
    return 'NULL'
  }
  if ($dt -in @('smallint', 'integer', 'bigint', 'numeric', 'real', 'double precision', 'decimal')) {
    if ($Raw -match '^-?\d+(\.\d+)?$') { return $Raw }
    return (Escape-SqlLiteral $Raw)
  }
  if ($dt -eq 'jsonb' -or $dt -eq 'json') {
    return ((Escape-SqlLiteral $Raw) + '::jsonb')
  }
  if ($dt -eq 'uuid') {
    return (Escape-SqlLiteral $Raw)
  }
  if ($dt -like 'timestamp*' -or $dt -eq 'date' -or $dt -eq 'time without time zone') {
    return (Escape-SqlLiteral $Raw)
  }
  return (Escape-SqlLiteral $Raw)
}

function Get-LocalColumnMeta {
  param([string]$Database, [string]$Table, [string[]]$Columns)
  $map = @{}
  $psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
  if (-not (Test-Path $psql)) {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { $psql = $cmd.Source }
  }
  if (-not (Test-Path $psql)) {
    foreach ($c in $Columns) { $map[$c] = @{ dataType = 'text'; isNullable = 'YES' } }
    return $map
  }
  $env:PGPASSWORD = 'postgres'
  $inList = ($Columns | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }) -join ','
  $sql = @"
SELECT column_name || '|' || data_type || '|' || is_nullable
FROM information_schema.columns
WHERE table_schema='public' AND table_name='$Table'
  AND column_name IN ($inList);
"@
  $raw = & $psql -h localhost -p 5432 -U postgres -d $Database -t -A -c $sql 2>$null
  foreach ($line in @($raw)) {
    if (-not $line) { continue }
    $parts = $line.Split('|')
    if ($parts.Count -ge 3) {
      $map[$parts[0]] = @{ dataType = $parts[1]; isNullable = $parts[2] }
    }
  }
  foreach ($c in $Columns) {
    if (-not $map.ContainsKey($c)) { $map[$c] = @{ dataType = 'text'; isNullable = 'YES' } }
  }
  return $map
}

function Write-TableSql {
  param(
    [string]$FilePath,
    [string]$DatabaseHint,
    [string]$Table,
    [string]$CsvPath,
    [string]$ColumnsFile,
    [string]$DeleteSql,
    [string]$ConflictSql = '',
    [switch]$OmitId,
    [switch]$OverridingIdentity
  )

  $cols = @(Get-Content $ColumnsFile | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() })
  if ($OmitId) { $cols = @($cols | Where-Object { $_ -ne 'id' }) }
  if ($cols.Count -eq 0) { return 0 }

  $types = Get-LocalColumnMeta -Database $DatabaseHint -Table $Table -Columns $cols
  $rows = Import-Csv -Path $CsvPath
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("-- Database: $DatabaseHint")
  [void]$sb.AppendLine("-- Table: $Table")
  [void]$sb.AppendLine('BEGIN;')
  if ($DeleteSql) { [void]$sb.AppendLine($DeleteSql) }

  $colList = ($cols | ForEach-Object { '"' + $_ + '"' }) -join ', '
  $count = 0
  foreach ($row in $rows) {
    $vals = @()
    foreach ($c in $cols) {
      $raw = [string]$row.$c
      $meta = $types[$c]
      $vals += (Convert-CsvValueToSql -Raw $raw -DataType $meta.dataType -IsNullable $meta.isNullable)
    }
    $override = ''
    if ($OverridingIdentity) { $override = ' OVERRIDING SYSTEM VALUE' }
    $conflict = ''
    if ($ConflictSql) { $conflict = ' ' + $ConflictSql }
    [void]$sb.AppendLine(("INSERT INTO `"$Table`" ($colList)$override VALUES ({0})$conflict;" -f ($vals -join ', ')))
    $count++
  }

  if ($cols -contains 'id' -or (-not $OmitId -and (Get-Content $ColumnsFile | Where-Object { $_.Trim() -eq 'id' }))) {
    [void]$sb.AppendLine(@"
DO `$`$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='$Table' AND column_name='id' AND is_identity='YES'
  ) THEN
    EXECUTE format(
      'SELECT setval(pg_get_serial_sequence(%L, ''id''), COALESCE((SELECT MAX(id)::bigint FROM %I), 1), true)',
      '$Table', '$Table'
    );
  END IF;
END`$`$;
"@)
  }

  [void]$sb.AppendLine('COMMIT;')
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  [System.IO.File]::WriteAllText($FilePath, $sb.ToString(), $utf8NoBom)
  return $count
}

$orgEsc = $OrganizationId.Replace("'", "''")
$n = 0
$index = New-Object System.Text.StringBuilder
[void]$index.AppendLine('Run these SQL files in DBeaver in this order.')
[void]$index.AppendLine('Connect each file to the matching database on RDS.')
[void]$index.AppendLine('')

function Emit {
  param(
    [string]$Seq,
    [string]$Db,
    [string]$Table,
    [string]$RelDir,
    [string]$DeleteSql,
    [string]$ConflictSql = '',
    [switch]$OmitId,
    [switch]$OverridingIdentity
  )
  $csv = Join-Path $ExportDir (Join-Path $RelDir "$Table.csv")
  $cols = Join-Path $ExportDir (Join-Path $RelDir "$Table.columns.txt")
  if (-not (Test-Path $csv)) {
    Write-Host "skip missing $csv"
    return
  }
  $file = Join-Path $OutDir ("{0}_{1}__{2}.sql" -f $Seq, $Db, $Table)
  $count = Write-TableSql -FilePath $file -DatabaseHint $Db -Table $Table `
    -CsvPath $csv -ColumnsFile $cols -DeleteSql $DeleteSql -ConflictSql $ConflictSql `
    -OmitId:$OmitId -OverridingIdentity:$OverridingIdentity
  Write-Host ("{0}: {1} row(s) -> {2}" -f $file.Replace($OutDir + '\', ''), $count, $Db)
  [void]$script:index.AppendLine(("{0}  DB={1}  rows={2}" -f (Split-Path $file -Leaf), $Db, $count))
  $script:n++
}

# --- run order ---
Emit '01' 'shopdb' 'shops' 'shopdb' `
  "DELETE FROM `"shops`" WHERE shop_id = '$orgEsc';" `
  'ON CONFLICT (shop_id) DO NOTHING'

Emit '02' 'authdb' 'auth_account' 'authdb' `
  "DELETE FROM `"auth_account`" WHERE shop_id = '$orgEsc';" `
  'ON CONFLICT (username) DO UPDATE SET shop_id = EXCLUDED.shop_id, tenant_id = EXCLUDED.tenant_id, email = EXCLUDED.email, password_hash = EXCLUDED.password_hash, role = EXCLUDED.role' `
  -OmitId

Emit '03' 'school_subscription_db' 'subscription_plan' 'school_subscription_db' `
  '' `
  'ON CONFLICT (id) DO NOTHING'

Emit '04' 'school_subscription_db' 'tenant_subscription' 'school_subscription_db' `
  "DELETE FROM `"tenant_subscription`" WHERE organization_id = '$orgEsc';" `
  'ON CONFLICT (organization_id) DO NOTHING'

$settingsTables = @(
  'design_theme','module_settings','menu_config','localization_settings','ui_screen_config',
  'ai_settings','role_dashboard_config','org_branch','offline_sync_batch','offline_sync_item'
)
$i = 10
foreach ($t in $settingsTables) {
  Emit ("{0:D2}" -f $i) 'school_settings_db' $t 'school_settings_db' `
    "DELETE FROM `"$t`" WHERE organization_id = '$orgEsc';" `
    'ON CONFLICT DO NOTHING' -OverridingIdentity
  $i++
}

$academic = @('academic_class','class_section','subject','timetable_period','timetable_room','teaching_assignment','timetable_slot')
$i = 20
foreach ($t in $academic) {
  Emit ("{0:D2}" -f $i) 'school_academic_db' $t 'school_academic_db' `
    "DELETE FROM `"$t`" WHERE organization_id = '$orgEsc';" `
    'ON CONFLICT DO NOTHING'
  $i++
}

Emit '30' 'school_admission_db' 'admission_application' 'school_admission_db' `
  "DELETE FROM `"admission_application`" WHERE organization_id = '$orgEsc';" `
  'ON CONFLICT DO NOTHING'

$fee = @('finance_definition','fee_collection','finance_transaction','expense_record','income_record','fee_due_reminder_outbox')
$i = 40
foreach ($t in $fee) {
  Emit ("{0:D2}" -f $i) 'school_fee_db' $t 'school_fee_db' `
    "DELETE FROM `"$t`" WHERE organization_id = '$orgEsc';" `
    'ON CONFLICT DO NOTHING'
  $i++
}

$student = @('student_record','lifecycle_definition','lifecycle_event','import_job','import_job_row','student_document','student_attachment','student_field_audit')
$i = 50
foreach ($t in $student) {
  Emit ("{0:D2}" -f $i) 'school_student_db' $t 'school_student_db' `
    "DELETE FROM `"$t`" WHERE organization_id = '$orgEsc';" `
    'ON CONFLICT DO NOTHING'
  $i++
}

Emit '60' 'school_staff_db' 'staff_record' 'school_staff_db' `
  "DELETE FROM `"staff_record`" WHERE organization_id = '$orgEsc';" `
  'ON CONFLICT DO NOTHING'

$orderPath = Join-Path $OutDir '00_RUN_ORDER.txt'
[System.IO.File]::WriteAllText($orderPath, $index.ToString(), (New-Object System.Text.UTF8Encoding $false))

Write-Host ''
Write-Host "SQL files ready: $OutDir"
Write-Host 'Open 00_RUN_ORDER.txt then execute each .sql against the matching RDS database in DBeaver.'
