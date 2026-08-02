# Phase 4 — Scale ops (CDN, analytics, quotas, rate limits)

Ops hardening for the multi-tenant School Website Platform. ERP remains SSOT; subscription limits stay in Platform Subscription.

## Delivered

| Piece | Detail |
|---|---|
| Cache headers | Public website/CMS GETs: `Cache-Control: public, max-age=60, stale-while-revalidate=300` |
| CDN config | `website.cdn.base-url` / `WEBSITE_CDN_BASE_URL` returned on resolve as `cdnBaseUrl` |
| Analytics | `POST /api/website/public/track` (+ `/events`); admin `GET /api/website/admin/analytics?days=` |
| Retention | Nightly purge older than `website.analytics.retention-days` (default 90) |
| Rate limits | Gateway rate-limits abuse POSTs only (`/track`, `/events`, admission public) — not cacheable GETs |
| Media quota | `POST /api/cms/admin/media` registers assets; enforces `website_storage_gb` |
| Page quota | Create page enforces `website_pages` from entitlements |
| Usage API | `GET /api/cms/admin/media/usage` |
| Public UI | Page-view tracking on navigation |
| ERP CMS | Analytics tab (events + quota snapshot) |
| Ops runbook | [OPS_SCHOOL_WEBSITE_CDN_SSL.md](./OPS_SCHOOL_WEBSITE_CDN_SSL.md) |

## Smoke

```http
POST http://localhost:9090/api/website/public/track
{"host":"hcp.localhost","eventType":"page_view","path":"/"}

GET http://localhost:9090/api/website/public/resolve?host=hcp.localhost
# → includes cdnBaseUrl

GET /api/website/admin/analytics?days=30
GET /api/cms/admin/media/usage
```

## Config

| Property | Default | Purpose |
|---|---|---|
| `WEBSITE_CDN_BASE_URL` | empty | CDN origin for media/static |
| `WEBSITE_ANALYTICS_RETENTION_DAYS` | 90 | Event retention |
| `CMS_SUBSCRIPTION_URL` | `http://localhost:8182` | Entitlements for quotas |

## Out of scope (later)

- Full ACM / Let's Encrypt automation API inside Super Admin
- Binary upload proxy (Phase 4 registers URL + size only; store on S3/CDN separately)
- Full SSR prerender farm
