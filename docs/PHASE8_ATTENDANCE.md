# Phase 8 — Attendance vertical slice (same pattern as Fee)

Config engines drive behaviour; `attendance-service` only orchestrates records.

| Concern | Source of truth |
|---|---|
| Marking fields | Form Builder · `attendance_mark` |
| Approval steps | Workflow · `attendance` (Teacher → Coordinator → Completed) |
| Percent gate | Rule Engine · `attendance.attendancePercent` |
| Module keys | Module Settings · `attendance` |
| Availability | Subscription · `FEATURE_ATTENDANCE` |
| Runtime | **attendance-service** `:8192` · `/api/attendance/**` |

## APIs

| Method | Path |
|---|---|
| GET | `/api/attendance/bootstrap` |
| GET | `/api/attendance/records` |
| GET | `/api/attendance/records/{id}` |
| POST | `/api/attendance/records` |
| POST | `/api/attendance/records/{id}/actions` |

## Verify

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
.\scripts\start-platform.ps1
.\scripts\start-services.ps1 -Restart
.\scripts\verify-attendance.ps1
```

Demo login: `demo-school` / `admin` / `password`.
