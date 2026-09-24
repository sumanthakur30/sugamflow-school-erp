# School ERP ↔ SugamFlow Platform Integration

School management **reuses** platform infrastructure from `D:/sugamflow` and keeps only school-domain microservices in `D:/school`.

## Shared (from D:/sugamflow)

| Component | Port | Role |
|---|---|---|
| `discovery-service` | 8761 | Eureka registry |
| `config-service` | 8888 | Spring Cloud Config Server (bootstrap props) |
| `gateway-service` | 9090 | Single API edge for shops + school |
| `notification-service` | 8087 | Actual SMS/Email/WhatsApp **delivery** |
| `auth-service` / `user-service` | 8085 / 8084 | Auth & tenants — **Phase 4 wired** (see [PHASE4_AUTH.md](PHASE4_AUTH.md)) |

## School-only (D:/school/services)

| Service | Port | Eureka name | Gateway path |
|---|---|---|---|
| `school-settings-service` | 8181 | school-settings-service | `/api/config/**` |
| `subscription-service` | 8182 | subscription-service | `/api/subscription/**` |
| `form-builder-service` | 8183 | form-builder-service | `/api/forms/**` |
| `workflow-service` | 8184 | workflow-service | `/api/workflows/**` |
| `rule-engine-service` | 8185 | rule-engine-service | `/api/rules/**` |
| `report-builder-service` | 8186 | report-builder-service | `/api/reports/**` |
| `school-notification-config-service` | 8187 | school-notification-config-service | `/api/school/notification-config/**` |
| `audit-service` | 8188 | audit-service | `/api/audit/**` |
| `admission-service` | 8189 | admission-service | `/api/admission/**` |
| `fee-service` | 8190 | fee-service | `/api/fee/**` |
| `student-service` | 8191 | student-service | `/api/student/**` |
| `attendance-service` | 8192 | attendance-service | `/api/attendance/**` |
| `exam-service` | 8193 | exam-service | `/api/exam/**` |
| `library-service` | 8194 | library-service | `/api/library/**` |
| `hostel-service` | 8195 | hostel-service | `/api/hostel/**` |
| `transport-service` | 8196 | transport-service | `/api/transport/**` |
| `payroll-service` | 8197 | payroll-service | `/api/payroll/**` |
| `staff-service` | 8198 | staff-service | `/api/staff/**` |
| `academic-structure-service` | 8199 | academic-structure-service | `/api/academic/**` |
| `website-service` | 8200 | website-service | `/api/website/**` (public: `/api/website/public/**`) |
| `cms-service` | 8201 | cms-service | `/api/cms/**` (public: `/api/cms/public/**`) |

**Naming note:** `school-settings-service` is Design Studio / module settings. It is **not** the Spring Cloud Config Server.

See [PHASE5_ADMISSION.md](PHASE5_ADMISSION.md), [PHASE5B_OFFER_LETTER.md](PHASE5B_OFFER_LETTER.md), [PHASE6_FEE.md](PHASE6_FEE.md), [PHASE7_STUDENT_ENROLLMENT.md](PHASE7_STUDENT_ENROLLMENT.md), [PHASE8_ATTENDANCE.md](PHASE8_ATTENDANCE.md), [PHASE9_EXAM.md](PHASE9_EXAM.md), [PHASE10_OPS_MODULES.md](PHASE10_OPS_MODULES.md), [PHASE11_CONFIG_AUDIT.md](PHASE11_CONFIG_AUDIT.md), [PHASE12_REPORT_DESIGNER.md](PHASE12_REPORT_DESIGNER.md), [PHASE13_PARENT_TEACHER_APPS.md](PHASE13_PARENT_TEACHER_APPS.md), [PHASE14_MULTI_BRANCH.md](PHASE14_MULTI_BRANCH.md), [PHASE15_DEVICE_ADAPTERS.md](PHASE15_DEVICE_ADAPTERS.md), [PHASE16_OFFLINE.md](PHASE16_OFFLINE.md), [PHASE17_SECURITY_PAGINATION.md](PHASE17_SECURITY_PAGINATION.md), [PHASE18_CONFIG_EDITORS.md](PHASE18_CONFIG_EDITORS.md), [PHASE19_FINANCE.md](PHASE19_FINANCE.md), [PHASE0_SCHOOL_WEBSITE_PLATFORM.md](PHASE0_SCHOOL_WEBSITE_PLATFORM.md), [PHASE1_SCHOOL_WEBSITE_CMS.md](PHASE1_SCHOOL_WEBSITE_CMS.md), [PHASE2_SCHOOL_WEBSITE_SSO_ADMISSION.md](PHASE2_SCHOOL_WEBSITE_SSO_ADMISSION.md), and [PHASE3_SCHOOL_WEBSITE_BUILDER_SEO.md](PHASE3_SCHOOL_WEBSITE_BUILDER_SEO.md).

## Start order

1. `discovery-service` (8761)
2. `auth-service` (8085) + `shop-service` (8080) — login
3. School domain services (8181–8188) — register with Eureka
4. `gateway-service` (9090)
5. `apps/school-ui` → `http://localhost:9090`

See [PHASE4_AUTH.md](PHASE4_AUTH.md) for login flow and demo credentials.

```powershell
# From D:/school after platform Eureka is up:
.\scripts\start-services.ps1
cd apps\school-ui
npm start
```

## Intentionally removed from D:/school

- Local `api-gateway` (use sugamflow `gateway-service`)
- Duplicate infra `config-service` name (renamed to `school-settings-service`)
- Duplicate delivery `notification-service` (renamed to `school-notification-config-service`)
