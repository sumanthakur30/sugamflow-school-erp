# Ops runbook — School Website CDN / SSL / monitoring

Practical ops for multi-tenant public school websites (`website-service` + `cms-service` + `school-website-ui`).

## DNS

1. School points apex + `www` (or custom host) **CNAME/ALIAS** to the platform edge (Nginx / CloudFront / ALB).
2. Register host in `website_domain` (`ACTIVE`) mapped to `organization_id`.
3. Smoke: `GET /api/website/public/resolve?host=<domain>`.

## TLS (SSL)

**Local / small fleet:** Certbot (HTTP-01 or DNS-01) on the edge Nginx terminating TLS for each host.

```bash
# Example (HTTP-01) — run on edge host
certbot --nginx -d hcpschool.com -d www.hcpschool.com
```

**AWS:** ACM certificate on CloudFront / ALB; DNS validation; attach to distribution with alternate domain names (SANs) or wildcard `*.schools.sugamflow.com`.

Update `website_domain.ssl_status` (`PENDING` → `ACTIVE`) after cert is live (manual for now).

## CDN / caching

1. Put CloudFront (or Nginx cache) in front of:
   - `school-website-ui` static assets (long TTL, fingerprint filenames)
   - Public API GETs (`/api/website/public/**`, `/api/cms/public/**`) — honor origin `Cache-Control` (60s + SWR)
2. **Do not** cache:
   - `POST /api/website/public/track`
   - `POST /api/admission/public/apply`
   - Authenticated `/admin/**`
3. Set `WEBSITE_CDN_BASE_URL=https://cdn.example.com` so resolve returns `cdnBaseUrl` for media URLs.
4. Media files: enable S3 (`CMS_MEDIA_S3_ENABLED=true`) + `CMS_MEDIA_CDN_BASE_URL`; CMS upload stores objects and returns CDN URLs.

### Bot prerender (SEO edge hook)

Serve the HTML shell from website-service for crawlers while humans get the SPA:

```nginx
map $http_user_agent $is_bot {
  default 0;
  ~*(googlebot|bingbot|yandex|baiduspider|twitterbot|facebookexternalhit|linkedinbot|slackbot) 1;
}

location / {
  if ($is_bot) {
    rewrite ^ /api/website/public/prerender?host=$host&path=$uri last;
  }
  # else: school-website-ui SPA
  try_files $uri $uri/ /index.html;
}
```

Smoke: `GET /api/website/public/prerender?host=hcpschool.com&path=/`

### Nginx sketch

```nginx
# Public API — short cache
location /api/website/public/ {
  proxy_pass http://gateway:9090;
  proxy_cache website_cache;
  proxy_cache_valid 200 60s;
  proxy_cache_methods GET HEAD;
  add_header X-Cache-Status $upstream_cache_status;
}

location /api/website/public/track {
  proxy_pass http://gateway:9090;
  proxy_cache off;
}
```

## Rate limits

Gateway `SensitiveEndpointRateLimitFilter` (default 30/min/IP) covers:

- `/api/admission/public/**`
- `/api/website/public/track`
- `/api/website/public/events`

Public resolve/CMS GETs are **not** rate-limited so CDN/edge can scale reads.

## Quotas

Enforced in `cms-service` from subscription entitlements (not a second plan editor):

| Limit code | Gate |
|---|---|
| `website_storage_gb` | Media register |
| `website_pages` | Page create |

Configure via Super Admin → Platform Subscription.

## Monitoring

| Signal | Source |
|---|---|
| `website.analytics.events` | Micrometer counter on track |
| Actuator health | `/actuator/health` on 8200 / 8201 |
| Gateway 429s | Rate-limit log `Rate limit exceeded` |
| Storage | `GET /api/cms/admin/media/usage` |
| Traffic | Admin analytics summary / CDN logs |

Alert ideas: spike in 429 on `/track`, resolve 5xx, Flyway migrate failures, disk on edge cert store.

## Captcha (prod)

```bash
# admission-service
ADMISSION_PUBLIC_CAPTCHA_ENABLED=true
ADMISSION_PUBLIC_CAPTCHA_SECRET=<recaptcha-secret>

# school-website-ui environment.captchaSiteKey = <recaptcha-site-key>
```

## S3 media (prod)

```bash
CMS_MEDIA_S3_ENABLED=true
CMS_MEDIA_S3_BUCKET=sugamflow-school-cms
CMS_MEDIA_S3_REGION=ap-south-1
CMS_MEDIA_CDN_BASE_URL=https://cdn.example.com
# optional MinIO: CMS_MEDIA_S3_ENDPOINT=http://minio:9000 + access/secret keys
```

## Super Admin domains

Shop Management UI → **School Website Domains** (`/admin/school-website-domains`) → map host + mark SSL ACTIVE after Certbot/ACM.

## HCP checklist

- [ ] `hcpschool.com` + `www` DNS → edge
- [ ] TLS active; `ssl_status=ACTIVE` (Super Admin domains UI)
- [ ] Plan has `FEATURE_WEBSITE*` + storage/page limits
- [ ] CDN base URL + optional S3 media enabled
- [ ] Captcha enabled in prod (site key + secret)
- [ ] Bot prerender Nginx map (optional)
- [ ] Smoke: `.\scripts\smoke-hcp-website.ps1 -BaseUrl https://api… -HostName hcpschool.com`
