# CRM Retail pilot — go / no-go checklist

**Vertical:** RETAIL (template `RETAIL.json`)  
**Branch:** `feature/crm-platform-mvp`  
**Profile:** `local,pilot-retail`

## Prerequisites

1. Postgres `crmdb` up; Flyway through **V10**.
2. `subscription-service` on `:8182` with V17 CRM catalog; assign **`crm-professional`** to pilot tenant (enables `FEATURE_CRM` + `FEATURE_CRM_QUOTE`).
3. Optional: `notification-service` on `:8087`.
4. Start CRM:

```powershell
cd D:\sugamFlow\crm-service
mvn spring-boot:run "-Dspring-boot.run.profiles=local,pilot-retail"
cd D:\sugamFlow\crm-ui
npm start
```

## E2E path (must pass)

| # | Step | Expected |
|---|------|----------|
| 1 | UI tenant = pilot org; Ping | `crm-service phase 5-pilot` |
| 2 | Bootstrap template **RETAIL** | Workspace + Quote stage |
| 3 | Campaigns → create ACTIVE campaign | `publicKey` present |
| 4 | Public capture (UI demo or `POST /api/v1/crm/public/capture/{key}`) | Lead created; score &gt; 0 (`CAMPAIGN_CAPTURE` ± email/phone) |
| 5 | Create opportunity → GST quote → Send | Quote SENT; score bumps `QUOTE_SENT`; notification `QUEUED` or fail-open |
| 6 | Convert lead `SHOP_CUSTOMER` | Convert event `SENT` (local ERP sink by default) |
| 7 | AI / Enterprise → Audit export | Job `DONE` with leads, quotes, converts, scoreEvents |

## Entitlement negative check

With `crm.entitlement.enabled=true` and tenant **without** CRM plan → authenticated CRM APIs return 403 `FEATURE_CRM`.

Starter plan (no `FEATURE_CRM_QUOTE`) → quote APIs 403.

## Live ERP adapters (optional)

Override env to leave CRM sinks:

```text
CRM_CONVERT_SHOP_URL=http://localhost:{user-or-shop}/api/v1/customers/from-crm
CRM_CONVERT_SCHOOL_URL=http://localhost:{admission}/api/v1/inquiries/from-crm
CRM_CONVERT_FF_URL=http://localhost:8090/api/v1/leads/from-crm
```

## Non-impact (must stay green)

See `docs/qa/CRM_PHASE0_NON_IMPACT_CHECKLIST.md` — Field Force `/api/v1/leads/**` and Renewals unchanged.
