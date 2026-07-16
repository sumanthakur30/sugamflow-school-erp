# Smoke: SMTP EMAIL delivery via notification-service + MailHog.
$ErrorActionPreference = 'Stop'

function Test-PortOpen([int]$Port) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.ConnectAsync('127.0.0.1', $Port).Wait(800) | Out-Null
    $ok = $c.Connected
    $c.Close()
    return $ok
  } catch {
    return $false
  }
}

Write-Host '=== Preconditions ==='
if (-not (Test-PortOpen 1025)) {
  throw 'SMTP :1025 not open - run .\scripts\start-platform.ps1 (MailHog) first'
}
if (-not (Test-PortOpen 8087)) {
  throw 'notification-service :8087 not open - run .\scripts\start-platform.ps1 first'
}
Write-Host 'OK   SMTP :1025 and notification-service :8087'

Write-Host ''
Write-Host '=== Direct EMAIL send ==='
$to = "smtp-smoke-$(Get-Random)@example.com"
$body = @{
  shopId    = 'demo-school'
  channel   = 'EMAIL'
  recipient = $to
  subject   = 'School SMTP smoke test'
  body      = "Hello from verify-smtp-email.ps1 at $(Get-Date -Format o)"
} | ConvertTo-Json

$resp = Invoke-RestMethod -Method Post -Uri 'http://localhost:8087/api/v1/notifications' `
  -ContentType 'application/json' -Body $body -TimeoutSec 30
if ($resp.status -ne 'SENT') {
  throw "Expected SENT, got $($resp.status) id=$($resp.id)"
}
Write-Host "OK   EMAIL SENT id=$($resp.id) to=$to"
Write-Host '     Inspect: http://localhost:8025'

Write-Host ''
Write-Host 'SMTP email verification passed.'
