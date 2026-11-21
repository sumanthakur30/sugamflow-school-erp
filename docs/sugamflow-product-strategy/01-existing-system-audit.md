# 01 — Existing system audit

Research date: 21 November 2026. Scope: `D:\school` on branch `dev` at `9fb67cb`, plus the shared platform services the school UI calls (auth, gateway, user, shop). Production data was not copied into this document. No schema or runtime was changed.

## What SugamFlow is today

SugamFlow School ERP is a multi-tenant web application for Indian schools, built as an Angular app (`apps/school-ui`) in front of about two dozen Spring Boot services. Login is the shared platform login: shop id, username, and password, through the gateway. A school is a tenant. A campus is a branch. An academic year is a session. Plans turn modules on and off with `FEATURE_*` flags. School settings, class lists, and staff roles are separate from the plan.

The product already covers the daily school loop better than a prototype: admission and enquiries, student records, class and section structure, class-wise standard fees, fee collection and receipts, attendance including a biometric device path, exams and report cards, staff and payroll screens, parent and teacher web areas, library, hostel, transport, a website CMS, and a CBSE-oriented compliance area. It is not yet a complete accounting system, a native mobile app, or an AI product.

## Stack (verified)

| Layer | What the code uses |
|---|---|
| School UI | Angular standalone components, route-level feature guards, one SPA for admin, parent, and teacher |
| API edge | Shared gateway. Local UI proxies `/api` to port 9090 |
| Identity | `auth-service` login and JWT. School UI stores the session in the browser |
| Tenancy | Organization, branch, and academic session on requests |
| Domain services | Spring Boot, Java 17, Flyway, PostgreSQL |
| Entitlements | `subscription-service` feature catalog. The UI hides a menu item when its flag is off |
| Discovery | Eureka. School services register and the gateway routes to them |
| Public site | `website-service` and `cms-service`, separate from the ERP login |

School services with their own controllers include settings, subscription, admission, student, academic structure, fee, attendance, exam, staff, payroll, library, hostel, transport, notification config, workflow, rules, forms, report builder, audit, compliance, support, website, and CMS.

## How a school is modeled

Four layers already exist and should stay separate:

1. **Plan.** What the subscription allows (`FEATURE_FEE`, `FEATURE_STUDENT_MASTER`, and the rest of the catalog in subscription migration `V3`).
2. **School settings.** What this school turned on, including institution size presets (small, large, coaching, college).
3. **Master data.** Classes, sections, fee heads, and sessions for one campus and one year.
4. **Role.** What this person may open. A shop owner bypasses some permission checks. Everyone else is filtered.

Classes are rows, not a hardcoded list in the fee screen. A standard ladder (Nursery, LKG, UKG, Class 1–12) can be suggested and saved. The school’s own name wins when it matches the same step, so “Grade 7” is not duplicated as “Class 7”. Sections belong to a class.

Student identity for the rich admission form is stored as JSON answers on the student record, not as a new table for every custom field. Documents attach after the student row exists.

## Fee and money (verified)

Fee heads and fee structures are versioned JSON definitions. Saving a structure creates a new active version and marks the previous one superseded. A demand stores the line amounts at the time it is generated. Changing the standard fee later does not rewrite those rows. That is the right financial rule and it is already implemented for standard fees.

The standard fee screen sets a frequency per category (one-time, monthly, quarterly, half-yearly, annual) and an amount per class. A newly selected class on the admission form can pick the matching structure. Issued receipts stay as they were.

What this is not: a general ledger. Income and expense controllers exist. A search of school service Java and SQL found no journal, double-entry, or general-ledger type. Fee collection and school accounting are different products. Only the first is substantially built.

Student discounts on `dev` and `main` use `StudentDiscountRules` for reason, percent, dates, and a cap against the eligible fee or outstanding balance. `dest` and `model` have an older inline check in the adjustment service and do not contain that class. Billing still caps a concession against the fee on those branches. The standard fee feature does not depend on `StudentDiscountRules`.

## Academic and daily operations (verified)

| Area | Evidence | Honest status |
|---|---|---|
| Attendance | Roster controller, staff attendance, biometric admin, ZKTeco iclock controller, offline route | Working desk plus device adapter. Face recognition is a feature flag, not a proven production engine |
| Exams | Exam, gradebook, date sheet, admit card, report card, homework controllers | Marks, cards, and native homework exist. This is not a full LMS |
| Timetable and calendar | Timetable controller, school calendar route | Present |
| Staff and payroll | Staff directory, invite, payroll and payroll-ops controllers | Present. Depth of statutory payroll was not re-tested in this pass |
| Library, hostel, transport | Circulation, beds, routes, and a GPS controller | Screens and APIs exist. Live GPS tracking of buses was not verified on a road |
| Parent and teacher | `/parent` and `/teacher` routes for attendance, fees, exams, homework, gradebook | Web portals inside the same Angular app. No separate iOS or Android project was found in this repo |
| Messages | Communications hub and notification config | In-app and configurable channels. WhatsApp delivery quality is an operations question, not just a flag |
| Website | CMS admin, public site, optional AI draft for editors | A school website module, not the ERP |
| Compliance | CBSE compliance routes, board disclosure, UDISE export route, privacy requests | Useful desks. They do not by themselves make the product legally compliant |
| Ask the school | `AskSchoolComponent` | Keyword routing to fee-defaulter and attendance APIs. It tells the user it only reads records. It is not an LLM |
| AI settings | `AiConfigComponent` saves boolean flags to `/api/config/ui/ai` | A switch panel |
| Compliance “AI scan” | `AiWarnScanService` sets provider `heuristic`, and can add an external tip | A rules scan with an optional model call |
| CMS draft | `CmsAiAssistService` | Local template text, optional OpenAI-compatible chat, always for an editor to revise |

An older gap note in `docs/FEATURE_GAP_ANALYSIS.md` describes AI, workflow, and report design as differentiators. Treat that file as an aspiration. This audit follows the controllers and screens.

## Security and tenancy (verified at design level)

- Unauthenticated users are sent to login. Feature guards block a direct URL when the plan flag is off.
- APIs are expected to scope rows by organization and branch. Isolation is a property of each query, not of a separate database per school.
- Student privacy has its own controller and admin route. Deleting a person is intentionally not the same as deleting academic history.
- Secrets, production `.env` files, and live student rows were not copied here.
- The school host is a single EC2 with a tight memory budget. That is an operational constraint on how many JVMs and how much AI can run beside the product.

## What is weak or duplicated

- **Four long-lived branches (`dev`, `dest`, `model`, `main`) have diverged.** The same fee change had to be cherry-picked. Discount rules, the fee quote panel, and some image tags are not the same on every branch. A school can lose behavior depending on which branch is deployed.
- **Menu breadth is ahead of daily clarity.** The simple nav correctly shows Home, Students, Fees, Attendance, Exams, Staff, Messages, Reports, and Settings. Many other screens exist under More or Settings. Some are real, some are thin.
- **Feature flags can hide an unfinished module or advertise a finished one.** A flag is not proof of depth. `FEATURE_AI`, `FEATURE_ACCOUNTING`, `FEATURE_INVENTORY`, `FEATURE_GPS`, and `FEATURE_FACE_RECOGNITION` are catalog entries.
- **Student custom fields in JSON** are flexible and easy to get wrong. Reports and board exports must know which answers are official.
- **No double-entry core.** Adding a balance sheet on top of fee receipts would produce numbers an auditor cannot trace.
- **Ask is a demo of the right idea with the wrong engine.** The questions are good. The matcher is a few English keywords.
- **Ops modules are wide and uneven.** Library, hostel, transport, and payroll should be judged one school workflow at a time, not by the presence of a page.

## What to preserve

- Shared login and JWT. Do not build a second user store for schools.
- Plan, school settings, master data, and roles as four layers.
- Versioned fee definitions and immutable issued demands.
- Class catalog that respects names the school already uses.
- Gateway plus feature guards.
- Parent and teacher routes in the same app until a real mobile client is justified.
- Native homework instead of pretending to be Moodle (`docs/LMS_CHOICE.md`).
- Production visual language already used on the school shell. Do not restyle the product as a new dashboard theme while the workflows are unfinished.

## What to redesign later, not now

- Release engineering, so one branch is the product.
- The Ask screen, after the underlying queries are permission-safe and tested.
- Accounting, as a later ledger that posts from receipts and expenses, not as a rename of the fee page.
- Navigation labels that still say “engine”, “hub”, or “workbench” to a clerk.

## Audit limit

This pass read routes, controllers, the fee and academic implementation, subscription feature keys, the Ask and AI screens, and the architecture notes. It did not execute every service against a production tenant, and it did not treat a menu item as a finished feature.
