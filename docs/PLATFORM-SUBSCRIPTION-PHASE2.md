# Platform Subscription — Phase 2 (Unify)

**Branch:** `august01`  
**Rules:** `.cursor/rules/platform-subscription-maintenance.mdc`, `.cursor/rules/platform-identity-subscription.mdc`  
**Predecessor:** [PLATFORM-IDENTITY-PHASE1.md](./PLATFORM-IDENTITY-PHASE1.md) (Identity done)  
**Related:** School `docs/SUBSCRIPTION_PLATFORM_ANALYSIS.md`, shop `docs/SUBSCRIPTION-MANAGEMENT.md`

---

## Goal

One **platform catalog** (School `subscription-service` + Super Admin → Platform Subscription) is the configure plane for every vertical. Runtimes **dual-read** then **cut over**; they never grow a second plan editor.

```
Super Admin → Platform Subscription (catalog / plans / assign / renew)
        ↓ snapshots
   ┌────┴────┐
School flags   Shop effective-config (+ @RequiresModule)
```

---

## As-is (why Phase 2)

| Plane | Today | Problem |
|-------|--------|---------|
| Configure | Super Admin UI → school `subscription-service` | Correct long-term home |
| School enforce | `subscription_plan` **JSON** via `/entitlements` + `/feature-flags/{flag}` | `plan_*` projection is dual-**write** only |
| Shop enforce | `plan_definitions` + `PlanEntitlements` + `enabled_modules` | Parallel SoT |
| Bridge | `shop.platform.subscription.enabled` (default **off**) | Overlay without module-code mapping |

Module mismatch: platform seeds `HOSPITAL_OPD`, `SCHOOL_FEE`, … vs shop `ModuleCode` (`BILLING`, `LAB`, `PHARMACY`, …).

---

## Non-breaking principles

1. Keep School HTTP contracts: entitlements `{ organizationId, planId, limits, featureFlags }`, flag `{ flag, enabled, planId }`, default plan `starter`, `-1` = unlimited.
2. Keep shop `effective-config` shape; adapter remains fail-open.
3. Flags default so Hospital/Pharmacy/School keep working with dual-read **off or JSON-preferred**.
4. No new microservice — evolve existing `subscription-service`.

---

## Phase map

| Step | Name | Deliverable |
|------|------|-------------|
| **2.0** | Plan + dual-read school | Doc; entitlements optionally merge `plan_feature` / `plan_limit` with JSON |
| **2.1** | Shop module mapping | Map platform module codes → shop `ModuleCode` in `PlatformSubscriptionAdapter` |
| **2.2** | Dual-read shop (opt-in) | Enable bridge per env; org id = shopId/tenantId matching `tenant_subscription` |
| **2.3** | Catalog completeness | RETAIL/HOSPITAL/SCHOOL plans composed from catalog; projection filled |
| **2.4** | Prefer projection / platform | Flip prefer flags after parity smoke |
| **2.5** | Cutover | Stop treating shop `plan_definitions` as SoT; Super Admin only; deprecate duplicate editors |

---

## 2.0 — School dual-read (this slice)

**Property** (default keeps today’s behaviour):

```properties
# json = today (default). dual = merge projection into JSON response. projection = prefer plan_* when non-empty.
subscription.entitlements.read-mode=json
```

| Mode | Behaviour |
|------|-----------|
| `json` | Unchanged — read `limits_json` / `feature_flags_json` only |
| `dual` | Start from JSON; fill missing feature/limit keys from `plan_feature` / `plan_limit` |
| `projection` | If projection has any feature rows, use those flags/limits; else fall back to JSON |

Response may add diagnostic (non-breaking) fields:

- `entitlementSource`: `json` | `dual` | `projection`
- `modules`: enabled module codes from `plan_module` (helps shop adapter; UI may ignore)

`GET /feature-flags/{flag}` continues to call the same `entitlements()` path.

---

## 2.1 — Shop module mapping

Static map (config-overridable later) examples:

| Platform module | Shop ModuleCode(s) |
|-----------------|-------------------|
| `HOSPITAL_OPD` / `POLY_OPD` | `DOCTORS`, `PRESCRIPTION`, `PATIENT_RECORDS`, `PATIENT_QUEUE`, `FOLLOW_UP` |
| `HOSPITAL_BILLING` / `PATHLAB_BILLING` | `BILLING` |
| `HOSPITAL_LAB` / `POLY_LAB` / `PATHLAB_*` | `LAB` |
| `HOSPITAL_PHARMACY` / `POLY_PHARMACY` / `PHARMACY_*` / `MEDSHOP_*` | `PHARMACY`, `PRODUCTS`, `INVENTORY`, `BATCH`, `EXPIRY` |
| `HOSPITAL_IPD` | `PATIENT_RECORDS` (IPD-specific gates stay feature-flagged later) |

Unmapped platform codes are still unioned as-is (harmless if unused by `@RequiresModule`).

---

## 2.2 — Opt-in shop bridge

```properties
shop.platform.subscription.enabled=true
shop.platform.subscription.base-url=http://localhost:9090
# or from Docker shop-service: http://host.docker.internal:8182
shop.platform.subscription.organization-id-source=SHOP_ID
shop.platform.subscription.prefer-platform-access=false
```

Compose passes `SHOP_PLATFORM_SUBSCRIPTION_*` into `shop-service` (default still off).

Assign a vertical plan to org id = **shopId** in Super Admin, then smoke:

```powershell
.\scripts\platform-subscription-bridge-smoke.ps1 -ShopId POLY-DEMO-01 -PlanId poly-starter
```

## 2.3 — Catalog completeness

Flyway **V19** + seeder: `hospital-starter`, `hospital-pro`, `poly-starter`, `pharmacy-starter`, `pathlab-starter`, `retail-starter` with feature→module catalog links. School `starter` untouched; School FEATURE_* merges skip these vertical SKUs.

Mapper also covers `RETAIL_*` → shop `ModuleCode`.

---

## Explicit non-goals (Phase 2)

- Replacing School `FEATURE_*` string names  
- Moving subscription DB out of school stack in this phase  
- Super Admin impersonation (Identity Phase 3)  
- Hard-gating every retail request through subscription-service (adapter + cache only)

---

## Test plan

- [ ] School: `read-mode=json` — Admission / HCP-01 flags identical to before  
- [ ] School: `read-mode=dual` — same flags when projection synced; missing keys filled from projection  
- [ ] Shop: adapter off — effective-config unchanged  
- [ ] Shop: adapter on + mapped modules — `@RequiresModule` sees shop codes  
- [ ] Fail-open: subscription down → school UI fail-open; shop keeps local plan  

---

## Engineering checklist

1. Persist Phase 2 doc (this file).  
2. School dual-read (`read-mode`) — done.  
3. Shop `PlatformModuleCodeMapper` + adapter merge — done.  
4. Seed/parity for retail+hospital plans (V19 + seeder) — done.  
5. Env opt-in dual-read shop + `platform-subscription-bridge-smoke.ps1` — done (enable flag to run).  
6. Prefer-projection / cutover only after smoke.
