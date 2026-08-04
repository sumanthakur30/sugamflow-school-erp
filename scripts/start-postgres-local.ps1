$ErrorActionPreference = 'Stop'
$svc = 'postgresql-x64-17'
Start-Service -Name $svc
Start-Sleep -Seconds 3
$status = (Get-Service -Name $svc).Status
$marker = 'D:\school\logs\pg-start-ok.txt'
New-Item -ItemType Directory -Force -Path (Split-Path $marker) | Out-Null
Set-Content -Path $marker -Value $status
if ($status -ne 'Running') { exit 1 }
exit 0
