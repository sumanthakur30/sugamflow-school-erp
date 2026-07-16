# Phase 5 — Admission vertical slice

First domain module wired through **config engines** (no school-specific business rules in code).

## What it proves

| Concern | Source of truth |
|---|---|
| Fields on the application | Form Builder · `admission_form` |
| Approval steps / roles / SLA metadata | Workflow Builder · `admission` |
| Age / docs gates | Rule Engine · `admission_*` rules → `BLOCK_ADMISSION` / `NOTIFY_ADMISSION` |
| Module on/off + form/workflow keys | Module Settings · `admission` |
| Feature availability | Subscription · `FEATURE_ADMISSION` |
| Notification intents + delivery | Notification config templates · event `ADMISSION` → platform `notification-service` |
| Offer letter PDF | Report Builder · `offer_letter` (rendered on final approve) |
| Runtime store + advance | **admission-service** `:8189` · `/api/admission/**` |

`admission-service` only orchestrates. Changing fields, steps, or rules does **not** require Java/Angular school-specific forks.

See also [PHASE5B_OFFER_LETTER.md](PHASE5B_OFFER_LETTER.md).

## APIs

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admission/bootstrap` | Form + workflow + module + flag check |
| GET | `/api/admission/applications` | Inbox for current org |
| GET | `/api/admission/applications/{id}` | Detail + history |
| POST | `/api/admission/applications` | Submit answers JSON |
| POST | `/api/admission/applications/{id}/actions` | `APPROVE` / `REJECT` / `REQUEST_INFO` |
| GET | `/api/admission/applications/{id}/offer-letter` | Download rendered offer letter PDF |

## Start

```powershell
cd D:\school
.\infra\postgres\init-local.ps1          # creates school_admission_db if missing
.\scripts\start-platform.ps1             # rebuilds gateway with /api/admission route
.\scripts\start-services.ps1 -Restart    # includes admission-service :8189
.\scripts\verify-admission.ps1
```

UI: `cd apps\school-ui; npm start` → **Admission** after login.

## Demo credentials

Same as Phase 4: org `demo-school`, user `admin`, password `password`.

## Config knobs (no code)

1. **Subscription** — toggle `FEATURE_ADMISSION` on the plan.
2. **Module Settings → admission** — `enabled`, `formKey`, `workflowKey`, `offerLetterTemplateKey`, `notifyOnApprove`, channels.
3. **Form Builder → admission_form** — add/remove fields (mandatory enforced at submit).
4. **Workflows → admission** — change step order / assignee roles.
5. **Rules** — e.g. `application.age LT 3 → BLOCK_ADMISSION`.
6. **Report Builder → offer_letter** — PDF layout / bind paths.
7. **Notification Config → admission_approved** — subject/body per channel.

## Out of scope (later)

- Email PDF attachments (platform `notification-service` is plain-text today — link used instead)
- Applicant public portal (unauthenticated apply link)
