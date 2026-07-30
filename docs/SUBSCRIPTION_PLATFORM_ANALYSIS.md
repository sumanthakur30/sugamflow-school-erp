# Platform Subscription Engine — Analysis & Additive Extension Blueprint

**Status:** Phase 7 implemented (optional Redis plan/entitlement cache; default OFF).  
**Branch:** `feature/subscription-plans` (School + shop-service)  
**Date:** 2026-07-29  
**Critical constraint:** 100% backward compatibility for School ERP; additive, configuration-driven, non-breaking.

---

## 1. Executive summary

SugamFlow already has **two parallel licensing stacks**:

| Stack | Location | Maturity |
|-------|----------|----------|
| **School subscription-service** | `D:\school\services\subscription-service` | Thin plan catalog + org→plan + feature flags |
| **Shop / hospital licensing** | Embedded in `D:\sugamFlow\shop-service` | Lifecycle (trial/grace/expiry), renew, effective-config, internal gates |

The user requirement is to **extend the School `subscription-service`** into a generic platform engine — **not** create a new microservice — while School ERP keeps working unchanged.

**Recommended strategy:** evolve School `subscription-service` into the **catalog + entitlement authority**, keep existing School APIs as a **compatibility façade**, and introduce **additive** tables/APIs for business types, modules, features, limits, add-ons, and usage. SugamFlow shop-service becomes a **consumer/adapter** over time (not a hard cutover in phase 1).

---

## 2. Current School subscription-service (as-is)

### 2.1 Location & runtime

| Item | Value |
|------|-------|
| Path | `services/subscription-service` |
| Package | `com.sugamflow.school.subscription` |
| Port | `8182` |
| DB | `school_subscription_db` |
| Gateway | `/api/subscription/**` |
| Flyway table | `flyway_schema_history_school_subscription` |

### 2.2 Schema (only two tables)

```text
subscription_plan
  id, code, name, plan_type, active
  limits_json (JSONB)
  feature_flags_json (JSONB)

tenant_subscription
  organization_id (PK)
  plan_id (FK)
  assigned_at
```

**Missing today:** business type, modules catalog, features catalog, add-ons, usage counters, trial/expiry, invoice/payment, roles/seats, branch licenses, audit of plan changes, Redis cache.

### 2.3 Existing APIs (must remain)

| Method | Path | Contract to preserve |
|--------|------|----------------------|
| GET | `/api/subscription/plans` | List plans |
| GET | `/api/subscription/plans/{planId}` | One plan |
| POST | `/api/subscription/plans` | Upsert plan |
| PUT | `/api/subscription/plans/{planId}` | Update plan |
| PUT | `/api/subscription/tenants/current/plan/{planId}` | Assign plan to current org |
| GET | `/api/subscription/tenants/current/entitlements` | `{ organizationId, planId, limits, featureFlags }` |
| GET | `/api/subscription/feature-flags/{flag}` | `{ flag, enabled, planId }` |

### 2.4 Hard contracts (do not break)

1. Entitlements map shape: `planId`, `limits`, `featureFlags`.
2. Flag response shape: `flag`, `enabled`, `planId`.
3. Default plan id `"starter"` when no assignment.
4. Limit convention: **`-1` = unlimited**.
5. Existing `FEATURE_*` string names (HOSTEL, LIBRARY, ADMISSION, …).
6. `ApiResponse` envelope + tenant headers.
7. Idempotent plan assign (provision calls repeatedly).
8. UI fail-open when entitlements unavailable (load-bearing).

### 2.5 What works today in School

| Capability | Status |
|------------|--------|
| Plan catalog + assign | Yes |
| Feature flags on plan JSON | Yes |
| Limits declared on plan JSON | Yes |
| UI nav / route guards by flag | Yes |
| Backend `FEATURE_OFF` via `ConfigEngineClient` | Yes (many services) |
| `maxBranches` enforcement | Yes (`BranchRegistryService`) |
| Student/staff/other limits enforcement | Declared only — **not enforced** |
| Trial / expiry / renew / invoice | **No** |
| Usage metering | **No** |
| Add-on marketplace | **Yes (Phase 9)** — ONE_TIME SKUs + credit wallet |
| Redis entitlement cache | **No** (in-process TTL in clients) |
| Unit tests in subscription-service | **None** |

### 2.6 School consumers (must keep working)

- `school-settings-service` → `SubscriptionClient` (entitlements, assign, maxBranches)
- Domain services → `ConfigEngineClient.isFeatureEnabled` → `GET /feature-flags/{flag}`
- `school-ui` → `EntitlementsService` + `featureGuard` + menu `requiredFeatureFlags`
- Provisioner assigns `"starter"` on first login

---

## 3. SugamFlow shop licensing (overlap — do not ignore)

Shop-service already provides richer SaaS lifecycle:

- Trial → Active → Grace → Expired; renew / suspend / change-plan
- `GET /shops/{id}/effective-config` (modules ∪ features ∪ accessLevel ∪ capabilities)
- Internal: `GET /api/v1/internal/features/shops/{shopId}/enabled|modules/enabled`
- Consumers: order / stock / product via `RemoteEntitlementGateClient`; notification via `ShopFeatureGateClient`

**Phase-1 rule:** Do **not** force shop services onto School APIs until a compatibility adapter exists. Breaking `@RequiresModule` would regress Hospital/Pharmacy/PathLab.

---

## 4. Reusable building blocks vs school glue

| Reusable (keep / generalize) | School glue (preserve as config defaults) |
|------------------------------|-------------------------------------------|
| Plan + tenant assignment | Seeded `FEATURE_HOSTEL`, `FEATURE_LIBRARY`, … |
| JSON bags for limits/flags | Limit keys `maxStudents`, `maxTeachers`, … |
| Entitlements + single-flag GET | Provision `"starter"` + school UI |
| Client TTL cache pattern | Branch = campus semantics |
| Gateway route `/api/subscription/**` | Plan types `government` / `trust` |

---

## 5. Target architecture (additive, domain-agnostic)

### 5.1 Design principle

The engine stores only:

`BusinessType → Plan → Modules → Features → Limits → Add-ons → Licenses (roles/branches) → Usage`

It must **not** hardcode Hospital/School/Pharmacy. Vertical catalogs are **seed data / admin config**.

### 5.2 Proposed additive schema (new tables; existing tables stay)

Keep `subscription_plan` + `tenant_subscription` intact.

Add (examples):

| Table | Purpose |
|-------|---------|
| `business_type` | SCHOOL, HOSPITAL, POLYCLINIC, PATHLAB, … + CUSTOM |
| `module_definition` | code, name, business_type_id (nullable = cross-cutting) |
| `feature_definition` | code, module_id, name |
| `limit_definition` | code, unit, aggregation (NUMERIC / UNLIMITED / PERIOD) |
| `plan_module` / `plan_feature` / `plan_limit` | Normalized plan composition (optional dual-write with JSON) |
| `addon_definition` / `tenant_addon` | Marketplace add-ons |
| `role_seat_limit` | Role → max seats per plan |
| `tenant_subscription_lifecycle` | status, trial_ends, grace_ends, expiry (1:1 with tenant) |
| `usage_counter` / `usage_event` | Metering |
| `subscription_audit_log` | Old/new, user, IP, reason |
| `coupon` / `tax_rule` (later) | Commercial |

**Compatibility bridge:**  
`GET /tenants/current/entitlements` continues to **project** normalized rows → `{ limits, featureFlags }` so School UI/services need zero changes.

### 5.3 Additive APIs (new paths; old paths unchanged)

| New API | Purpose |
|---------|---------|
| `GET /subscriptions/current` | Alias / richer current license (also keep entitlements) |
| `GET /subscriptions/modules` | Enabled modules for tenant |
| `GET /subscriptions/features` | Enabled features |
| `GET /subscriptions/limits` | Limit definitions + remaining |
| `GET /subscriptions/license` | Lifecycle + seats + branches |
| `POST /subscriptions/validate-feature` | `{ featureCode }` → allow/deny + reason |
| `POST /subscriptions/validate-limit` | `{ limitCode, delta }` |
| `POST /subscriptions/increment-usage` / `decrement-usage` | Metering |
| Admin CRUD | business-types, modules, features, plans, add-ons |

School-specific callers keep using `/api/subscription/tenants/current/entitlements` and `/feature-flags/{flag}`.

### 5.4 Phased delivery (non-breaking)

| Phase | Scope | School impact |
|-------|-------|---------------|
| **0 — Analysis** (this doc) | Document as-is + blueprint | None |
| **1 — Catalog tables + seed** | business_type, module, feature, limit defs; seed SCHOOL + hospital/pharmacy catalogs | None if unused |
| **2 — Dual-write / projection** | Plan JSON remains source of truth; optionally sync to normalized tables; entitlements still from JSON | None |
| **3 — Lifecycle additive** | trial/expiry/grace columns + APIs; default “ACTIVE forever” for existing School rows | No behavior change until configured |
| **4 — Usage APIs** | counters + validate-limit; School still not required to call | Optional adoption |
| **5 — Admin UI** | Extend school admin / platform admin for catalogs | Additive screens |
| **6 — Shop adapter** | shop-service effective-config optionally reads platform engine | Behind feature flag |
| **7 — Redis shared cache** | Cache plans/entitlements; invalidate on admin change | Performance only |

---

## 6. Migration principles

1. **Never drop** `limits_json` / `feature_flags_json` in early phases.
2. Existing `tenant_subscription` rows remain valid; lifecycle defaults = unlimited/active.
3. Seed SCHOOL modules/features to **mirror** current `FEATURE_*` keys exactly.
4. Seed other business types as inactive/demo until product enables them.
5. Flyway only additive (`V3+`); no destructive renames without a dual-read window.
6. Provide rollback notes: disable new endpoints via config; old APIs remain.

---

## 7. Authorization & performance targets

- Protected APIs validate: subscription active + module + feature + limit (when enforced).
- Prefer **server-side** checks; UI only hides (never sole authority).
- Cache plan + entitlement snapshots in Redis (phase 7); keep client TTL as fallback.
- Internal S2S key + tenant headers for `validate-feature` / usage.

---

## 8. Security & audit

- Every plan/assignment/addon/limit change → audit row (actor, IP, before/after, reason).
- Super Admin: plan/catalog CRUD.
- Tenant Admin: view own license + request upgrade / purchase add-on.

---

## 9. Success criteria mapping

| Criterion | How we meet it |
|-----------|----------------|
| School unchanged | Keep entitlements + flag APIs + starter default + FEATURE_* names |
| Multi-vertical plans | `business_type` + config catalogs |
| Config-only new plans/modules | Admin CRUD + DB seed; no Java per vertical |
| No vertical hardcoding in engine | Only generic entities; SCHOOL data is seed |
| Single source of truth | Long-term: subscription-service; shop-service becomes adapter |

---

## 10. Explicit non-goals for Phase 1 implementation

- Do not delete or rename existing School endpoints.
- Do not replace shop-service entitlement gates yet.
- Do not require Hospital/Pharmacy services to call new APIs until adapters exist.
- Do not hardcode Hospital module lists in Java — seed SQL / admin only.

---

## 11. Recommended next implementation step (after approval)

1. Stay on `feature/subscription-plans` (School repo).
2. Add Flyway `V3__platform_catalog_tables.sql` (additive only).
3. Seed `business_type=SCHOOL` + existing FEATURE_* as `feature_definition` rows.
4. Keep runtime resolution on JSON; add read-only admin list APIs for catalog.
5. Add characterization tests that lock current entitlements/flag response contracts.

### Phase 1 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Flyway V3 catalog + SCHOOL/vertical seeds | `services/subscription-service/.../V3__platform_catalog_tables.sql` |
| JPA entities + repos | `persistence/entity/*Definition*`, `BusinessTypeEntity`, matching repos |
| Read-only catalog APIs | `GET /api/subscription/catalog/{business-types,modules,features,limits}` |
| Characterization tests | `SubscriptionServiceCharacterizationTest`, `CatalogServiceTest` |

### Phase 2 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Flyway V4 projection tables | `V4__plan_composition_projection.sql` (`plan_feature`, `plan_limit`, `plan_module`) |
| Dual-write on plan save | `SubscriptionService.savePlan` → `PlanProjectionService.syncFromPlanEntity` |
| Startup backfill | `SubscriptionPlanSeeder` end → `syncAllPlans()` |
| Read-only projection APIs | `GET /api/subscription/plans/{planId}/projection[/features|/limits|/modules]` |
| Tests | `PlanProjectionServiceTest` (dual-write + entitlements still JSON) |

### Phase 3 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Flyway V5 lifecycle + backfill | `V5__tenant_subscription_lifecycle.sql` |
| Lifecycle service | `SubscriptionLifecycleService` (ACTIVE/TRIAL/GRACE/EXPIRED/SUSPENDED/CANCELLED) |
| Assign hook | `assignPlan` → `ensureActiveForever` (enforcement off) |
| License APIs | `GET/PUT /tenants/current/license`, renew/suspend/cancel/resume; alias `GET /subscriptions/license` |
| Startup backfill | `SubscriptionLifecycleBackfill` |
| Tests | `SubscriptionLifecycleServiceTest` |

**Enforcement:** `accessAllowed` is false only when `enforcementEnabled=true` and resolved status is not ACTIVE/TRIAL/GRACE. Entitlements + feature-flag APIs are **not** gated in Phase 3.

### Phase 4 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Flyway V6 | `V6__usage_metering.sql` (`usage_counter`, `usage_event`) |
| Metering service | `UsageMeteringService` |
| APIs | `GET .../usage`, `GET .../limits`, `GET .../usage/events`, `POST validate-limit`, `validate-feature`, `increment-usage`, `decrement-usage` |
| Period keys | `ALL` for NUMERIC; `YYYY-MM` for MONTHLY/PERIOD catalog aggregation |
| Soft enforce | `increment` with `enforce=true` refuses overage; default meters even if over |
| Tests | `UsageMeteringServiceTest` |

School is **not** required to call these APIs; entitlements remain plan JSON only.

### Local gateway note (2026-07-29)

Docker gateway must set `GATEWAY_SCHOOL_SUBSCRIPTION_URI=http://host.docker.internal:8182` when School jars run on the host (empty Eureka → `lb://subscription-service` 503; missing Bearer → 401). Wired in `D:\sugamFlow\docker-compose.yml` + `.env.local`.

### Phase 5 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Catalog upsert APIs | `PUT /api/subscription/catalog/{business-types,modules,features,limits}` |
| UI API client | `apps/school-ui/.../subscription/subscription-api.service.ts` |
| Admin screen tabs | `/admin/subscription` → Plans · Catalog · License · Usage |
| Plans | Assign, edit limits/flags JSON, show projection summary + entitlements |
| Catalog | Browse/filter by business type; create/edit modules, features, limits |
| License | View resolved status; renew / enforce toggle / suspend / resume |
| Usage | Limits+used+remaining table; recent usage events |

### Phase 6 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Feature flag (default OFF) | `shop.platform.subscription.enabled` + `base-url` in shop-service `application.properties` |
| Client | `PlatformSubscriptionClient` → entitlements + plan projection modules + license |
| Merge adapter | `PlatformSubscriptionAdapter` overlays into `EffectiveConfigService.build` |
| Access remap | Only when `prefer-platform-access=true` **and** license `enforcementEnabled` |
| Downstream | Unchanged — order/stock still call shop internal feature gates |
| Tests | `PlatformSubscriptionAdapterTest`; `EffectiveConfigServiceTest` stubs adapter empty |

**Enable example:**
```
SHOP_PLATFORM_SUBSCRIPTION_ENABLED=true
SHOP_PLATFORM_SUBSCRIPTION_BASE_URL=http://host.docker.internal:8182
```

### Phase 7 delivered (2026-07-29)

| Item | Location |
|------|----------|
| Opt-in Redis cache | `subscription.cache.enabled=false` (default) |
| Caches | plans list, plan by id, entitlements by org (`school:subscription:*`) |
| Invalidation | `savePlan` → plan + all entitlements; `assignPlan` → org entitlements; seeder → `evictAll` |
| Fail-safe boot | Redis autoconfig excluded unless cache enabled |
| Client fallback | Existing `TtlCache` / `SCHOOL_CONFIG_CACHE_TTL_SECONDS` unchanged |
| Tests | `RedisSubscriptionCacheSupportTest` |

**Enable example:**
```
SUBSCRIPTION_CACHE_ENABLED=true
SUBSCRIPTION_CACHE_REDIS_HOST=localhost
SUBSCRIPTION_CACHE_TTL_SECONDS=60
```

Platform subscription phases 0–7 complete for the blueprint.

### Phase 8 MVP delivered (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V7 commercial tables | `V7__commercial_billing.sql` (billing_cycle, price_book, plan_price, subscription_invoice, subscription_invoice_line) |
| Service | `CommercialBillingService` — cycles, price books, plan prices, draft/issue/void invoices |
| APIs | `GET/PUT /api/subscription/billing/*`, `POST .../invoices/draft`, issue/void |
| Amounts | Minor units (paise); tax_minor fixed 0 until GST phase |
| Payments | Not included (no gateway) |
| Tests | `CommercialBillingServiceTest` |

**Compatibility:** existing entitlements / feature-flag / lifecycle / usage APIs unchanged.

### Phase 8 extended slices (2026-07-30)

| Slice | Migration / surface |
|-------|---------------------|
| GST tax rules + tax lines | `V8__gst_tax_rules.sql` · CGST/SGST/IGST bps · invoice tax lines |
| Coupons + proration | `V9__billing_coupons.sql` · `couponCode` / `remainingDays` on draft |
| Payment webhook → PAID + renew | `V10__subscription_payments.sql` · `POST /billing/payments/webhook` |
| Super Admin Billing UI | SugamFlow Platform Subscription → **Billing** tab |

Seeded: `IN_GST_18`, `WELCOME10`. Inter-state uses IGST when `placeOfSupply` ≠ `sellerStateCode`.

### Phase 9 marketplace + Razorpay (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V11 | `V11__addon_marketplace.sql` — addon_definition / addon_price / tenant_addon / credit_wallet / credit_ledger + `gateway_order_id` |
| Marketplace | `AddonMarketplaceService` — catalog, ONE_TIME purchase draft (+GST), fulfill on PAID |
| Razorpay | `SubscriptionRazorpayClient` — Orders API; blank keys → `SIMULATED`; checkout confirm + server webhook |
| APIs | `GET /billing/marketplace/addons`, `POST .../marketplace/purchase`, `POST .../invoices/{id}/razorpay-order`, `POST /billing/payments/razorpay/confirm`, `POST /billing/payments/razorpay/webhook` |
| Config | `subscription.payment.mode` / `subscription.payment.razorpay.*` (env fallbacks `RAZORPAY_*`) |
| Super Admin | Billing tab — draft addon invoice, Razorpay (simulate when keys blank), tenant addons/credits |
| Tests | `AddonMarketplaceServiceTest`, extended `CommercialBillingServiceTest` |

ONE_TIME paid invoices skip license renew; addon lines fulfill `tenant_addon` + credit wallet (idempotent by invoice_id).

### Phase 10 admin analytics (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V12 | `V12__subscription_analytics_indexes.sql` — invoice/payment/usage indexes |
| Service | `SubscriptionAnalyticsService` — overview (MRR/ARR), plan mix, renewals, revenue series, usage heat |
| APIs | `GET /api/subscription/analytics/{dashboard,overview,plan-mix,renewals,revenue,usage}` |
| MRR model | Sum of default price-book **MONTHLY** list prices for ACTIVE/TRIAL tenants (unpriced counted separately) |
| Super Admin | Platform Subscription → **Analytics** tab |
| Tests | `SubscriptionAnalyticsServiceTest` |

### Phase 11 Plan Builder UX (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V13 | `V13__plan_builder_versions.sql` — `plan_version`, `plan_price_schedule` |
| Service | `PlanBuilderService` — ensure/save draft, publish → live `subscription_plan`, schedule/apply prices |
| APIs | `POST /plans/{id}/versions/draft`, `PUT .../versions/{vid}`, `POST .../publish`, `GET/POST .../price-schedules` |
| Publish | Writes feature flags + limits via existing `SubscriptionService.savePlan` (entitlements unchanged until publish) |
| Super Admin | Feature Builder — drag-drop module order, Save draft / Publish live, version picker, schedule list price |
| Tests | `PlanBuilderServiceTest` |

### Phase 12 CRM renewals (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V14 | `V14__crm_renewals.sql` — `renewal_opportunity`, `renewal_reminder` |
| Service | `RenewalCrmService` — pipeline sync, health 0–100, T30/T14/T7/GRACE/FAIL_PAY reminders, upsell near-limit |
| APIs | `GET /crm/dashboard`, `POST /crm/sync`, `GET /crm/pipeline`, health/upsell, reminders generate/mark |
| Health factors | days-to-expiry · lifecycle grace/expired · payment success/fail 90d · usage depth |
| Super Admin | Platform Subscription → **CRM Renewals** tab |
| Tests | `RenewalCrmServiceTest` |

### Phase 13 AI monetization (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V15 | `V15__credit_monetization.sql` — `credit_policy`, `credit_period_run` (+ seed ai_credits/whatsapp/sms) |
| Service | `CreditMonetizationService` — policies, consume/grant, carry-forward % + cap + expire, period runs |
| Carry rule | `carried = min(opening × bps/10000, cap)`; if `expireUnused` remainder expires; then `planGrantAmount` |
| APIs | `/credits/policies`, tenant consume/grant/carry-forward, batch carry-forward |
| Super Admin | Platform Subscription → **AI Credits** tab |
| Tests | `CreditMonetizationServiceTest` |

### Phase 14 enterprise controls (2026-07-30)

| Item | Location |
|------|----------|
| Flyway V16 | `V16__enterprise_controls.sql` — `enterprise_org_settings`, `enterprise_audit_event`, `enterprise_audit_export` |
| Service | `EnterpriseControlsService` — SSO/WL/residency/HA settings, entitlement gates, CSV/JSON audit export |
| Entitlement gates | SSO: `FEATURE_SSO` \| `FEATURE_WHITE_LABEL` \| `FEATURE_CUSTOM_DOMAIN`; WL: `FEATURE_WHITE_LABEL` |
| APIs | `/enterprise/dashboard`, `/enterprise/tenants/{org}/settings`, audit-events, audit-exports |
| Scope | Config store + local enterprise audit trail only — IdP runtime / Design Studio branding / platform `audit-service` unchanged |
| Super Admin | Platform Subscription → **Enterprise** tab |
| Tests | `EnterpriseControlsServiceTest` |


---

## 12. Practical maintenance rule (locked 2026-07-29)

**Configure** in SugamFlow Super Admin → **Platform Subscription** (`/admin/platform-subscription`) against platform `subscription-service` (catalog, plans, assign/renew).

**Enforce** only in existing runtimes:
- shop-service `effective-config` / `@RequiresModule` (Phase 6 adapter, **OFF by default**)
- School entitlements / feature-flags

**Never** duplicate plan/module/limit logic in feature services or UI feature modules.

| Layer | Role |
|-------|------|
| Super Admin (shop-management-ui) | **Control plane** — Feature Builder + shop package overrides |
| School UI `/admin/subscription` | Tenant self-view / school-admin tools (not the global hub) |
| shop-service adapter | Pull/cache platform snapshots into effective-config when enabled |
| Feature code | Consume entitlements/config only — no hardcoded plan matrices |

### Feature Builder UI (2026-07-29)

SugamFlow Super Admin `/admin/platform-subscription` redesigned as a **Business Feature Builder**:

| Tab | Behavior |
|-----|----------|
| Feature Builder | Business type chips, plan picker, module cards, Configure drawer (feature checkboxes → existing codes), presets, live summary, Save plan → `PUT /plans/{id}` |
| Plan compare | Matrix of feature flags across plans |
| Shop Subscription | Assign plan, custom enable/disable overrides → save as `shop-{orgId}` plan package then assign (no new entitlement API) |
| Catalog (advanced) | Raw module/feature/limit CRUD (unchanged APIs) |
| Enterprise | SSO / white-label / residency / HA settings + audit CSV/JSON export (Phase 14) |

UI helpers live in `platform-subscription.presets.ts` (presets, soft deps, badges, illustrative pricing). **No** duplication of runtime entitlement logic in feature services.

Cursor rules: `.cursor/rules/platform-subscription-maintenance.mdc` (School + sugamFlow, alwaysApply).

