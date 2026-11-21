# 10 — Prioritized product roadmap

Research date: 21 November 2026. Sequence changed from the brief: AI waits until tenant tests, consent, and named queries exist. Accounting waits until receipts reconcile. No code is started by this document.

Complexity: S a few days, M a few weeks, L a quarter or more. Estimates are for a small team that already knows this repo. They are not a bid.

## Phase 0 — Stabilize (do this first)

**Modules:** release process, access tests, fee regression, production memory.

**Tasks**

- Name `dev` or another single branch as the only integration line. Cherry-pick outward on a schedule, or stop using the other branches for product work.
- Write the access tests in the testing document for two tenants.
- Add a fee regression: edit a structure, assert an old demand and an old receipt amount.
- Remove or hide sales language for flags with no service.

**Dependencies:** none.

**Database:** none required.

**API:** none required.

**Frontend:** none required.

**Security:** the access tests are the security work.

**Testing:** the two tests above are the exit.

**Complexity:** M.

**Done when:** a fix merged once is the fix that will be deployed, and the fee history test is green.

## Phase 1 — Core school day

**Modules:** admission, students, fees, attendance, exams, parent phone, Hindi and English on those screens, accountant “today”.

**Tasks**

- Application approved creates or links the student, including documents.
- Fee: late fee rule that writes a new charge, installment schedule that does not edit old receipts, refund as a new transaction, reminder list.
- One payment gateway path with success and failure.
- Parent pages at phone width for due, receipt, and today’s attendance.
- Teacher marks a class from one screen.
- Marks for one exam print a class set of report cards.
- Accountant sees today’s collection and can open each receipt.
- Translate those screens.

**Dependencies:** Phase 0.

**Database:** new charge and refund rows. No change to historical demand amounts.

**API:** extend fee service. Reuse attendance and exam APIs.

**Frontend:** parent layout, fee edges, accountant landing.

**Security:** parent sees only their child. Payment webhook verifies its signature.

**Testing:** the scripts in document 13 for this phase.

**Complexity:** L.

**Done when:** a pilot school can admit, mark, collect, and print for one month without a spreadsheet beside the product.

## Phase 2 — Operations a school asks for

**Modules:** transport routes, library issue, hostel beds, staff leave tied to payroll, messages a human sends.

**Tasks:** only for modules the pilot uses. A day school does not need hostel to finish Phase 2.

**Dependencies:** Phase 1 student and fee records, because transport fees and library fines must use the same child.

**Database:** only if a pilot workflow cannot be stored today.

**API:** existing controllers first.

**Frontend:** phone layout if a driver or librarian will use it.

**Security:** GPS coordinates are student-adjacent. Do not expose a bus location on an unauthenticated URL.

**Testing:** one scripted day per enabled module.

**Complexity:** M per module.

**Done when:** the enabled module runs for ten school days without a parallel register.

## Phase 3 — AI foundation

**Modules:** named queries, Ask UI, audit, usage count, consent purpose.

**Tasks**

- Queries: collection today, dues by class, low attendance, missing marks.
- Ask calls only those queries.
- Consent purpose stored before any external call.
- CMS and compliance model calls use the same adapter and the same log shape.
- No new model feature in this phase beyond drafts the user edits.

**Dependencies:** Phase 0 tests and the consent record.

**Database:** consent and AI usage tables.

**API:** read-only query API.

**Frontend:** rename Ask. Show sources (“from the dues list”).

**Security:** query API uses the caller’s role. Logs omit document bodies.

**Testing:** a parent token cannot ask for another class’s dues.

**Complexity:** M.

**Done when:** the three current Ask examples return the same numbers as the screens, for the right user only.

## Phase 4 — Intelligence a human approves

**Modules:** fee reminder drafts, class summaries, document pre-fill, Hindi and English drafts.

**Dependencies:** Phase 3 tools and a cost cap.

**Database:** draft status, not a new student score.

**API:** tool calls only.

**Frontend:** edit then send.

**Security:** no training on tenant data. No automatic send.

**Testing:** a draft is not a receipt and not a mark.

**Complexity:** L.

**Done when:** one pilot uses drafts for a month and can show the audit row for each send.

## Phase 5 — Commercial scale

**Modules:** onboarding checklist, plan packaging, restore drill, second-host plan, board export review.

**Dependencies:** Phase 1 is in production for more than one school.

**Database:** none for its own sake.

**API:** usage already counted.

**Frontend:** setup wizard over existing settings.

**Security:** restore tested.

**Testing:** a new school reaches “first receipt” on a script.

**Complexity:** M, plus L if a second host is required.

**Done when:** a new school is live without an engineer in the building, and a backup has been restored onto a spare database.

## Explicitly later

Full double-entry, native store apps, inventory, canteen, alumni, recruitment, and a public student app. Each waits for a paying school or a legal need, not for a competitor matrix.
