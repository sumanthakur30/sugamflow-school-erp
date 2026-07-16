# SugamFlow School ERP — Architecture

## Goals

- School management is a **separate Angular UI** (`apps/school-ui`) and **school-domain microservices**.
- Platform infra is **shared** with SugamFlow (`D:/sugamflow`): Eureka, Spring Cloud Config, gateway, notification delivery.
- Everything school-facing is **configuration-driven**.

## Topology

```text
                    ┌─────────────────────────┐
                    │   school-ui (Angular)    │
                    └────────────┬────────────┘
                                 │ :4200 → :9090
                    ┌────────────▼────────────┐
                    │ sugamflow gateway-service│
                    │     (+ auth / CORS)      │
                    └────────────┬────────────┘
                                 │ Eureka lb://
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
  school-settings-*      form / workflow / rule     sugamflow
  subscription           report / audit             notification-service
  notification-config                               (delivery)
                                 │
                    ┌────────────▼────────────┐
                    │ discovery-service :8761 │
                    │ config-service    :8888 │
                    └─────────────────────────┘
```

See [PLATFORM_INTEGRATION.md](PLATFORM_INTEGRATION.md) for ports and route map.

## Config resolution order

1. Platform defaults  
2. Subscription plan feature flags & limits  
3. Organization settings  
4. Branch overrides  
5. Academic session overrides  
6. Role-based UI / menu filters  

## Tenancy headers

- `X-Tenant-Id` (organization)
- `X-Branch-Id`
- `X-Academic-Session-Id`
- `X-User-Id` / `X-Role-Code`
