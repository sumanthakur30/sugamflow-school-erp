# 04 — Complete module inventory

Research date: 21 November 2026. Status is from routes and controllers in `D:\school`, not from a production walkthrough of every button.

Status words:

- **Working:** a service and a screen exist and the recent fee and class work shows the pattern is real.
- **Partial:** a route or controller exists, and the daily workflow is incomplete or unverified.
- **Flag only:** a subscription feature key exists without a matching service in this repo.
- **Absent:** no controller or screen was found.

## Platform

| Module | Status | Notes |
|---|---|---|
| Login and JWT | Working | Shared auth service. School id, username, password |
| Tenant, campus, session | Working | Headers and settings. Session default in the client is a school year such as 2025-26 |
| Feature entitlements | Working | `FEATURE_*` catalog. UI hides disabled modules |
| Roles and invites | Partial | Staff invite and access screens exist. A full permission matrix per action was not re-audited |
| Audit | Partial | Audit service and admin route exist |
| Forms, workflows, rules | Partial | Services and screens exist and are hidden from the daily menu. Do not assume every school process runs through them |
| Report builder | Partial | Service and designer route |
| Support tickets | Partial | Support service |
| Design studio and website | Partial | Branding and CMS are real products beside the ERP |
| Subscription and rate card | Partial | Catalog, lifecycle, usage, and per-student billing controllers exist. Commercial packaging is not a finished storefront |

## School administration

| Module | Status | Notes |
|---|---|---|
| School profile and presets | Working | Small, large, coaching, college |
| Campuses | Working | Branch controller. Large preset shows Campuses |
| Sessions and classes | Working | Academic structure, standard ladder, sections |
| Subjects | Partial | Used by exams. A standalone curriculum product was not established |
| Calendar | Working | School calendar route |
| Enquiries | Working | Lead controller, admin route |
| Admission applications | Working | Admission controllers, including a public application path |
| Certificates and ID cards | Partial | ID card work exists in the student and report area. Not re-tested here |
| Announcements | Partial | Notification and comms routes |
| Visitor desk | Flag and route mix | `FEATURE_VISITOR` exists. Front-desk behaviour is partly the campus desk, not a full visitor log |

## Students

| Module | Status | Notes |
|---|---|---|
| Profile and admission form | Working | Rich form writes student answers. Short add still works |
| Guardians | Partial | Earlier phase work. Not re-verified field by field |
| Documents | Working | Upload after the student exists. Public verify-by-token route exists |
| Promotion and transfer | Partial | Lifecycle route |
| Directory and student 360 | Partial | Search and a 360 route |
| Health and emergency | Partial | Some fields exist on forms and ID cards. Access control for health data needs a deliberate policy |
| Behaviour | Absent as a module | No discipline service found |
| Alumni | Absent | No alumni service found |
| Privacy requests | Partial | Privacy controller. This is a desk, not a finished DPDP program |
| UDISE-style export | Partial | Export route. Mapping to the current government file must be checked when the format changes |

## Fees

| Module | Status | Notes |
|---|---|---|
| Fee heads and categories | Working | Unique labels, versioned definitions |
| Class-wise standard fees | Working | One frequency per category, amounts per class, save in one place |
| Section-specific structure | Partial | Matcher prefers the class without a section when both exist |
| Student adjustments | Working on `dev` / `main` | Stricter rules there. Older checks on `dest` / `model` |
| Frequencies | Working | One-time, monthly, quarterly, half-yearly, annual |
| Demand and receipt | Working | Demand snapshots amounts. Receipts are not rewritten by a later fee edit |
| Online payment | Partial | Payment adapter and webhook controller. Production gateway coverage was not re-tested |
| Dues and defaulters | Working | Used by the Ask screen |
| Refunds, installments, late fees | Partial or absent | Do not claim parity with Teachmint’s fine and installment product |
| Fee analytics | Partial | Income-expense and collection views |

## Finance

| Module | Status | Notes |
|---|---|---|
| Income and expense | Partial | Controllers and an admin route. Cash book, not a ledger |
| General ledger, journals, vouchers, bank reconciliation, balance sheet, budget | Absent | No types found in school Java or SQL |
| Vendor purchase | Absent | No purchase service |

## Academics

| Module | Status | Notes |
|---|---|---|
| Student attendance | Working | Roster |
| Staff attendance | Working | Controller |
| Biometric | Partial | ZKTeco iclock and admin controller. A device on a school LAN is an installation, not just code |
| Offline attendance | Partial | Route and settings controller |
| Face recognition and GPS attendance | Flag only | Catalog keys |
| Timetable | Partial | Controller and screen. Substitution was not found |
| Homework | Working as MVP | Native, under the LMS flag. External Moodle is a later switch |
| Lesson plans and syllabus | Absent | Not a service |
| Exams, marks, date sheet, admit card, report card | Working | Board-specific layouts vary. CBSE compliance is a separate module |
| Online classes | Partial | Route exists. Depth unknown |
| Classroom | Partial | Controller exists |

## People operations

| Module | Status | Notes |
|---|---|---|
| Staff directory and invite | Working | |
| Leave | Partial | Admin leave route. Full policy engine not established |
| Payroll and slips | Partial | Payroll controllers. Statutory depth unverified |
| Recruitment and performance | Absent | |
| Transport and routes | Partial | Controllers, including GPS. Live map not verified |
| Library | Partial | Catalog and circulation screens |
| Hostel | Partial | Beds and residents |
| Gate pass | Partial | Route. Branch difference: some branches open a campus desk instead of the dedicated gate-pass screen |
| Inventory, assets, canteen, maintenance | Flag or absent | `FEATURE_INVENTORY` has no inventory service in the service list |

## Experience

| Module | Status | Notes |
|---|---|---|
| Admin shell | Working | Simple daily nav plus More |
| Parent web | Partial | Attendance, fees, exams, report cards, homework |
| Teacher web | Partial | Students, attendance, gradebook, report cards, homework |
| Student web | Partial | `/student` shows attendance, results, and notices. Fee payment stays on the parent phone |
| Native mobile apps | Absent in this repo | |
| Ask | Partial | Three example questions, keyword dispatch, read-only |
| AI settings | Partial | Boolean flags |
| Localization | Partial | Settings route. Hindi coverage of every screen was not measured |

## How to read this inventory

A Working row is allowed to have bugs. A Partial row must not be sold as a finished module. A Flag only row must not appear in a sales deck.
