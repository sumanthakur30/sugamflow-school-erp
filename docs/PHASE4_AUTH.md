# Phase 4 — Auth integration (SugamFlow auth-service + JWT gateway)

School UI signs in through **SugamFlow `auth-service`**; school APIs require a valid JWT at the gateway.

## Start order

```powershell
cd D:\school

# 1) Eureka, auth (:8085), shop (:8080), gateway (:9090)
.\scripts\start-platform.ps1

# 2) Demo org + admin in authdb/shopdb (first time)
.\scripts\seed-school-demo-auth.ps1

# 3) School microservices :8181-8188
.\scripts\start-services.ps1 -Restart

# 4) Verify JWT login + school routes
.\scripts\verify-auth.ps1

# 5) School UI
cd apps\school-ui
npm start
```

Open http://localhost:4200 → redirects to `/login`.

## Demo credentials

| Field | Value |
|---|---|
| Organization ID | `demo-school` |
| Username | `admin` (scoped to `admin_demo-school`) |
| Password | `password` |

## How it works

1. **Login** — `POST /api/v1/auth/login` via gateway (`shopId` = organization slug).
2. **JWT** — school-ui stores `accessToken` and sends `Authorization: Bearer …` on every API call.
3. **Gateway** — validates JWT and maps claims to school headers:
   - `X-Tenant-Id` ← organization (from UI or JWT `shopId`)
   - `X-User-Id` / `X-Role-Code` ← JWT subject / role
   - `X-Branch-Id`, `X-Academic-Session-Id` ← UI selection (defaults `main`, `2025-26`)
4. **School services** — `TenantFilter` reads those headers (falls back to `X-Auth-*` from gateway).

## Platform services

| Service | Port | Required for |
|---|---|---|
| `auth-service` | 8085 | Login, JWT issuance |
| `shop-service` | 8080 | Shop status check during login (`ShopLoginGuard`) |
| `gateway-service` | 9090 | JWT enforcement + routing |

Skip auth/shop if already running elsewhere:

```powershell
.\scripts\start-platform.ps1 -SkipAuthServices
```

## Super admin

Platform `superadmin` / `PLATFORM` / `password` (local seeder) can access school UI when sending `X-Tenant-Id: demo-school` after login.

## Notes

- Gateway JWT secret must match auth-service (`security.jwt.secret`).
- Unauthenticated school API calls return **401** from the gateway.
- MFA-enabled accounts must complete MFA in SugamFlow shop UI until school-ui adds an MFA step.
