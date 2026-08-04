# HCP production cutover — what to change (localhost → https://hcpschool.com)

Goal: public pages like `https://hcpschool.com/about` instead of `http://localhost:4300/about`.

You do **not** rewrite every `/about` path in code. The **host** comes from DNS + domain mapping; the SPA routes stay `/about`.

---

## 1. Already correct in code (no change needed for path)

| Item | Behavior |
|---|---|
| Routes `/about`, `/alumni`, … | Same paths on any host |
| Domain seed | `hcpschool.com`, `www.hcpschool.com` → org `HCP-01` |
| Gateway routes | `/api/website/**`, `/api/cms/**` |
| Resolve tenant | `GET /api/website/public/resolve?host=hcpschool.com` (browser host in prod) |

---

## 2. Must change for production

### A. DNS + TLS (ops)

| What | Where |
|---|---|
| `hcpschool.com` + `www` → platform edge | DNS provider |
| HTTPS certificates | Certbot / ACM — see `OPS_SCHOOL_WEBSITE_CDN_SSL.md` |
| Mark SSL active | Super Admin → School Website Domains → `ssl_status=ACTIVE` |

### B. Deploy apps

| App | Build | Serve as |
|---|---|---|
| `school-website-ui` | `ng build --configuration=production` | Static site for **hcpschool.com** (Nginx/CloudFront) |
| `school-ui` | production build | `https://school.sugamflow.com` (ERP / Sign in) |
| `website-service` + `cms-service` | jars/images | Behind gateway |
| `gateway-service` | branch with website/cms routes | `https://api…` or same-origin `/api` |

### C. Environment / config files

| Setting | File / location | Production value |
|---|---|---|
| Public API base | `apps/school-website-ui/src/environments/environment.prod.ts` → `apiBaseUrl` | `''` (same origin) **or** `https://api.sugamflow.com` if API is separate |
| ERP Sign in fallback | same file → `erpBaseUrl` | `https://school.sugamflow.com` |
| `defaultHost` | same file | leave `''` so browser uses real `hcpschool.com` |
| CMS preview iframe | `apps/school-ui/.../environment.prod.ts` → `websitePreviewUrl` | `https://hcpschool.com` |
| CDN | `.env.school.production` / EC2 | `WEBSITE_CDN_BASE_URL=https://cdn.sugamflow.com` |
| ERP deep-link in DB | `website_site.erp_login_url` for `HCP-01` | `https://school.sugamflow.com/login` (Flyway **V12**) |
| Default ERP (new sites) | `WEBSITE_DEFAULT_ERP_LOGIN_URL` | `https://school.sugamflow.com/login` |
| JWT + internal key | School + SugamFlow env | `SECURITY_JWT_ENFORCE=true`, matching `SECURITY_JWT_SECRET` + `SECURITY_INTERNAL_API_KEY` |
| SMTP | SugamFlow `.env.production` `SPRING_MAIL_*` | Real SMTP host (not `localhost`) |

Preflight:

```powershell
.\scripts\check-prod-env.ps1
```

### D. Nginx (or equivalent) for `hcpschool.com`

Must:

1. Serve `school-website-ui` `index.html` for SPA routes (`/about`, `/alumni`, …).
2. Proxy `/api/` → gateway (so `apiBaseUrl: ''` works).
3. Optional: bot prerender — see `OPS_SCHOOL_WEBSITE_CDN_SSL.md`.

### E. Platform Subscription

Super Admin → assign **School + Website** plan to `HCP-01`.

### F. Do **not** set

```bash
WEBSITE_CDN_BASE_URL=https://hcpschool.com   # wrong
```

CDN ≠ school domain.

---

## 3. Local-only values (keep for dev; do not ship as prod DB)

| Item | Local | Prod |
|---|---|---|
| `environment.ts` `apiBaseUrl` | `http://localhost:9090` | prod file |
| `defaultHost` | `hcp.localhost` | empty / real host |
| `erp_login_url` (after V3) | `http://localhost:4200/login` | V12 → school.sugamflow.com |
| Preview | `http://localhost:4300` | `https://hcpschool.com` |

---

## 4. Verify after deploy

```powershell
.\scripts\smoke-hcp-website.ps1 -BaseUrl https://<your-api-host> -HostName hcpschool.com
```

Browser checks:

- https://hcpschool.com/
- https://hcpschool.com/about
- https://hcpschool.com/contact
- **Sign in** → `https://school.sugamflow.com/login?org=HCP-01` (no forced Parent destination)
- Resolve: `GET https://<api>/api/website/public/resolve?host=hcpschool.com` → `HCP-01`, `PUBLISHED`

---

## 5. Code already updated for this cutover

- Flyway `V12__hcp_prod_erp_login_url.sql` — HCP Sign in → production school-ui  
- `school-ui` `environment.prod.ts` — `websitePreviewUrl: https://hcpschool.com`  
- CMS homepage save notice no longer hardcodes localhost:4300  

If you keep a **local** DB after pulling V12 and still need local Sign in:

```sql
UPDATE website_site
SET erp_login_url = 'http://localhost:4200/login'
WHERE organization_id = 'HCP-01' AND branch_id = 'main';
```
