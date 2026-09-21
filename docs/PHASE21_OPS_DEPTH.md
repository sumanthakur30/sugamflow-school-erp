# Phase 21 — Ops depth

Config-driven operational masters and domain previews for Library, Hostel,
Transport, and Payroll — extending Phase 10 workflow slices with JSONB
definition catalogs (same pattern as Finance / Academic Lifecycle).

## Feature flags

| Flag | Role |
|---|---|
| `FEATURE_OPS_DEPTH` | Required for all `/api/{module}/ops/**` endpoints |
| `FEATURE_LIBRARY` | Library ops + records |
| `FEATURE_HOSTEL` | Hostel ops + records |
| `FEATURE_TRANSPORT` | Transport ops + records |
| `FEATURE_PAYROLL` | Payroll ops + records |

## Module settings (per ops module)

| Module | Keys added |
|---|---|
| `library` | `mastersEnabled`, `defaultFinePolicyKey`, `defaultCirculationPolicyKey` |
| `hostel` | `mastersEnabled`, `defaultBlockKey`, `defaultRoomTypeKey`, `defaultAllocationPolicyKey` |
| `transport` | `mastersEnabled`, `defaultRouteKey`, `defaultFareSlabKey` |
| `payroll` | `mastersEnabled`, `defaultSalaryStructureKey`, `defaultPayCycleKey` |

## Storage

Each service has `ops_definition` (versioned JSONB, tenant-scoped) — mirrors
`finance_definition` / `lifecycle_definition`.

## APIs

### Library (`:8194`)

| Method | Path |
|---|---|
| GET | `/api/library/ops/bootstrap` |
| GET/PUT | `/api/library/ops/categories`, `/categories/{key}` |
| GET/PUT | `/api/library/ops/fine-policies`, `/fine-policies/{key}` |
| POST | `/api/library/ops/fines/preview` |

### Hostel (`:8195`)

| Method | Path |
|---|---|
| GET | `/api/hostel/ops/bootstrap` |
| GET/PUT | `/api/hostel/ops/room-types`, `/room-types/{key}` |
| POST | `/api/hostel/ops/allocations/preview` |

### Transport (`:8196`)

| Method | Path |
|---|---|
| GET | `/api/transport/ops/bootstrap` |
| GET/PUT | `/api/transport/ops/routes`, `/routes/{key}` |
| POST | `/api/transport/ops/fares/preview` |

### Payroll (`:8197`)

| Method | Path |
|---|---|
| GET | `/api/payroll/ops/bootstrap` |
| GET/PUT | `/api/payroll/ops/structures`, `/structures/{key}` |
| POST | `/api/payroll/ops/payslips/preview` |

Existing `/api/{module}/records/**` unchanged. Module bootstrap embeds `ops`
when `FEATURE_OPS_DEPTH` is on.

## UI

Admin → **Ops Depth** (`/admin/ops`): tabbed masters browse + preview actions
for all four modules.

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-ops-depth.ps1
```

Keep `verify-library.ps1`, `verify-hostel.ps1`, `verify-transport.ps1`, and
`verify-payroll.ps1` green for the workflow record slices.
