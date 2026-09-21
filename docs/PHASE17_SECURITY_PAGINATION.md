# Phase 17 — Security hardening + list pagination

Addresses production-audit blockers: tenant spoofing via headers, missing nav/route
feature gates, and unbounded org-wide list APIs.

## Security

| Control | Change |
|---|---|
| Gateway school routes | JWT `shopId` is authoritative org id for non-`SUPER_ADMIN`; client `X-Tenant-Id` ignored |
| `X-Gateway-Verified` | Required by `TenantFilter` (default `school.security.require-gateway-verified=true`) |
| Internal hops | `TenantHeaders.apply` sets `X-Gateway-Verified` + `X-Internal-Service` |
| Identity headers | Prefer `X-Auth-User` / `X-Auth-Role` over client school headers |
| Admin nav | Filtered by subscription feature flags + portal roles |
| Routes | `featureGuard` on gated admin/portal paths |
| Menus | `GET /api/config/menus/effective` filters by role / branch / flags |

Local bypass (direct service ports only): `--school.security.require-gateway-verified=false`

## Pagination

Domain lists return `PageResult`:

```json
{ "items": [...], "page": 0, "size": 50, "totalElements": 12, "totalPages": 1, "hasNext": false }
```

Query params: `page` (default 0), `size` (default 50, max 200).

When branch + academic session are present on the tenant scope, lists filter by
`organizationId + branchId + academicSessionId`.

Covered: admission, fee, student, attendance, exam, library, hostel, transport, payroll.

## UI

- `ApiService.getPage` / `getItems`
- Shell nav catalog + entitlements load
- Portal sections unwrap page envelopes

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-security-pagination.ps1
```

Requires rebuilt gateway + school services, then gateway restart.
