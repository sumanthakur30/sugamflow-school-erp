# Phase 5b — Offer letter PDF + notification delivery

Extends the Admission vertical slice: on **final approve**, admission-service renders a
template-driven offer letter PDF and delivers configured notifications via SugamFlow
`notification-service`.

## Config (no school-specific code)

| Knob | Where | Default |
|---|---|---|
| `offerLetterTemplateKey` | Module Settings → `admission` | `offer_letter` |
| `notifyOnApprove` | Module Settings → `admission` | `true` |
| `approveNotificationChannels` | Module Settings → `admission` | `EMAIL`, `IN_APP` |
| `approveNotificationTemplateId` | Module Settings → `admission` | `admission_approved` |
| Offer letter layout | Report Builder → `offer_letter` | seeded elements with `{{application.*}}` |
| Email/SMS copy | Notification Config → `admission_approved` | event `ADMISSION` / intent `ADMISSION_APPROVED` |

## Flow

1. Final workflow `APPROVE` → status `APPROVED`
2. `POST /api/reports/templates/{offerLetterTemplateKey}/render` (OpenPDF)
3. Store PDF on application `documents[]` (type `OFFER_LETTER`)
4. Resolve template → `POST` each channel to platform `/api/v1/notifications`
5. `IN_APP` is recorded locally; `EMAIL`/`SMS` go to `:8087`
6. Email body includes gateway download URL for the PDF (platform email is plain text — no attachment)

**SMTP:** local MailHog on `:1025` (UI `:8025`) — see [SMTP_LOCAL.md](SMTP_LOCAL.md). Without it, EMAIL status is `FAILED`.

## APIs

| Method | Path |
|---|---|
| GET | `/api/admission/applications/{id}/offer-letter` → `application/pdf` |
| POST | `/api/reports/templates/{key}/render` |
| POST | `/api/school/notification-config/templates/resolve` |

## Verify

```powershell
cd D:\school
.\scripts\start-platform.ps1   # MailHog :1025 + notification-service :8087
.\scripts\start-services.ps1 -Restart
.\scripts\verify-admission.ps1   # expects EMAIL -> SENT
```

Inspect mail: http://localhost:8025

