# Pratibha Public School, Rampur — local + production steps

Organization: `PPS-01` · branch key: `rampur` · template: `classic-school` · premium skin in `theme_json`.
ERP login target: `https://school.sugamflow.com/login`.
Public hosts (prod): `pratibhapublicschoolrampur.com` + `www` — stay **PENDING** until DNS, Nginx vhost, and SSL exist.
Local preview host: `pps-rampur.localhost` — seeded **ACTIVE**.

Do not copy Katihar town phone/address/staff onto Rampur. Do not invent fees, affiliation numbers, or results.

Related: `docs/HCP_EC2_WEBSITE_DEPLOY.md` (general school website pattern), `scripts/pps-rampur-*.sql`.

---

## Local run (Windows)

### 1. Databases

Local Postgres roles/dbs (dev defaults):

| Service | Database | User | Password |
|---|---|---|---|
| website-service | `school_website_db` | `school_website` | `school_website` |
| cms-service | `school_cms_db` | `school_cms` | `school_cms` |

`psql` path: `C:\Program Files\PostgreSQL\17\bin\psql.exe`.
On PowerShell use **`-f`** (not `-c`) so quotes are not split.

### 2. Apply Flyway + seeds

1. Start (or rebuild) **cms-service** so Flyway `V8__cms_site_scope_notices_documents.sql` runs.
2. Start **website-service**.
3. Seed website (idempotent; does **not** force a published site back to `DRAFT`):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_website -d school_website_db -f "D:\school\scripts\pps-rampur-website-seed.sql"
```

4. Seed CMS pages / content / palette / logo as needed:

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_cms -d school_cms_db -f "D:\school\scripts\pps-rampur-cms-seed.sql"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_cms -d school_cms_db -f "D:\school\scripts\pps-rampur-pages.sql"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_cms -d school_cms_db -f "D:\school\scripts\pps-rampur-content.sql"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_cms -d school_cms_db -f "D:\school\scripts\pps-rampur-palette.sql"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U school_cms -d school_cms_db -f "D:\school\scripts\pps-rampur-logo.sql"
```

Optional manifest of migrated images: `scripts/pps-rampur-image-manifest.txt`.
Static files live under `apps/school-website-ui/public/pps/` (logo + `images/`).

### 3. Gateway + UI preview

- Gateway must be on `http://localhost:9090` (SugamFlow gateway with `/api/website/**` and `/api/cms/**`).
- `apps/school-website-ui/proxy.conf.json` must target `http://localhost:9090` (not a mock).
- Hosts file: map `pps-rampur.localhost` → `127.0.0.1`.
- One `ng serve` only:

```powershell
cd D:\school\apps\school-website-ui
npx ng serve --port 4310 --host 0.0.0.0 --proxy-config proxy.conf.json
```

Open: `http://pps-rampur.localhost:4310/`.

If port 4310 is already in use, do **not** start a second server; reuse the existing listener.

### 4. Smoke (local)

```powershell
curl.exe -sS "http://localhost:9090/api/website/public/resolve?host=pps-rampur.localhost" | Select-Object -First 1
curl.exe -sS "http://localhost:9090/api/cms/public/pages/academics?organizationId=PPS-01&branchId=rampur" | Select-Object -First 1
```

---

## Production deploy (EC2)

Host: `ec2-user@16.192.54.132`  
School compose root (existing pattern): `/home/ec2-user/opt/school` (docs also mention `/opt/sugamflow` for the shop/gateway stack).  
Compose file: `docker-compose.school.ec2-rds.yml` · env: `.env.school.production`.  
Image prefix: `sumanthakur30` · bump `IMAGE_TAG` for each release.

### A. Build & push services (from PC)

```powershell
cd D:\school
$Tag = "1.0.x"   # use a new tag; do not reuse a stale tag
$Prefix = "sumanthakur30"

foreach ($Module in @("website-service","cms-service","admission-service")) {
  docker build -f Dockerfile.school-service --build-arg "MODULE=$Module" `
    -t "${Prefix}/${Module}:${Tag}" .
  docker push "${Prefix}/${Module}:${Tag}"
}
```

Rebuild gateway from the SugamFlow repo only if prod gateway still lacks website/cms routes.

### B. Build public UI

```powershell
cd D:\school\apps\school-website-ui
npm ci
npm run build
# output: dist/school-website-ui/browser/ (or dist/school-website-ui/)
```

Confirm `environment.prod.ts`: `apiBaseUrl: ''`, same-origin `/api` via Nginx.

Deploy the same SPA build used by other school public sites (HCP pattern: static root under `/var/www/...`).
Until the Rampur DNS/vhost exists, you may refresh the shared school-website static tree that already serves other ACTIVE hosts; Rampur resolve still keys off `Host`.

### C. Pull & restart on EC2

```bash
ssh ec2-user@16.192.54.132
cd /home/ec2-user/opt/school   # or /opt/school if that is the live path

# Set IMAGE_TAG in .env.school.production to the tag you pushed
grep IMAGE_TAG .env.school.production

export COMPOSE_PROFILES=website
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull website-service cms-service admission-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d website-service cms-service admission-service

docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production ps
docker logs --tail 120 school-cms-service-1      # confirm Flyway V8
docker logs --tail 80 school-website-service-1
```

### D. Production SQL seeds

Copy the SQL files to the host (or run from a PC that can reach RDS with SSL).
Apply **cms** V8 via the new cms-service container first, then:

```bash
# website DB — does not force DRAFT on re-run
psql "$SCHOOL_WEBSITE_URL" -f scripts/pps-rampur-website-seed.sql

# cms DB
psql "$SCHOOL_CMS_URL" -f scripts/pps-rampur-cms-seed.sql
psql "$SCHOOL_CMS_URL" -f scripts/pps-rampur-pages.sql
psql "$SCHOOL_CMS_URL" -f scripts/pps-rampur-content.sql
psql "$SCHOOL_CMS_URL" -f scripts/pps-rampur-palette.sql
psql "$SCHOOL_CMS_URL" -f scripts/pps-rampur-logo.sql
```

Production domain rows for `pratibhapublicschoolrampur.com` / `www` remain **PENDING**.
Do **not** set them ACTIVE until Nginx + certificate are in place.
Do not invent a production staff password; public resolve does not need a PPS-01 login.

### E. Nginx + DNS + SSL (blocked until DNS is ready)

Same pattern as `hcpschool.com` (`docs/HCP_EC2_WEBSITE_DEPLOY.md` Step 7):

1. DNS A/ALIAS for `pratibhapublicschoolrampur.com` (+ www) → EIP `16.192.54.132`.
2. Nginx `server_name` for those hosts, `root` → school-website-ui static files, `/api/` → `127.0.0.1:9090`.
3. `sudo certbot --nginx -d pratibhapublicschoolrampur.com -d www.pratibhapublicschoolrampur.com`
4. Only then set domain rows **ACTIVE** and site status **PUBLISHED** in Website CMS / Super Admin domain registry.

If DNS/certbot is not ready: deploy images + seeds, leave domains PENDING, and treat the public URL as **not live**.

### F. Verify (prod)

On EC2 (works even while public DNS is PENDING):

```bash
curl -sS "http://127.0.0.1:9090/api/website/public/resolve?host=pps-rampur.localhost" | head -c 500
curl -sS "http://127.0.0.1:9090/api/website/public/resolve?host=pratibhapublicschoolrampur.com" | head -c 500
```

Expect PPS / Rampur JSON for the localhost host (ACTIVE). Production host may resolve only after ACTIVE + published.

---

## Script index

| Path | Purpose |
|---|---|
| `scripts/pps-rampur-website-seed.sql` | Site, theme, homepage, nav, domains |
| `scripts/pps-rampur-cms-seed.sql` | CMS site-scoped seed |
| `scripts/pps-rampur-pages.sql` | CMS pages (about, academics, …) |
| `scripts/pps-rampur-content.sql` | Notices / gallery / related content |
| `scripts/pps-rampur-palette.sql` | Palette / theme helpers |
| `scripts/pps-rampur-logo.sql` | Logo asset rows |
| `scripts/pps-rampur-image-manifest.txt` | Migrated image inventory |
| `apps/school-website-ui/public/pps/` | Static logo + photos |
| `apps/school-website-ui/proxy.conf.json` | Local proxy → `:9090` |
| `services/cms-service/.../V8__cms_site_scope_notices_documents.sql` | Flyway site scope |
| `docker-compose.school.ec2-rds.yml` | EC2 school compose |
| `docs/HCP_EC2_WEBSITE_DEPLOY.md` | Shared school website deploy pattern |

---

## Checklist

- [ ] cms-service image with V8 deployed
- [ ] website-service image deployed
- [ ] admission-service image if public apply changed
- [ ] school-website-ui built with `/pps/**` assets
- [ ] Production SQL seeds applied
- [ ] Gateway resolve smoke for `pps-rampur.localhost`
- [ ] DNS + Nginx + certbot for public domain
- [ ] Domain rows ACTIVE only after vhost+SSL
- [ ] Site PUBLISHED when ready for parents
- [ ] Holly Cross (`HCP-01`) unchanged
