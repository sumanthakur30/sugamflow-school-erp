# Phase 22 — Global Directory (Student + Staff)

## Verdict (pre-implementation audit)

| Capability | Existed before Phase 22? | Notes |
|---|---|---|
| Centralized **Student Directory** | **No** | Only `/admin/students` master+guardian editor; unfiltered list of first 50 |
| Centralized **Staff Directory** | **No** | No staff/employee entity; `FEATURE_HR` was a dangling flag; payroll held free-text employee fields only |

Industry baselines compared: PowerSchool, Fedena, Teachmint, Entab CampusCare, MyClassCampus, OpenEduCat.

## What shipped

### Student Directory (`FEATURE_STUDENT_MASTER`)
- UI: `/admin/student-directory`
- APIs under `/api/student/directory/**`
  - `GET /bootstrap` — config-driven columns, filters, search fields, quick/bulk actions, summary widgets
  - `GET /students` — server-side pagination + filters (`q`, status, class, gender, category, house, transport, hostel, scholarship)
  - `GET /summary` — totals, active, alumni, TC, gender, new admissions, class/branch breakdowns
  - `GET /export.csv` — CSV export of filtered set
- PostgreSQL JSONB search on answers (name, mobile, email, roll, aadhaar, rfid, guardians text)
- Existing `/admin/students` retained as **Student Master** (profile/guardians)

### Staff Directory (`FEATURE_STAFF_MASTER`)
- New microservice: `staff-service` (:8198), DB `school_staff_db`
- UI: `/admin/staff-directory`
- APIs under `/api/staff/**` and `/api/staff/directory/**`
- Form key: `employee_master` (enriched in Form Builder)
- Module settings key: `staff`
- Create employee, search/filter, summary, CSV export

## Configuration-driven design

- Directory columns / filters / quick actions overridable via module settings (`directoryColumns`, `directoryFilters`, `directoryQuickActions`)
- No school-specific Angular/Java forks
- Feature-flag and subscription gated
- Org / branch / session scoped queries

## Gaps vs industry (still open / next increments)

| Area | Gap |
|---|---|
| Student | Fee-pending live join, attendance-today widget, birthday-today, house/category seeded enums, Excel/PDF list export, WhatsApp/SMS bulk send, saved filters, customizable column picker persistence |
| Staff | Leave/attendance live widgets, performance, promotion/transfer workflows, documents vault, org-chart, role→person mapping for workflow assignees |
| UX | Saved filters, keyboard shortcuts, Excel export, true Excel/PDF from Report Builder for list layouts |
| RBAC | Fine-grained per-action permissions beyond role+feature flag (export vs communicate vs lifecycle) |

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-directory.ps1
```

## Gateway / ops

- Gateway route: `/api/staff/**` → `staff-service`
- Start script includes port **8198**
- Create DB once: user/db `school_staff` (see `infra/postgres/01-create-school-databases-simple.sql`)
