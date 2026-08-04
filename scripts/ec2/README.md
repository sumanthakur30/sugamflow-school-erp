# EC2 service batches — start / stop / deploy

Three batches on the shared EC2 host. Paths assume:

| Stack | Folder | Compose | Env |
|-------|--------|---------|-----|
| Shared + SugamFlow apps | `/opt/sugamflow` | `docker-compose.ec2-rds.yml` | `.env.production` |
| School ERP | `/opt/school` | `docker-compose.school.ec2-rds.yml` | `.env.school.production` |

Docker network (external): `sumanthakur30_default`

---

## 1. Clean file list

### Batch scripts (this folder)

| File | Purpose |
|------|---------|
| `00-full-deploy-and-flyway-check.sh` | Pull + start batches 1→3→2, then verify Flyway max versions via `psql` |
| `01-common-start.sh` | Start shared platform |
| `01-common-stop.sh` | Stop shared platform (both UIs lose API) |
| `02-school-start.sh` | Start School microservices (Phase A default; `PHASE=b` for A+B; `PHASE=website` for website+cms; `PHASE=all` for A+B+website) |
| `02-school-stop.sh` | Stop School only |
| `03-sugamflow-start.sh` | Start SugamFlow shop apps only |
| `03-sugamflow-stop.sh` | Stop SugamFlow shop apps only |
| `04-pull-recreate-all-school.sh` | Pull Hub images + recreate all School services (Phase A+B) |

**PC prod env preflight:** `D:\school\scripts\check-prod-env.ps1`  
(JWT secret match, internal API key match, SMTP not localhost, Website CMS DB vars)

**PC build/push all School images:** `D:\school\scripts\build-push-all-school-images.ps1`

Copy to EC2:

```bash
# from PC via WinSCP → /opt/school/scripts/ec2/  (or /home/ec2-user/opt/school/scripts/ec2/)
# then on EC2:
chmod +x /opt/school/scripts/ec2/*.sh
```

**One-shot full deploy + Flyway check** (after RDS DBs exist and env files are filled):

```bash
cd /home/ec2-user/opt/school
# Phase A school only:
bash scripts/ec2/00-full-deploy-and-flyway-check.sh

# Phase A + B school:
SCHOOL_PHASE=b bash scripts/ec2/00-full-deploy-and-flyway-check.sh

# Also start IPD overlay:
WITH_IPD=1 SCHOOL_PHASE=b bash scripts/ec2/00-full-deploy-and-flyway-check.sh

# Re-check Flyway only (no pull/start):
CHECK_ONLY=1 bash scripts/ec2/00-full-deploy-and-flyway-check.sh
```

Script defaults: `SUGAMFLOW_DIR=/home/ec2-user/opt/sugamflow`, `SCHOOL_DIR=/home/ec2-user/opt/school`. Override if your paths differ (`/opt/sugamflow`, etc.).

### Compose + env (production)

| File | Repo | On EC2 |
|------|------|--------|
| `docker-compose.ec2-rds.yml` | `D:\sugamflow` | `/opt/sugamflow/` |
| `.env.production` | `D:\sugamflow` (secrets, gitignored) | `/opt/sugamflow/` |
| `docker-compose.school.ec2-rds.yml` | `D:\school` | `/opt/school/` |
| `.env.school.production` | `D:\school` (from `.env.school.production.example`) | `/opt/school/` |
| `Dockerfile.school-service` | `D:\school` | PC build only |

### UI + Nginx

| Path | Role |
|------|------|
| `/var/www/sugamflow-ui` | Shop Angular |
| `/var/www/school-ui` | School Angular (`dist/school-ui/browser`) |
| `/etc/nginx/conf.d/sugamflow-ui.conf` | Shop / default |
| `/etc/nginx/conf.d/school-ui.conf` | `server_name school.sugamflow.com;` → `/api/` → `:9090` |
| `/var/www/hcpschool.com` | HCP public site (`school-website-ui` prod build) |
| `/etc/nginx/conf.d/hcpschool.com.conf` | `hcpschool.com` SPA + `/api/` → `:9090` — see `docs/HCP_EC2_WEBSITE_DEPLOY.md` |

### Optional helpers (not required to run batches)

| File | Role |
|------|------|
| `EC2-FILES-TO-COPY.txt` | Short copy checklist |
| `docs/EC2_SCHOOL_PHASE_A_FOLLOW_ALONG.md` | School follow-along |
| `scripts/rds-demo-school/*` | Demo-school CSV/SQL import |

---

## 2. Service lists

### Batch 1 — Common (shared)

```
redis
config-service
discovery-service
auth-service
shop-service
user-service
notification-service
gateway-service          # host :9090
```

### Batch 2 — School-specific

```
school-settings-service          # 8181
subscription-service             # 8182
form-builder-service             # 8183
workflow-service                 # 8184
academic-structure-service       # 8199
staff-service                    # 8198
student-service                  # 8191
admission-service                # 8189
fee-service                      # 8190
attendance-service               # 8192
exam-service                     # 8193
payroll-service                  # 8197
library-service                  # 8194
hostel-service                   # 8195
transport-service                # 8196
school-notification-config-service  # 8187 (Comms Hub)
```

### Batch 3 — SugamFlow-specific (shop apps)

```
product-service
stock-service
order-service
payment-service
reporting-service
account-service
fieldforce-service
gst-service
ledger-service
doctor-service
appointment-service
queue-management-service
```

---

## 3. Commands to run batches

```bash
# Order for cold start
bash /opt/school/scripts/ec2/01-common-start.sh
bash /opt/school/scripts/ec2/03-sugamflow-start.sh   # optional if you need shop apps
bash /opt/school/scripts/ec2/02-school-start.sh

# Stop (safe order)
bash /opt/school/scripts/ec2/02-school-stop.sh
bash /opt/school/scripts/ec2/03-sugamflow-stop.sh
bash /opt/school/scripts/ec2/01-common-stop.sh        # last — kills gateway for both
```

Single-service example:

```bash
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d library-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  logs -f --tail=80 library-service
```

---

## 4. Production fixes we applied (keep these)

### A) JWT must match (both stacks)

In `/opt/sugamflow/.env.production` and `/opt/school/.env.school.production`:

```bash
SECURITY_JWT_SECRET=<same long secret>
SECURITY_JWT_ENFORCE=true
```

### B) Gateway CORS (login 403 from browser)

In `/opt/sugamflow/.env.production`:

```bash
GATEWAY_CORS_ALLOWED_ORIGIN_PATTERN=*
```

Recreate gateway:

```bash
cd /opt/sugamflow
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production \
  up -d --force-recreate gateway-service
```

### C) School feature flags inside Docker (not localhost)

School compose must call Docker DNS, e.g.:

```text
ADMISSION_SUBSCRIPTION_URL=http://subscription-service:8182
LIBRARY_SUBSCRIPTION_URL=http://subscription-service:8182
… same pattern for each school service
```

Already set in `docker-compose.school.ec2-rds.yml`. Do **not** point at `http://localhost:8182` from containers.

### D) Hikari / RDS pool (Flyway deadlock + connection slots)

In `/opt/school/.env.school.production`:

```bash
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=0
```

If RDS says `remaining connection slots are reserved for rds_reserved`:

- Close extra DBeaver sessions
- Temporarily stop unused school services, then start what you need

### E) DB user password mismatch (service won’t start)

Example (Exam / Library / Hostel / Transport / Notif):

```sql
ALTER USER school_exam WITH PASSWORD 'examdb';
ALTER USER school_library WITH PASSWORD 'librarydb';
ALTER USER school_hostel WITH PASSWORD 'hosteldb';
ALTER USER school_transport WITH PASSWORD 'transportdb';
ALTER USER school_notif_cfg WITH PASSWORD 'notifcfgdb';
```

Passwords must match `.env.school.production`. Then:

```bash
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d --force-recreate exam-service
```

### F) Nginx: school domain must not serve shop UI

`/etc/nginx/conf.d/school-ui.conf` must include:

```nginx
listen 80;
listen [::]:80;
server_name school.sugamflow.com;
root /var/www/school-ui;
# /api/ → http://127.0.0.1:9090
```

Then:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### G) New school DB (DBeaver, one statement each)

```sql
CREATE USER school_library WITH PASSWORD 'librarydb';
CREATE DATABASE school_library_db OWNER school_library;
GRANT ALL PRIVILEGES ON DATABASE school_library_db TO school_library;
```

Same pattern for hostel / transport / notif_cfg (and any new module).

---

## 5. Move code changes to production

### Step 0 — PC: commit / push (School)

```powershell
cd D:\school
git checkout sugamflow-school-erp
git pull
# after local work:
git push origin sugamflow-school-erp
```

Repo: https://github.com/sumanthakur30/sugamflow-school-erp.git

### Step 1 — PC: build & push School images (all features)

Preferred one-shot (fat-JAR safe Dockerfile):

```powershell
cd D:\school
docker login -u sumanthakur30

# Fresh build + push ALL modules (Phase A + B + audit/rules/reports) as tag 1.0.2
.\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.2 -NoCache -Phase all

# Or Phase A+B only (matches EC2 compose profiles):
.\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.2 -NoCache -Phase b
```

Script rejects jars smaller than 5MB (prevents `no main manifest attribute` from `*-copy.jar`).

Manual loop (same as before):

```powershell
cd D:\school
$Tag = "1.0.0"
$Prefix = "sumanthakur30"
# example — only modules you changed:
$Modules = @(
  "library-service",
  "hostel-service",
  "transport-service",
  "school-notification-config-service"
)
foreach ($Module in $Modules) {
  docker build -f Dockerfile.school-service --build-arg "MODULE=$Module" `
    -t "${Prefix}/${Module}:${Tag}" .
  docker push "${Prefix}/${Module}:${Tag}"
}
```

SugamFlow image rebuilds (if shop code changed): use `D:\sugamflow` Dockerfiles / compose build there.

### Step 2 — WinSCP: sync config files (if compose/env/scripts changed)

To `/opt/school/`:

- `docker-compose.school.ec2-rds.yml`
- `.env.school.production` (**set `IMAGE_TAG=1.0.2`** to match push)
- `scripts/ec2/*.sh` (include `04-pull-recreate-all-school.sh`)

To `/opt/sugamflow/` (only if shop compose/env changed):

- `docker-compose.ec2-rds.yml`
- `.env.production`

### Step 3 — EC2: pull & recreate School stack

```bash
cd /opt/school
chmod +x scripts/ec2/*.sh

# Set IMAGE_TAG in .env.school.production to the tag you pushed, then:
bash scripts/ec2/04-pull-recreate-all-school.sh          # Phase A+B
# PHASE=a bash scripts/ec2/04-pull-recreate-all-school.sh  # Phase A only
```

Or manual:

```bash
# School example
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d --force-recreate library-service hostel-service transport-service school-notification-config-service

# Or full school restart
bash /opt/school/scripts/ec2/02-school-stop.sh
bash /opt/school/scripts/ec2/02-school-start.sh
```

```bash
# SugamFlow app example
cd /opt/sugamflow
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production pull
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production \
  up -d --force-recreate product-service

# Gateway after route/CORS env changes
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production \
  up -d --force-recreate gateway-service
```

### Step 4 — School UI (Angular)

On PC:

```powershell
cd D:\school\apps\school-ui
npm ci
npm run build
# output: dist/school-ui/browser
```

WinSCP upload `browser\*` → `/var/www/school-ui/`, then:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### Step 5 — Smoke test

```bash
curl -fsS http://127.0.0.1:9090/actuator/health
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9090/api/student/health
# Browser: https://school.sugamflow.com  (or http:// until TLS)
# Login: org demo-school / admin / password — assign Enterprise plan if features show off
```

### Step 6 — Rollback (one service)

```bash
cd /opt/school
# retag previous image or pin IMAGE_TAG in .env.school.production, then:
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d --force-recreate <service-name>
```

---

## 6. Quick decision guide

| Symptom | Likely batch / fix |
|---------|-------------------|
| Login 503 / no gateway | Batch 1 common; check `:9090` |
| Login CORS / 403 | Fix B — gateway CORS + recreate |
| Library / Hostel / Transport / Comms 503 | Batch 2; that service image + RDS DB user |
| Feature flag always off | Fix C — Docker DNS subscription URLs |
| Flyway / Hikari timeout | Fix D — pool size; close DBeaver |
| Password authentication failed | Fix E — `ALTER USER` |
| `school.sugamflow.com` shows shop UI | Fix F — nginx `school-ui.conf` |
