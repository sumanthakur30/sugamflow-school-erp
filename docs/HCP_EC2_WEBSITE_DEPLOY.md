# HCP School Website — EC2 production deploy (step by step)

Target host (your stack): **`sugamflows`** · EIP often `16.192.54.132` · region `eu-north-1`.

| Path on EC2 | Purpose |
|---|---|
| `/home/ec2-user/opt/sugamflow/` | Shop stack + gateway `:9090` |
| `/home/ec2-user/opt/school/` | School compose + `.env.school.production` |
| Nginx (host) | TLS + `hcpschool.com` + `/api` → gateway |

Related docs: `PHASE_A_EC2_DEPLOY.md`, `EC2_SCHOOL_PHASE_A_FOLLOW_ALONG.md`, `HCP_PRODUCTION_CUTOVER.md`, `OPS_SCHOOL_WEBSITE_CDN_SSL.md`.

---

## Architecture (prod)

```
Browser
  https://hcpschool.com/*     → Nginx → school-website-ui (static SPA)
  https://hcpschool.com/api/* → Nginx → gateway:9090 → website-service / cms-service / admission-…
  https://school.sugamflow.com → school-ui (ERP + Website CMS)
```

Tenant is chosen by **Host** `hcpschool.com` (already seeded → `HCP-01`). Paths stay `/about`, etc.

---

## Prerequisites (before website)

- [ ] SugamFlow shop stack up (`sumanthakur30_default`, gateway healthy on `127.0.0.1:9090`)
- [ ] School Phase A (at least settings, subscription, admission) already running
- [ ] Gateway image includes routes for `/api/website/**` and `/api/cms/**` + public path whitelist  
  → rebuild/redeploy **gateway-service** from branch that has school website routes
- [ ] JWT secret identical in SugamFlow `.env.production` and School `.env.school.production`

---

## Step 1 — RDS: website + CMS databases

Matches `application.properties` (role ≠ database name):

| Service | Login role | Password env | Database |
|---|---|---|---|
| cms-service | `school_cms` | `SCHOOL_CMS_DB_PASSWORD` | `school_cms_db` |
| website-service | `school_website` | `SCHOOL_WEBSITE_DB_PASSWORD` | `school_website_db` |

**Do not** create a user named `school_cms_db` — that is the database name only.

### Option A — PowerShell (from PC with network to RDS)

```powershell
cd D:\school
.\infra\postgres\create-website-cms-dbs.ps1 `
  -HostName YOUR_RDS_ENDPOINT `
  -AdminUser postgres `
  -AdminPassword 'MASTER_PASS' `
  -CmsPassword 'STRONG_CMS' `
  -WebsitePassword 'STRONG_WEBSITE' `
  -SslMode require
```

### Option B — DBeaver paste

Run `infra/postgres/06-create-website-cms-databases-dbeaver.sql` as RDS master  
(change passwords; keep `GRANT school_cms TO postgres;` / `GRANT school_website TO postgres;`).

Mark: `[ ] Step 1`

---

## Step 2 — Update `.env.school.production` on EC2

File: `/home/ec2-user/opt/school/.env.school.production`

Add/confirm (use your real RDS host + passwords):

```bash
# Auth (must match SugamFlow .env.production)
SECURITY_JWT_ENFORCE=true
SECURITY_JWT_SECRET=<same-as-sugamflow>
SECURITY_INVITE_INTERNAL_KEY=<same-as-sugamflow>
SECURITY_INTERNAL_API_KEY=<same-as-sugamflow-internal-or-invite>

# Website platform
SCHOOL_WEBSITE_DB_URL=jdbc:postgresql://YOUR_RDS:5432/school_website_db?sslmode=require
SCHOOL_WEBSITE_DB_USERNAME=school_website
SCHOOL_WEBSITE_DB_PASSWORD=CHANGE_ME

SCHOOL_CMS_DB_URL=jdbc:postgresql://YOUR_RDS:5432/school_cms_db?sslmode=require
SCHOOL_CMS_DB_USERNAME=school_cms
SCHOOL_CMS_DB_PASSWORD=CHANGE_ME

WEBSITE_CDN_BASE_URL=https://cdn.sugamflow.com
CMS_MEDIA_CDN_BASE_URL=https://cdn.sugamflow.com
WEBSITE_DEFAULT_ERP_LOGIN_URL=https://school.sugamflow.com/login

# Optional S3 media later
CMS_MEDIA_S3_ENABLED=false
```

On SugamFlow `/home/ec2-user/opt/sugamflow/.env.production` also set:

```bash
SECURITY_JWT_ENFORCE=true
SECURITY_INTERNAL_API_KEY=<same-as-school>
# Real SMTP — NEVER localhost inside Docker
SPRING_MAIL_HOST=<ses-or-sendgrid-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
```

**Do not** set `WEBSITE_CDN_BASE_URL=https://hcpschool.com`.

Bump `IMAGE_TAG` when you push new images (e.g. `1.0.1`).

From PC (or EC2 if PowerShell available):

```powershell
powershell -File .\scripts\check-prod-env.ps1 `
  -SchoolEnv D:\school\.env.school.production `
  -SugamEnv D:\sugamFlow\.env.production
```

Mark: `[ ] Step 2`

---

## Step 3 — Build & push images (on your PC)

```powershell
cd D:\school
$Tag = "1.0.1"   # use a new tag for website release
$Prefix = "sumanthakur30"

foreach ($Module in @("website-service","cms-service","admission-service","fee-service")) {
  docker build -f Dockerfile.school-service --build-arg "MODULE=$Module" `
    -t "${Prefix}/${Module}:${Tag}" .
  docker push "${Prefix}/${Module}:${Tag}"
}

# ERP admin (Website CMS screens live in school-ui)
cd D:\school\apps\school-ui
npm ci
npx ng build --configuration=production
# deploy dist → /var/www/school-ui
```

Also rebuild/push **gateway-service** (SugamFlow repo) if production gateway does not yet have website/cms routes.

Mark: `[ ] Step 3`

---

## Step 4 — Start website + cms on EC2

```bash
ssh ec2-user@16.192.54.132   # or your EIP
cd /home/ec2-user/opt/school

# Ensure env has new IMAGE_TAG + website DB vars
grep IMAGE_TAG .env.school.production
grep SCHOOL_WEBSITE_DB_URL .env.school.production

# Pull + start website profile (Phase A stays as-is)
export COMPOSE_PROFILES=website
# Or combine: COMPOSE_PROFILES=website   (add phase-b only if you already use it)

docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull website-service cms-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d website-service cms-service

docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production ps
docker logs -f school-website-service-1   # Ctrl+C when Flyway + Started
docker logs --tail 80 school-cms-service-1
```

Flyway will create tables and apply HCP seeds (including **V12** ERP login URL → `https://school.sugamflow.com/login`).

Verify registration:

```bash
curl -sS http://127.0.0.1:9090/api/website/public/resolve?host=hcpschool.com | head -c 400
curl -sS "http://127.0.0.1:9090/api/cms/public/pages/about?organizationId=HCP-01" | head -c 400
```

Expect JSON with `Holly Cross` / About page — not 404.

Mark: `[ ] Step 4`

---

## Step 5 — Build & deploy public UI (`school-website-ui`)

On PC:

```powershell
cd D:\school\apps\school-website-ui
npm ci
npm run build
# output: dist/school-website-ui/browser/  (Angular 19) or dist/school-website-ui/
```

Confirm `environment.prod.ts`:

- `apiBaseUrl: ''` (same-origin `/api` via Nginx)
- `defaultHost: ''`
- `erpBaseUrl: 'https://school.sugamflow.com'`

Copy to EC2, e.g.:

```bash
# on EC2
sudo mkdir -p /var/www/hcpschool.com
# from PC (WinSCP/scp): upload browser/* into /var/www/hcpschool.com/
sudo chown -R nginx:nginx /var/www/hcpschool.com   # or apache/ec2-user per your setup
```

Mark: `[ ] Step 5`

---

## Step 6 — Deploy school-ui (ERP + Website CMS) if not already

Production school ERP should already be at `https://school.sugamflow.com`.

Ensure prod build has:

```ts
websitePreviewUrl: 'https://hcpschool.com'
```

Redeploy school-ui static files the same way you usually do for school ERP.

Mark: `[ ] Step 6`

---

## Step 7 — Nginx on EC2 for `hcpschool.com`

### 7a DNS

At your DNS provider:

| Record | Value |
|---|---|
| `hcpschool.com` | A / ALIAS → EC2 EIP (or CloudFront) |
| `www.hcpschool.com` | CNAME → `hcpschool.com` or same A |

### 7b TLS

```bash
sudo certbot --nginx -d hcpschool.com -d www.hcpschool.com
```

### 7c Site config (sketch)

Create `/etc/nginx/conf.d/hcpschool.com.conf` (paths may be `/etc/nginx/sites-available/` on some AMIs):

```nginx
server {
  listen 80;
  server_name hcpschool.com www.hcpschool.com;
  return 301 https://$host$request_uri;
}

server {
  listen 443 ssl http2;
  server_name hcpschool.com www.hcpschool.com;

  # ssl_certificate ... managed by certbot

  root /var/www/hcpschool.com;
  index index.html;

  # Public API → existing gateway on this host
  location /api/ {
    proxy_pass http://127.0.0.1:9090/api/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  # Optional: do not cache admission / track
  location /api/website/public/track {
    proxy_pass http://127.0.0.1:9090;
    proxy_cache off;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Mark: `[ ] Step 7`

---

## Step 8 — Platform configuration (browser)

1. Super Admin → **School Website Domains**  
   - Confirm `hcpschool.com` (primary), `www` → org `HCP-01`  
   - Set **ssl_status = ACTIVE**
2. Super Admin → **Platform Subscription**  
   - Apply **School + Website** to `HCP-01`
3. School ERP (`https://school.sugamflow.com`) → Website CMS  
   - Preview should load `https://hcpschool.com`  
   - Publish pages if needed

Mark: `[ ] Step 8`

---

## Step 9 — Smoke test

From your PC:

```powershell
cd D:\school
.\scripts\smoke-hcp-website.ps1 -BaseUrl https://hcpschool.com -HostName hcpschool.com
# If API is separate:
# .\scripts\smoke-hcp-website.ps1 -BaseUrl https://api.YOURDOMAIN -HostName hcpschool.com
```

Browser:

- https://hcpschool.com/
- https://hcpschool.com/about
- https://hcpschool.com/contact
- **Sign in** → `https://school.sugamflow.com/login?org=HCP-01`
- Choose role on login screen → Admin / Parent / Teacher

Mark: `[ ] Step 9`

---

## Step 10 — Rollback (if needed)

```bash
# Stop only website containers
cd /home/ec2-user/opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production stop website-service cms-service

# Or suspend site in DB / Super Admin domains (status DISABLED / site SUSPENDED)
```

Disable Nginx site or point DNS away.

---

## EC2-specific checklist (copy/paste)

```
[ ] Gateway has website+cms routes (redeployed)
[ ] RDS school_website_db + school_cms_db
[ ] .env.school.production website vars + CDN + IMAGE_TAG
[ ] Docker images website-service + cms-service pushed
[ ] compose profile website up; resolve?host=hcpschool.com works on :9090
[ ] school-website-ui prod build in /var/www/hcpschool.com
[ ] school-ui websitePreviewUrl = https://hcpschool.com
[ ] DNS A/CNAME for hcpschool.com → EIP
[ ] Certbot TLS
[ ] Nginx SPA + /api proxy
[ ] Domains SSL ACTIVE + School+Website plan
[ ] Smoke + Sign in works
```

---

## Common failures on EC2

| Symptom | Fix |
|---|---|
| `/api/website/...` 404 | Gateway image missing routes — redeploy gateway |
| Resolve 404 / empty | website-service not on Eureka / wrong network |
| Flyway fail | DB user/password/URL wrong in `.env.school.production` |
| Site loads, API fails CORS/404 | Nginx missing `/api` proxy or wrong `apiBaseUrl` |
| Sign in goes to localhost | DB still has V3 `erp_login_url`; ensure V12 migrated / update URL |
| Preview blank in CMS | `websitePreviewUrl` empty or wrong; set to `https://hcpschool.com` |
| OOM on EC2 | Raise free memory; website profile alone is ~2×384m; stop unused phase-b |

---

## Compose reminder

```bash
# Website only (this guide)
COMPOSE_PROFILES=website docker compose -f docker-compose.school.ec2-rds.yml \
  --env-file .env.school.production up -d

# Website + phase-b (if you already run phase-b)
COMPOSE_PROFILES=website,phase-b docker compose -f docker-compose.school.ec2-rds.yml \
  --env-file .env.school.production up -d
```
