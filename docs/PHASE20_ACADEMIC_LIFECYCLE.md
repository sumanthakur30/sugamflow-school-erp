# Phase 20 — Academic lifecycle

Config-driven promotion maps, session rollover, transfer certificate (TC), and
terminal status transitions on top of the Student Master vertical slice.

## Feature flags

| Flag | Role |
|---|---|
| `FEATURE_STUDENT_MASTER` | Required for student records and lifecycle APIs |
| `FEATURE_ACADEMIC_LIFECYCLE` | Promotion, rollover, TC, dropout, alumni |
| `FEATURE_REPORT_BUILDER` | TC PDF via `transfer_certificate` template |

## Module settings (`student`)

| Key | Default |
|---|---|
| `lifecycleEnabled` | `true` |
| `classFieldKey` | `classApplied` |
| `defaultPromotionMapKey` | `default_grade_map` |
| `defaultStatusPolicyKey` | `default_statuses` |
| `defaultTcPolicyKey` | `default_tc` |
| `tcTemplateKey` | `transfer_certificate` |
| `currentSessionKey` | `2025-26` |

## APIs (`student-service`)

| Method | Path |
|---|---|
| GET | `/api/student/lifecycle/bootstrap` |
| GET/PUT | `/api/student/lifecycle/promotion-maps`, `/promotion-maps/{key}` |
| GET/PUT | `/api/student/lifecycle/sessions`, `/sessions/{key}` |
| GET/PUT | `/api/student/lifecycle/status-policies`, `/status-policies/{key}` |
| GET/PUT | `/api/student/lifecycle/tc-policies`, `/tc-policies/{key}` |
| POST | `/api/student/lifecycle/promote` |
| POST | `/api/student/lifecycle/promote-by-class` |
| POST | `/api/student/lifecycle/rollover` |
| POST | `/api/student/lifecycle/rollover-by-class` |
| POST | `/api/student/lifecycle/tc/clearance/preview` |
| POST | `/api/student/lifecycle/tc` |
| POST | `/api/student/lifecycle/dropout` |
| POST | `/api/student/lifecycle/alumni` |
| GET | `/api/student/lifecycle/events?studentId=` |

Definitions are versioned JSONB in `lifecycle_definition`. Events are audited in
`lifecycle_event` with optional idempotency keys (TC).

## TC clearance (Rule Engine)

Before TC is issued, `student-service` loads clearance snapshots from fee and
library services, evaluates platform rules, and blocks on `BLOCK_TC`.

| Rule id | Condition | Action |
|---|---|---|
| `tc_fee_dues_block` | `fees.pendingAmount` > 0 | `BLOCK_TC` |
| `tc_library_books_block` | `library.outstandingBooks` > 0 | `BLOCK_TC` |

**Clearance APIs**

| Service | Path |
|---|---|
| fee-service | `GET /api/fee/clearance/{admissionNo}` |
| library-service | `GET /api/library/clearance/{admissionNo}` |
| student-service | `POST /api/student/lifecycle/tc/clearance/preview` |

**TC policy** (`default_tc`) fields: `clearanceEnabled`, `clearanceProviders`
(`fee`, `library`), `blockActions` (`BLOCK_TC`).

Fee pending = non-`APPROVED` collections for the admission number. Library
outstanding = `APPROVED` issues without `returned` / `returnDate`.

## Defaults seeded per org

- **Promotion map** `default_grade_map`: Grade 7-A → 12-A ladder
- **Status policy** `default_statuses`: ACTIVE, TRANSFERRED, DROPOUT, ALUMNI terminal rules
- **TC policy** `default_tc`: template `transfer_certificate`, blocks terminal students
- **Sessions** `2025-26` → `2026-27`

## UI

Admin → **Academic Lifecycle** (`/admin/lifecycle`): promotion map editor, session/TC
policy browse, promote / rollover / issue TC, recent events.

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-lifecycle.ps1
```

Keep `verify-student-enrollment.ps1` green for the enrollment slice.
