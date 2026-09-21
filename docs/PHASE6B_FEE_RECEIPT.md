# Phase 6b — Fee receipt PDF + notification delivery

On **final fee approve**, fee-service renders a template-driven receipt PDF and delivers
configured notifications (same pattern as admission Phase 5b).

| Concern | Source of truth |
|---|---|
| Receipt layout | Report Builder · `fee_receipt` |
| Email/SMS copy | Notification Config · `fee_approved` |
| Knobs | Module Settings · `fee` |
| Runtime | **fee-service** · `GET /api/fee/collections/{id}/receipt` |

## Config knobs

| Key | Default |
|---|---|
| `feeReceiptTemplateKey` | `fee_receipt` |
| `notifyOnApprove` | `true` |
| `approveNotificationChannels` | `EMAIL`, `IN_APP` |
| `approveNotificationTemplateId` | `fee_approved` |

## Verify

```powershell
.\scripts\verify-fee.ps1
```

Expect `hasFeeReceipt=true`, PDF magic `%PDF`, and `FEE_APPROVED` delivery rows.
