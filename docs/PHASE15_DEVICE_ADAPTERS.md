# Phase 15 — AI attendance / device adapters

Config-driven **device adapter registry** for biometric, face, GPS, and AI camera
punches. No vendor SDKs in school code — adapters are metadata + normalized ingest
that can auto-submit into the existing attendance form/workflow.

## Feature flags

| Flag | Adapter types unlocked |
|---|---|
| `FEATURE_BIOMETRIC` | `BIOMETRIC` |
| `FEATURE_FACE_RECOGNITION` | `FACE` |
| `FEATURE_GPS` | `GPS` |
| `FEATURE_AI` | `AI_CAMERA` |
| `FEATURE_ATTENDANCE` | Required for all device APIs |

## Module settings (`attendance`)

| Key | Default | Role |
|---|---|---|
| `aiAttendanceEnabled` | `true` | Master switch for device → attendance |
| `autoSubmitFromDevice` | `true` | Create attendance records on ingest |
| `minConfidence` | `0.8` | Reject low-confidence events |
| `acceptedAdapterTypes` | all four | Allow-list |

## APIs (`attendance-service` `:8192`)

| Method | Path |
|---|---|
| GET | `/api/attendance/devices/bootstrap` |
| GET/POST | `/api/attendance/devices` |
| PUT | `/api/attendance/devices/{id}` |
| GET | `/api/attendance/devices/events?deviceId=` |
| POST | `/api/attendance/devices/{id}/events` |
| POST | `/api/attendance/devices/{id}/simulate` |

Event statuses: `SUBMITTED`, `ACCEPTED`, `REJECTED_LOW_CONFIDENCE`, `IGNORED_AI_OFF`,
`SUBMIT_FAILED`.

## UI

Admin → **Device Adapters** (`/admin/devices`) + link from AI Config.

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-device-adapters.ps1
```

Requires gateway `:9090`, subscription `:8182`, settings `:8181`, attendance `:8192`,
plus form/workflow engines used by attendance submit.
