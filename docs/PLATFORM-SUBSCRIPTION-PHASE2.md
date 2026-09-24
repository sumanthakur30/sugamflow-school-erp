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

## 2.4 — Prefer projection / platform (soft cutover)

**Defaults stay safe** (`json`, bridge off, prefer flags false). After a **successful** bridge smoke:

### School

```properties
# Stage A — fill gaps from projection
subscription.entitlements.read-mode=dual
# Stage B — projection wins on conflicts (still dual merge)
subscription.entitlements.prefer-projection=true
# Stage C — projection is primary when plan_* rows exist
subscription.entitlements.read-mode=projection
```

Env: `SUBSCRIPTION_ENTITLEMENTS_READ_MODE`, `SUBSCRIPTION_ENTITLEMENTS_PREFER_PROJECTION`.

### Shop

```properties
shop.platform.subscription.enabled=true
shop.platform.subscription.base-url=http://host.docker.internal:8182
# After smoke: skip plan_definitions when platform returns modules
shop.platform.subscription.prefer-platform-modules=true
```

Env: `SHOP_PLATFORM_SUBSCRIPTION_PREFER_MODULES=true`.

When preferred, effective-config uses **registry + business-type + mapped platform modules** (not shop `plan_definitions` / `PlanEntitlements`). Feature flag `PLATFORM_MODULES_PREFERRED=true` appears on the config.

### Cutover checklist (2.4 → 2.5)

1. [ ] Smoke PASS: `tools/platform-subscription-bridge-smoke.ps1 -ShopId POLY-DEMO-01 -PlanId poly-starter`  
2. [ ] School: `read-mode=dual` then `prefer-projection=true` on a non-prod org; Admission flags unchanged  
3. [ ] Shop: enable bridge + assign vertical plan; confirm `PLATFORM_SUBSCRIPTION_LINKED`  
4. [ ] Shop: set `prefer-platform-modules=true` (or `cutover=true`); confirm `PLATFORM_MODULES_PREFERRED` / `PLATFORM_SUBSCRIPTION_CUTOVER`  
5. [ ] Super Admin is the only module-plan editor; shop list “billing cycle” is not entitlement SoT  
6. [x] (2.5) Bridged cutover skips `plan_definitions` DB read; shop UI clarifies billing vs Platform Subscription  

### Phase 2.5 — Hard cutover (bridged shops)

```properties
shop.platform.subscription.enabled=true
shop.platform.subscription.base-url=http://host.docker.internal:8182
shop.platform.subscription.cutover=true
# cutover implies prefer-platform-modules
```

Env: `SHOP_PLATFORM_SUBSCRIPTION_CUTOVER=true`.

Behaviour when platform returns modules:

- `EffectiveConfigService` **does not** call `plan_definitions` / `PlanEntitlements`
- Features include `PLATFORM_MODULES_PREFERRED` and `PLATFORM_SUBSCRIPTION_CUTOVER`
- Structured log: `Platform subscription cutover modules …`
- Non-bridged shops unchanged (flag off / empty snapshot → legacy path)

Shop list “Change plan” renamed to **billing cycle** — modules stay in Super Admin → Platform Subscription.

`plan_definitions` table and Flyway seeds remain for legacy shops; they are no longer SoT under cutover.

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
6. Prefer-projection / soft cutover flags — done (opt-in; enable after smoke).  
7. Hard cutover (2.5): `cutover` flag skips `plan_definitions` read; shop UI = billing cycle only — done.
