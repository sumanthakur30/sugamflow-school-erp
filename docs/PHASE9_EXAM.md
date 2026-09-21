# Phase 9 — Exam / Gradebook vertical slice (same pattern as Attendance)

Config engines drive behaviour; `exam-service` only orchestrates records.

| Concern | Source of truth |
|---|---|
| Marks fields | Form Builder · `exam_marks` |
| Approval steps | Workflow · `exam` (Teacher → Exam Coordinator → Principal → Completed) |
| Marks gate | Rule Engine · `exam.marksObtained` |
| Module keys | Module Settings · `exam` |
| Availability | Subscription · `FEATURE_EXAM` |
| Runtime | **exam-service** `:8193` · `/api/exam/**` |

## APIs

| Method | Path |
|---|---|
| GET | `/api/exam/bootstrap` |
| GET | `/api/exam/records` |
| GET | `/api/exam/records/{id}` |
| POST | `/api/exam/records` |
| POST | `/api/exam/records/{id}/actions` |

## Rules (platform seeds)

| Rule | Condition | Action |
|---|---|---|
| `exam_marks_overflow` | `exam.marksObtained` > 100 | `BLOCK_EXAM` |
| `exam_marks_underflow` | `exam.marksObtained` < 0 | `BLOCK_EXAM` |
| `exam_fail_notify` | `exam.marksObtained` < 33 | `NOTIFY_EXAM` |

## Verify

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
.\scripts\start-platform.ps1
.\scripts\start-services.ps1 -Restart
.\scripts\verify-exam.ps1
```

Demo login: `demo-school` / `admin` / `password`.
