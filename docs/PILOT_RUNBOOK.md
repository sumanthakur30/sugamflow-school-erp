# Pilot runbook — SugamFlow School ERP

Use this before any campus pilot day. It assumes daily start already works
(`docs/DAILY_START.md`).

## 1) Pre-flight

1. Start platform + school services + UI (`docs/DAILY_START.md`).
2. Confirm MailHog / SMTP if parent email alerts matter:
   - SMTP `:1025`, UI `http://localhost:8025`
   - notification-service `:8087` health UP
3. Take a fresh DB backup:

```powershell
cd D:\school
.\scripts\backup-school-dbs.ps1
```

Backups land in `D:\school\backups\<timestamp>\` with `manifest.json`.

Restore one DB if needed:

```powershell
.\scripts\restore-school-db.ps1 `
  -DumpFile 'D:\school\backups\<stamp>\school_student_db.dump' `
  -Database school_student_db `
  -Clean
```

## 2) Go / no-go

```powershell
cd D:\school
.\scripts\verify-pilot-ready.ps1
# Fail hard when every school port must be green:
.\scripts\verify-pilot-ready.ps1 -Strict
```

Checks:

| Check | Script |
|-------|--------|
| Health / liveness / readiness / prometheus / correlation | `verify-ops-readiness.ps1` |
| Parent/teacher portal contracts | `verify-portals.ps1` |
| Multi-campus RBAC | `verify-branch-rbac.ps1` |
| Attendance ABSENT/LATE parent alerts | `verify-attendance-alerts.ps1` |

Skip alerts when notification stack is intentionally offline:

```powershell
.\scripts\verify-pilot-ready.ps1 -SkipAlerts
```

## 3) Pilot smoke paths (manual)

| Persona | Path | Expect |
|---------|------|--------|
| Admin / Principal | `/admin/dashboard` | Live campus KPIs |
| Reception | `/admin/admission` | Inbox filters + workflow actions |
| Accounts | `/admin/finance`, `/admin/income-expense` | Collections / dues |
| Teacher | `/teacher` then Attendance | Live home + submit shows parent alert summary |
| Parent | `/parent` | Live attendance / fee / result widgets |
| QR verify | `/verify/document/:token` | Public document status |

## 4) Alert triage

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Alert smoke `SMS:FAILED, EMAIL:FAILED` | notification-service down | Start `:8087`; re-run `verify-attendance-alerts.ps1` |
| EMAIL fails only | MailHog/SMTP down | Start MailHog or set `SPRING_MAIL_*` |
| `notified=0 skipped=1` with contact present | Prior alert already sent (idempotent) | Expected on resubmit |
| Teacher UI shows 0 alerts | Mark status PRESENT/LEAVE | Only ABSENT/LATE notify by default |
| Parent home empty | Account not linked to student guardians | Attach guardian mobile/email matching parent login |

Attendance alert settings (module `attendance`):

- `notifyOnRosterSubmit` (default true)
- `rosterAlertStatuses` = `ABSENT`, `LATE`
- `rosterNotificationChannels` = `SMS`, `EMAIL`

Delivery audit (durable outbox): `GET /api/attendance/sessions/{sessionId}/alerts` lists every
per-channel attempt with status (`PENDING`/`SENT`/`FAILED`), attempts, recipient, and last error.
FAILED/PENDING rows are retried automatically every 5 minutes (`school.attendance.alert-retry-ms`)
and on the next session submit; duplicate delivery is prevented end-to-end by a per-row
idempotency key honoured by notification-service.

## 5) Security notes for pilot

- Frontend route roles now gate parent/teacher portals and sensitive admin/config pages.
- Backend JWT role remains authoritative; UI role switching only affects presentation.
- Public QR verify stays unauthenticated on `/api/student/public/**`.

## 6) End of day

1. Re-run `.\scripts\backup-school-dbs.ps1`
2. Capture any FAIL lines from `verify-pilot-ready.ps1`
3. Note Eureka instances that stayed `OUT_OF_SERVICE` after restarts
