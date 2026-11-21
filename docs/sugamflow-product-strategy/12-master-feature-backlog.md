# 12 — Master feature backlog

Research date: 21 November 2026. Acceptance criteria are the definition of done. None of these items are approved to build until the strategy is accepted.

Priority uses the same scale as the gap analysis.

## P0

### B-01 One integration branch

**Why:** Cherry-picks already dropped or kept discount behaviour differently. **Who:** the team and every school. **Exists:** four branches. **Depends on:** nothing. **Complexity:** S. **Value:** every later fix.

**Acceptance:** A written branch is the only place features merge. A second branch is not deployed. The fee history test from B-02 runs on that branch.

### B-02 Fee history regression

**Why:** The product’s promise is that a new structure does not change old demands. **Who:** accountant. **Exists:** versioned definitions and snapshots. **Depends on:** current fee service. **Complexity:** S. **Value:** trust.

**Acceptance:** Given an issued demand, when the standard fee amount changes, the stored demand lines and a sample receipt total stay equal to the values before the edit.

### B-03 Cross-tenant access tests

**Why:** AI and parent logins multiply the chance of a leak. **Who:** every child. **Exists:** tenant filters, unproven as a suite. **Depends on:** two test tenants. **Complexity:** M. **Value:** permission to grow.

**Acceptance:** Parent A cannot read Parent B’s fees. Teacher cannot read payroll. School A’s token cannot list School B’s students. Tests run in CI.

### B-04 Consent purpose record

**Why:** DPDP expects a purpose and a way to withdraw, and AI must not be a silent extra purpose. **Who:** parent and the company. **Exists:** privacy desk. **Depends on:** counsel for the final wording. **Complexity:** M. **Value:** legal posture.

**Acceptance:** A stored row has child, parent or staff actor, purpose text, time, and withdrawn-at. An AI or bulk-message send refuses to run when the purpose is absent. Wording is reviewed by counsel before it is shown to a school.

## P1

### B-05 Approved application becomes a student

**Acceptance:** Approving an application creates or links one student, copies the confirmed fields, and keeps the uploaded documents. A second click does not create a duplicate.

### B-06 Late fee as a new charge

**Acceptance:** A configured late rule adds a new line. It does not change the original tuition line on an old receipt.

### B-07 Refund as a new transaction

**Acceptance:** A refund references the receipt, reduces the balance, and leaves the original receipt amount readable.

### B-08 Installment schedule

**Acceptance:** A structure can be split into dated portions. Paying one portion does not mark the year fully paid. Editing next year’s portions does not edit a paid portion.

### B-09 One payment gateway happy path

**Acceptance:** A parent or clerk can pay a due, the webhook is signature-checked, and a receipt appears once even if the webhook is delivered twice.

### B-10 Parent phone: due, pay, receipt, attendance

**Acceptance:** On a 390-pixel width, a parent completes those four tasks without horizontal scrolling and without the admin sidebar.

### B-11 Class attendance in one save

**Acceptance:** A teacher marks a class, saves once, and the parent view for that date matches the saved marks.

### B-12 One exam to a class set of report cards

**Acceptance:** After marks are complete, the office prints or downloads every card for that class and section. A missing mark is listed, not printed as zero unless the school saved a zero.

### B-13 Accountant today

**Acceptance:** The total on the landing page equals the sum of the receipts in the list under it for that date and campus.

### B-14 Daily-screen Hindi and English

**Acceptance:** The screens in B-10 through B-13 switch language and do not leave mixed labels on buttons that move money.

## P2

### B-15 Human-sent fee reminders

**Acceptance:** The office sees a list of dues and sends a drafted message. Nothing is sent by a schedule until the school turns that schedule on. Each send is logged.

### B-16 Setup checklist

**Acceptance:** A new school is shown year, classes, one fee structure, and the first student, with links to the existing screens. Finishing the list is visible to support.

### B-17 Payroll uses staff attendance

**Acceptance:** For one pilot, unpaid leave days on the staff attendance record appear on the slip input. A person still confirms the run.

### B-18 Transport routes without a public map

**Acceptance:** Routes and stops are stored per campus. A GPS page is hidden unless a pilot flag is on. No unauthenticated location URL.

## P3

### B-19 Named Ask queries

**Acceptance:** “Outstanding fees” and “low attendance” return the same figures as the fee and attendance screens for that user, and no other figures.

### B-20 Notice draft

**Acceptance:** A draft is created in the selected language, the user edits it, and only the edited text can be sent. The student table is not attached to the model request.

### B-21 Admission document pre-fill

**Acceptance:** An upload suggests fields. Nothing is stored on the student until the clerk saves. The original file remains attached.

## P4

### B-22 Ledger posting design

**Acceptance:** A design note, not code, shows how a receipt would become a debit and a credit. No screen is renamed to “balance sheet” in this item.

The note is `15-ledger-posting-from-receipts.md`. Receipt RCP-1042 for ₹5,000 cash tuition becomes a debit to Cash and a credit to Tuition income. The finance screen keeps its name.

### B-23 Student web

**Acceptance:** A student opens `/student` and sees their own attendance, results, report cards, homework, timetable, and notices. Fee payment stays on the parent phone.
