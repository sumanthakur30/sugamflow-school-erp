# CRM Phase 1 — Status (updated)

**crm-service:** https://github.com/sumanthakur30/crm-service/tree/feature/crm-platform-mvp  
**crm-ui:** `D:\sugamFlow\crm-ui` (local git `feature/crm-platform-mvp`)

## Done

| Slice | Status |
|-------|--------|
| Scaffold + Lead CRUD + workspace/pipeline | Done (committed/pushed) |
| CSV/XLSX import | Done |
| Round-robin assignment + team members | Done |
| Minimal crm-ui (tenant, list, create, import) | Done — port **4400** |
| Compose profile `crm` | Done (optional) |

## Run

```powershell
# DB
createdb crmdb

# API
cd D:\sugamFlow\crm-service
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# UI
cd D:\sugamFlow\crm-ui
npm start
# http://localhost:4400  (proxies /api → :8095)
```

Optional Docker: `docker compose --profile crm up -d crm-service`

## Next Phase 1 / 2

Kanban board · saved filters · more templates · ERP event adapters · quotes
