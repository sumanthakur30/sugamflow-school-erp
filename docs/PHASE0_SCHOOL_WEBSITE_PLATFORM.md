# Phase 0 — School Website Platform (Foundation)

Additive foundation for the multi-tenant **SugamFlow School Website Platform**. Does not change ERP transactional modules.

## Delivered

| Piece | Detail |
|---|---|
| `website-service` | Port **8200**, DB `school_website_db` |
| Domain registry | `website_domain` (host → `organization_id`) |
| Site shell | `website_site` (theme / homepage / navigation JSON) |
| Public resolve | `GET /api/website/public/resolve?host=` |
| Admin bootstrap | `GET /api/website/admin/bootstrap` (JWT + tenant) |
| Catalog | Module `SCHOOL_WEBSITE` + `FEATURE_WEBSITE*` + limits |
| Gateway | Route `/api/website/**` + public whitelist `/api/website/public/` |
| HCP seed | `hcpschool.com`, `www.hcpschool.com`, `hcp.localhost` → `HCP-01` |

## Local setup

```powershell
# Create DB/user (idempotent)
cd D:\school
.\infra\postgres\init-local.ps1

# Build
cd D:\school\services
mvn -pl website-service -am package -DskipTests

# Start with other school services (or alone after Eureka)
# website-service listens on 8200
```

Smoke (via gateway once registered):

```http
GET http://localhost:9090/api/website/public/resolve?host=hcpschool.com
GET http://localhost:9090/api/website/public/resolve?host=hcp.localhost
```

## Constraints

- ERP remains SSOT — no duplicated admission/fee/portal logic.
- Tenant for public traffic comes from **domain registry**, not client-supplied org id.
- CMS service + public Angular UI are **Phase 1**.
- SSO / public admission apply are **Phase 2**.

## Feature flags (Platform Subscription)

- `FEATURE_WEBSITE`
- `FEATURE_WEBSITE_CMS`
- `FEATURE_WEBSITE_ADMISSION`
- `FEATURE_WEBSITE_SEO`
- `FEATURE_WEBSITE_BLOG`

Limits: `website_pages`, `website_storage_gb`, `website_custom_domains`.

Enable for HCP via Super Admin → Platform Subscription (assign plan with website flags, or toggle features on the school plan).
