<#
.SYNOPSIS
  School-repo entry: run SugamFlow SEQ 03 (School ERP).

.EXAMPLE
  cd D:\school
  .\scripts\start-school-sequence.ps1 -WithUi
#>
param(
    [string]$SugamFlowRoot = 'D:\sugamFlow',
    [switch]$Restart,
    [switch]$WithUi,
    [switch]$SkipSeed,
    [switch]$StartCommon
)

$ErrorActionPreference = 'Stop'
$seq00 = Join-Path $SugamFlowRoot 'scripts\sequences\00-common-platform.ps1'
$seq03 = Join-Path $SugamFlowRoot 'scripts\sequences\03-school-erp.ps1'

if ($StartCommon) {
    if (-not (Test-Path $seq00)) { throw "Missing $seq00" }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $seq00 -ExposeSchoolPorts -SkipMailHog
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path $seq03)) { throw "Missing $seq03" }
$args = @('-File', $seq03, '-SchoolRoot', (Split-Path $PSScriptRoot -Parent), '-SugamFlowRoot', $SugamFlowRoot)
if ($Restart) { $args += '-Restart' }
if ($WithUi) { $args += '-WithUi' }
if ($SkipSeed) { $args += '-SkipSeed' }
& powershell -NoProfile -ExecutionPolicy Bypass @args
exit $LASTEXITCODE
