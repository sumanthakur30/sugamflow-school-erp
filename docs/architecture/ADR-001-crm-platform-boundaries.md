# ADR-001: SugamFlow Generic CRM Platform Boundaries

**Status:** Accepted  
**Date:** 2026-07-30  
**Phase:** 3 — Scale (Campaigns + UTM)  
**Branch:** `feature/crm-platform-mvp` (school + gateway + crm-service + crm-ui)

## Context

SugamFlow must sell a business-agnostic CRM as standalone SaaS and optionally integrate it with School, Hospital, Pharmacy, Retail, and other ERPs — without impacting existing modules, APIs, or schemas.

## Decision

1. **Bounded context:** New `crm-service` + dedicated `crmdb` + `crm-ui` (Phase 1+).  
2. **Topology:** Modular monolith for MVP/Advanced — not 10 microservices.  
3. **ERP integration:** Events/HTTP adapters only (RabbitMQ). No shared DB. No ERP package imports in CRM.  
4. **Licensing:** Extend School `subscription-service` catalog (`business_type=CRM`, plans `crm-starter` / `crm-professional` / `crm-enterprise`). Opt-in only.  
5. **Industry behavior:** Template JSON packs — zero hardcoding of School/Hospital columns in CRM schema.  
6. **Preserve:** Field Force `/api/v1/leads/**` and Platform CRM Renewals `/api/subscription/**/crm/**` unchanged. New sales CRM uses `/api/v1/crm/**` only.

## Consequences

- Existing ERP tenants unchanged until CRM module/plan assigned.  
- Standalone CRM customers need auth + subscription + notification (+ CRM) — not product/stock/order.  
- Gateway routes `/api/v1/crm/**` → `crm-service`.  
- School plan seeders must **skip** CRM plans when merging School feature flags.  
- Phase 2: opportunities + GST quotations live only in `crm-service` / `crm-ui`; Field Force and Renewals unchanged.

## References

- `docs/CRM_PLATFORM_ARCHITECTURE_BLUEPRINT.md`  
- `docs/CRM_LEAD_MANAGEMENT_GAP_ANALYSIS.md`  
- Flyway `V17__crm_standalone_catalog.sql`
