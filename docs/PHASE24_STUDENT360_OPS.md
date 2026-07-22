# Phase 24 — Student 360, Comms Hub, LMS choice, deep ops, Import Workbench

Operational depth beyond config-driven workflow slices: student command center, bulk intake,
school-wide announcements, real ledger ops for library/hostel/transport, and an explicit LMS path.

## Tracks

| Track | Status | Entry points |
|---|---|---|
| **Student 360** | API + admin UI | `GET /api/student/students/{id}/360`, UI `/admin/students/:id/360` |
| **Import Workbench** | Jobs + dry-run/commit + UI | `/api/student/import/**`, UI `/admin/import`, flag `FEATURE_IMPORT_WORKBENCH` |
| **Comms Hub** | Announcements scaffold | `/api/school/notification-config/comms/**`, UI `/admin/comms`, flag `FEATURE_COMMS_HUB` |
| **Deep library** | Catalog + issue/return ledger | `/api/library/circulation/**`, clearance merges ledger + workflow |
| **Deep hostel** | Bed inventory + occupancy | `/api/hostel/beds/**` |
| **Deep transport** | Routes + assignments | `/api/transport/routes/**` |
| **LMS choice** | Decision + module settings | `docs/LMS_CHOICE.md`, module `lms`, flag `FEATURE_LMS` |

## Feature flags

Seeded onto all plans by `SubscriptionPlanSeeder`:

- `FEATURE_IMPORT_WORKBENCH`
- `FEATURE_COMMS_HUB`
- `FEATURE_LMS`

UI nav currently gates Import/Comms on `FEATURE_STUDENT_MASTER` so screens work before subscription restart; switch to dedicated flags after seeder restart.

## Deep ops APIs (summary)

### Library circulation
| Method | Path |
|---|---|
| GET/POST | `/api/library/circulation/books` |
| GET | `/api/library/circulation/issues` |
| POST | `/api/library/circulation/issue` |
| POST | `/api/library/circulation/issues/{id}/return` |

### Hostel beds
| Method | Path |
|---|---|
| GET/POST | `/api/hostel/beds` |
| GET | `/api/hostel/beds/occupancies` |
| POST | `/api/hostel/beds/allocate` |
| POST | `/api/hostel/beds/occupancies/{id}/release` |

### Transport routes
| Method | Path |
|---|---|
| GET/POST | `/api/transport/routes` |
| GET | `/api/transport/routes/assignments` |
| POST | `/api/transport/routes/assign` |
| POST | `/api/transport/routes/assignments/{id}/end` |

Workflow inboxes remain available as secondary tabs for approvals/notifications.

## Migrations

| Service | Migration |
|---|---|
| student-service | `V4__import_workbench.sql` |
| school-notification-config-service | `V3__comms_announcement.sql` |
| library-service | `V4__library_circulation.sql` |
| hostel-service | `V4__hostel_beds.sql` |
| transport-service | `V4__transport_assignments.sql` |

## Restart note (Windows)

Elevated Java processes may lock JARs — rebuild/restart with admin `start-services.ps1 -Restart` for:
`student-service`, `library-service`, `hostel-service`, `transport-service`,
`school-notification-config-service`, `subscription-service`, `school-settings-service`.

## Smoke

```powershell
.\scripts\verify-phase24.ps1
.\scripts\verify-complete-school-flow.ps1
```

`verify-phase24.ps1` covers: import create → dry-run → commit, Student 360, comms announcement,
library issue/return + clearance, hostel allocate/release, transport assign/end, LMS module settings.

`verify-complete-school-flow.ps1` runs the full school verification chain in dependency order
(admission → enrollment → fee/attendance/exam → ops → portals → Phase 24). Each child script
inserts the demo data it needs.

## Related

- [LMS_CHOICE.md](./LMS_CHOICE.md)
- [PHASE10_OPS_MODULES.md](./PHASE10_OPS_MODULES.md) (original workflow slices)
- [PHASE23_OPS_READINESS.md](./PHASE23_OPS_READINESS.md)
