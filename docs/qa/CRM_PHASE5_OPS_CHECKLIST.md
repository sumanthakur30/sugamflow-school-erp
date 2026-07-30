# CRM non-impact + catalog smoke (Phase 5)

Companion to Phase 0 checklist. Run after deploying CRM gateway route / catalog changes.

## Field Force / Renewals (no regression)

| # | Check | Pass? |
|---|--------|-------|
| 1 | `GET/POST /api/v1/leads/**` still routes to fieldforce-service | |
| 2 | Platform renewals `/api/subscription/**/crm/**` unchanged | |
| 3 | Gateway `Path=/api/v1/crm/**` → crm-service only | |
| 4 | School UI has no accidental CRM Super Admin plan CRUD | |

## Catalog

| # | Check | Pass? |
|---|--------|-------|
| 5 | `business_type=CRM` present after V17 | |
| 6 | Plans `crm-starter` / `crm-professional` / `crm-enterprise` | |
| 7 | Flag `FEATURE_CRM` true on professional; `FEATURE_CRM_QUOTE` true on professional+ | |

## Flyway shared env

| # | Check | Pass? |
|---|--------|-------|
| 8 | crmdb Flyway table `flyway_schema_history_crm` at V10+ | |
| 9 | No industry columns on `crm_lead` (geo in nullable columns / JSON only) | |

## CI hooks

- crm-service: `.github/workflows/ci.yml` unit tests + compile
- school: catalog assertion script / workflow step for V17 CRM flags (see `scripts/verify-crm-catalog.sql` if present)
