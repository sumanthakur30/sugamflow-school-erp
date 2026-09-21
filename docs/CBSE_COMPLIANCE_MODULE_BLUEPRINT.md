# CBSE Compliance & Regulatory Management — Module Blueprint

**Status:** Design (no production code changed)  
**Module:** CBSE Compliance & Regulatory Management  
**Service:** `compliance-service` (proposed port **8202**)  
**API prefix:** `/api/compliance/**`  
**Feature flag:** `FEATURE_CBSE_COMPLIANCE`  
**DB:** `school_compliance_db`  

Interactive canvas: open `cbse-compliance-module-blueprint.canvas.tsx` beside chat in Cursor.

---

## 1. Goal

Make SugamFlow the single source of truth for board-mandated data so schools stop building Excel packs by hand. Validate once, approve, export CBSE-compatible files, auto-publish mandatory disclosure to the school website, and stay ready for future official APIs via adapters.

**Non-negotiable:** do not break existing microservices or master schemas. Student/staff remain form-driven JSON; compliance **projects** them through metadata.

---

## 2. Gap analysis (market vs SugamFlow)

| Capability | Typical school ERPs | SugamFlow today | Target |
|---|---|---|---|
| Student → board file | Partial Excel export | Form JSON masters | Mapped projection + export |
| Teacher CBSE fields | Strong | Staff form JSON | Field registry + gaps |
| Infrastructure | Medium | Missing | Compliance-owned domain |
| Document expiry / NOCs | Medium | Student vault only | Compliance document vault |
| Mandatory disclosure site | Medium (often dual-entry) | Generic CMS pages | Auto-publish package |
| Config board templates | Weak–medium | None | Metadata + Superadmin push |
| Principal workflow | Partial | `workflow-service` exists | Reuse workflows |
| AI pre-submit checks | Rare | None | Rule + AI hybrid |
| Official board API | None public | N/A | Adapter SPI |

**Opportunity vs Entab / Fedena / CampusCare / Teachmint / LEAD / Vidyalaya:** combine gov-grade validation + SaaS config push + existing SugamFlow CMS (ERP ↔ website without dual maintenance).

---

## 3. Architecture

```
school-ui (Compliance nav)
    → gateway :9090  Path=/api/compliance/**
        → compliance-service :8202
            → reads: student, staff, academic, settings, fee (internal)
            → engines: forms, workflows, rules, reports, notification-config
            → writes: CMS disclosure pages, audit events
            → adapters: FILE (now) | SFTP | REST/OAuth | XML (later)
```

### Patterns to follow (existing)

- `school-common`: `ApiResponse`, `TenantFilter` / `TenantContext`, gateway-verified headers  
- Own Flyway DB + dedicated history table  
- Eureka `compliance-service`, gateway **no StripPrefix**  
- Entitlement via `FEATURE_CBSE_COMPLIANCE` (subscription-service catalog)  
- Port **8202** (avoid 8195/8196 clash with support/hostel/transport)

### What compliance owns vs reuses

| Owns | Reuses unchanged |
|---|---|
| Board templates, field maps, rules packs | student-service, staff-service |
| School compliance profile | academic-structure-service |
| Infrastructure assets | form-builder, workflow, rule-engine |
| Compliance documents + expiry | report-builder, notification-config |
| Campaigns, runs, findings, artifacts | cms-service / website-service |
| Disclosure bindings + adapters | audit-service, subscription-service |

---

## 4. Database (core tables)

- `board_definition` — CBSE / ICSE / State  
- `compliance_template` — versioned packs (`CBSE-2026.1`)  
- `compliance_field_map` — source path → export column/node  
- `validation_rule` — declarative validators  
- `school_compliance_profile` — affiliation#, school code, UDISE+, principal, bank, trust  
- `infrastructure_asset` — classrooms, labs, toilets, CCTV, safety, buses, hostel  
- `compliance_document` — NOCs/certificates, expiry, versions  
- `submission_campaign` / `submission_run` — cycle + locked snapshot  
- `validation_finding` — severity + suggestion  
- `approval_step` — principal / management trail  
- `export_artifact` — Excel/CSV/JSON/XML/PDF  
- `disclosure_binding` — section → CMS slug  
- `adapter_job` — future upload jobs  
- `compliance_audit_log` — field + action audit  

**PII:** Aadhaar/PAN optional by policy; prefer HMAC hash + last-4 for uniqueness.

---

## 5. API surface (v1)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/compliance/dashboard` | Score, pending, due, errors |
| GET/PUT | `/api/compliance/profile` | Affiliation profile |
| GET | `/api/compliance/campaigns` | Campaigns by session |
| POST | `/api/compliance/campaigns/{id}/validate` | Master validation |
| GET | `/api/compliance/findings` | Filter findings |
| POST | `/api/compliance/campaigns/{id}/lock` | Lock after approvals |
| POST | `/api/compliance/campaigns/{id}/export` | Generate artifacts |
| POST | `/api/compliance/campaigns/{id}/submit` | Submit / queue adapter |
| GET/POST | `/api/compliance/documents` | Vault |
| POST | `/api/compliance/import` | Bulk Excel |
| POST | `/api/compliance/disclosure/publish` | Push CMS |
| GET | `/api/compliance/public/disclosure/{org}` | Optional public JSON |
| CRUD | `/api/compliance/platform/templates` | Superadmin packs |

---

## 6. Validation layers

1. **Schema** — required / type / enum (field registry)  
2. **Format** — regex / checksum (mobile, email, optional Aadhaar)  
3. **Cross-field** — age vs class, DOJ ≤ today (`rule-engine-service`)  
4. **Uniqueness** — admission/employee numbers, hashed IDs  
5. **Completeness** — coverage % (photo, parent contact)  
6. **AI (async WARN)** — fuzzy duplicates, photo quality, outliers  

Blockers are deterministic. AI never blocks solely on model timeout.

---

## 7. Workflow

`Prepare → Validate → Principal → Management (optional) → Lock → Generate → Submit → Archive`

- Implemented as default definition in `workflow-service`  
- Scheduler: T-14 / T-7 / T-1 reminders via notification-config → SugamFlow notification-service  

---

## 8. UI (school-ui)

Sidebar **Compliance** (feature-gated):

1. Compliance Home (score, due dates, principal actions)  
2. School Profile  
3. Data Readiness (student/teacher/staff gaps)  
4. Infrastructure  
5. Documents Vault  
6. Campaign Workspace  
7. Import Center  
8. Disclosure Preview  
9. Platform Templates (SugamFlow Superadmin only)

---

## 9. Website mandatory disclosure

Build `DisclosurePackage` from profile + staff projection + fee snapshot + documents → upsert CMS page slug `mandatory-public-disclosure` (+ attachments). Website sitemap already consumes CMS public pages — no second public stack.

---

## 10. Board adapter SPI

```text
BoardSubmissionAdapter
  boardCode(): CBSE | ICSE | STATE_*
  supports(FILE | SFTP | REST | XML)
  submit(ctx, artifacts) → AdapterResult
```

**Today:** FILE download for manual portal upload.  
**Later:** swap adapter only when CBSE/state publishes APIs.

---

## 11. Phased delivery (zero impact)

### P0 Foundation — **STARTED in repo**

Delivered in this sequence:

| Item | Location |
|---|---|
| `compliance-service` skeleton | `services/compliance-service` (port **8202**) |
| Flyway V1 profile + campaigns | `V1__compliance_foundation.sql` |
| APIs | `GET /api/compliance/dashboard`, `GET/PUT /api/compliance/profile` |
| Feature flags | `subscription-service` `V22__cbse_compliance_feature_flags.sql` |
| Gateway route | `routes[57]` + school path allowlist |
| Compose | `compliance-service` in `docker-compose.school.ec2-rds.yml` |
| school-ui | Compliance nav + dashboard + profile (feature-gated) |

**Next sequence:** clean demo masters / HCP-01 pilot / EC2 deploy (Import Center delivered in P7).

| Phase | Weeks | Scope |
|---|---|---|
| P0 Foundation | 2–3 | Service, profile, flag, gateway, dashboard shell |
| P1 Validation | 3–4 | Projections, findings UI, rule packs |
| P2 Docs + Infra | 2–3 | Vault, expiry alerts, infrastructure |
| P3 Export + Workflow | 3–4 | Generators, approvals, lock, archive |
| P4 Disclosure | 2 | CMS auto-publish |
| P5 AI + Adapters | 2–3 | AI WARN jobs, SFTP/REST stubs |
| P6 Multi-board | done | ICSE / State packs via config + platform templates |
| P7 Import Center | done | Bulk CSV/XLSX gap-fill into student/staff answers |

**P7 delivered:**

| Item | Location |
|---|---|
| Flyway V8 import jobs | `V8__compliance_import_center.sql` |
| Parse + match + commit | `ComplianceImportService` (POI + CSV) |
| APIs | `GET/POST /api/compliance/import/**` |
| school-ui | Import Center page + nav |

**P6 delivered:**

| Item | Location |
|---|---|
| Flyway V7 packs + `compliance_template` | `V7__multi_board_packs.sql` |
| ICSE / STATE field maps & rules | seeded by board_code |
| Profile `active_pack_key` + campaign `pack_key` | profile/campaign entities |
| Platform CRUD | `/api/compliance/platform/templates`, field-maps, rules |
| Board-aware validate/readiness/export | profile board default (not hardcoded CBSE) |
| school-ui | board/pack selector + Platform Templates page |

**Rollback:** disable feature flag + stop container. Masters untouched.

**Pilot:** `demo-school` → `HCP-01` → GA.

---

## 12. Superadmin capabilities

- CRUD board templates & versions  
- Enable/disable fields and validation rules without school redeploys  
- Push pack `CBSE-YYYY.N` to all entitled tenants  
- Multi-board framework (same engine, different metadata)

---

## 13. Out of scope for P0

- Claiming live CBSE government API integration (none public as of design date)  
- Rewriting student/staff physical schemas  
- Replacing CMS with a separate disclosure CMS  

---

*Aligned with current SugamFlow School layout: form-driven student/staff, CMS/website, engines, gateway `/api/*`, feature flags.*
