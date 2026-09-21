# Smoke-test School ERP notification channels via gateway / notification-service.
# EMAIL -> MailHog. SMS/WhatsApp -> stub (or Twilio/Msg91 when -Live and env is set).
param(
  [string]$Gateway = 'http://127.0.0.1:9090',
  [string]$Notify = 'http://127.0.0.1:8087',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password',
  [string]$EmailTo = 'channel-check@demo-school.local',
  [string]$Mobile = '8800706663',
  [string]$InternalKey = $(if ($env:SECURITY_INTERNAL_API_KEY) { $env:SECURITY_INTERNAL_API_KEY } else { 'dev-invite-key-change-in-production' }),
  [switch]$Live
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'

function Send-Direct([string]$channel, [string]$recipient, [string]$subject, [string]$body) {
  $payload = @{
    shopId = $ShopId
    channel = $channel
    recipient = $recipient
    subject = $subject
    body = $body
    idempotencyKey = "verify-$channel-$stamp"
  } | ConvertTo-Json
  $headers = @{
    'Content-Type' = 'application/json'
    'X-Internal-Api-Key' = $InternalKey
  }
  return Invoke-RestMethod -Method Post -Uri "$Notify/api/v1/notifications" -Headers $headers -Body $payload -TimeoutSec 30
}

Write-Host "Login $ShopId / $Username ..."
$login = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/auth/login" -ContentType 'application/json' `
  -Body (@{ shopId = $ShopId; username = $Username; password = $Password } | ConvertTo-Json) -TimeoutSec 30

Write-Host '--- EMAIL ---'
$email = Send-Direct 'EMAIL' $EmailTo "Channel check EMAIL $stamp" "School ERP EMAIL channel check $stamp"
"EMAIL status=$($email.status) id=$($email.id)"

Write-Host '--- SMS ---'
$sms = Send-Direct 'SMS' $Mobile "Channel check SMS $stamp" "School ERP SMS channel check $stamp"
"SMS status=$($sms.status) id=$($sms.id)"
if ($sms.status -ne 'SENT') {
  Write-Host '  SMS FAILED — ensure NOTIFICATION_SMS_PROVIDER=twilio and TWILIO_* are loaded by notification-service' -ForegroundColor Yellow
}

Write-Host '--- WHATSAPP ---'
$wa = Send-Direct 'WHATSAPP' $Mobile "Channel check WA $stamp" "School ERP WhatsApp channel check $stamp"
"WHATSAPP status=$($wa.status) id=$($wa.id)"
if ($wa.status -ne 'SENT') {
  Write-Host '  WA FAILED — trial needs Twilio WhatsApp sandbox join + TWILIO_WHATSAPP_FROM=whatsapp:+14155238886' -ForegroundColor Yellow
}

Write-Host '--- IN_APP ---'
$in = Send-Direct 'IN_APP' $Username "Channel check IN_APP $stamp" "School ERP in-app check $stamp"
"IN_APP status=$($in.status) id=$($in.id)"

Write-Host ''
if ($email.status -eq 'SENT') {
  Write-Host 'Email: check Zoho inbox (or MailHog http://localhost:8025 if SPRING_MAIL_HOST=mailhog).'
}
Write-Host 'Docs: docs/NOTIFICATION_CHANNELS.md'
