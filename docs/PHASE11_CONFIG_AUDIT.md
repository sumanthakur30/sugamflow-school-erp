# Phase 11 — Config audit + rollback UI

Deepens the existing `audit-service` and Admin **Audit** screen into configuration version
control with approval and restore, gated by `FEATURE_AUDIT_LOGS`.

## Components

| Piece | Role |
|---|---|
| `audit-service` `:8188` | Store config change history; approve; rollback (+ apply restore) |
| `school-settings-service` | On intentional PUTs, POST old/new snapshots to audit |
| `apps/school-ui` `/admin/audit` | Filters, detail JSON diff, approve / rollback |
| Subscription | `FEATURE_AUDIT_LOGS` (seeded on starter-tier plans) |

## APIs

| Method | Path | Notes |
|---|---|---|
| GET | `/api/audit/bootstrap` | `featureEnabled`, entity types, statuses |
| GET | `/api/audit/config-changes` | Query: `entityType`, `status`, `entityKey` |
| GET | `/api/audit/config-changes/{id}` | Detail + `canApprove` / `canRollback` |
| POST | `/api/audit/config-changes` | Record change (manual or from settings) |
| POST | `/api/audit/config-changes/{id}/approve` | `PENDING_APPROVAL` → `APPROVED` |
| POST | `/api/audit/config-changes/{id}/rollback` | Body optional `{ reason }`; restores settings |

## Audited writers (school-settings)

| Entity type | Key | Trigger |
|---|---|---|
| `DESIGN_THEME` | `theme` | Design Studio save / publish |
| `MODULE_SETTINGS` | `{moduleKey}` | Module settings PUT |
| `LOCALIZATION` | `localization` | Localization PUT |
| `MENU_CONFIG` | `menu` | Menu builder PUT |

Rollback restore calls the matching settings PUT with header `X-Skip-Config-Audit: true`
so restores are not re-audited as new pending changes.

## Status flow

```
PENDING_APPROVAL ──approve──► APPROVED ──rollback──► ROLLED_BACK
                                      │
                                      └── creates sibling entry (APPROVED, rollbackOf=id)
                                          with swapped old/new after settings restore
```

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-audit.ps1
```

Requires gateway `:9090`, subscription `:8182`, settings `:8181`, audit `:8188`.
