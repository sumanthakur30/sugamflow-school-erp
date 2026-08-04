# School application — daily start

Use this checklist every day to bring up **SugamFlow School ERP** locally.

**Repos:** `D:\school`  
**Platform (shared):** `D:\sugamFlow` (Eureka, gateway, auth, shop, notification)

---

## Prerequisites (once per machine / after reboot)

| Check | Expected |
|--------|----------|
| PostgreSQL | Listening on `:5432` (`postgres` / `postgres`) |
| Java 17+ | `java -version` |
| Maven | `mvn -v` |
| Node / npm | For `apps\school-ui` |
| SugamFlow jars | Built under `D:\sugamFlow\*\target\*.jar` (discovery, gateway, auth, shop, notification) |
| Docker Desktop | Optional — only if you want MailHog for email UI (`:8025`) |

If platform jars are missing:

```powershell
cd D:\sugamFlow
mvn -q -DskipTests package -pl discovery-service,gateway-service,auth-service,shop-service,notification-service -am
```

---

## Daily start (order matters)

**Preferred (sequenced scripts):** see `D:\sugamFlow\scripts\sequences\README.md`

```powershell
cd D:\sugamFlow
.\scripts\sequences\00-common-platform.ps1 -SkipMailHog
# Eureka is on host :8761 by default (same as school jars). -ExposeSchoolPorts is optional/legacy.
.\scripts\sequences\03-school-erp.ps1 -WithUi
# or from school repo:
# .\scripts\start-school-sequence.ps1 -StartCommon -WithUi
```

**Rule:** one Eureka on host **`:8761`**. Do not run jar `discovery-service` and Docker discovery at the same time. Details: [EUREKA_ONE_PORT.md](EUREKA_ONE_PORT.md).

### Legacy (same order)

Open PowerShell and run from `D:\school`:

### 1) Platform (Eureka + auth + shop + gateway)

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"
```

If Docker Desktop is **not** running (MailHog fails):

```powershell
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow" -SkipMailHog
```

**Wait until you see:** Eureka UP (`:8761`), gateway UP (`:9090`).

Script skips services that are already listening — safe to re-run.

### 2) Demo login seed (only if login fails / first time / fresh DB)

```powershell
.\scripts\seed-school-demo-auth.ps1
```

### 3) School domain services

```powershell
.\scripts\start-services.ps1
```

Rebuild + restart school ports if something is stuck:

```powershell
.\scripts\start-services.ps1 -Restart
```

### 4) School UI

```powershell
cd D:\school\apps\school-ui
npx ng serve --port 4300
```

---

## Open & login

| Item | Value |
|------|--------|
| UI | http://localhost:4300 |
| Gateway | http://localhost:9090 |
| Eureka | http://localhost:8761 |
| Organization | `demo-school` |
| Username | `admin` (stored as `admin_demo-school`) |
| Password | `password` |

MailHog (email catcher, if started): http://localhost:8025

---

## Quick health checks

```powershell
cd D:\school

# Auth + gateway path
.\scripts\verify-auth.ps1

# Optional: gateway school routes
.\scripts\verify-gateway.ps1

# Operational readiness (probes + prometheus + correlation)
.\scripts\verify-ops-readiness.ps1

# Pilot go/no-go (ops + portals + RBAC + attendance alerts)
.\scripts\verify-pilot-ready.ps1
```

Before a campus pilot day, also follow `docs/PILOT_RUNBOOK.md` (backup, alert triage, persona smoke).

```powershell
# Snapshot all school_* databases
.\scripts\backup-school-dbs.ps1
```

Manual:

```powershell
Invoke-WebRequest http://localhost:8761/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:9090/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:8181/actuator/health -UseBasicParsing   # settings
Invoke-WebRequest http://localhost:4300 -UseBasicParsing
```

---

## Port map (local)

| Port | Service |
|------|---------|
| 8761 | Eureka (discovery) |
| 9090 | API Gateway (UI `apiBaseUrl`) |
| 8085 | auth-service |
| 8080 | shop-service |
| 8087 | notification-service |
| 1025 / 8025 | MailHog SMTP / UI (optional) |
| 8181–8198 | School microservices |
| 4200 | shop-management-ui (platform admin / Register school) |
| 4300 | school-ui |

School services: settings `8181`, subscription `8182`, form-builder `8183`, workflow `8184`, rule-engine `8185`, report-builder `8186`, notification-config `8187`, audit `8188`, admission `8189`, fee `8190`, student `8191`, attendance `8192`, exam `8193`, library `8194`, hostel `8195`, transport `8196`, payroll `8197`, staff `8198`.

---

## Common failures

| Symptom | Fix |
|---------|-----|
| Login: `Http failure ... localhost:9090/api/v1/auth/login: 0 Unknown Error` | Gateway/Eureka down → re-run `start-platform.ps1` |
| Login **401** / bad credentials | Run `seed-school-demo-auth.ps1`; use org `demo-school`, user `admin`, password `password` |
| `start-platform` MailHog / Docker error | Re-run with `-SkipMailHog` |
| School APIs 503 / empty modules | Eureka up, then `start-services.ps1` (wait for registration) |
| Port already in use | Platform script skips busy ports; for school use `-Restart` |
| Postgres / seed fails | Start Postgres; confirm `psql` and password `postgres` |

---

## One-liner daily sequence

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow" -SkipMailHog
# first time / after DB wipe only:
# .\scripts\seed-school-demo-auth.ps1
.\scripts\start-services.ps1
cd apps\school-ui; npx ng serve --port 4300
```

Then open http://localhost:4300 → `demo-school` / `admin` / `password`.
