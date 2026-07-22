# School registration & login — required services

Option A: **super admin registers the school** in shop-management-ui; **school owner logs in** via school-ui. Both talk to the same gateway (`:9090`).

---

## Architecture (what each piece does)

```text
PostgreSQL (:5432)
    authdb, shopdb, school_* DBs

discovery-service (:8761)     ← Eureka — everything registers here
    │
auth-service (:8085)          ← login, JWT, owner invites
shop-service (:8080)          ← org registry (school = shop with businessType=SCHOOL)
notification-service (:8087)  ← set-password / invite email
MailHog (:1025 / :8025)       ← optional local SMTP catcher
    │
gateway-service (:9090)       ← single API edge (JWT + routes)
    │
school microservices (:8181–8198)  ← settings, subscription, admission, …
    │
UIs:
  shop-management-ui (:4200)  → Register school (SUPER_ADMIN)
  school-ui (:4300)           → School owner login + ERP
```

---

## Minimum services by goal

| Goal | Must be UP |
|------|------------|
| **Register school** (Option A) | Postgres, Eureka, auth, shop, gateway, notification (+ MailHog if you want invite email). shop-management-ui. |
| **School login + ERP** | Postgres, Eureka, auth, shop, gateway, school services (`start-services.ps1`), school-ui. |
| **Demo login only** (`demo-school`) | Same as login; seed once with `seed-school-demo-auth.ps1` (skips invite email). |

`config-service` (:8888) is **not** required for local school start (scripts disable config import).

---

## Prerequisites (once)

| Check | Expected |
|--------|----------|
| PostgreSQL | `:5432` — user/password typically `postgres` / `postgres` |
| Java 17+ | `java -version` |
| Maven | `mvn -v` |
| Node / npm | For both UIs |
| Platform jars | Built under `D:\sugamFlow\*\target\` |

Build platform jars if missing:

```powershell
cd D:\sugamFlow
mvn -q -DskipTests package `
  -pl discovery-service,gateway-service,auth-service,shop-service,notification-service `
  -am
```

---

## Start order (copy-paste)

### 1) Platform — Eureka, auth, shop, notification, gateway

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"
```

If Docker Desktop is off (MailHog fails):

```powershell
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow" -SkipMailHog
```

Wait for:

| Service | Port | Health |
|---------|------|--------|
| discovery-service | 8761 | http://localhost:8761 |
| auth-service | 8085 | http://localhost:8085/actuator/health |
| shop-service | 8080 | http://localhost:8080/actuator/health |
| notification-service | 8087 | http://localhost:8087/actuator/health |
| gateway-service | 9090 | http://localhost:9090/actuator/health |
| MailHog (optional) | 1025 / 8025 | http://localhost:8025 |

### 2) School domain microservices

Needed for school-ui after login (modules, campuses, admission, etc.):

```powershell
cd D:\school
.\scripts\start-services.ps1
```

Stuck ports:

```powershell
.\scripts\start-services.ps1 -Restart
```

| Port | Service |
|------|---------|
| 8181 | school-settings-service |
| 8182 | subscription-service |
| 8183 | form-builder-service |
| 8184 | workflow-service |
| 8185 | rule-engine-service |
| 8186 | report-builder-service |
| 8187 | school-notification-config-service |
| 8188 | audit-service |
| 8189 | admission-service |
| 8190 | fee-service |
| 8191 | student-service |
| 8192 | attendance-service |
| 8193 | exam-service |
| 8194 | library-service |
| 8195 | hostel-service |
| 8196 | transport-service |
| 8197 | payroll-service |
| 8198 | staff-service |

### 3A) Register a new school (super admin)

```powershell
cd D:\sugamFlow\shop-management-ui
npx ng serve --port 4200
```

1. Open shop UI: http://localhost:4200
2. Login: org/workspace **`PLATFORM`**, user **`superadmin`**, password = your local super-admin password
3. Sidebar → **Register school** (`/admin/create-school`)
4. Paste Admin API key if prompted (local env often has `dev-shop-admin-key`)
5. Fill School ID, name, owner email/username, state/city → submit
6. Copy set-password link (or open MailHog `:8025`)
7. Owner opens link → sets password

School ID (e.g. `sunrise-public`) = Organization on school login.  
Username is scoped as `ownerUsername_schoolId`.

### 3B) School owner login (school ERP)

Start school-ui on the fixed local school port:

```powershell
cd D:\school\apps\school-ui
npx ng serve --port 4300
```

Login:

| Field | Value |
|--------|--------|
| Organization | school ID from registration (or `demo-school`) |
| Username | invited owner (or `admin` for demo) |
| Password | password set via invite (demo: `password`) |

### 4) Demo school only (no register UI)

```powershell
cd D:\school
.\scripts\seed-school-demo-auth.ps1
```

Then school-ui → `demo-school` / `admin` / `password`.

---

## Login flow (what calls what)

```text
school-ui  POST /api/v1/auth/login  →  gateway :9090
                                      →  auth-service :8085
                                      →  shop-service :8080  (org ACTIVE?)
                                      →  JWT returned
school-ui  APIs with Bearer + X-Tenant-Id
                                      →  gateway validates JWT
                                      →  school services :8181+
```

Register flow:

```text
shop-management-ui  POST /api/v1/admin/shops  (+ X-Admin-Api-Key)
                                      →  gateway
                                      →  shop-service (create org, businessType=SCHOOL)
                                      →  auth-service (owner invite)
                                      →  notification-service (email)
```

---

## Verify

```powershell
cd D:\school
.\scripts\verify-auth.ps1
.\scripts\verify-gateway.ps1
```

Manual:

```powershell
Invoke-WebRequest http://localhost:8761/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:9090/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:8085/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:8181/actuator/health -UseBasicParsing
```

---

## One-liner (login-ready stack)

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow" -SkipMailHog
.\scripts\start-services.ps1
cd apps\school-ui; npx ng serve --port 4300
```

For registration, also run `shop-management-ui` on `:4200` and open **Register school**.

---

## Common failures

| Symptom | Cause / fix |
|---------|-------------|
| Login `Unknown Error` on `:9090` | Gateway/Eureka down → `start-platform.ps1` |
| Login 401 | Wrong org/user; seed demo or finish set-password invite |
| Register fails / admin key | Paste `SHOP_ADMIN_API_KEY` on create-school page |
| Invite email missing | Start without `-SkipMailHog`, or copy link from onboard success card |
| School modules empty / 503 | `start-services.ps1` after Eureka is up |
| Port 4200 busy | Keep `:4200` for shop-management-ui; run school-ui with `--port 4300` |

See also: [DAILY_START.md](DAILY_START.md), [PHASE4_AUTH.md](PHASE4_AUTH.md), [PHASE14_MULTI_BRANCH.md](PHASE14_MULTI_BRANCH.md).
