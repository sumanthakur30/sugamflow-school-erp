# 02 — Industry research

Research date: 21 November 2026. Prices are omitted unless a cited page states them. A missing cell means the public source did not confirm the capability. It does not mean the vendor cannot do it.

## How schools actually buy

Indian schools, especially smaller ones, buy software to collect fees, mark attendance, print a receipt or report card, message parents, and survive an inspection. They do not buy a catalog of 100 modules. The products that win are the ones a clerk can use on a phone, in Hindi or English, on a slow connection, without a trainer in the building every week.

International products (PowerSchool, Alma, Classter) sell to districts that already have IT staff, procurement, and student-information rules that are not India’s. They are useful as design references for analytics, permissions, and parent experience. They are a poor template for the first SugamFlow price list.

## Sources checked on 21 November 2026

| Product | Source | What was actually read |
|---|---|---|
| Entab CampusCare | [entab.in school ERP](https://www.entab.in/school-erp-software.html) | Vendor page: admissions, fees and online payment, CBSE-aligned exams, holistic progress cards, attendance, bus tracking, HR and payroll, NEP 2020 claims, leadership dashboards. “2,500+ schools” is the vendor’s own claim |
| Entab modules, secondary | [Techjockey CampusCare 10X](https://www.techjockey.com/detail/school-erp1), accessed via search snippet | Marketplace list: SIS, admissions, fees, exams, HR, transport and GPS, attendance, library, hostel, alumni, inventory, visitor desk, WhatsApp, online classes, behaviour, events. Marketplace copy, not a contract |
| Entab AI claims, secondary | [FindThatSoftware Entab review](https://findthatsoftware.com/tools/entab), [Smart Class Sync comparison, 8 Nov 2026](https://smartclasssync.com/blog/smart-class-sync-vs-entab-campuscare) | These pages attribute an AI dashboard, auto-grading, a chatbot, and question-paper help to Entab. They are not Entab’s own page. Treat AI claims as **unconfirmed by the vendor page fetched in this pass** |
| Teachmint | [School ERP](https://www.teachmint.com/en-us/features/school-erp), [fees](https://www.teachmint.com/en-us/fee-management), [attendance](https://www.teachmint.com/en-us/features/attendance-management-system), [school management](https://www.teachmint.com/en-us/features/school-management-software), [Teachpay](https://teachpay.in/) | Admissions, fees with discounts, fines, installments, online and counter payments, UPI QR, parent app for attendance, homework, results, and announcements, staff records, ID cards and report cards, 20+ fee reports. Teachpay adds EMI and auto-debit. Those payment products are financial services with their own KYC, not a normal ERP screen |
| MyClassboard | [myclassboard.com](https://www.myclassboard.com/), [product list](https://www.myclassboard.com/explore-all-products/) | Separate products: ERP, LMS, Finance, Admissions, HR, Connect. ERP list includes SIS, certificates, documents, inventory, library, hostel, alumni, alerts, transport, forms. Finance list includes expenses, canteen, connected banking, PDC. Admissions list includes leads and WhatsApp campaigns |
| Fedena | [fedena.com](https://fedena.com/), [Foradian](https://www.foradian.com/), [Fedena support: what is Fedena](https://support.fedena.com/support/solutions/articles/266501-what-is-fedena-) | Fees, exams and gradebook, admission, timetable, attendance, parent collaboration, online classes via Meet, Zoom, or BigBlueButton. Support article: Fedena is an ERP, not an LMS, and lists fees, finance, transport, hostel, attendance, communication, inventory, and exams among common uses. “100+ modules” and “40,000+ institutions” are vendor claims |
| PowerSchool | [PowerBuddy for staff](https://ps.powerschool-docs.com/naviance/latest/powerbuddy-for-staff), [Naviance notes, March 2026](https://ps.powerschool-docs.com/naviance/latest/naviance-release-notes-march-2026) | Staff assistant that finds reports, summarizes a downloaded report, and looks up a student inside that report. It is permission-gated and must be turned on. This is a real product pattern: AI over reports the user can already open |
| Alma | [BeaconAI](https://www.getalma.com/beaconAI/) | Analytics across attendance, enrollment, grades, behaviour, and family engagement. The page describes early-warning views, not an autonomous decision about a child |
| Classter | [Classter vs PowerSchool](https://www.classter.com/compare/competitors/classter-vs-powerschool/) | Vendor comparison page. Claims one student record, open API, OneRoster, Microsoft 365, and an assistant named Epicurus “rolling out through the year” for at-risk flags and operational workflows. Competitor-comparison pages are marketing |
| OpenEduCat | Not fetched from the vendor in this pass | A third-party ranking ([iVake, 22 Sep 2026](https://www.ivake.com/top-10-school-management-software-india-2026), pages checked 18 Sep 2026) says OpenEduCat publishes a large AI tool count and a parent app. That number is **not** repeated here as a verified vendor fact |
| Edunext | Search on 21 Nov 2026 | No official school-ERP feature page was found. `edunext.com` was a parked domain. **No Edunext feature is listed below** |

## Comparison matrix

Status key: **Yes** = stated on a source above. **Claim** = vendor marketing without a workflow description. **Unknown** = not on the sources read. **No public AI** = support or product page reviewed did not describe an AI module.

| Area | Entab | Teachmint | MyClassboard | Fedena | PowerSchool / Alma / Classter | SugamFlow today |
|---|---|---|---|---|---|---|
| Administration | Yes, SIS and multi-function ERP | Yes | Yes, plus franchise mention | Yes | Yes, district scale | Yes: tenant, campus, session, settings |
| Student lifecycle | Yes, including alumni on marketplace list | Yes, admission into SIS | Yes, alumni listed | Yes, admission | Yes | Admission, students, promotion route, documents. Alumni depth not established |
| Fees | Yes, online pay, dues, receipts | Yes, discounts, fines, installments, reminders | Finance is a separate product | Yes, fines and discounts | Yes | Class-wise standard fees, collection, receipts, adjustments. Installment product is not a finished rival to Teachpay |
| Accounting | “Fee and accounting” on the vendor page. Ledger depth unknown | Budget and forecasting mentioned at headline level | Separate Finance product: expenses, banking, PDC | Support article says finances are a common use. Ledger depth unknown | Unknown from the AI pages read | Income and expense screens. No general ledger found |
| Attendance | Yes | Yes, including classroom board | Unknown on the pages read | Yes | Yes, Alma analytics | Roster, staff, biometric adapter, offline route |
| Exams | Yes, CBSE and holistic card claim | Results and report cards | Grade books listed, including CBSE and IB | Yes | Yes | Marks, date sheet, admit card, report card |
| Timetable | On secondary Entab lists | Unknown on pages read | Unknown | Yes | Unknown here | Timetable controller and screen |
| Parent communication | WhatsApp on marketplace list | Parent app and announcements | Connect product and WhatsApp campaigns | Parent collaboration and alerts | Family engagement in Alma | Web parent portal and notification config |
| Teacher work | Mobile app claim | Classroom attendance | LMS and teaching plan are separate products | Online classes via video tools | PowerBuddy is staff-only | Teacher web routes for attendance, gradebook, homework |
| HR and payroll | Yes | Staff repository | Separate HR product | Common use, depth unknown | Unknown here | Staff directory and payroll controllers |
| Transport | Yes, GPS claim | Unknown on pages read | Listed | Listed in support article | Unknown here | Routes and a GPS controller. Live tracking not verified |
| Library | Marketplace list | Unknown | Listed | Not on the pages quoted | Unknown | Circulation controller and screen |
| Inventory | Marketplace list | Unknown | Listed | Support article | Unknown | Feature flag. No inventory service was in the service list |
| LMS | Entab sells an LMS and content brand beside the ERP | LMS is part of the suite headline | Separate LMS product | Explicitly not an LMS | Not the subject of the pages read | Native homework only, by a written decision |
| Mobile apps | Vendor and secondary sources say apps exist | Parent app described | Unknown | Unknown on pages read | District portals | Responsive web only in this repo |
| AI | Secondary pages only | Not described on the pages read | Not described | No AI module found on the pages read | PowerBuddy, BeaconAI, and Classter’s Epicurus are documented differently | Keyword Ask, AI flag screen, heuristic compliance scan, optional CMS draft |
| Analytics | Leadership dashboards claimed | Fee and classroom reports | Unknown | “Insightful reports” claim | BeaconAI is the clearest analytics product in this set | Report builder and fee reports. Not an early-warning product |
| Security | Secondary page mentions encryption, MFA, roles | Not assessed | Not assessed | “Data security” claim | Permission-gated assistant | JWT, feature guards, tenant headers. MFA and a formal DPDP program were not found as finished product features |
| Integrations | UPI, Tally, and biometric appear on secondary Entab pages | Payment gateway, UPI, POS on Teachpay | Connected banking claimed | Zoom, Meet, BigBlueButton | OneRoster and Microsoft on Classter’s page | Razorpay-style fee payment adapter and ZKTeco adapter exist in code. Tally was not found |
| Commercial model | Demo, then a price for the school. No public rate card on the page read | Public list price not on the pages read | Not published on the pages read | Foradian stresses affordability. A public rupee list was not on fedena.com in this pass | District contracts. Not a useful Indian SMB price | Feature flags, a rate-card controller, and a per-student billing controller already exist in code |

## What the market is actually rewarding

Verified pattern across the Indian sources:

- Fee structure, discount, fine, installment, receipt, and a parent reminder.
- Attendance that a teacher can mark quickly, then a parent can see.
- Admission that becomes a student without typing the child twice.
- Report cards and ID cards the school can print.
- WhatsApp or SMS as the channel parents read. Email alone is not the product.
- A small set of reports an accountant already knows: today’s collection, dues, class-wise.

Verified pattern across the international AI sources:

- The assistant answers from data the user may already see.
- Analytics warn a human. They do not punish a student.
- The feature is off until an administrator enables it.

## What SugamFlow can do better

Recommendation, not a claim that it is already better:

- Keep one login and one fee history. Several Indian suites split ERP, finance, LMS, and admissions into products that schools then pay to connect.
- Make the standard fee change safe for last year’s receipts. That rule is already in SugamFlow and is worth more than a longer module list.
- Ship a parent phone experience for fees and attendance before a GPS story or an AI story.
- Use Hindi and English in the screens a clerk touches every day.
- Refuse student scoring that a parent cannot understand. Alma’s public page is a better model than an opaque “risk score”.

## Research limits

Edunext has no confirmed feature list. OpenEduCat’s AI count was left unverified. Entab’s AI features were only on secondary pages. No vendor price was copied into a SugamFlow price. Customer reviews were not treated as a feature inventory.
