# School ERP — Phase A EC2 Follow-Along (Production)

Follow this document **top to bottom**. It matches the production-ready files already in this repo.

| Your host | Value |
|-----------|--------|
| EC2 name | `sugamflows` |
| Type | `m7i-flex.xlarge` (~16 GiB / 4 vCPU) |
| Region | `eu-north-1` |
| Public IP (EIP) | `16.192.54.132` |
| Existing shop stack | `D:\sugamflow\docker-compose.ec2-rds.yml` (Compose project `sumanthakur30`) |
| Existing gateway | `127.0.0.1:9090` on EC2 |

**Do not deploy all 19 school services yet.** Phase A = 7 services only.

---

## Production files checked (repo)

| File | Status | Purpose |
|------|--------|---------|
| **`docker-compose.school.ec2-rds.yml`** | Ready | School compose (name distinct from SugamFlow) |
| **`.env.school.production.example`** | Ready | Copy → `.env.school.production` on EC2 (**gitignored**) |
| `EC2-FILES-TO-COPY.txt` | Ready | Short “which files to move” cheat sheet |
| `Dockerfile.school-service` | Ready | Build one image per school module |
| `apps/school-ui/.../environment.prod.ts` | Ready | `apiBaseUrl: ''` → same-origin `/api` → Nginx → gateway `:9090` |
| `infra/postgres/01-create-school-databases-simple.sql` | Ready | Create users + DBs (needs RDS `GRANT` fix below) |

> Prefer `docker-compose.school.ec2-rds.yml` + `.env.school.production`.  
> Do **not** reuse SugamFlow’s `docker-compose.ec2-rds.yml` / `.env.production` filenames on the same EC2.


### Important production rules

1. **Same JWT secret** as SugamFlow `.env.production` (`SECURITY_JWT_SECRET`).
2. School containers join **existing** network `sumanthakur30_default` (confirm with `docker network ls`).
3. **No public ports** for 8181–8199 — only gateway `:9090` is public (via Nginx).
4. Use `*_DB_USERNAME` (not `*_DB_USER`).
5. Put **full JDBC URLs** in `.env` (do not rely on nested `${RDS_HOST}` expansion).
6. Tables are created by **Flyway** when services start — no manual schema scripts.

---

## Phase A services

| Service | Port | Database | DB user |
|---------|------|----------|---------|
| school-settings-service | 8181 | `school_settings_db` | `school_settings` |
| subscription-service | 8182 | `school_subscription_db` | `school_subscription` |
| admission-service | 8189 | `school_admission_db` | `school_admission` |
| fee-service | 8190 | `school_fee_db` | `school_fee` |
| student-service | 8191 | `school_student_db` | `school_student` |
| staff-service | 8198 | `school_staff_db` | `school_staff` |
| academic-structure-service | 8199 | `school_academic_db` | `school_academic` |

---

## Step 0 — Pre-checks on EC2 (SSH)

```bash
# Shop stack healthy?
curl -sS http://127.0.0.1:9090/actuator/health

# Docker network name (need this for env)
docker network ls | grep sumanthakur30
# expect: sumanthakur30_default

# Free memory (Phase A needs ~2–3 GiB headroom)
free -h
```

Confirm you can open existing **shop-management-ui** in the browser.

Mark done: `[ ] Step 0`

---

## Step 1 — RDS: create users + databases

Connect in DBeaver as **RDS master** (often `postgres`) to database `postgres`.

### 1.1 Create users (use strong passwords)

```sql
CREATE USER school_settings WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_subscription WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_admission WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_fee WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_student WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_staff WITH PASSWORD 'YOUR_STRONG_PASSWORD';
CREATE USER school_academic WITH PASSWORD 'YOUR_STRONG_PASSWORD';
```

Skip any role that already exists.

### 1.2 RDS-required grants (fixes `must be able to SET ROLE`)

If master user is `postgres`:

```sql
GRANT school_settings TO postgres;
GRANT school_subscription TO postgres;
GRANT school_admission TO postgres;
GRANT school_fee TO postgres;
GRANT school_student TO postgres;
GRANT school_staff TO postgres;
GRANT school_academic TO postgres;
```

If master username is different, replace `postgres` with that name.

### 1.3 Create databases

```sql
CREATE DATABASE school_settings_db OWNER school_settings;
CREATE DATABASE school_subscription_db OWNER school_subscription;
CREATE DATABASE school_admission_db OWNER school_admission;
CREATE DATABASE school_fee_db OWNER school_fee;
CREATE DATABASE school_student_db OWNER school_student;
CREATE DATABASE school_staff_db OWNER school_staff;
CREATE DATABASE school_academic_db OWNER school_academic;
```

Refresh DBeaver — all 7 DBs should appear.

Mark done: `[ ] Step 1`  ✅ (you already completed this)

---

## Step 2 — Prepare folder + env on EC2

```bash
sudo mkdir -p /home/ec2-user/opt/school
cd /home/ec2-user/opt/school
```

Copy from your PC (WinSCP / scp) these files into that folder:

- `docker-compose.school.ec2-rds.yml`
- `.env.school.production.example` → save on EC2 as `.env.school.production`

### 2.1 Fill `.env.school.production`

Use this shape (**full JDBC URLs** — replace host + passwords):

```dotenv
SPRING_PROFILES_ACTIVE=prod
IMAGE_PREFIX=sumanthakur30
SCHOOL_IMAGE_TAG=1.0.0
IMAGE_TAG=1.0.0

SUGAMFLOW_DOCKER_NETWORK=sumanthakur30_default

SECURITY_JWT_ENFORCE=true
SECURITY_JWT_SECRET=PASTE_EXACT_SAME_VALUE_FROM_SUGAMFLOW_ENV_PRODUCTION
SECURITY_INVITE_INTERNAL_KEY=PASTE_SAME_AS_SUGAMFLOW_IF_USED

JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata -Xms96m -Xmx192m -XX:+ExitOnOutOfMemoryError
JVM_SERVICE_MEM_LIMIT=384m
DB_POOL_MAX_SIZE=3
DB_POOL_MIN_IDLE=1

# Replace YOUR_RDS_ENDPOINT — example:
# sugamflow-postgres.cnci2xxxx.eu-north-1.rds.amazonaws.com

SCHOOL_SETTINGS_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_settings_db?sslmode=require
SCHOOL_SETTINGS_DB_USERNAME=school_settings
SCHOOL_SETTINGS_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_SUBSCRIPTION_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_subscription_db?sslmode=require
SCHOOL_SUBSCRIPTION_DB_USERNAME=school_subscription
SCHOOL_SUBSCRIPTION_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_ADMISSION_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_admission_db?sslmode=require
SCHOOL_ADMISSION_DB_USERNAME=school_admission
SCHOOL_ADMISSION_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_FEE_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_fee_db?sslmode=require
SCHOOL_FEE_DB_USERNAME=school_fee
SCHOOL_FEE_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_STUDENT_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_student_db?sslmode=require
SCHOOL_STUDENT_DB_USERNAME=school_student
SCHOOL_STUDENT_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_STAFF_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_staff_db?sslmode=require
SCHOOL_STAFF_DB_USERNAME=school_staff
SCHOOL_STAFF_DB_PASSWORD=YOUR_STRONG_PASSWORD

SCHOOL_ACADEMIC_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/school_academic_db?sslmode=require
SCHOOL_ACADEMIC_DB_USERNAME=school_academic
SCHOOL_ACADEMIC_DB_PASSWORD=YOUR_STRONG_PASSWORD
```

**How to copy JWT secret:**

```bash
# on EC2, where SugamFlow env lives (adjust path if different)
grep SECURITY_JWT_SECRET /home/ec2-user/opt/sugamflow/.env.production
```

Paste that exact value into `.env.school.production`.

Mark done: `[ ] Step 2`

---

## Step 3 — Build & push Docker images (on your Windows PC)

Requires Docker Desktop logged into Docker Hub as `sumanthakur30` (or your prefix).

```powershell
cd D:\school
$Tag = "1.0.0"
$Prefix = "sumanthakur30"
$Modules = @(
  "school-settings-service",
  "subscription-service",
  "admission-service",
  "fee-service",
  "student-service",
  "staff-service",
  "academic-structure-service"
)
foreach ($Module in $Modules) {
  Write-Host "Building $Module ..."
  docker build -f Dockerfile.school-service --build-arg "MODULE=$Module" `
    -t "${Prefix}/${Module}:${Tag}" .
  if ($LASTEXITCODE -ne 0) { throw "Build failed: $Module" }
  docker push "${Prefix}/${Module}:${Tag}"
  if ($LASTEXITCODE -ne 0) { throw "Push failed: $Module" }
}
Write-Host "All Phase A images pushed."
```

Mark done: `[ ] Step 3`

---

## Step 4 — Start Phase A on EC2

```bash
cd /home/ec2-user/opt/school

docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production ps
```

Wait 1–2 minutes for Flyway + Eureka registration.

### 4.1 Verify containers

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'school-|student-|fee-|admission-|staff-|academic-|subscription'
```

All should be `Up`.

### 4.2 Verify logs (if a container restarts)

```bash
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production logs --tail=80 student-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production logs --tail=80 academic-structure-service
```

Common failures:

| Symptom | Fix |
|---------|-----|
| Auth / JWT errors | JWT secret mismatch with SugamFlow |
| Connection refused to DB | Wrong RDS host / password / SG |
| Cannot resolve `discovery-service` | Wrong `SUGAMFLOW_DOCKER_NETWORK` |
| OOM / killed | Lower heap or free RAM; do not add more services yet |

### 4.3 Verify via existing gateway

```bash
curl -sS -o /dev/null -w "gateway_health=%{http_code}\n" http://127.0.0.1:9090/actuator/health
curl -sS -o /dev/null -w "student=%{http_code}\n" http://127.0.0.1:9090/api/student/health
curl -sS -o /dev/null -w "academic=%{http_code}\n" http://127.0.0.1:9090/api/academic/bootstrap
```

Expect non-502 codes (401/403 without token is OK for protected routes; 200 for health if exposed).

If always **404** for school paths: gateway may need direct URIs in SugamFlow `.env.production` and a gateway recreate:

```dotenv
GATEWAY_SCHOOL_SETTINGS_URI=http://school-settings-service:8181
GATEWAY_SCHOOL_SUBSCRIPTION_URI=http://subscription-service:8182
GATEWAY_SCHOOL_ADMISSION_URI=http://admission-service:8189
GATEWAY_SCHOOL_FEE_URI=http://fee-service:8190
GATEWAY_SCHOOL_STUDENT_URI=http://student-service:8191
GATEWAY_SCHOOL_STAFF_URI=http://staff-service:8198
GATEWAY_SCHOOL_ACADEMIC_URI=http://academic-structure-service:8199
```

Then recreate **only** gateway (do not tear down whole shop stack unless you know the procedure).

Mark done: `[ ] Step 4`

---

## Step 5 — Build & deploy School Angular UI

### 5.1 Build on Windows PC

```powershell
cd D:\school\apps\school-ui
npm ci
npx ng build --configuration=production
```

Output folder is usually:

- `D:\school\apps\school-ui\dist\school-ui\browser`  
  or  
- `D:\school\apps\school-ui\dist\school-ui`

Use whichever contains `index.html`.

### 5.2 Copy to EC2

WinSCP example:

- Local: `dist/school-ui/browser/*`
- Remote: `/home/ec2-user/releases/school-ui/`

Then on EC2:

```bash
sudo mkdir -p /var/www/school-ui
sudo rsync -a --delete /home/ec2-user/releases/school-ui/ /var/www/school-ui/
sudo chown -R nginx:nginx /var/www/school-ui
sudo chmod -R 755 /var/www/school-ui
```

### 5.3 Nginx site for school-ui

Keep **shop-management-ui** as-is. Add a second server block (subdomain recommended).

Create `/etc/nginx/conf.d/school-ui.conf` (or edit your main conf):

```nginx
server {
    listen 80;
    server_name school.YOUR_DOMAIN.com;   # or use EIP temporarily

    root /var/www/school-ui;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:9090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Optional HTTPS:

```bash
sudo certbot --nginx -d school.YOUR_DOMAIN.com
```

DNS: point `school.YOUR_DOMAIN.com` → EIP `16.192.54.132`.

Mark done: `[ ] Step 5`

---

## Step 6 — Register a school + login smoke test

1. Open existing **shop-management-ui**.
2. Super-admin → create / register shop with business type **SCHOOL**.
3. Note `shopId` (Organization ID) and admin username/password.
4. Open **school-ui** (new Nginx site).
5. Sign in with Organization ID = `shopId`.
6. Smoke checks:
   - Dashboard loads
   - Academic Structure loads
   - Students / Fees / Staff pages load without gateway 502

Mark done: `[ ] Step 6`

---

## Step 7 — Stability check (before Phase B)

```bash
free -h
docker stats --no-stream
docker compose -f /home/ec2-user/opt/school/docker-compose.school.ec2-rds.yml --env-file /home/ec2-user/opt/school/.env.school.production ps
```

If RAM and logs look healthy for 24–48 hours, then plan Phase B (attendance, exam, …).

Mark done: `[ ] Step 7`

---

## Rollback (school only — shop untouched)

```bash
cd /home/ec2-user/opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production down
```

UI rollback: restore previous `/var/www/school-ui` and `sudo nginx -t && sudo systemctl reload nginx`.

---

## Architecture (what you are deploying)

```
Browser
  ├─ shop-management-ui  (already on EC2)  → register SCHOOL shops
  └─ school-ui           (new)             → school operations
        │
        └─ /api/*  → Nginx → gateway:9090
                              │
                              ├─ auth / shop / … (existing SugamFlow)
                              └─ Phase A school services (new containers)
                                    └─ RDS school_*_db
```

---

## Checklist summary

- [x] Step 1 — RDS users + DBs (done)
- [ ] Step 2 — `.env.school.production` on EC2 (JWT + full JDBC URLs)
- [ ] Step 3 — Build/push 7 images
- [ ] Step 4 — `docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d`
- [ ] Step 5 — Build/deploy `school-ui` + Nginx
- [ ] Step 6 — Register school in shop UI + login school UI
- [ ] Step 7 — RAM / stability

---

## Related docs

- Short overview: `docs/PHASE_A_EC2_DEPLOY.md`
- Full platform guide: `docs/PRODUCTION_DEPLOYMENT_SCHOOL_AND_SUGAMFLOW.md`
- Registration runbook: `docs/SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md`
