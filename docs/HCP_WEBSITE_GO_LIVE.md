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

## B. Ops (must do in prod — human)

1. **DNS** — apex + `www` CNAME/ALIAS → platform edge (Nginx / CloudFront / ALB).
2. **TLS** — Certbot or ACM; set `website_domain.ssl_status=ACTIVE` when live.
3. **CDN** — set `WEBSITE_CDN_BASE_URL`; long-cache static UI; short-cache public GETs; never cache `/track` or `/admission/public`.
4. **Subscription** — assign plan with website flags (or apply **School + Website** preset) to HCP tenant.
5. **ERP login URL** — confirm `erp_login_url` points at production school-ui.
6. **Smoke prod** — run script with `-BaseUrl https://api…` and `-Host hcpschool.com`.

## C. Acceptance smoke

| Check | Expect |
|---|---|
| `GET …/api/website/public/resolve?host=hcpschool.com` | `organizationId=HCP-01`, `status=PUBLISHED` |
| Public home (UI) | Sections render |
| CMS `/about` | Published page |
| `POST …/api/admission/public/apply` | Application accepted when feature on |
| `POST …/api/website/public/track` | `accepted: true` |
| Parent/Teacher links | Deep-link ERP login with `org=HCP-01` |

## D. Rollback

- Suspend site: set `website_site.status=SUSPENDED` (resolve returns 403).
- Disable domain: `website_domain.status=DISABLED`.
- Turn off flags in Platform Subscription without undeploying services.

See also: [OPS_SCHOOL_WEBSITE_CDN_SSL.md](./OPS_SCHOOL_WEBSITE_CDN_SSL.md).
