# Phase 13 — Parent / Teacher apps (config-driven portals)

Feature-flagged **routed experiences** in `apps/school-ui` (not separate native
codebases). Nav, widgets, profile, notices, and section API paths come from module
settings — no school-specific portal code.

## Feature flags

| Flag | Portal |
|---|---|
| `FEATURE_PARENT_APP` | `/parent` |
| `FEATURE_TEACHER_APP` | `/teacher` |

Seeded onto starter-tier plans via `SubscriptionPlanSeeder`.

## Module settings

| Module key | Defaults |
|---|---|
| `parent_portal` | title, nav, widgets, sections, profile, summary, notices |
| `teacher_portal` | same shape for teacher |

Editable under Admin → Module Settings. Section `apiPath` values point at existing
domain APIs (`/api/attendance/records`, `/api/fee/records`, `/api/exam/records`,
`/api/student/students`).

## APIs

| Method | Path |
|---|---|
| GET | `/api/config/portals` |
| GET | `/api/config/portals/{parent\|teacher}/bootstrap` |

Bootstrap returns `featureEnabled`, filtered `nav` (by child feature flags),
`widgets`, `sections`, `profile`, `summary`, `notices`.

## UI

- Login → choose **Parent App** / **Teacher App** / School Admin
- Portal shell with config nav; home dashboard widgets; section pages load live lists
- Admin shell links to Parent / Teacher apps
- Active role header set to `PARENT` / `TEACHER` while in portal (`sf.role`)

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-portals.ps1
```

Requires gateway `:9090`, subscription `:8182`, settings `:8181`.
