# Local SMTP — real EMAIL SENT

Approve flows (admission / fee / attendance / exam) call SugamFlow
`notification-service` (`:8087`) for `EMAIL`. That service defaults to:

| Property | Default |
|---|---|
| `spring.mail.host` | `localhost` |
| `spring.mail.port` | `1025` |
| auth / starttls | `false` |

Without an SMTP listener on `:1025`, delivery status is **`FAILED`**.

## Local demo (MailHog)

`.\scripts\start-platform.ps1` starts MailHog automatically (unless `-SkipMailHog`):

- SMTP: `localhost:1025`
- UI: http://localhost:8025

Manual:

```powershell
cd D:\school
docker compose up -d mailhog
# or
docker run -d --name school-mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog:v1.0.1
```

If notification-service was already running **before** MailHog, restart it (or re-run
`start-platform.ps1` after stopping the process on `:8087`).

## Verify EMAIL SENT

```powershell
.\scripts\start-platform.ps1
.\scripts\start-services.ps1   # if not already up
.\scripts\verify-smtp-email.ps1
# or full slices:
.\scripts\verify-admission.ps1   # expects EMAIL -> SENT
.\scripts\verify-fee.ps1
```

Captured messages appear in MailHog UI.

For SMS / WhatsApp (stub vs Twilio / Msg91) see [NOTIFICATION_CHANNELS.md](./NOTIFICATION_CHANNELS.md).

## Production / real Gmail

Pass env or JVM args when starting notification-service:

```text
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=you@gmail.com
SPRING_MAIL_PASSWORD=<app-password>
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
```

Platform email is plain text (no PDF attachments) — bodies include download links.
