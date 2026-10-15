# Forwards to the canonical script. Edit D:\sugamflow-doc\scripts, not this copy.
$target = 'D:\sugamflow-doc\scripts\start-services.ps1'
if (-not (Test-Path -LiteralPath $target)) { throw "Missing canonical script: $target" }
& powershell -NoProfile -ExecutionPolicy Bypass -File $target @args
exit $LASTEXITCODE
