# Phase 3 — Homepage builder + SEO

## Delivered

| Piece | Detail |
|---|---|
| Homepage admin | `PUT /api/website/admin/homepage` enable + reorder |
| Theme/SEO admin | `PUT /api/website/admin/theme`, `/seo` |
| Bootstrap | Admin bootstrap returns homepage + theme + seo |
| Sitemap | `GET /api/website/public/sitemap?host=` |
| Robots | `GET /api/website/public/robots.txt?host=` |
| CMS pages index | `GET /api/cms/public/pages?organizationId=` |
| Public UI | Section-driven home + SeoService meta tags |
| ERP CMS UI | Tabs: Pages / Homepage / SEO |

## Notes

- Drag-drop mega-builder remains deferred; Phase 3 uses enable + reorder.
- Full SSR/prerender remains Phase 4+; meta tags + sitemap cover SEO MVP.
- Multi-school onboarding: configure domain in `website_domain`, assign `FEATURE_WEBSITE*`, publish CMS pages.
