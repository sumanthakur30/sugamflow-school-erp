<#
.SYNOPSIS
  Thin wrapper: start shared A1+A2 common platform from the School repo.

.EXAMPLE
  .\scripts\start-common-platform.ps1
  .\scripts\start-common-platform.ps1 -ExposeSchoolPorts -SkipMailHog
#>
param(
    [switch]$SkipMailHog,
    [switch]$SkipDockerStart,
    [switch]$ExposeSchoolPorts,
    [int]$HealthTimeoutSec = 180,
    [string]$SugamFlowRoot = 'D:\sugamFlow'
)

$ErrorActionPreference = 'Stop'
$target = Join-Path $SugamFlowRoot 'scripts\start-common-platform.ps1'
if (-not (Test-Path -LiteralPath $target)) {
    throw "Common start script not found: $target"
}

$args = @('-File', $target)
if ($SkipMailHog) { $args += '-SkipMailHog' }
if ($SkipDockerStart) { $args += '-SkipDockerStart' }
if ($ExposeSchoolPorts) { $args += '-ExposeSchoolPorts' }
$args += @('-HealthTimeoutSec', "$HealthTimeoutSec")

& powershell -NoProfile -ExecutionPolicy Bypass @args
exit $LASTEXITCODE
