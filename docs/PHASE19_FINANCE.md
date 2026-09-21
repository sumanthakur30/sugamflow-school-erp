# Phase 19 — Finance depth

Config-driven fee masters and payment intents on top of the existing Fee Collection
vertical slice. No school-specific ledgers in code — definitions are JSONB
configuration versioned per tenant.

## Feature flags

| Flag | Role |
|---|---|
| `FEATURE_FEE` | Required for all finance APIs and UI |
| `FEATURE_MULTI_PAYMENT_GATEWAY` | Payment intent + simulate capture |
| `FEATURE_ACCOUNTING` | Reserved (bootstrap exposes `accountingEnabled`) |

## Module settings (`fee`)

| Key | Default |
|---|---|
| `financeMastersEnabled` | `true` |
| `defaultStructureKey` | `grade_8_annual` |
| `defaultCurrency` | `INR` |

## APIs (`fee-service` `:8190`)

| Method | Path |
|---|---|
| GET | `/api/fee/finance/bootstrap` |
| GET/PUT | `/api/fee/finance/heads`, `/heads/{key}` |
| GET/PUT | `/api/fee/finance/structures`, `/structures/{key}` |
| GET/PUT | `/api/fee/finance/concessions`, `/concessions/{key}` |
| POST | `/api/fee/finance/demands/preview` |
| POST | `/api/fee/finance/payments/intents` |
| POST | `/api/fee/finance/payments/intents/{id}/simulate-capture` |
| GET | `/api/fee/finance/transactions` |

Existing `/api/fee/collections/**` unchanged. Fee bootstrap now embeds a `finance`
catalog when available.

## Defaults seeded per org

- Heads: TUITION, TRANSPORT, LIBRARY, EXAM
- Structure: `grade_8_annual` (Tuition 12000 + Library 500 + Exam 800)
- Concession: `sibling_10` (10% on TUITION, cap 2000)
- Provider: `simulated` (UPI/CARD/NETBANKING)

## UI

Admin → **Finance** (`/admin/finance`): heads editor, structure/concession browse,
demand preview, payment intent (when gateway flag on), transaction list.

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-finance.ps1
```

Also keep `verify-fee.ps1` green for the collection slice.
