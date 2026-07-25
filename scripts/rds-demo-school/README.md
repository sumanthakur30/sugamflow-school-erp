# RDS insert: copy local `demo-school` → production

Exports **all rows** for shop/org `demo-school` from local Postgres and applies them to RDS (SugamFlow `shopdb`/`authdb` + school ERP databases).

## Prerequisites

- Local Postgres has demo data (`localhost:5432`, user `postgres` / `postgres`)
- RDS databases already exist (Flyway schema applied by running school services once)
- `psql` / `pg_dump` client tools installed
- Same JWT secret on school + SugamFlow (login uses shared `authdb`)

## Run order

### 1) Export from local (on your PC)

```powershell
cd D:\school
.\scripts\rds-demo-school\01-export-demo-school.ps1
```

Optional:

```powershell
.\scripts\rds-demo-school\01-export-demo-school.ps1 -OrganizationId demo-school -PhaseAOnly
```

Output folder example:

`D:\school\backups\demo-school-20260725-091500\`

Contains:

- `shopdb/shops.csv` + column list
- `authdb/auth_account.csv` + column list
- one folder per `school_*_db` with per-table CSV
- `manifest.json`

### 2) Copy export folder to a machine that can reach RDS

WinSCP the whole `demo-school-*` folder to EC2 (e.g. `/opt/school/backups/...`) **or** run import from your PC if RDS security group allows your IP.

### 3) Import into RDS

```powershell
.\scripts\rds-demo-school\02-import-demo-school-to-rds.ps1 `
  -ExportDir 'D:\school\backups\demo-school-YYYYMMDD-HHMMSS' `
  -RdsHost 'YOUR_RDS_ENDPOINT.eu-north-1.rds.amazonaws.com' `
  -AdminUser 'postgres' `
  -AdminPassword 'YOUR_MASTER_PASSWORD' `
  -Replace
```

`-Replace` deletes existing `demo-school` rows in target tables before insert (safe merge for that tenant only).

SSL is on by default (`sslmode=require`).

### 4) Verify login

Production school UI:

- Organization: `demo-school`
- Username: `admin` (stored as `admin_demo-school`)
- Password: same as local (usually `password`)

Also seeded: `teacher_demo-school`, `parent_demo-school` (password from local hashes).

## What gets copied

| Target DB | Filter |
|-----------|--------|
| `shopdb.shops` | `shop_id = demo-school` |
| `authdb.auth_account` | `shop_id = demo-school` |
| `school_subscription_db.subscription_plan` | all plans (global catalog) |
| every `school_*` table with `organization_id` | `organization_id = demo-school` |

Platform-default rows (`organization_id IS NULL` in forms/rules) are **not** copied; services recreate those.

## Notes

- Do **not** full-restore dumps into multi-tenant RDS — these scripts are tenant-scoped.
- Re-run export anytime local demo data changes, then import with `-Replace`.
- Phase A only: pass `-PhaseAOnly` on export (settings, subscription, admission, fee, student, staff, academic).
