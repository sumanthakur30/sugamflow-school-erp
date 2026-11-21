# 08 — UI and UX improvement plan

Research date: 21 November 2026. The live school shell is the visual source of truth. This plan does not ask for a new theme.

## What already works

The daily menu is short on purpose: Home, Students, Fees, Attendance, Exams, Staff, then Messages, Reports, and Settings. A large school also sees Transport, Library, Payroll, and Campuses. Forms, workflows, rules, and the AI flag page are kept out of that menu. That is the right instinct for a clerk.

Parent and teacher areas are separate route trees, which is the right split even while they live in one web app.

## What makes the product feel unfinished

- Too many capable screens sit under More, with names that match the code (`ops`, `desk`, `hub`) more than the job.
- Settings contains both “set the school’s classes” and “set the product’s engines”. A principal cannot tell which one is theirs.
- Tables and filters differ in density from screen to screen. The fee and student lists should be the pattern for the rest.
- Empty states often fail to say the one next action, such as “Add classes in Academic Structure, then return here”.
- The Ask page looks like chat and behaves like three keywords. Either label it “Questions about your records” or wait until it can answer more than fees and attendance.
- Hindi is a setting, not yet a proven translation of the daily screens.
- Parent pages must be usable at phone width. That was not re-measured in this pass and should be the first UX test.

## Design rules

- One primary action per page.
- Money and marks use the same number format everywhere.
- A destructive or financial action is a button with a verb: “Save fee structure”, “Record payment”, not “Submit”.
- Status uses the existing badge style.
- Do not add a card grid to replace a table the accountant already understands.
- Loading and errors say what failed and whether the record was saved.

## Proposed navigation

### Super Admin (platform, not a school clerk)

Tenants, plans, feature flags, renewal, usage, support tickets, impersonation with an audit row. This stays in the platform admin, not in the school sidebar.

### School admin (owner or principal’s office)

Home. Students. Fees. Attendance. Exams. Staff. Messages. Reports. Settings.

Settings order:

1. School, year, and classes.
2. Standard fee structure.
3. Admission numbers.
4. Modules this school uses.
5. Staff roles.
6. Notices and language.
7. Website, only if the plan has it.

More holds transport, library, hostel, payroll, compliance, and gate pass. Hidden until the school turns the module on.

### Accountant

Fees. Today’s collection. Dues. Receipts. Income and expense. Reports. No exam markup, no website CMS.

### Teacher

Today’s timetable. Mark attendance. Homework. Enter marks. My classes. Messages to parents of those classes.

### Parent

This child. Attendance. Fees and receipts. Homework. Results. Notices. Apply for leave.

One child switcher if the parent has siblings. No admin settings.

### Student

Later. Homework, timetable, notices. No fee negotiation and no other child’s data. Do not build this until the parent phone layout is solid.

## Settings information architecture

| Belongs in Settings | Does not belong in the daily sidebar |
|---|---|
| Year, classes, sections | Workflow builder |
| Standard fees | Rule engine |
| Admission number format | Menu designer |
| Which modules are on | AI provider keys |
| Language | Raw feature-flag JSON |

## First UX work, after approval

1. Phone pass on parent fees and attendance.
2. One empty-state sentence on the fee screen when the class list is only the saved classes.
3. Rename Ask so it does not promise a general assistant.
4. Accountant landing: collection today, dues, last receipt. Three numbers, then the table.
