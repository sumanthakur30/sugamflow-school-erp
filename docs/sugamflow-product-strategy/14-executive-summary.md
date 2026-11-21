# 14 — Executive summary

Research date: 21 November 2026. Approved the same day. The only integration branch is `dev`. `dest`, `model`, and `main` are not a second product line. New school work merges to `dev` and is deployed from there.

## What SugamFlow is today

A real school web ERP: shared login, tenants, campuses, sessions, classes, admission, students, a versioned fee engine, attendance with a biometric adapter, exams and report cards, staff and payroll screens, parent and teacher web areas, and a set of operations modules that are uneven. It is not a general ledger, not a native mobile app, and not an AI system. The Ask page answers a few English keywords from live APIs and refuses to change records. That restraint is worth keeping.

## Where it stands

Against Entab, Teachmint, MyClassboard, and Fedena, SugamFlow has the core nouns those products sell, and it has a stronger fee-history rule than their public pages bother to explain. It is behind on parent-phone payments, reminder and installment polish, Hindi coverage, and a single trustworthy release. Against PowerSchool, Alma, and Classter, it should copy the idea that an assistant only sees what the user may already open, and it should not copy district complexity.

Edunext could not be verified. OpenEduCat’s AI claims were left unverified. Entab’s AI claims came from secondary pages, not from the vendor page fetched here.

## The biggest weaknesses

1. Four branches that are not one product.
2. Fee edges parents and accountants feel: installments, late charges, refunds, a phone receipt.
3. Modules and flags that sound finished and are not, especially accounting and AI.
4. No tested proof that every API enforces the school boundary.
5. A host and a service count that leave little room for a local model or a sloppy deploy.

## What it should become

The system a small Indian school runs for admission, attendance, fees, and report cards, with parents on a phone, last year’s money left alone, and an assistant that answers from those records only after a person could have opened the same screen.

## What to add, improve, or leave

**Improve:** fees, parent phone, attendance save, report cards, language, roles, onboarding.

**Add later:** named queries, drafts, consent records, a ledger designed from receipts, GPS only with a pilot.

**Leave:** a second fee product, a second login, Moodle, inventory, canteen, student scoring, and any claim that the AI settings page is the product.

## How AI creates an advantage

Not by chatting. By answering “who has not paid?” and “who was absent?” with the same numbers as the screen, then drafting a Hindi notice the office edits. That is faster than training every clerk to build a report, and it is safer than a model with a copy of the database. The current keyword page is the seed. The next step is named queries, not a new vendor logo.

## Architecture

Keep the Angular app, the gateway, the school services, and PostgreSQL with organization scope. Make one branch the product. Add a queue only for reminders, imports, and scans. Call a model only through tools. Do not add Kubernetes or a service per AI feature.

## Affordable and usable

Sell a standard set a single-campus school can run. Price by student band and by module, using the billing hooks that already exist. Do not publish a competitor’s price, and do not add lending. Train for one morning. Activate the school when the first real receipt is saved. Put Hindi on the screens that move money.

## Commercial position

“Receipts stay true, and a parent can open them on a phone.” Website, hostel, and AI are expansions after that sentence is true for a pilot.

## Development sequence

Phase 0 stabilize and test boundaries. Phase 1 the school day. Phase 2 only the operations a pilot uses. Phase 3 named queries. Phase 4 drafts a human sends. Phase 5 onboarding and restore. Ledger, store apps, and extra modules wait.

## Top 20 recommendations

| Rank | Recommendation | Why this rank |
|---|---|---|
| 1 | One integration branch and one deploy line | Otherwise every later rank is done twice |
| 2 | Automated proof that old fee demands survive an edit | This is the commercial promise |
| 3 | Cross-tenant and cross-role tests | Required before parents and before AI |
| 4 | Consent purpose stored before bulk messages or models | DPDP and trust |
| 5 | Parent phone flow for due, pay, and receipt | How Indian schools feel the product |
| 6 | Late fee, refund, and installment as new money rows | Closes the fee gap without rewriting history |
| 7 | One payment webhook that cannot double-charge | Cash integrity |
| 8 | Approved application creates one student | Stops duplicate children |
| 9 | Teacher saves a class attendance once | Daily habit |
| 10 | One exam prints the class report cards | What parents ask for in March |
| 11 | Accountant “today” equals the receipt list | Stops the side spreadsheet |
| 12 | Hindi and English on those daily screens | Small schools are not English-only |
| 13 | Four roles that match the nav proposal | Owner, accountant, teacher, parent |
| 14 | Human-sent fee reminders with a log | Better than a silent blast |
| 15 | Setup checklist over the screens that exist | Activation without a new subsystem |
| 16 | Named Ask queries, same numbers as the screens | The first honest AI step |
| 17 | Hide flags and pages that have no service | Protects the sales promise |
| 18 | Payroll reads staff attendance for one pilot | Only after the core day works |
| 19 | Transport routes, GPS only under a pilot flag | Avoid a live-tracking claim |
| 20 | A ledger design that posts from receipts | Do not build it until rank 11 is true |

## First five tasks after this strategy is approved

1. **B-01.** Declare the integration branch and stop deploying the others.
2. **B-02.** Add the fee-history regression and run it on that branch.
3. **B-03.** Add the two-tenant access tests for parent, teacher, and school.
4. **B-04.** Draft the consent-purpose record and have counsel review the parent wording before any new model call.
5. **B-10 and B-09 together.** Parent phone layout for dues and receipts, and one signature-checked payment path, because that is the first thing a pilot school will judge.

Items 1–4 are P0. Item 5 is the first P1 a customer can see. Do not start the chatbot, the ledger, or a new visual theme in that first sprint.
