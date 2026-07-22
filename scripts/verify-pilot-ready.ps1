# Pilot go/no-go: ops probes + critical wave smoke checks.
param(
  [switch]$Strict,
  [switch]$SkipAlerts,
  [string]$Gateway = 'http://localhost:9090'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$failed = 0

function Run-Check([string]$Name, [scriptblock]$Block) {
  Write-Host ''
  Write-Host "=== $Name ==="
  try {
    & $Block
    Write-Host "PASS $Name"
  } catch {
    Write-Host "FAIL $Name : $($_.Exception.Message)"
    $script:failed++
  }
}

Run-Check 'Ops readiness' {
  $args = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'verify-ops-readiness.ps1'))
  if ($Strict) { $args += '-Strict' }
  & powershell @args
  if ($LASTEXITCODE -ne 0) {
    if ($Strict) {
      throw "verify-ops-readiness exit $LASTEXITCODE"
    }
    Write-Warning "Ops readiness reported issues (non-strict). Continue with remaining checks."
  }
}

Run-Check 'Portal bootstrap' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-portals.ps1')
  if ($LASTEXITCODE -ne 0) { throw "verify-portals exit $LASTEXITCODE" }
}

Run-Check 'Branch RBAC' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-branch-rbac.ps1')
  if ($LASTEXITCODE -ne 0) { throw "verify-branch-rbac exit $LASTEXITCODE" }
}

if (-not $SkipAlerts) {
  Run-Check 'Attendance parent alerts' {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-attendance-alerts.ps1') -Gateway $Gateway
    if ($LASTEXITCODE -ne 0) { throw "verify-attendance-alerts exit $LASTEXITCODE" }
  }
}

Write-Host ''
if ($failed -gt 0) {
  Write-Host "PILOT NOT READY - $failed check(s) failed"
  exit 1
}
Write-Host 'PILOT READY'
