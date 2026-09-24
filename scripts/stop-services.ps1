<#
.SYNOPSIS
  School wrapper for shared stop (SugamFlow scripts\stop-shared-runtime.ps1).
.EXAMPLE
  .\scripts\stop-services.ps1
  .\scripts\stop-services.ps1 -WithUi
  .\scripts\stop-services.ps1 -All
#>
param(
  [switch]$WithUi,
  [switch]$All,
  [switch]$CommonDocker,
  [string]$SugamFlowRoot = 'D:\sugamFlow'
)
$ErrorActionPreference = 'Stop'
$target = Join-Path $SugamFlowRoot 'scripts\stop-shared-runtime.ps1'
if (-not (Test-Path $target)) { throw "Missing $target" }
$args = @('-File', $target, '-School')
if ($WithUi -or $All) { $args += '-SchoolUi' }
if ($CommonDocker -or $All) { $args += '-CommonDocker' }
if ($All) { $args += '-All' }
& powershell -NoProfile -ExecutionPolicy Bypass @args
exit $LASTEXITCODE
