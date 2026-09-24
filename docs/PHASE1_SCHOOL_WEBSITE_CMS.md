# Phase 1 — School Website CMS + Public UI

## Delivered

| Piece | Detail |
|---|---|
| `cms-service` | Port **8201**, DB `school_cms_db` |
| Content model | pages, news, events, gallery |
| Public APIs | `/api/cms/public/**` |
| Admin APIs | `/api/cms/admin/pages` CRUD + publish |
| `school-website-ui` | Public Angular app on port **4300** |
| School ERP CMS | `/admin/website-cms` gated by `FEATURE_WEBSITE_CMS` |
| HCP seed | About, Admission, Contact, Faculty, news, events, gallery |

## Local run

```powershell
cd D:\school
.\infra\postgres\init-local.ps1

# services
cd D:\school\services
mvn -pl website-service,cms-service -am package -DskipTests

# public website UI
cd D:\school\apps\school-website-ui
npm start
# http://localhost:4300  (uses host hcp.localhost via environment.defaultHost)
```

Smoke:

```http
GET http://localhost:9090/api/website/public/resolve?host=hcpschool.com
GET http://localhost:9090/api/cms/public/pages/about?organizationId=HCP-01
GET http://localhost:9090/api/cms/public/news?organizationId=HCP-01
```

## Notes

- Public website stays separate from `school-ui`.
- ERP portals remain deep-links via `erpLoginUrl`.
- Phase 2: SSO bridge + public admission apply.
