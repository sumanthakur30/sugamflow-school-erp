# CRM Phase 1 — Scaffold + Lead CRUD

**Branch:** `feature/crm-platform-mvp`  
**Service:** `D:\sugamFlow\crm-service` (new git repo)

## Delivered

| Item | Detail |
|------|--------|
| Scaffold | Spring Boot 3.5 / Java 17 / Flyway / Eureka / port **8095** |
| Schema | `crm_workspace`, `crm_pipeline`, `crm_stage`, `crm_lead` (JSONB attributes — no industry columns) |
| APIs | Status, workspace bootstrap, pipelines/stages, Lead CRUD |
| Tenant | `X-Tenant-Id` (string) — multi-tenant isolation |
| Entitlement | Optional `FEATURE_CRM` via subscription-service (`crm.entitlement.enabled`) |
| Isolation | Does **not** touch `/api/v1/leads` (Field Force) or Renewals |

## Run locally

1. `createdb crmdb` (user/pass `crmdb`)  
2. `cd D:\sugamFlow\crm-service && mvn test && mvn spring-boot:run -Dspring-boot.run.profiles=local`  
3. Smoke: see `crm-service/README.md`  
4. Gateway: ensure route `[53]` + Eureka registration (or `GATEWAY_CRM_URI=http://localhost:8095`)

## Still Phase 1 backlog (next slices)

- CSV import · Kanban UI (`crm-ui`) · assignment round-robin · more industry templates  
- docker-compose service entry · DB init script in shared Postgres  

## Non-impact

ERP modules unchanged. CRM off unless tenant uses these APIs / later UI.
