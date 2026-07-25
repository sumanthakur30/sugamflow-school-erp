# School ERP — Phase A deploy on existing SugamFlow EC2

> **Follow-along runbook (recommended):** [`EC2_SCHOOL_PHASE_A_FOLLOW_ALONG.md`](./EC2_SCHOOL_PHASE_A_FOLLOW_ALONG.md)
>
> Production files (school-specific names):
> - `docker-compose.school.ec2-rds.yml`
> - `.env.school.production` (from `.env.school.production.example`)
> - `EC2-FILES-TO-COPY.txt`


Target host (remembered): **`sugamflows`** · `m7i-flex.xlarge` · **16 GiB / 4 vCPU** · `eu-north-1`.

SugamFlow already runs via `D:\sugamFlow\docker-compose.ec2-rds.yml` (Compose project `sumanthakur30`).  
Phase A adds **7 School services** onto the **same Docker network** and the **same gateway :9090**.

## Phase A services (only these)

| Service | Port | DB |
|---------|------|-----|
| school-settings-service | 8181 | school_settings_db |
| subscription-service | 8182 | school_subscription_db |
| admission-service | 8189 | school_admission_db |
| fee-service | 8190 | school_fee_db |
| student-service | 8191 | school_student_db |
| staff-service | 8198 | school_staff_db |
| academic-structure-service | 8199 | school_academic_db |

Do **not** start attendance/exam/library/hostel/… until Phase A is stable.

## Files in this repo

| File | Purpose |
|------|---------|
| `Dockerfile.school-service` | Build one image per module |
| `docker-compose.ec2-phase-a.yml` | Run Phase A on EC2 |
| `.env.school.phase-a.example` | Env template (`*_DB_USERNAME`) |
| `apps/school-ui/.../environment.prod.ts` | Same-origin `/api` for prod UI |

## 1) Create Phase A databases on RDS

On RDS (psql as admin), run the relevant parts of:

`infra/postgres/01-create-school-databases-simple.sql`

At minimum create users/DBs for the 7 Phase A databases above. Use strong passwords in production.

Confirm EC2 security group can reach RDS :5432.

## 2) Build and push images (from your build machine)

```powershell
cd D:\school
$Tag = "1.0.0"
$Prefix = "sumanthakur30"
$Modules = @(
  "school-settings-service","subscription-service","admission-service",
  "fee-service","student-service","staff-service","academic-structure-service"
)
foreach ($Module in $Modules) {
  docker build -f Dockerfile.school-service --build-arg "MODULE=$Module" `
    -t "${Prefix}/${Module}:${Tag}" .
  docker push "${Prefix}/${Module}:${Tag}"
}
```

## 3) Env file on EC2

```bash
# on EC2
cp .env.school.phase-a.example .env.school.phase-a
# edit: SECURITY_JWT_SECRET = SAME as SugamFlow .env.production
# edit: RDS host + passwords
```

Find SugamFlow network name:

```bash
docker network ls | grep sumanthakur30
# usually: sumanthakur30_default
```

Set `SUGAMFLOW_DOCKER_NETWORK=sumanthakur30_default` in `.env.school.phase-a`.

## 4) Start Phase A (do not restart shop stack)

```bash
docker compose -f docker-compose.ec2-phase-a.yml --env-file .env.school.phase-a pull
docker compose -f docker-compose.ec2-phase-a.yml --env-file .env.school.phase-a up -d
docker compose -f docker-compose.ec2-phase-a.yml --env-file .env.school.phase-a ps
```

## 5) Verify

```bash
# School services registered in Eureka (via discovery container or UI if mapped)
docker ps --format '{{.Names}}' | grep -E 'school-|student-|fee-|admission-|staff-|academic-|subscription'

# API through EXISTING gateway (host port often 9090)
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9090/api/student/health || true
```

Gateway routes for School are already in `gateway-service` (`lb://student-service`, etc.).  
No new public ports for 818x — keep them internal.

## 6) School UI (Nginx)

Build locally:

```powershell
cd D:\school\apps\school-ui
npm ci
npx ng build --configuration=production
```

Copy `dist/school-ui/browser` (or `dist/school-ui`) to EC2, e.g. `/var/www/school-ui`.

Nginx example (separate host or path):

```nginx
server {
  server_name school.sugamflow.com;   # or use /school on main domain
  root /var/www/school-ui;
  index index.html;
  location / {
    try_files $uri $uri/ /index.html;
  }
  location /api/ {
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

`environment.prod.ts` uses empty `apiBaseUrl` so the browser calls **same-origin** `/api` → gateway.

## 7) RAM checklist (16 GiB)

After Phase A is up:

```bash
free -h
docker stats --no-stream
```

If free RAM stays healthy for 24–48h, add Phase B (attendance, exam, …) later.

## 8) Rollback

```bash
docker compose -f docker-compose.ec2-phase-a.yml --env-file .env.school.phase-a down
```

SugamFlow shop stack is untouched.

## Notes

- School org = Shop with `businessType=SCHOOL` (shared auth/shop).
- Use `*_DB_USERNAME` (not `*_DB_USER`).
- Full 19-service runbook: `PRODUCTION_DEPLOYMENT_SCHOOL_AND_SUGAMFLOW.md`.
