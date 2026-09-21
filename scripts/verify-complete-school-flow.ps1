# Complete school flow smoke.
# Runs existing phase checks in dependency order. Individual scripts create the demo data they need.
param(
  [switch]$IncludeExternal,
  [switch]$StopOnFirstFailure
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot

function Test-PortOpen([int]$Port) {
  try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect('127.0.0.1', $Port)
    $tcp.Close()
    return $true
  } catch {
    return $false
  }
}

# Ensure SMTP sink exists so admission/fee EMAIL delivery can reach SENT.
if (-not (Test-PortOpen 1025)) {
  $docker = Get-Command docker -ErrorAction SilentlyContinue
  if ($docker) {
    Write-Host 'SMTP :1025 down — starting school-mailhog...'
    $existing = docker ps -a --filter 'name=^school-mailhog$' --format '{{.Names}}' 2>$null
    if ($existing -eq 'school-mailhog') {
      docker start school-mailhog | Out-Null
    } else {
      docker run -d --name school-mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog:v1.0.1 | Out-Null
    }
    for ($i = 1; $i -le 20; $i++) {
      if (Test-PortOpen 1025) { break }
      Start-Sleep -Seconds 1
    }
  }
}
if (Test-PortOpen 1025) {
  Write-Host 'OK   MailHog SMTP :1025'
} else {
  Write-Host 'WARN MailHog SMTP :1025 unavailable — admission/fee EMAIL may FAIL'
}
if (-not (Test-PortOpen 8087)) {
  Write-Host 'WARN notification-service :8087 down — EMAIL delivery will fail'
}

$required = @(
  'verify-ops-readiness.ps1',
  'verify-auth.ps1',
  'verify-gateway.ps1',
  'verify-config-editors.ps1',
  'verify-branches.ps1',
  'verify-academic.ps1',
  'verify-admission.ps1',
  'verify-student-enrollment.ps1',
  'verify-student-guardians.ps1',
  'verify-directory.ps1',
  'verify-lifecycle.ps1',
  'verify-fee.ps1',
  'verify-finance.ps1',
  'verify-attendance.ps1',
  'verify-roster-attendance.ps1',
  'verify-attendance-alerts.ps1',
  'verify-exam.ps1',
  'verify-gradebook.ps1',
  'verify-report-cards.ps1',
  'verify-library.ps1',
  'verify-hostel.ps1',
  'verify-transport.ps1',
  'verify-payroll.ps1',
  'verify-ops-depth.ps1',
  'verify-device-adapters.ps1',
  'verify-offline.ps1',
  'verify-portals.ps1',
  'verify-responsive-portals.ps1',
  'verify-persona-rbac.ps1',
  'verify-tenant-isolation.ps1',
  'verify-security-pagination.ps1',
  'verify-report-designer.ps1',
  'verify-audit.ps1',
  'verify-phase24.ps1'
)

$external = @(
  'verify-smtp-email.ps1',
  'verify-payment-gateway.ps1',
  'verify-provision.ps1'
)

$scripts = @($required)
if ($IncludeExternal) {
  $scripts += $external
}

$results = New-Object System.Collections.Generic.List[object]
$startedAt = Get-Date

Write-Host '=== Complete school flow smoke ==='
Write-Host "Root: $root"
Write-Host "Scripts: $($scripts.Count)"
Write-Host ''

foreach ($script in $scripts) {
  $path = Join-Path $PSScriptRoot $script
  $start = Get-Date
  Write-Host ">>> $script"
  if (-not (Test-Path $path)) {
    $msg = "missing script: $path"
    Write-Host "FAIL $msg"
    $results.Add([pscustomobject]@{
      Script = $script
      Status = 'FAIL'
      Seconds = 0
      Error = $msg
    })
    if ($StopOnFirstFailure) { break }
    continue
  }

  Push-Location $root
  try {
    # Clear stale native exit codes so a prior docker/java command cannot fail a PowerShell smoke that threw nothing.
    $global:LASTEXITCODE = 0
    & $path
    if ($null -ne $LASTEXITCODE -and [int]$LASTEXITCODE -gt 0) {
      throw "script exited with code $LASTEXITCODE"
    }
    $elapsed = [int]((Get-Date) - $start).TotalSeconds
    Write-Host "PASS $script ($elapsed sec)"
    $results.Add([pscustomobject]@{
      Script = $script
      Status = 'PASS'
      Seconds = $elapsed
      Error = ''
    })
  } catch {
    $elapsed = [int]((Get-Date) - $start).TotalSeconds
    $err = $_.Exception.Message
    Write-Host "FAIL $script ($elapsed sec): $err"
    $results.Add([pscustomobject]@{
      Script = $script
      Status = 'FAIL'
      Seconds = $elapsed
      Error = $err
    })
    if ($StopOnFirstFailure) {
      Pop-Location
      break
    }
  } finally {
    Pop-Location
  }
  Write-Host ''
}

$failed = @($results | Where-Object { $_.Status -ne 'PASS' })
$passed = @($results | Where-Object { $_.Status -eq 'PASS' })
$totalSeconds = [int]((Get-Date) - $startedAt).TotalSeconds

Write-Host ''
Write-Host '=== Complete school flow summary ==='
Write-Host ("PASS {0}/{1} checks in {2} sec" -f $passed.Count, $results.Count, $totalSeconds)

if ($failed.Count -gt 0) {
  Write-Host ''
  Write-Host 'Failures:'
  foreach ($f in $failed) {
    Write-Host ("- {0}: {1}" -f $f.Script, $f.Error)
  }
  exit 1
}

Write-Host 'ALL COMPLETE SCHOOL FLOW CHECKS PASSED'
