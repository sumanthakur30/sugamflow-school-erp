# School ERP notification channels (Email / SMS / WhatsApp)

School modules (admission, fee, attendance, Comms Hub, …) resolve templates in
`school-notification-config-service` (`:8187`) and deliver through SugamFlow
`notification-service` (`:8087`).

| Channel | Local default | Live provider |
|---------|---------------|---------------|
| **EMAIL** | MailHog SMTP `:1025` → UI http://localhost:8025 | SES / Gmail SMTP (`SPRING_MAIL_*`) |
| **SMS** | `stub` (log + SENT) | `twilio` or `msg91` or `http` adapter |
| **WHATSAPP** | `stub` | `twilio` (sandbox/production) or `http` |
| **IN_APP** | Persisted inbox | Same |

Comms Hub **In-app** compose also fans out **EMAIL + SMS + WhatsApp** when guardian
email/mobile exist (portal login still required for true IN_APP inbox rows).

## Local (free)

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"   # MailHog + notification
.\scripts\verify-notification-channels.ps1
```

- Email → open MailHog
- SMS / WhatsApp → stub logs in notification-service (no phone traffic)

## Enable Twilio trial (real phone / WhatsApp sandbox)

1. Create a [Twilio trial](https://www.twilio.com/try-twilio); verify your mobile.
2. For WhatsApp: join the [WhatsApp sandbox](https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn) and message the join code from your phone.
3. Put secrets in `D:\sugamFlow\.env` (docker) **or** export before starting the jar:

```text
NOTIFICATION_SMS_PROVIDER=twilio
NOTIFICATION_WHATSAPP_PROVIDER=twilio
TWILIO_ACCOUNT_SID=ACxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxx
TWILIO_SMS_FROM=+1xxxxxxxxxx
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
NOTIFICATION_DEFAULT_COUNTRY_CODE=91
```

4. Recreate notification:

```powershell
cd D:\sugamFlow
docker compose up -d --force-recreate notification-service
# or rebuild image if code changed:
# docker compose build notification-service && docker compose up -d notification-service
```

5. Publish from Comms Hub (SMS / WhatsApp / In-app) or:

```powershell
.\scripts\verify-notification-channels.ps1 -Live
```

Trial limits: verified numbers only; SMS may include a Twilio prefix.

## Enable Msg91 (India SMS)

```text
NOTIFICATION_SMS_PROVIDER=msg91
MSG91_AUTH_KEY=xxxxxxxx
MSG91_SENDER=SCHERP
MSG91_ROUTE=4
NOTIFICATION_DEFAULT_COUNTRY_CODE=91
```

DLT-registered sender/templates are required for production traffic in India.

## Production (EC2)

Channel delivery runs in **SugamFlow** `notification-service`, not school jars.

1. Copy / merge into `/opt/sugamflow/.env.production` (or your EC2 path):
   - Zoho: `SPRING_MAIL_*` + `SPRING_MAIL_FROM` (must match mailbox)
   - Twilio: `NOTIFICATION_SMS_PROVIDER=twilio`, `NOTIFICATION_WHATSAPP_PROVIDER=twilio`, `TWILIO_*`
2. Ensure `docker-compose.ec2-rds.yml` passes those env vars into `notification-service` (already wired).
3. Deploy a **notification-service image** that includes Twilio/Msg91 providers + email `From` fix (build/push then set `IMAGE_TAG`).
4. Recreate on EC2:

```bash
cd /opt/sugamflow   # adjust path
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d --force-recreate notification-service
docker logs --tail 80 notification-service   # or container name from compose
```

5. School stack only needs matching `SECURITY_INTERNAL_API_KEY` so domain services can POST `/api/v1/notifications`.

**WhatsApp on EC2:** sandbox (`whatsapp:+14155238886`) is for trial/dev. Production needs an approved Twilio WhatsApp Business sender; until then keep SMS + Email live and WhatsApp stub or sandbox for staff test numbers only.

Related: [SMTP_LOCAL.md](./SMTP_LOCAL.md) · [PLATFORM_INTEGRATION.md](./PLATFORM_INTEGRATION.md)
