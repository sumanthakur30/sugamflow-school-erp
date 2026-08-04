# Enterprise Website Builder — phased roadmap

Goal: evolve `/admin/website-cms` into a commercial, multi-tenant Website Builder (WordPress/Elementor-class) **without breaking** current HCP flows (pages, SEO, media, admission, campuses, marketplace, AI).

Public site remains `school-website-ui`; ERP CMS remains the authoring surface. Theme/content stay JSON on `website-service` / `cms-service`.

## Non‑negotiables

- Backward compatible APIs and existing tabs/modules keep working
- No hardcoded school values; drive from tenant resolve + plan flags
- Platform Subscription owns entitlements (`FEATURE_WEBSITE*`, storage limits)
- Super Admin domains / CDN / templates stay platform-owned

## Phase map

| Phase | Name | Outcome |
|---|---|---|
| **1 (now)** | Shell + Dashboard + Builder foundation | Module nav, overview dashboard, homepage builder with live preview, theme colors/logo, all legacy modules reachable |
| **2** | Page & nav builder | Visual page canvas, nav builder, version/schedule stubs wired to APIs |
| **3** | Content modules | Blog/Events/News/Gallery/Staff/Downloads UIs on existing CMS APIs |
| **4** | Forms ↔ ERP | Admission/contact/enquiry forms mapped to admission/CRM/HR |
| **5** | Full canvas DnD | Widget palette, responsive breakpoints, saved blocks, animations |
| **6** | SEO/Perf/Publish | Schema, redirects, Core Web Vitals hooks, workflow approve/publish |
| **7** | Marketplace & Super Admin | Premium widgets, global templates, CDN/backup policies |

## Phase 1 delivered in product UI

- Left **module rail** (Dashboard, Builder, Pages, Theme, Media, …)
- **Dashboard** cards from bootstrap / analytics / media usage
- **Website Builder**: section library + reorder + hero properties + iframe preview (`websitePreviewUrl`)
- **Theme**: primary/secondary/logo/favicon → `PUT /api/website/admin/theme`
- Existing Pages / SEO / Media / Campuses / Alumni / AI / Marketplace / Analytics preserved

## Later modules (UI placeholders until APIs land)

Forms, Blogs, Events, News, Gallery, Admissions, Staff, Achievements, Downloads, Navigation, Publish workflow, Multi-language.

See also: `HCP_WEBSITE_GO_LIVE.md`, `PHASE0`–`PHASE6` website docs.
