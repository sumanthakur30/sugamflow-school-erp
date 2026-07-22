# LMS choice — native homework vs external LMS

## Decision (Phase 24)

**Ship native homework MVP first** under `FEATURE_LMS`, with module settings that can later switch to an external LMS.

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| Native homework (assignments + due dates + parent/teacher visibility) | Fits existing tenant model, portals, notifications; no SSO/vendor lock | Not a full LMS (no SCORM, forums, grade sync depth) | **Chosen for MVP** |
| Moodle / Google Classroom / Canvas integrate | Rich pedagogy tools schools already know | SSO, roster sync, multi-tenant complexity, ops burden | Phase 25+ via `mode=EXTERNAL` |

## Settings (`moduleKey=lms`)

Defaults in `SettingsConfigService.applyLmsDefaults`:

| Key | Default | Meaning |
|---|---|---|
| `requiredFeatureFlag` | `FEATURE_LMS` | Plan gate (seeded on all plans) |
| `mode` | `NATIVE_HOMEWORK` | or `EXTERNAL` |
| `externalProvider` | `NONE` | `MOODLE` / `GOOGLE_CLASSROOM` / `CANVAS` later |
| `homeworkEnabled` | `true` | Native assignment surface |
| `assignmentNotifyChannels` | `IN_APP`, `EMAIL` | Delivery channels |

## Next slices

1. ~~Homework entity + teacher create / student submit APIs~~ (`/api/exam/homework/**`)
2. ~~Teacher portal + parent visibility~~ (`/teacher/homework`, `/parent/homework`, `/api/exam/homework/mine`)
3. Optional gradebook link (marks from homework).
4. External adapter interface when `mode=EXTERNAL`.
