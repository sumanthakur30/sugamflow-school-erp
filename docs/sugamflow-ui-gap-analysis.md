# SugamFlow School ERP — UI gap analysis

Inspected on `origin/main` (`73504ce`, 19 Oct 2026) in `D:\school`. The SSMS screens (`ssms.erpsonline.com`) were not opened while logged in. Comparison uses the supplied screenshots (fee particulars, promote students, result update student wise) and the named public URLs only.

This branch (`feature/exam-result-search-filters`) changes class marks search behavior. The matrix below describes the code after that change. Items still missing are called out as remaining gaps.

## Architecture

School ERP is a separate product from retail shop-management.

| Layer | What it is |
|---|---|
| UI | Angular 19 standalone components in `apps/school-ui`. Shell, `authGuard`, `featureGuard`. |
| Gateway | Shared SugamFlow gateway. School UI calls `/api/...`. |
| Identity | Shared `auth-service` / `user-service`. School routes also check plan features and roles. |
| Domain services | Spring Boot services under `services/` (academic structure, student, exam, fee, report builder, and others). |
| Data | PostgreSQL per school service, Flyway migrations. Tenant scope is organization, branch, and academic session (`TenantContext`). |
| Design | App shell plus feature SCSS. Shared list pieces: `sf-list-toolbar`, `sf-list-pager`, `sf-student-lookup`. Tokens such as `--sf-border` and `--sf-radius`. No second component library. |

`school-ui` has `ng build` and `scripts/smoke-routes.mjs`. It has no unit-test script.

## Routes and components relevant to the reference screens

| Area | Route | Code |
|---|---|---|
| Exam / gradebook | `/admin/exam` (`FEATURE_EXAM`) | `features/exam/exam.component.*`, `exam-bulk.component.*` |
| Teacher gradebook | `/teacher/gradebook` | `features/portals/teacher-gradebook.component.*` |
| Report cards | `/teacher/report-cards`, `/parent/report-cards` | portal report-card components |
| ID / admit / transfer certificate | `/admin/reports?template=...` | `features/report-builder`, `report-builder-service` templates `id_card`, `admit_card`, `transfer_certificate` |
| Academic structure | `/admin/academic` | `features/academic` |
| Students | `/admin/students` | `features/students` |
| Promotion | `/admin/lifecycle` | `features/lifecycle`, `POST /api/student/lifecycle/promote` and `promote-by-class` |
| Fees | `/admin/fee`, `/admin/fee-desk` | `features/fee`. Fee heads are form/catalog values, not a particulars master screen. |

Nav source: `apps/school-ui/src/app/layout/nav-catalog.ts`. Exam is one Academics item, **Exam / Gradebook**.

## Feature status

| Feature | Status |
|---|---|
| Class, section, subject, exam definition | Implemented. Section list is filtered by class. Subject list is filtered by section assignments. |
| Subject-wise marks for one section | Implemented. One exam definition is one subject for one section. `GET /api/exam/gradebook`, `PUT /api/exam/gradebook/bulk`. |
| Marks vs maximum | Implemented on client (`markError`) and server (`GradebookService.bulkSave`). Locked statuses are read-only. |
| Admission-number filter on class entry | Implemented in this branch as an optional search field. It filters the loaded rows (name, roll, admission). It does not call a separate student API. |
| Explicit Search / Reset before load | Implemented in this branch. Catalog load no longer picks the first class. |
| Student-wise grid of every subject for one exam name | Missing. Individual entry is one workflow record (`exam_marks`) per student and subject. |
| Result print formats (grade-only, CBSE term, class rank) | Not separate pages. Report cards and the report designer cover published output. |
| Certificates of Appreciation | Missing. No entity, API, list, or print template. |
| Transfer / ID / admit cards | Implemented as report templates, not as the appreciation-certificate desk. |
| Fee particulars master (name, mode, edit, delete) | Partial. Fee collection uses fee-head keys. There is no particulars table matching the screenshot. |
| Promote students (from class/section/session to class/section/session) | Partial. Lifecycle can promote one student or a class using the next-class map. It is not the SSMS from/to session form. |

## UI and workflow notes

- Class marks previously auto-selected the first class, section, subject, and exam, then loaded the gradebook. A failed reload cleared the grid, including unsaved marks. This branch stops that auto-load, requires Search, and keeps unsaved rows when a reload fails.
- Entry-board **Open** still passes `?definition=` and loads that exam. Creating an exam still loads its empty gradebook. Both are explicit actions.
- Individual marks use `sf-student-lookup` and a workflow inbox with `sf-list-toolbar` / `sf-list-pager`.
- Destructive grid actions (clear all, submit) already use the in-page dialog. Reset uses the same dialog when the sheet is dirty.
- Responsive layout of the marks table was not measured in a browser for this document. `.selector-grid` and `.toolbar` already wrap.

## API and data model

- Exam definitions and marks: `exam-service`, tables extended by `V7__marks_entry.sql` (`marks_obtained`, `max_marks`, `passing_marks`, audit).
- Gradebook writes require staff write access, subject access, a writable exam status, and `expectedUpdatedAt`. Teachers are limited to their section and subject.
- `ExamRecordService` checks `FEATURE_EXAM` for the workflow inbox. The gradebook UI route is also behind `featureGuard` for `FEATURE_EXAM`.
- Appreciation certificates would need a new record model and print template. Not added.
- No schema or API change in this branch.

## Security

No P0 issue was confirmed in this read.

- Hiding a button is not the only gate: `bulkSave` calls `PersonaRoles.requireStaffWrite`, `requireSubjectAccess`, and `assertWritable`.
- Tenant scope comes from `TenantContext.require()`, not from a client-supplied school id in the gradebook body.
- Marks over the exam maximum are rejected in `GradebookService` before save.
- Appreciation-certificate authorization cannot be assessed because the feature does not exist.
- This pass did not re-test CSRF, gateway auth, or audit log contents at runtime.

## Gap matrix

| Area | Reference behavior | SugamFlow current behavior | Gap | Priority | Proposed improvement |
|---|---|---|---|---|---|
| Navigation | Result and certificate groups in the sidebar, including many result print links | One **Exam / Gradebook** item. Report designer links for ID, admit, and transfer certificates. | Result print formats and appreciation certificates are not nav items. | P2 for print-format nav. P1 for appreciation certificates (no module). | Keep one gradebook entry. Add appreciation certificates only after a data model exists. |
| Filters | Class, section, admission number, exam, Search, Reset, and a prompt until filters are set | Class entry: class, dependent section, dependent subject, exam, optional admission number, Search, Reset. Incomplete search shows an error. | Student-wise page in the screenshot has no subject filter because that screen is per exam name, not per subject definition. | P1 was the missing Search gate. Remaining: no academic-year filter on this form (session comes from tenant context). | Done for subject-wise class entry. Do not add a year dropdown unless the API accepts one. |
| Results | Subject-wise and student-wise mark update, including several print layouts | Subject-wise section grid with save draft, submit, lock, import. Student-wise all-subjects grid is absent. | All-subjects student update and SSMS print layouts. | P1 for the all-subjects grid (needs a composed read across definitions, not done here). P2 for print layouts. | Keep one definition per subject. A later change can compose definitions that share a name and section. |
| Certificates | Appreciation list: student, class, issue date, search, class filter, pagination | No appreciation records. Transfer certificates are a report template. | Whole appreciation workflow. | P1 | New table, API, and list only when product confirms wording and who may issue them. |
| Actions | Print, edit, delete on certificate rows | Marks: edit cells, save, submit, correction request. No certificate row actions. | Certificate print/edit/delete. | P1, blocked on the missing module. | Do not fake print output. |
| Tables | Columns, record count, pager | Class grid is the full section roster (directory cap 500). Individual inbox is paged. | Class grid has no pager. Not confirmed as a defect at current section sizes. | P3 | Add paging only if a section exceeds a measured limit. |
| Feedback | Required-filter message, success and error after save | Search validates required filters. Save and load errors use the existing alert. Failed reload keeps unsaved rows. Reset confirms when dirty. | Toast component is not used; status is an inline panel. | P3 | Keep inline status. |
| Responsive UI | Stacked filters and a usable table on a narrow screen | Filter row uses `flex-wrap`. Table sits in `.table-wrap`. | Not verified in a running browser at 390px or 768px. | P2 if a later check shows overflow. | Verify before changing layout. |

## This branch

`apps/school-ui/src/app/features/exam/exam-bulk.component.ts` and `.html`:

- Placeholders instead of auto-selecting the first class.
- Section disabled until a class is chosen; changing class clears section, subject, and exam.
- Subject disabled until a section is chosen; exam limited to that section and subject.
- Search loads `GET /api/exam/gradebook` once. The same exam does not refetch when nothing is unsaved.
- Reset clears filters and the sheet, with a confirm dialog when marks differ from the last load.
- A failed reload does not clear unsaved rows.

No certificate, fee-particular, or promotion screen was added.
