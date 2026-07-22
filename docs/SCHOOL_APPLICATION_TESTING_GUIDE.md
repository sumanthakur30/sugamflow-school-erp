# School application — complete testing guide (step by step)

End-to-end guide to **start**, **seed**, **automate**, and **manually test** SugamFlow School ERP.

| Item | Value |
|------|--------|
| School repo | `D:\school` |
| Platform repo | `D:\sugamFlow` |
| UI | http://localhost:4200 |
| API gateway | http://localhost:9090 |
| Eureka | http://localhost:8761 |
| MailHog (email catcher) | http://localhost:8025 |
| Demo org | `demo-school` |
| Login | username `admin` (stored as `admin_demo-school`) / password `password` |

Related: [DAILY_START.md](./DAILY_START.md) · [SMTP_LOCAL.md](./SMTP_LOCAL.md) · [PHASE24_STUDENT360_OPS.md](./PHASE24_STUDENT360_OPS.md)

---

## 1. Prerequisites

| Check | How |
|--------|-----|
| PostgreSQL on `:5432` | `postgres` / `postgres` (see [POSTGRES.md](./POSTGRES.md)) |
| Java 17+ | `java -version` |
| Maven | `mvn -v` |
| Node / npm | for `apps\school-ui` |
| Platform jars | under `D:\sugamFlow\*\target\*.jar` |
| Docker Desktop | optional — required for MailHog / full EMAIL SENT |

If platform jars are missing:

```powershell
cd D:\sugamFlow
mvn -q -DskipTests package -pl discovery-service,gateway-service,auth-service,shop-service,notification-service -am
```

---

## 2. Start the stack (order matters)

Open PowerShell as needed (admin if Windows locks elevated JARs).

### Step 2.1 — Platform (Eureka, auth, shop, gateway, notification, MailHog)

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"
```

If Docker is off:

```powershell
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow" -SkipMailHog
```

**Expect:** Eureka `:8761`, gateway `:9090`, notification `:8087`, MailHog SMTP `:1025` (if Docker on).

### Step 2.2 — Demo auth seed (first time / fresh DB / login fails)

```powershell
.\scripts\seed-school-demo-auth.ps1
```

### Step 2.3 — School domain services (`:8181`–`:8199`)

```powershell
.\scripts\start-services.ps1
```

After code changes / Flyway migrations:

```powershell
.\scripts\start-services.ps1 -Restart
```

### Step 2.4 — School UI

```powershell
cd D:\school\apps\school-ui
npm start
```

Open http://localhost:4200

### Step 2.5 — Quick health

```powershell
Invoke-WebRequest http://localhost:8761 -UseBasicParsing | Select-Object StatusCode
Invoke-WebRequest http://localhost:9090/actuator/health -UseBasicParsing | Select-Object StatusCode
.\scripts\verify-ops-readiness.ps1
```

---

## 3. Demo login credentials

| Field | Value |
|--------|--------|
| Organization / shop | `demo-school` |
| Username | `admin` |
| Password | `password` |
| Branch | `main` |
| Academic session | `2025-26` |

API smoke scripts use JWT from:

`POST http://localhost:9090/api/v1/auth/login`  
body: `{ "shopId":"demo-school", "username":"admin_demo-school", "password":"password" }`

Headers used by most verifies:

```text
Authorization: Bearer <token>
X-Tenant-Id: demo-school
X-Branch-Id: main
X-Academic-Session-Id: 2025-26
X-Shop-Id: demo-school
X-User-Id: admin_demo-school
X-Role-Code: SHOP_OWNER
```

---

## 4. Automated testing (recommended first)

Scripts create any missing demo data (admission, students, fees, books, beds, routes, etc.).

### Step 4.1 — Full school flow (all modules)

```powershell
cd D:\school
.\scripts\verify-complete-school-flow.ps1
```

**Expect:** `ALL COMPLETE SCHOOL FLOW CHECKS PASSED` (≈2–3 minutes).

This orchestrator auto-starts MailHog if `:1025` is down (Docker required).

### Step 4.2 — Phase 24 only (Student 360 / Import / Comms / deep ops / LMS)

```powershell
.\scripts\verify-phase24.ps1
```

### Step 4.3 — Email delivery specifically

```powershell
# MailHog must be up; restart notification if it started before MailHog
docker start school-mailhog
.\scripts\verify-smtp-email.ps1
.\scripts\verify-admission.ps1   # EMAIL -> SENT
.\scripts\verify-fee.ps1         # EMAIL -> SENT
```

Inspect messages: http://localhost:8025

### Step 4.4 — Module-by-module scripts

Run any one when debugging a single area:

| Area | Script |
|------|--------|
| Ops / probes | `verify-ops-readiness.ps1` |
| Auth / gateway | `verify-auth.ps1`, `verify-gateway.ps1` |
| Config editors | `verify-config-editors.ps1` |
| Campuses | `verify-branches.ps1` |
| Classes / timetable | `verify-academic.ps1` |
| Admission + offer PDF | `verify-admission.ps1` |
| Enrollment | `verify-student-enrollment.ps1` |
| Guardians | `verify-student-guardians.ps1` |
| Global directory | `verify-directory.ps1` |
| Promote / TC | `verify-lifecycle.ps1` |
| Fee + receipt | `verify-fee.ps1` |
| Finance / gateway | `verify-finance.ps1` |
| Attendance | `verify-attendance.ps1`, `verify-roster-attendance.ps1`, `verify-attendance-alerts.ps1` |
| Exam / gradebook / report cards | `verify-exam.ps1`, `verify-gradebook.ps1`, `verify-report-cards.ps1` |
| Library / hostel / transport / payroll | `verify-library.ps1`, `verify-hostel.ps1`, `verify-transport.ps1`, `verify-payroll.ps1` |
| Ops depth previews | `verify-ops-depth.ps1` |
| Devices / offline | `verify-device-adapters.ps1`, `verify-offline.ps1` |
| Parent / teacher portals | `verify-portals.ps1`, `verify-responsive-portals.ps1` |
| RBAC / tenant / security | `verify-persona-rbac.ps1`, `verify-tenant-isolation.ps1`, `verify-security-pagination.ps1` |
| Report designer / audit | `verify-report-designer.ps1`, `verify-audit.ps1` |
| Phase 24 | `verify-phase24.ps1` |

Optional external (need extra config):

```powershell
.\scripts\verify-complete-school-flow.ps1 -IncludeExternal
# adds: verify-smtp-email, verify-payment-gateway, verify-provision
```

---

## 5. Manual UI testing (step by step)

Log in at http://localhost:4200 with `demo-school` / `admin` / `password`.

Work through screens in this order. Mark each as Pass / Fail.

### 5.1 Platform shell

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Login | Lands on admin shell; nav visible |
| 2 | Switch branch if multi-branch | Branch selector works; data scopes |
| 3 | Design Studio | Theme loads / save works |
| 4 | Subscription | Plan + feature flags visible |
| 5 | Module Settings | Open `admission`, `lms`, `library` — defaults present |

### 5.2 Academic structure

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Academic | Create/list class, section, subject |
| 2 | Timetable | Periods + section slots save |
| 3 | Campuses | `main` / `north` listed |

### 5.3 Admission → student

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | **Admission** — submit application | Record appears IN_PROGRESS |
| 2 | Approve each workflow step | Ends APPROVED |
| 3 | Offer letter | PDF downloads |
| 4 | Check MailHog | Offer / approval email present (if SMTP up) |
| 5 | **Student Master** | Enrolled student ACTIVE with admission no |
| 6 | Guardians | Parent contacts listed / editable |
| 7 | **Student Directory** | Search finds student; CSV export works |
| 8 | Click **360** | Profile, fee/library clearance, tabs load |

### 5.4 Import Workbench (Phase 24)

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Open **Import Workbench** | Bootstrap enabled |
| 2 | Paste CSV → create job | Job MAPPED, ready ≥ 1 |
| 3 | Dry-run | Status VALIDATED |
| 4 | Commit | Status COMMITTED; students appear in directory |

Sample CSV:

```csv
fullName,admissionNo,age,mobile,email,classApplied
Test Student,ADM-UI-001,12,9800000001,test@demo.local,Grade 8-A
```

### 5.5 Fees & finance

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Fee Collection — submit + approve | APPROVED + receipt PDF |
| 2 | Finance — fee heads / demand / payment | Intent CAPTURED (simulate) |
| 3 | MailHog | FEE email SENT (if SMTP up) |

### 5.6 Attendance & exams

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Attendance (admin workflow) | Submit + approve |
| 2 | Teacher portal → Attendance roster | Mark ABSENT/LATE/PRESENT → submit |
| 3 | Parent portal → attendance | Sees marks |
| 4 | Exam / Gradebook (teacher) | Definitions + marks save |
| 5 | Report cards | PDF generate; parent can open mine |

### 5.7 Ops: library / hostel / transport / payroll

**Workflow inbox** (original Phase 10) still works under each module’s “Workflow” tab.

**Deep ledgers** (Phase 24):

| Module | UI path | Actions | Pass criteria |
|--------|---------|---------|----------------|
| Library | Admin → Library → **Circulation** | Add book → Issue → Return | Clearance no outstanding |
| Hostel | Admin → Hostel → **Beds** | Add bed → Allocate → Release | Bed VACANT after release |
| Transport | Admin → Transport → **Routes** | Add route → Assign → End | Assignment ENDED |
| Payroll | Admin → Payroll | Submit run + approve | APPROVED |

### 5.8 Comms Hub & LMS (Phase 24)

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | **Comms Hub** — publish announcement | Appears in list (QUEUED) |
| 2 | **LMS** | Shows `NATIVE_HOMEWORK` mode; link to Module Settings |

### 5.9 Lifecycle

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Academic Lifecycle — promote | Class changes; event recorded |
| 2 | Issue TC | Blocked if fee/library dues; succeeds when clear |

### 5.10 Config & audit

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Form / Workflow / Rule builders | Save + evaluate |
| 2 | Report Designer | Preview / render PDF |
| 3 | Audit | Save localization → list → approve → rollback |

### 5.11 Parent & Teacher portals

| # | Route | Pass criteria |
|---|--------|----------------|
| 1 | `/parent` | Nav + widgets; attendance / exams / report cards |
| 2 | `/teacher` | Attendance, gradebook, report cards |
| 3 | Phone / tablet widths | Layout stacks; no horizontal clip (see `verify-responsive-portals.ps1`) |

### 5.12 Devices & offline

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Device Adapters | Register device; simulate punch |
| 2 | Offline Mode | Sync batch COMPLETED |

---

## 6. Recommended test day checklist

Use this as a single-day pass/fail sheet.

```text
[ ] Platform + school services up (ops-readiness)
[ ] Login works
[ ] verify-complete-school-flow.ps1 PASS
[ ] UI: Admission → enroll → Directory → 360
[ ] UI: Import commit
[ ] UI: Fee approve + PDF + MailHog email
[ ] UI: Library circulation issue/return
[ ] UI: Hostel allocate/release
[ ] UI: Transport assign/end
[ ] UI: Comms announcement
[ ] UI: Teacher roster + gradebook
[ ] UI: Parent report cards
[ ] UI: LMS settings page
```

---

## 7. What the automated flow already inserts

You do **not** need to hand-seed most data. Scripts create:

- Admission applications + approvals + offer PDF
- Students + guardians + directory rows
- Academic class/section/subject/timetable
- Fee collections + finance intents
- Attendance / exam / gradebook / report-card samples
- Library / hostel / transport / payroll workflow records
- Phase 24: import students, books, beds, routes, announcements

Directory counts grow on each full run (expected).

---

## 8. Ports reference

| Port | Service |
|------|---------|
| 5432 | PostgreSQL |
| 8761 | Eureka |
| 8085 | auth-service |
| 8087 | notification-service (delivery) |
| 9090 | gateway |
| 1025 / 8025 | MailHog SMTP / UI |
| 8181 | school-settings |
| 8182 | subscription |
| 8183–8188 | form / workflow / rule / report / notif-config / audit |
| 8189 | admission |
| 8190 | fee |
| 8191 | student |
| 8192 | attendance |
| 8193 | exam |
| 8194–8197 | library / hostel / transport / payroll |
| 8198 | staff |
| 8199 | academic-structure |
| 4200 | school-ui |

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| Login `Unknown Error` / `:9090` | Re-run `start-platform.ps1` |
| School APIs 503 | Wait Eureka, then `start-services.ps1` |
| EMAIL → FAILED | Start MailHog; **restart** notification-service; see [SMTP_LOCAL.md](./SMTP_LOCAL.md) |
| JAR Access Denied on Windows | Admin shell: `start-services.ps1 -Restart` |
| Feature flag false | Restart `subscription-service` (seeder) |
| Import/Comms UI hidden | Needs `FEATURE_STUDENT_MASTER` (nav) + flags seeded |
| Deep library/hostel APIs 404 | Service not restarted after V4 migrations |
| Notification health 503 | Often OK for API; MailHog health can mark DOWN |

Restart notification after MailHog (PowerShell sketch):

```powershell
# find PID on 8087 via netstat, Stop-Process, then:
java -jar D:\sugamFlow\notification-service\target\notification-service-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=local --server.port=8087 `
  --eureka.client.service-url.defaultZone=http://localhost:8761/eureka `
  --spring.mail.host=localhost --spring.mail.port=1025
```

Or re-run `start-platform.ps1` after stopping the process on `:8087`.

---

## 10. Document map

| Doc | Purpose |
|-----|---------|
| This file | Complete testing steps |
| [DAILY_START.md](./DAILY_START.md) | Bring-up order |
| [SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md](./SCHOOL_REGISTRATION_LOGIN_RUNBOOK.md) | Register new school |
| [PHASE24_STUDENT360_OPS.md](./PHASE24_STUDENT360_OPS.md) | 360 / import / comms / deep ops / LMS |
| Phase 5–23 docs | Feature-specific API details |

---

## 11. One-command happy path

```powershell
cd D:\school
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"
.\scripts\seed-school-demo-auth.ps1   # if needed
.\scripts\start-services.ps1
# other terminal:
cd D:\school\apps\school-ui; npm start
# then:
cd D:\school
.\scripts\verify-complete-school-flow.ps1
```

Open UI http://localhost:4200 and walk **Section 5** for manual sign-off.
