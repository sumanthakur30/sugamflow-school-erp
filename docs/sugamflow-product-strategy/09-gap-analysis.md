# 09 — Gap analysis

Research date: 21 November 2026. Priority: P0 foundation or data integrity, P1 essential school work, P2 competitive, P3 advanced AI, P4 later.

| Module | Current status | Industry expectation | Gap | Priority | Recommendation |
|---|---|---|---|---|---|
| Release line | Four diverged branches | One product a customer can be patched on | Fixes land unevenly | P0 | Choose one integration branch and stop feature work on the others |
| Tenant safety | Headers and service filters exist. Not proven for every query | A user never sees another school | Missing systematic access tests | P0 | Test parent, teacher, and accountant against a second tenant before any AI tool |
| Fee history | Versioned definitions and snapshotted demands | Old receipts never change | Keep it, and test it on every fee change | P0 | Make “history unchanged” a release test |
| Fee collection | Structure, collection, receipts, dues | Discounts, fines, installments, reminders, online pay, refunds | Fines, installments, refunds, and reminder policy are thin | P1 | Finish collection edges on the current engine |
| Parent fees | Web route | Phone-friendly pay and receipt | Mobile layout and payment completion unverified | P1 | Parent phone flow for due, pay, receipt |
| Admission to student | Both modules exist | One capture becomes the student | Risk of typing twice or losing documents | P1 | One path from approved application to student |
| Attendance | Roster, staff, biometric adapter | Fast class mark, parent visibility | Device installs and parent view need a scripted test | P1 | One class-marking path and a parent view of that day |
| Exams to report card | Marks, date sheet, cards | Teacher enters marks, office prints | Board layouts and a single “print the class” path | P1 | Close the marks-to-card path for one board first |
| Language | Localization settings | Hindi and English on daily screens | Coverage unknown | P1 | Translate the ten screens a clerk and a parent use daily |
| Accountant totals | Income-expense and fee reports | Today’s collection matches receipts | No single tested reconciliation | P1 | One “today” total with a drill to receipts |
| Roles | Owner bypass and feature flags | Accountant, teacher, parent see different work | Action-level permissions are uneven | P1 | Define four roles and test them |
| DPDP consent record | Privacy desk | Purpose, parent, time, withdrawal | No consent ledger for AI or marketing messages | P0 | Record purpose before the first model call or bulk WhatsApp |
| Ask | Keyword router | Questions over data the user may see | Not a general assistant, and not yet tool-safe | P2 | Named queries first. Model later |
| Online payments | Adapter and webhook | UPI and a receipt without the office | Production gateway proof not in this audit | P1 | One gateway, one successful and one failed payment test |
| Reminders | Notification config | Parents reminded before the due date | Policy and channel quality | P2 | Reminder list a human sends |
| Staff and payroll | Controllers exist | Attendance, leave, slip | Statutory and leave depth unverified | P2 | Match payroll to staff attendance for one school |
| Transport | Routes and GPS controller | Routes parents trust. GPS only if live | Live tracking not verified | P2 | Sell routes first. GPS only after a pilot bus |
| Library and hostel | Screens exist | Daily issue and bed allocation | Need a pilot, not a rewrite | P2 | Keep. Do not expand until a boarding or library school asks |
| Inventory and canteen | Flag or absent | Stock and tuck shop in larger suites | No service | P4 | Build only for a paying school that asks |
| Accounting ledger | Not found | Vouchers, bank, statements | Would be a false label today | P2 | Design a ledger that posts from receipts. Do not rename the fee page |
| LMS | Native homework by decision | Either homework or a real LMS | External mode is a switch, not a connector | P3 | Stay with homework |
| Native apps | Not in the repo | Parent app is how Teachmint is felt | Web can be enough if the phone layout works | P2 | Phone web first. Store apps after parents fail at the browser |
| AI assistant | Flags and keywords | Role-safe answers and drafts | No tool calling, no cost cap, no audit of prompts | P3 | Follow the AI strategy. Do not start with a chatbot |
| Document read | Upload exists | Scan an admission form into fields | No confirmed extraction pipeline | P3 | Pilot OCR with human confirm |
| Analytics | Reports | Early patterns for fees and attendance | Easy to over-claim | P3 | Deterministic class lists before predictive scores |
| Onboarding | Settings pages | A new school live in a day | No guided first week | P2 | A short setup: year, classes, one fee, one class of students |
| Website and CMS | Real module | School site and enquiry form | Keep it out of the ERP critical path | P4 | Leave it on its own release |
| Compliance exports | UDISE route and CBSE desks | Files the board accepts | Formats drift | P2 | Re-check the export against the current template before a sales claim |

## Why the top gaps matter

**One branch.** Users affected: every customer and the team. Problem: a fix on `dev` is not a fix on `dest`. Dependency: git policy, not a feature. Complexity: low technically, high socially. Value: every later feature is cheaper.

**Fee edges and parent phone.** Users: accountant, parent, owner. Problem: the school’s cash depends on it. Dependency: the current fee engine. Complexity: medium. Value: the reason a school pays.

**Consent and tenant tests.** Users: children and the company. Problem: AI and messaging without a purpose record is a legal and trust risk. Dependency: privacy desk. Complexity: medium. Value: permission to sell.

**Ledger.** Users: auditor and owner. Problem: “accounting” in a brochure would be false. Dependency: receipts that already balance. Complexity: high. Value: medium until the fee desk is trusted.
