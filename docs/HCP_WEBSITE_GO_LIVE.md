# HCP School website — go-live checklist

First production tenant: **HCP School** (`organization_id=HCP-01`, domains `hcpschool.com` / `www.hcpschool.com`).

Automated local/staging smoke: `.\scripts\smoke-hcp-website.ps1`

## A. Platform readiness (engineering)

- [x] `website-service` + `cms-service` seeded for `HCP-01`
- [x] Domains: `hcpschool.com` (primary), `www.hcpschool.com`, `hcp.localhost`
- [x] Public resolve / CMS pages / admission apply / analytics track
- [x] Gateway routes + public whitelist + abuse rate limits
- [x] Catalog: `FEATURE_WEBSITE*` + `website_pages` / `website_storage_gb` / `website_custom_domains`
- [x] Super Admin preset: **School + Website** (`shop-management-ui`)

## Where to configure (HCP)

| What | Where | Notes |
|---|---|---|
| Public host `hcpschool.com` / `www` | Super Admin → **School Website Domains** (`shop-management-ui` → `/admin/school-website-domains`) | Seed already maps → `HCP-01`; mark `ssl_status=ACTIVE` after TLS |
| DNS / TLS | Edge Nginx / CloudFront / ALB + Certbot or ACM | Point apex + www at platform edge |
| `cdnBaseUrl` / `WEBSITE_CDN_BASE_URL` | Env on **website-service** (prod file below) | **Not** the school domain — CDN/media origin only |
| Media CDN / S3 | Env on **cms-service** | `CMS_MEDIA_CDN_BASE_URL` (falls back to `WEBSITE_CDN_BASE_URL`) |
| Plan flags | Super Admin → **Platform Subscription** → apply **School + Website** to `HCP-01` | Owns admission/blog/etc. |
| Content | school-ui → **Website CMS** | Pages, campuses, alumni, AI, marketplace |

### File / env locations

| Environment | File or command |
|---|---|
| Prod template (committed) | `D:\school\.env.school.production.example` → section **School Website Platform** |
| Prod on EC2 (do not commit) | `/home/ec2-user/opt/school/.env.school.production` (local copy: `D:\school\.env.school.production`) |
| website-service property | `services/website-service/src/main/resources/application.properties` → `website.cdn.base-url=${WEBSITE_CDN_BASE_URL:}` |
| cms-service property | `services/cms-service/src/main/resources/application.properties` → `cms.media.cdn-base-url=…` |
| Local jar run | Set `$env:WEBSITE_CDN_BASE_URL=…` then `.\scripts\start-services.ps1` (or export in the service process env) |
| Ops runbook | [OPS_SCHOOL_WEBSITE_CDN_SSL.md](./OPS_SCHOOL_WEBSITE_CDN_SSL.md) |

```bash
WEBSITE_CDN_BASE_URL=https://cdn.sugamflow.com
CMS_MEDIA_CDN_BASE_URL=https://cdn.sugamflow.com
```

HCP prod default is `https://cdn.sugamflow.com` (see `.env.school.production.example`). Local jar smoke stays empty unless you export the same env before starting website-service.

## B. Ops (must do in prod — human)

1. **DNS** — apex + `www` CNAME/ALIAS → platform edge (Nginx / CloudFront / ALB).
2. **TLS** — Certbot or ACM; set domain `ssl_status=ACTIVE` in Super Admin domains UI.
3. **CDN** — set `WEBSITE_CDN_BASE_URL` in `.env.school.production` (or process env); restart website-service (+ cms-service); long-cache static UI; short-cache public GETs; never cache `/track` or `/admission/public`.
4. **Subscription** — assign plan with website flags (or apply **School + Website** preset) to HCP tenant.
5. **ERP login URL** — confirm `erp_login_url` / `WEBSITE_DEFAULT_ERP_LOGIN_URL` points at production school-ui.
6. **Smoke prod** — `.\scripts\smoke-hcp-website.ps1 -BaseUrl https://api… -HostName hcpschool.com`.

## C. Acceptance smoke

| Check | Expect |
|---|---|
| `GET …/api/website/public/resolve?host=hcpschool.com` | `organizationId=HCP-01`, `status=PUBLISHED` |
| Public home (UI) | Sections render |
| CMS `/about` | Published page |
| `POST …/api/admission/public/apply` | Application accepted when feature on (send `age` ≥ 3) |
| `POST …/api/website/public/track` | `accepted: true` |
| Parent/Teacher links | Deep-link ERP login with `org=HCP-01` |

## D. Rollback

- Suspend site: set `website_site.status=SUSPENDED` (resolve returns 403).
- Disable domain: `website_domain.status=DISABLED`.
- Turn off flags in Platform Subscription without undeploying services.

See also: [OPS_SCHOOL_WEBSITE_CDN_SSL.md](./OPS_SCHOOL_WEBSITE_CDN_SSL.md), **[HCP_PRODUCTION_CUTOVER.md](./HCP_PRODUCTION_CUTOVER.md)** (localhost → hcpschool.com checklist).
