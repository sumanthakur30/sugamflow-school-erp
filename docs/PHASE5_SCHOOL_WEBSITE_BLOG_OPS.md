# Phase 5 — Blog, media upload, platform domain ops + hardening

Continues School Website Platform after Phase 4 scale ops.

## Delivered

| Piece | Detail |
|---|---|
| Blog | `cms_blog_post`; public `/api/cms/public/blog`; admin CRUD gated by `FEATURE_WEBSITE_BLOG` |
| Public UI | `/blog`, `/blog/:slug` + HCP nav seed |
| Media upload | `POST /api/cms/admin/media/upload` (multipart) + public file GET |
| Domain ops | School `POST /api/website/admin/domains`; platform `GET/POST /api/website/platform/domains`, SSL update |
| Captcha | Optional `admission.public-captcha.*` on public apply (`captchaToken`) |
| Quota metering | Best-effort `increment-usage` on page create / media upload |
| Prerender | `GET /api/website/public/prerender?host=&path=` HTML SEO shell |
| HCP go-live | Checklist + `scripts/smoke-hcp-website.ps1` (step 1) |

## Config

| Property | Default | Notes |
|---|---|---|
| `CMS_MEDIA_STORAGE_DIR` | tmp `school-cms-media` | Local disk store |
| `CMS_MEDIA_PUBLIC_BASE_URL` | empty | Prefix for returned media URLs |
| `ADMISSION_PUBLIC_CAPTCHA_ENABLED` | false | Enable in prod |
| `ADMISSION_PUBLIC_CAPTCHA_SECRET` | empty | reCAPTCHA secret; blank + enabled = non-blank token only |

## Enable blog for a school

Super Admin → Platform Subscription → toggle `FEATURE_WEBSITE_BLOG` (included in **School + Website** preset).

## Still later

- AI / marketplace / alumni / multi-campus site trees
- Full SSR farm + CDN bot routing
- Super Admin Angular console for domains (API ready)
