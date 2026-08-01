# Platform Identity & Subscription — Implementation Plan

**Branch:** `august01`  
**Rule:** `.cursor/rules/platform-identity-subscription.mdc`  
**Goal:** One Identity + Access layer for all verticals; no School-only user/subscription fork. Nothing breaks on existing shop Staff or school owner login.

---

## Current state (baseline)

| Piece | Today | Gap |
|-------|--------|-----|
| Login / invite / reset | `auth-service` (`/api/v1/auth/invite*`, accept, password reset) | Works; keep contracts |
| Staff invite UI | `shop-management-ui` `/staff` — Name/Username/Email/Phone + **job preset** (UI-only) | Presets are TS constants, not platform templates |
| Auth role | Often `SHOP_EMPLOYEE` + permissions applied after invite | School roles not in same template catalog |
| Permissions | `security-common` `PermissionCatalog` + staff permission APIs | No `business_type`-scoped **role templates** in DB |
| School users | Shop/org users + school domain roles elsewhere | Must not add parallel identity store |
| Subscription | School `subscription-service` + shop platform adapter (Phase 6) | Phase 2 unify — **not** Phase 1 |

Invite sequence today (must keep working):

1. UI → account/shop APIs → `auth-service` createInvite (internal key)  
2. User sets password via acceptInvite  
3. UI applies job preset permissions (`applyStaffJobAfterInvite`)

---

## Phase map

| Phase | Scope | Break-glass rule |
|-------|--------|------------------|
| **1 — Identity** | Role templates by `business_type`, Invite UX, APIs, seed Retail/Hospital/School templates | Additive only; fallback to current presets |
| **2 — Subscription** | Single catalog; migrate school-local plans → platform snapshots | Dual-read then cutover |
| **3 — Super Admin ops** | Cross-tenant reset/unlock/impersonate + audit + dashboards | New routes; old Staff UI stays |

---

## Phase 1 — Identity (detail)

### 1.1 Principles (non-breaking)

- **Additive schema** — new tables; no drop/rename of existing user/role columns.  
- **Additive APIs** — new endpoints; existing invite/login unchanged.  
- **UI progressive** — new Invite wizard can call templates API; if API missing/empty → current `staff-job-presets.ts` fallback.  
- **Same JWT / headers** — `SHOP OWNER`, `SHOP_EMPLOYEE`, school tokens, `X-Tenant-Id` unchanged.  
- **No School auth DB** — school continues to use platform auth/user.

### 1.2 Tables (proposed — `userdb` / user-service Flyway)

```text
business_role_template
  id, business_type_code (RETAIL|HOSPITAL|SCHOOL|…),
  code (TEACHER|RECEPTION|…), name, description,
  auth_role (e.g. SHOP_EMPLOYEE), active, created_at, updated_at
  UNIQUE(business_type_code, code)

business_role_template_permission
  template_id, permission_code
  PK(template_id, permission_code)

-- optional later:
business_role_template_notify
  template_id, channel (EMAIL|SMS|WHATSAPP), enabled
```

Seed Phase 1:

- **HOSPITAL / CLINIC / POLYCLINIC:** map existing `StaffJobPreset` (Doctor, Reception, Pharmacist, Lab, Warehouse, Purchase, Accountant, General).  
- **RETAIL / WHOLESALE:** General + Warehouse + Purchase + Accountant.  
- **SCHOOL:** Teacher, Reception, Accountant, Principal (permissions subset as codes that school UI already understands — or placeholder codes documented for school Phase 1.1).

### 1.3 APIs (user-service, gateway-routed)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/users/role-templates?businessType=` | List active templates (+ permissions) |
| GET | `/api/v1/users/role-templates/{code}?businessType=` | One template |
| POST | `/api/v1/users/role-templates` | Super Admin / owner create (Phase 1.2; optional) |
| POST | `/api/v1/users/invites` (facade) **or** keep existing invite | Prefer **keep** existing shop invite path; pass `templateCode` as optional field |

**Invite contract extension (optional, backward compatible):**

```json
{
  "name", "username", "email", "phone", "role",
  "templateCode": "RECEPTION",
  "businessType": "HOSPITAL",
  "branchId": null,
  "notify": { "email": true, "sms": false, "whatsapp": false }
}
```

If `templateCode` absent → behavior identical to today.

### 1.4 UI screens

| Screen | Change |
|--------|--------|
| Shop **Staff** (`/staff`) | Replace flat form with **Invite User** wizard: Role template → employee type → personal → access (branch) → modules (from template, editable) → notify → Invite |
| Admin login invite | Leave as-is initially (Super Admin path Phase 3) |
| School staff (if any) | Later: same wizard filtered `businessType=SCHOOL` |

### 1.5 Migration / rollout

1. Deploy user-service with empty-or-seeded template tables + GET APIs.  
2. Deploy UI with wizard + **fallback presets**.  
3. Wire invite to apply permissions from template (server-side preferred) while still calling existing auth invite.  
4. Only then remove TS hardcoded preset maps (or keep as offline fallback).

### 1.6 Explicit non-goals (Phase 1)

- Parent/Student mobile apps  
- Super Admin impersonation  
- Subscription plan migration (Phase 2)  
- Replacing school `FEATURE_*` enforcement  

### 1.7 Test plan (nothing breaks)

- [ ] Existing shop owner login  
- [ ] Existing staff invite without `templateCode`  
- [ ] Accept invite + set password  
- [ ] Hospital job presets still grant same permissions as before  
- [ ] School owner login (`*_HCP-01` / demo-school)  
- [ ] Gateway routes unchanged for `/api/v1/auth/**` and staff account APIs  

---

## Phase 2 — Subscription (preview only)

- Single catalog in platform subscription service.  
- Super Admin → Platform Subscription remains configure plane.  
- School-local `subscription_plan` / `tenant_subscription` → dual-read via adapter → cutover.  
- Enforce only via `effective-config` + school feature-flags.

---

## Phase 1 delivery checklist (engineering)

1. Persist rules (done).  
2. user-service Flyway + seed + GET templates.  
3. Optional invite DTO field `templateCode`.  
4. Staff UI wizard (fallback).  
5. Server-side apply template permissions on invite.  
6. E2E smoke on retail + polyclinic shop.
