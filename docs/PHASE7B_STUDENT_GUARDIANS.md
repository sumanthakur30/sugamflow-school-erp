# Phase 7b — Parent / guardian link on student

Config-driven guardians on `student_record.answers.guardians`, shaped by Form Builder
`parent_master`. No separate parent-service.

| Concern | Source of truth |
|---|---|
| Guardian fields | Form Builder · `parent_master` |
| Admission capture | Form Builder · `admission_form` guardian section (flat keys) |
| Mapping | Module Settings · `student.guardianFieldMap` |
| Storage key | Module Settings · `student.guardiansAnswerKey` (default `guardians`) |
| Feature | `FEATURE_STUDENT_MASTER` |

## Flow

1. Admission submit may include `guardianFullName`, `guardianRelation`, `guardianMobile`, `guardianEmail`
2. Final approve → enroll-from-admission maps flat keys → `answers.guardians[]`
3. Staff can `PUT /api/student/students/{id}/guardians` to replace the list (validated against `parent_master`)

## APIs

| Method | Path |
|---|---|
| GET | `/api/student/bootstrap` (includes `parentForm`) |
| PUT | `/api/student/students/{id}/guardians` `{ "guardians": [...] }` |

## Module knobs (`student`)

| Key | Default |
|---|---|
| `parentFormKey` | `parent_master` |
| `guardiansAnswerKey` | `guardians` |
| `captureGuardiansOnEnroll` | `true` |
| `maxGuardians` | `4` |
| `guardianFieldMap` | admission → parent field keys |

## Verify

```powershell
.\scripts\verify-student-guardians.ps1
```
