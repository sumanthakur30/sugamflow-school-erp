# Phase 7 — Student master enrollment after admission approve

Config-driven SIS enrollment: final admission `APPROVE` creates a student record via
`student-service`. No school-specific mapping code — form keys + module settings drive it.

| Concern | Source of truth |
|---|---|
| Student fields | Form Builder · `student_master` |
| Enroll gate / form key | Module Settings · `admission.enrollOnApprove`, `studentFormKey` |
| Feature flag | Subscription · `FEATURE_STUDENT_MASTER` |
| Student module | Module Settings · `student` |
| Runtime | **student-service** `:8191` · `/api/student/**` |

## Flow

1. Final workflow approve → status `APPROVED`
2. Offer letter + notifications (Phase 5b)
3. If `enrollOnApprove` → `POST /api/student/enroll-from-admission`
4. Map overlapping form keys (+ optional `admissionFieldMap`)
5. Generate `admissionNo` when `generateAdmissionNo` is true
6. Persist enrollment ref on application (`hasEnrollment`, `enrolledStudentId`)

Idempotent by `(organizationId, sourceApplicationId)`.

See also [PHASE7B_STUDENT_GUARDIANS.md](PHASE7B_STUDENT_GUARDIANS.md) for parent/guardian links.

## APIs

| Method | Path |
|---|---|
| GET | `/api/student/bootstrap` |
| GET | `/api/student/students` |
| GET | `/api/student/students/{id}` |
| POST | `/api/student/enroll-from-admission` |
| PUT | `/api/student/students/{id}/guardians` |

## Config knobs

| Key | Module | Default |
|---|---|---|
| `enrollOnApprove` | admission | `true` |
| `studentFormKey` | admission | `student_master` |
| `generateAdmissionNo` | admission | `true` |
| `admissionFieldMap` | admission | `{}` (admissionKey → studentKey) |
| `formKey` | student | `student_master` |

## Verify

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
.\scripts\start-platform.ps1
.\scripts\start-services.ps1 -Restart
.\scripts\verify-student-enrollment.ps1
```
