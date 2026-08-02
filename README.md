# SugamFlow School ERP

Configuration-driven School Management platform.

**Philosophy:** Configuration > Customization > Development

## Structure

```text
apps/school-ui                 Separate Angular school management UI
services/                      School-domain Spring Boot services (Eureka clients)
  school-settings-service      Design Studio / module settings (NOT Spring Cloud Config)
  subscription-service
  form-builder-service
  workflow-service
  rule-engine-service
  report-builder-service
  school-notification-config-service   Templates only — delivery via sugamflow
  admission-service                    Runtime applications (config-driven)
  fee-service                          Fee collection runtime (config-driven)
  student-service                      Student master + Global Student Directory + lifecycle
  staff-service                        Staff / employee master + Global Staff Directory
  attendance-service                   Attendance marking runtime (config-driven)
  exam-service                         Exam / gradebook runtime (config-driven)
  library-service                      Library / book issue runtime (config-driven)
  hostel-service                       Hostel allocation runtime (config-driven)
  transport-service                    Transport route runtime (config-driven)
  payroll-service                      Payroll run runtime (config-driven)
  website-service                      School Website Platform (domain → tenant resolve)
  audit-service
shared/contracts
docs/
```

**Platform reuse (`D:/sugamflow`):** `discovery-service`, `config-service` (Spring Cloud Config), `gateway-service` (:9090), `notification-service` (delivery), `auth-service`, `shop-service`.

## Principles

See [docs/IMPLEMENTATION_RULES.md](docs/IMPLEMENTATION_RULES.md).

## Quick start

```powershell
.\scripts\start-platform.ps1
.\scripts\seed-school-demo-auth.ps1
.\scripts\start-services.ps1
cd apps\school-ui
npm start
```

Open `http://localhost:4200` — login with `demo-school` / `admin` / `password`.

**Daily run:** see [docs/DAILY_START.md](docs/DAILY_START.md) (order, ports, health checks, common failures).  
**Complete testing (automated + UI):** see [docs/SCHOOL_APPLICATION_TESTING_GUIDE.md](docs/SCHOOL_APPLICATION_TESTING_GUIDE.md).  
**School register + login services:** see [docs/SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md](docs/SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md).

## Docs

- [School application testing guide (step by step)](docs/SCHOOL_APPLICATION_TESTING_GUIDE.md)
- [Daily start](docs/DAILY_START.md)
- [School registration & login runbook](docs/SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md)
- [Postgres](docs/POSTGRES.md)
- [Platform Integration](docs/PLATFORM_INTEGRATION.md)
- [Phase 3 Runtime](docs/PHASE3_RUNTIME.md)
- [Phase 4 Auth](docs/PHASE4_AUTH.md)
- [Phase 5 Admission](docs/PHASE5_ADMISSION.md)
- [Phase 5b Offer letter + notifications](docs/PHASE5B_OFFER_LETTER.md)
- [Phase 6 Fee](docs/PHASE6_FEE.md)
- [Phase 6b Fee receipt](docs/PHASE6B_FEE_RECEIPT.md)
- [Phase 7 Student enrollment](docs/PHASE7_STUDENT_ENROLLMENT.md)
- [Phase 7b Student guardians](docs/PHASE7B_STUDENT_GUARDIANS.md)
- [Phase 8 Attendance](docs/PHASE8_ATTENDANCE.md)
- [Phase 9 Exam / Gradebook](docs/PHASE9_EXAM.md)
- [Phase 10 Ops modules: Library / Hostel / Transport / Payroll](docs/PHASE10_OPS_MODULES.md)
- [Phase 11 Config audit + rollback](docs/PHASE11_CONFIG_AUDIT.md)
- [Phase 12 Report / Certificate designer](docs/PHASE12_REPORT_DESIGNER.md)
- [Phase 13 Parent / Teacher apps](docs/PHASE13_PARENT_TEACHER_APPS.md)
- [Phase 14 Multi-branch UX](docs/PHASE14_MULTI_BRANCH.md)
- [Phase 15 AI attendance / device adapters](docs/PHASE15_DEVICE_ADAPTERS.md)
- [Phase 16 Offline mode](docs/PHASE16_OFFLINE.md)
- [Phase 17 Security + pagination](docs/PHASE17_SECURITY_PAGINATION.md)
- [Phase 18 Config editors](docs/PHASE18_CONFIG_EDITORS.md)
- [Phase 19 Finance depth](docs/PHASE19_FINANCE.md)
- [Phase 21 Ops depth](docs/PHASE21_OPS_DEPTH.md)
- [Phase 22 Global Directory](docs/PHASE22_GLOBAL_DIRECTORY.md)
- [Phase 23 Operational readiness](docs/PHASE23_OPS_READINESS.md)
- [Phase 24 Student 360 / Comms / LMS / deep ops / Import](docs/PHASE24_STUDENT360_OPS.md)
- [LMS choice](docs/LMS_CHOICE.md)
- [Local SMTP / EMAIL SENT](docs/SMTP_LOCAL.md)
- [Design Studio live theme](docs/DESIGN_STUDIO_LIVE_THEME.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Implementation Rules](docs/IMPLEMENTATION_RULES.md)
- [Feature Gap Analysis](docs/FEATURE_GAP_ANALYSIS.md)
