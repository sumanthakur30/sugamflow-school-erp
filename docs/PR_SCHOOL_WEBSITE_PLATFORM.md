# Pull requests — School Website Platform (Phases 0–5)

`gh` is not logged in on this machine. After `gh auth login`, create PRs:

```powershell
# school
cd D:\school
gh pr create --base dev --head feature/school-website-platform --title "feat(website): School Website Platform Phases 0–5" --body-file docs/PR_SCHOOL_WEBSITE_PLATFORM.md

# gateway
cd D:\sugamFlow\gateway-service
gh pr create --base dev --head feature/school-website-platform --title "feat(gateway): School Website Platform public routes" --body @"
## Summary
- Route website-service + cms-service
- Whitelist public website/CMS/admission paths
- Rate-limit abuse POSTs (track/events/admission), not cacheable GETs

## Test plan
- [ ] GatewayPublicPathsTest
- [ ] resolve / cms / track / apply / prerender via gateway
"@

# shop-management-ui
cd D:\sugamFlow\shop-management-ui
gh pr create --base dev --head feature/school-website-platform --title "feat(admin): School + Website subscription preset" --body @"
## Summary
- Add School + Website preset with FEATURE_WEBSITE* (incl. SEO + Blog)

## Test plan
- [ ] Super Admin → Platform Subscription → apply School + Website preset
"@
```

## Summary (school)

- Phases 0–4: foundation, CMS, SSO/admission, builder/SEO, scale ops
- Phase 5: blog, media upload, platform domain APIs, captcha hook, prerender, HCP go-live smoke

## Test plan

- [ ] `.\scripts\smoke-hcp-website.ps1`
- [ ] Build website/cms/admission
- [ ] Enable FEATURE_WEBSITE_BLOG and publish a post
- [ ] Ops checklist in docs/HCP_WEBSITE_GO_LIVE.md

Branches are already pushed to `origin/feature/school-website-platform`.
