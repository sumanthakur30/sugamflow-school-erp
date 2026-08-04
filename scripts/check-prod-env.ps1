# Production env check — School ERP + SugamFlow (no secrets printed).
# Usage (from PC with both repos):
#   powershell -File D:\school\scripts\check-prod-env.ps1
#   powershell -File D:\school\scripts\check-prod-env.ps1 -SchoolEnv D:\school\.env.school.production -SugamEnv D:\sugamFlow\.env.production
#
# On EC2:
#   powershell -File ./scripts/check-prod-env.ps1 -SchoolEnv /home/ec2-user/opt/school/.env.school.production -SugamEnv /home/ec2-user/opt/sugamflow/.env.production

param(
  [string]$SchoolEnv = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env.school.production'),
  [string]$SugamEnv = 'D:\sugamFlow\.env.production'
)

$ErrorActionPreference = 'Continue'
$script:fail = 0

function Read-EnvMap([string]$Path) {
  $map = @{}
  if (-not (Test-Path $Path)) { return $map }
  Get-Content $Path | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      $map[$Matches[1]] = $Matches[2].Trim()
    }
  }
  return $map
}

function Ok($msg) { Write-Host "OK   $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "WARN $msg" -ForegroundColor Yellow }
function Bad($msg) { Write-Host "FAIL $msg" -ForegroundColor Red; $script:fail++ }

Write-Host '=== Prod env check ==='
Write-Host "School: $SchoolEnv"
Write-Host "Sugam:  $SugamEnv"
Write-Host ''

if (-not (Test-Path $SchoolEnv)) { Bad "School env missing: $SchoolEnv" }
if (-not (Test-Path $SugamEnv)) { Bad "SugamFlow env missing: $SugamEnv" }
if ($script:fail -gt 0) { exit 1 }

$s = Read-EnvMap $SchoolEnv
$g = Read-EnvMap $SugamEnv

# --- JWT ---
if ($s['SECURITY_JWT_ENFORCE'] -ne 'true') { Bad 'School SECURITY_JWT_ENFORCE must be true in production' } else { Ok 'School SECURITY_JWT_ENFORCE=true' }
if (-not $g.ContainsKey('SECURITY_JWT_ENFORCE')) { Warn 'SugamFlow SECURITY_JWT_ENFORCE missing (compose defaults to true)' }
elseif ($g['SECURITY_JWT_ENFORCE'] -ne 'true') { Bad 'SugamFlow SECURITY_JWT_ENFORCE must be true in production' }
else { Ok 'SugamFlow SECURITY_JWT_ENFORCE=true' }

$sj = $s['SECURITY_JWT_SECRET']; $gj = $g['SECURITY_JWT_SECRET']
if ([string]::IsNullOrWhiteSpace($sj) -or $sj -match 'CHANGE_ME') { Bad 'School SECURITY_JWT_SECRET missing/placeholder' }
elseif ([string]::IsNullOrWhiteSpace($gj) -or $gj -match 'CHANGE_ME') { Bad 'SugamFlow SECURITY_JWT_SECRET missing/placeholder' }
elseif ($sj -ne $gj) { Bad 'SECURITY_JWT_SECRET mismatch between School and SugamFlow' }
else { Ok ("SECURITY_JWT_SECRET match (len={0})" -f $sj.Length) }

# --- Internal API key (EMAIL auth to notification-service) ---
$si = $s['SECURITY_INTERNAL_API_KEY']; if ([string]::IsNullOrWhiteSpace($si)) { $si = $s['SECURITY_INVITE_INTERNAL_KEY'] }
$gi = $g['SECURITY_INTERNAL_API_KEY']; if ([string]::IsNullOrWhiteSpace($gi)) { $gi = $g['SECURITY_INVITE_INTERNAL_KEY'] }
if ([string]::IsNullOrWhiteSpace($si) -or $si -match 'CHANGE_ME') { Bad 'School SECURITY_INTERNAL_API_KEY / INVITE key missing' }
elseif ([string]::IsNullOrWhiteSpace($gi) -or $gi -match 'CHANGE_ME') { Bad 'SugamFlow SECURITY_INTERNAL_API_KEY / INVITE key missing' }
elseif ($si -ne $gi) { Bad 'SECURITY_INTERNAL_API_KEY mismatch (School vs SugamFlow) - EMAIL will 401' }
else { Ok ("SECURITY_INTERNAL_API_KEY match (len={0})" -f $si.Length) }

if (-not $s.ContainsKey('SECURITY_INTERNAL_API_KEY') -or [string]::IsNullOrWhiteSpace($s['SECURITY_INTERNAL_API_KEY'])) {
  Warn 'School SECURITY_INTERNAL_API_KEY unset - compose falls back to INVITE key; set explicitly for clarity'
}
if (-not $g.ContainsKey('SECURITY_INTERNAL_API_KEY') -or [string]::IsNullOrWhiteSpace($g['SECURITY_INTERNAL_API_KEY'])) {
  Warn 'SugamFlow SECURITY_INTERNAL_API_KEY unset - compose falls back to INVITE key; set explicitly for clarity'
}

# --- SMTP (notification-service) ---
$hostMail = $g['SPRING_MAIL_HOST']
if ([string]::IsNullOrWhiteSpace($hostMail) -or $hostMail -match 'CHANGE_ME') {
  Bad 'SugamFlow SPRING_MAIL_HOST missing/placeholder'
} elseif ($hostMail -eq 'localhost' -or $hostMail -eq '127.0.0.1') {
  Bad ("SugamFlow SPRING_MAIL_HOST={0} - EMAIL fails inside Docker; set real SMTP host" -f $hostMail)
} else {
  Ok ("SPRING_MAIL_HOST set ({0})" -f $hostMail)
}
$port = $g['SPRING_MAIL_PORT']
if ([string]::IsNullOrWhiteSpace($port)) { Warn 'SPRING_MAIL_PORT unset' } else { Ok ("SPRING_MAIL_PORT={0}" -f $port) }
$auth = $g['SPRING_MAIL_SMTP_AUTH']
if ($auth -eq 'true') {
  if ([string]::IsNullOrWhiteSpace($g['SPRING_MAIL_USERNAME'])) { Bad 'SPRING_MAIL_SMTP_AUTH=true but SPRING_MAIL_USERNAME empty' }
  else { Ok 'SPRING_MAIL_USERNAME set' }
  if ([string]::IsNullOrWhiteSpace($g['SPRING_MAIL_PASSWORD'])) { Bad 'SPRING_MAIL_SMTP_AUTH=true but SPRING_MAIL_PASSWORD empty' }
  else { Ok 'SPRING_MAIL_PASSWORD set' }
} else {
  Warn 'SPRING_MAIL_SMTP_AUTH is not true - OK only for open relay / local MailHog (not typical prod)'
}

# --- Website CMS ---
foreach ($k in @('SCHOOL_WEBSITE_DB_URL','SCHOOL_CMS_DB_URL','SCHOOL_WEBSITE_DB_PASSWORD','SCHOOL_CMS_DB_PASSWORD')) {
  if ([string]::IsNullOrWhiteSpace($s[$k]) -or $s[$k] -match 'CHANGE_ME|YOUR_RDS') {
    Bad ("School {0} missing/placeholder (required for Website CMS)" -f $k)
  } else { Ok ("{0} set" -f $k) }
}
$cdn = $s['WEBSITE_CDN_BASE_URL']
if ([string]::IsNullOrWhiteSpace($cdn)) { Warn 'WEBSITE_CDN_BASE_URL empty (OK until CDN ready)' }
elseif ($cdn -match 'hcpschool\.com') { Bad 'WEBSITE_CDN_BASE_URL must not be hcpschool.com (use CDN origin)' }
else { Ok ("WEBSITE_CDN_BASE_URL={0}" -f $cdn) }
$erp = $s['WEBSITE_DEFAULT_ERP_LOGIN_URL']
if ($erp -match 'school\.sugamflow\.com') { Ok ("WEBSITE_DEFAULT_ERP_LOGIN_URL={0}" -f $erp) }
elseif ([string]::IsNullOrWhiteSpace($erp)) { Warn 'WEBSITE_DEFAULT_ERP_LOGIN_URL unset' }
else { Warn ("WEBSITE_DEFAULT_ERP_LOGIN_URL={0} (confirm for prod)" -f $erp) }

Write-Host ''
if ($script:fail -eq 0) {
  Write-Host 'Prod env check PASSED (fix SMTP host + keys before deploy).' -ForegroundColor Green
  Write-Host 'Website CMS start: PHASE=website bash scripts/ec2/02-school-start.sh'
  Write-Host 'Full HCP steps: docs/HCP_EC2_WEBSITE_DEPLOY.md'
  exit 0
} else {
  Write-Host ("Prod env check FAILED ({0} issue(s))." -f $script:fail) -ForegroundColor Red
  exit 1
}
