# CRM Phase 0 — Prep status

**Started:** 2026-07-30  
**Branches:**  
- School: `feature/crm-platform-mvp`  
- Gateway: `D:\sugamFlow\gateway-service` → `feature/crm-platform-mvp`

## Done

| Item | Location |
|------|----------|
| ADR (modular monolith, events-only ERP) | `docs/architecture/ADR-001-crm-platform-boundaries.md` |
| CRM catalog + plans + add-ons | `services/subscription-service/.../V17__crm_standalone_catalog.sql` |
| Seeder skips School-flag merge on CRM plans | `SubscriptionPlanSeeder` + `SubscriptionPlan.crm*()` |
| Gateway path reservation | `gateway-service/.../application.properties` route `[53]` |
| Non-impact checklist | `docs/qa/CRM_PHASE0_NON_IMPACT_CHECKLIST.md` |

## Explicitly not in Phase 0

- ~~`crm-service` / `crmdb` / `crm-ui`~~ → **Phase 1 started:** `crm-service` + Lead CRUD (see `CRM_PHASE1_STATUS.md`)  
- CRM nav in ERP UIs  
- Assigning CRM plan to existing tenants  
- Changes to Field Force or Renewals APIs  

## Next (Phase 1 continued)

Scaffold done. Remaining: import, crm-ui, round-robin assignment, compose wiring.
