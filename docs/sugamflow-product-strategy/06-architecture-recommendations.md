# 06 — Architecture recommendations

Research date: 21 November 2026.

## Decision

Keep the current shape. Do not start a new platform, and do not collapse every school service into one deployable on a single afternoon. The next architectural job is to make the system already written reliable: one release line, strict tenant filters, and fewer services that are only a flag.

## What is already right

- Angular UI, gateway, JWT, Eureka, and PostgreSQL are a normal and maintainable stack for this stage.
- School domain services are separated by the words staff already use: student, fee, exam, attendance. That matches how a bug is found.
- Shared identity with the rest of SugamFlow avoids a second user table.
- Feature flags are the commercial switch. School settings are the operational switch. They are different and should stay different.
- Fee versions and demand snapshots are the correct data design for money.

## What is hurting

- **Branch drift.** `dev`, `dest`, `model`, and `main` are not the same product. Cherry-picks drop or keep behaviour by accident. A customer bug fix must have one home.
- **Service count versus team size.** More than twenty school JVMs plus the platform is a heavy layout for one EC2. The production host is already memory-constrained. New services need a reason other than a new noun.
- **Flags without modules.** Inventory, face recognition, and GPS-as-attendance are catalog keys. They create sales risk.
- **JSON student answers.** Good for a form builder. Risky for board export and for any future model prompt. Official fields need a known list.
- **Read models.** Ask, dashboards, and reports each query differently. A small set of named read APIs would stop every screen from inventing its own totals.

## Target, still boring on purpose

```text
Browser (admin, parent, teacher)
        │
     Gateway ── JWT, tenant, trace id
        │
 Named school APIs (existing services)
        │
 PostgreSQL, one database per product, row scoped by organization
        │
 Object storage for documents
        │
 A job runner for reminders, imports, and later OCR
        │
 Optional model adapter, called only by named tools
```

Recommendation: one Postgres database for the school product, with organization id on every row, is enough until a paying school has a contractual or load reason to move. A database per school multiplies backups and migrations before it improves safety. Row scope plus tests is the present control. Say so in the security paper, and revisit when a large group asks for a dedicated database.

Background work (fee reminders, bulk import, document scans) should not run inside the request that drew the page. A single queue is enough. Do not add Kafka for a reminder.

Caching belongs on entitlements and on read-only class lists. It does not belong on a fee balance unless the invalidation story is tested.

## Migration, not a rewrite

| Keep | Change gradually | Do not do |
|---|---|---|
| Services that own a real table | Merge a service only when it has no independent data and no one deploys it alone | A new microservice for “AI” |
| Gateway and auth | One supported branch and one image set per release | A second auth service for parents |
| Feature flags | Delete or hide flags that have no code | A new subscription product |
| Fee definitions | Add installment and refund as new transactions that reference the old receipt | Recalculating history |
| Parent and teacher routes | A phone layout of those routes | A separate mobile codebase in the same quarter as the fee gaps |

## Performance and availability

The current school host cannot also run a large local model, a log stack, and every module JVM. AI calls, if approved, go to an external or separate machine. Product JVMs stay small. Backups of PostgreSQL and of document storage are an operations task with a restore drill, not a feature flag named cloud backup.

High availability is a second host and a tested restore. It is not Kubernetes. Add it when there is more than one production school that cannot share a short outage window, or when the contract says so.

## API rule for new work

Every new school endpoint states its tenant, its role, and whether it writes money or marks. Writes of money are idempotent. Reads used by Ask are the same reads used by the screen, so the assistant cannot see a private row the screen cannot.
