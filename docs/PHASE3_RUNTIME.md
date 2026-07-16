# Phase 3 — Platform runtime (Eureka + Gateway)

School services reuse SugamFlow **discovery-service** and **gateway-service**.

## Start order

```powershell
cd D:\school

# 1) Eureka :8761 + Gateway :9090 (from D:\sugamflow jars)
.\scripts\start-platform.ps1

# 2) School microservices :8181-8188 (register with Eureka)
#    Use -Restart if ports are already taken by an older run
.\scripts\start-services.ps1
# .\scripts\start-services.ps1 -Restart

# 3) Verify gateway routing
.\scripts\verify-gateway.ps1

# 4) School UI (already points at http://localhost:9090)
cd apps\school-ui
npm start
```

## Endpoints

| Component | URL |
|---|---|
| Eureka dashboard | http://localhost:8761 |
| API gateway | http://localhost:9090 |
| School UI | http://localhost:4200 |
| Example API | http://localhost:9090/api/config/design-studio/theme |

Required headers for school APIs:

- `X-Tenant-Id`
- `X-Branch-Id`
- optional: `X-Academic-Session-Id`, `X-User-Id`, `X-Role-Code`

## Notes

- Gateway school routes are configured in `D:\sugamflow\gateway-service` (`lb://school-*-service`).
- `start-platform.ps1` rebuilds the gateway jar when sources are newer and restarts `:9090`.
- School paths (`/api/config`, `/api/subscription`, `/api/forms`, …) require a valid JWT (Phase 4). See [PHASE4_AUTH.md](PHASE4_AUTH.md).
- If Docker maps Eureka to host port `18761`, set:
  `$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE='http://localhost:18761/eureka'`
  before `start-services.ps1`. Prefer host jars via `start-platform.ps1` (uses `:8761`).
