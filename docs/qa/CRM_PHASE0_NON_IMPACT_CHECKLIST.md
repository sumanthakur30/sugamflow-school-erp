# CRM Phase 0 — Non-impact / smoke checklist

Use before merging `feature/crm-platform-mvp`. Goal: CRM prep must not change ERP behavior for tenants without CRM.

## Automated / manual smoke

| # | Check | Pass criteria |
|---|--------|---------------|
| 1 | Gateway existing routes | `/api/v1/products`, `/api/v1/orders`, `/api/v1/auth`, `/api/v1/leads` (Field Force) still route as before |
| 2 | CRM path reserved | `/api/v1/crm/**` matches `crm-service` route only (503 OK until Phase 1) |
| 3 | Field Force isolation | `/api/v1/leads` does **not** go to crm-service |
| 4 | Renewals isolation | `/api/subscription/crm/**` (or renewals aliases) still hit subscription-service |
| 5 | School plans untouched | Existing `starter` / `professional` / `enterprise` feature JSON unchanged by CRM seeder |
| 6 | CRM plans additive | `crm-starter`, `crm-professional`, `crm-enterprise` present after Flyway V17 |
| 7 | Catalog | `business_type=CRM` + CRM modules/features/limits visible in Super Admin catalog APIs |
| 8 | No CRM UI leak | shop-management-ui / school-ui have **no** new CRM nav (Phase 0) |
| 9 | Subscription seeder | Restart subscription-service: School flags not copied onto `crm-*` plans |
| 10 | DB | No migrations on shop/product/stock/order/fieldforce schemas |

## Quick probes (local)

```bash
# Field Force path still fieldforce (expect auth/business response, not crm)
curl -s -o /dev/null -w "%{http_code}" http://localhost:9090/api/v1/leads

# CRM path reserved (503/404 from gateway until crm-service up — not 200 ERP)
curl -s -o /dev/null -w "%{http_code}" http://localhost:9090/api/v1/crm/health

# Catalog lists CRM business type (with gateway-verified headers as required)
curl -s "http://localhost:8182/api/subscription/catalog/business-types" \
  -H "X-Gateway-Verified: true"
```

## Sign-off

| Role | Name | Date | OK |
|------|------|------|----|
| Backend | | | |
| QA | | | |

## Phase 0 exit

- [x] Branches created  
- [x] Catalog SKUs + Flyway V17  
- [x] Gateway `/api/v1/crm/**` reserved  
- [x] ADR-001 accepted  
- [x] This checklist filed  
- [ ] Local Flyway applied + catalog smoke (ops)  
- [ ] Gateway restarted with new route (ops)  
