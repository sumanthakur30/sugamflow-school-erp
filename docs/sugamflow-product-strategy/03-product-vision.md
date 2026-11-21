# 03 — Product vision

Research date: 21 November 2026. This is the proposed product, not a build instruction.

## The sentence

SugamFlow is the school system a clerk, a teacher, and a parent can finish their day on: admission, attendance, fees, exams, and a clear answer to “what is pending?”, with AI only where it saves that work and never where it decides a child’s record.

## Who it is for

First customers are Indian K-12 schools that still run fees in a register or in a tool the accountant does not trust. The first fit is a single-campus school, then a school with two or three campuses. Coaching centres and colleges can stay as presets. They are not the design center.

A school with a full-time IT team and a foreign curriculum can wait. PowerSchool-shaped features are a later market.

## What “complete” means

Complete does not mean every module Entab or Fedena lists. Complete means a school can:

1. Set the year, the classes, and the fee once.
2. Admit a child and find that child again.
3. Mark attendance and see who is absent.
4. Collect a fee, print a receipt, and know the due.
5. Enter marks and print a report card.
6. Tell parents in Hindi or English without a second login.
7. Hand an accountant a collection total that matches the receipts.
8. Add a campus, a session, or a staff role without a code change.
9. Ask a plain question and get an answer from records that person is allowed to see.
10. Leave next year with last year’s receipts unchanged.

## Product principles

- **One record.** Enquiry, application, student, fee, and attendance point at the same child.
- **History is frozen.** A new fee, a new discount rule, or a new report card layout does not rewrite old money or old marks.
- **The plan is not the workflow.** A small school on a small plan still admits a student and collects a fee. Premium modules add transport, hostel, payroll depth, and AI. They do not add extra clicks to the basic day.
- **AI is a reader and a drafter.** It may list dues, summarize a class, or draft a notice. It may not mark a child failed, waive a fee, or message a parent until a person confirms.
- **Indian first.** UPI, receipts, academic sessions that start in April, classes from Nursery to 12, Hindi, and board exports matter more than a district OneRoster story.
- **Same login as the rest of SugamFlow.** School staff do not get a second identity product.

## The school, in modules

These names match the areas in the brief. Each one is judged against the code, not against a wish list. Detail is in the module inventory.

| Area | Vision | Already true enough to keep |
|---|---|---|
| Administration | Profile, campuses, sessions, classes, calendar, admission, enquiries, certificates, notices | Settings, branches, academic structure, calendar, admission, leads, ID cards, notices |
| Students | Profile, guardians, promotion, documents, health with limited access, alumni later | Student master, guardians work already started in earlier phases, documents, lifecycle route |
| Fees | Categories, class amounts, optional section or student exceptions, every common frequency, concessions, late fee, installments, online pay, receipts, dues, reminders, refunds | Standard structure, collection, receipts, adjustments, payment adapter. Several of the commercial edges are still thin |
| Accounting | A ledger a chartered accountant can follow, fed by receipts and expenses | Only a cash-style income and expense desk. Do not call it accounting yet |
| Academics | Attendance, timetable, substitution, homework, exams, grades, report cards | Most of the daily academic desk exists. Substitution and syllabus tracking are not established |
| HR | Staff file, leave, salary, slip, documents | Staff and payroll exist. Recruitment and performance management are not the current product |
| Operations | Transport, library, hostel, front desk, gate pass | Present as modules. Inventory, canteen, assets, and maintenance are not established services |
| People | Parent, teacher, and student surfaces that match the phone | Parent and teacher web routes exist. A student portal and native apps do not |
| Intelligence | An assistant with the user’s school, campus, and permissions | A keyword page and a few optional generators. The permission rule is the right rule to keep |

## What SugamFlow should refuse

- A second fee system beside the versioned definitions.
- A public model trained on student rows.
- An AI score that changes discipline, promotion, or fees.
- A module menu that is longer than the work of a small school.
- A redesign that throws away the current shell to look like a generic analytics product.

## Five-year shape (recommendation)

Year 1 is a trustworthy core for fees, attendance, exams, and parents. Year 2 is operations and a deterministic assistant. Year 3 is a real ledger and carefully scoped language models. The architecture section explains why this order follows the code that already exists.
