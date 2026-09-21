# Phase 6 — Fee vertical slice (same pattern as Admission)

Config engines drive behaviour; `fee-service` only orchestrates collections.

| Concern | Source of truth |
|---|---|
| Collection fields | Form Builder · `fee_collection` |
| Approval steps | Workflow · `fee` (Cashier → Accounts → Completed) |
| Amount / overdue gates | Rule Engine · `payment.amount` / `payment.pendingDays` |
| Module keys | Module Settings · `fee` |
| Availability | Subscription · `FEATURE_FEE` |
| Runtime | **fee-service** `:8190` · `/api/fee/**` |

## APIs

| Method | Path |
|---|---|
| GET | `/api/fee/bootstrap` |
| GET | `/api/fee/collections` |
| GET | `/api/fee/collections/{id}` |
| POST | `/api/fee/collections` |
| POST | `/api/fee/collections/{id}/actions` |

## Verify

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
.\scripts\start-platform.ps1
.\scripts\start-services.ps1 -Restart
.\scripts\verify-fee.ps1
```

Demo login: `demo-school` / `admin` / `password`.
