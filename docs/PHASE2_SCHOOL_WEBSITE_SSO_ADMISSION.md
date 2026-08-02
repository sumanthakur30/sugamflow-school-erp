# Phase 2 — SSO bridge + public online admission

## Delivered

| Piece | Detail |
|---|---|
| Public apply | `POST /api/admission/public/apply` |
| Feature gate | `FEATURE_WEBSITE_ADMISSION` (falls back to `FEATURE_ADMISSION`) |
| Gateway | Whitelist + rate-limit `/api/admission/public/` |
| Website UI | `/admission` hub + `/admission/apply` form |
| SSO bridge | ERP links with `?org=&destination=&returnUrl=` |
| school-ui login | Honors `destination` + safe relative `returnUrl` |
| Portal deep-links | Parent / Teacher / Staff from website footer |

## Apply payload

```json
{
  "organizationId": "HCP-01",
  "fullName": "Student Name",
  "mobile": "9999999999",
  "email": "parent@example.com",
  "classApplied": "Class 5",
  "message": "Looking for admission"
}
```

Creates a normal admission application (`source=WEBSITE`) in ERP for staff follow-up.

## SSO examples

```text
http://localhost:4200/login?org=HCP-01&destination=parent&returnUrl=/parent
http://localhost:4200/login?org=HCP-01&destination=teacher&returnUrl=/teacher
```

## Smoke

```http
POST http://localhost:9090/api/admission/public/apply
Content-Type: application/json

{ "organizationId":"HCP-01", "fullName":"Test", "mobile":"9999999999", "classApplied":"1" }
```

Enable `FEATURE_WEBSITE_ADMISSION` (or `FEATURE_ADMISSION`) on the school plan via Platform Subscription.
