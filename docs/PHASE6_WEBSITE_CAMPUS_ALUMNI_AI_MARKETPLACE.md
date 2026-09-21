# Phase 6 — Multi-campus, alumni, AI assist, marketplace

## Multi-campus trees

- `website_site.branch_id` + `is_default` (unique per org+branch); HCP stays `main`
- `website_domain.site_id` links host → campus site
- Resolve returns `branchId` + `siteId`
- Admin: `GET/POST /api/website/admin/campuses`
- Domain upsert accepts optional `siteId`
- Gate multi-site with ERP `FEATURE_MULTI_BRANCH` / limit `website_campuses` (catalog)

## Alumni

- Table `cms_alumni_profile`
- Public `/api/cms/public/alumni`, admin CRUD gated by `FEATURE_WEBSITE_ALUMNI`
- UI `/alumni`, `/alumni/:slug`

## AI assist

- `POST /api/cms/admin/ai/draft` gated by `FEATURE_WEBSITE_AI`
- Local deterministic drafts by default; optional `CMS_AI_API_URL` + `CMS_AI_API_KEY`

## Marketplace

- Catalog `website_template` (classic-school, modern-campus)
- Public list `/api/website/public/marketplace/templates`
- Apply `/api/website/admin/marketplace/apply` → theme/homepage/nav/seo
- Flag `FEATURE_WEBSITE_MARKETPLACE`

## Catalog flags (V21)

`FEATURE_WEBSITE_ALUMNI`, `FEATURE_WEBSITE_AI`, `FEATURE_WEBSITE_MARKETPLACE`, limit `website_campuses`.
