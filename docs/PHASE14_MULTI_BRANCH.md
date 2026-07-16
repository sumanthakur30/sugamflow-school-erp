# Phase 14 — Multi-branch UX depth

First-class **campus / branch** registry with switcher UX and plan limits. Tenant
header `X-Branch-Id` already scoped settings (theme, modules, localization); this
phase makes branches visible, selectable, and manageable without school-specific code.

## Feature flag & limit

| Control | Meaning |
|---|---|
| `FEATURE_MULTI_BRANCH` | Enables campus switcher + create/manage UI |
| Plan limit `maxBranches` | Cap on campuses (`-1` = unlimited). Starter demo raised to `3`. |

## Data

Table `org_branch` (school-settings DB): `branch_key`, `name`, `code`, `city`,
`address`, `status`, `is_default`, JSON `payload`.

Default campus `main` / "Main Campus" is auto-created per organization.

## APIs

| Method | Path |
|---|---|
| GET | `/api/config/branches/bootstrap` |
| GET | `/api/config/branches` |
| GET | `/api/config/branches/{branchKey}` |
| POST | `/api/config/branches` |
| PUT | `/api/config/branches/{branchKey}` |

Bootstrap returns `featureEnabled`, `maxBranches`, `canAdd`, `currentBranch`, `branches`.
Creates are audited as `ORG_BRANCH`.

## UI

- Campus switcher in Admin + Parent/Teacher portal shells (`sf.branchId` → `X-Branch-Id`)
- Admin → **Campuses** (`/admin/branches`) — list, edit, set default, add campus
- Session label shows active branch key

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-branches.ps1
```

Requires gateway `:9090`, subscription `:8182`, settings `:8181`.
