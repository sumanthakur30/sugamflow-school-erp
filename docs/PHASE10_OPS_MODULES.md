# Phase 10 — Ops modules: Library, Hostel, Transport, Payroll (same pattern as Exam / Attendance)

Config engines drive behaviour; each service (`library-service`, `hostel-service`, `transport-service`,
`payroll-service`) only orchestrates records — no school-specific business logic is hardcoded.

## Module matrix

| Module | Service | Port | Feature flag | Form key | Workflow key | Rule context | DB |
|---|---|---|---|---|---|---|---|
| Library | `library-service` | `8194` | `FEATURE_LIBRARY` | `library_issue` | `library` | `library.*` | `school_library_db` |
| Hostel | `hostel-service` | `8195` | `FEATURE_HOSTEL` | `hostel_allocation` | `hostel` | `hostel.*` | `school_hostel_db` |
| Transport | `transport-service` | `8196` | `FEATURE_TRANSPORT` | `transport_route` | `transport` | `transport.*` | `school_transport_db` |
| Payroll | `payroll-service` | `8197` | `FEATURE_PAYROLL` | `payroll_run` | `payroll` | `payroll.*` | `school_payroll_db` |

Package base: `com.sugamflow.school.{library|hostel|transport|payroll}`. Eureka names match the
service artifact ids above. Entity tables: `{module}_record` (same columns as `exam_record` /
`attendance_record`: `id`, `organization_id`, `form_key`, `workflow_key`, `status`, `answers`,
`history`, `matched_actions`, `notification_intents`, timestamps).

## APIs (identical shape per module)

| Method | Path |
|---|---|
| GET | `/api/{module}/bootstrap` |
| GET | `/api/{module}/records` |
| GET | `/api/{module}/records/{id}` |
| POST | `/api/{module}/records` |
| POST | `/api/{module}/records/{id}/actions` (`APPROVE`\|`REJECT`\|`REQUEST_INFO`) |

On final `APPROVE` each service emits a `{MODULE}_APPROVED` notification intent
(`LIBRARY_APPROVED`, `HOSTEL_APPROVED`, `TRANSPORT_APPROVED`, `PAYROLL_APPROVED`) and records
delivery via the notification-delivery integration, same as exam/attendance.

## Forms (Form Builder seeds)

| Form key | Fields |
|---|---|
| `library_issue` | studentName, admissionNo, bookTitle, bookId, issueDate, dueDate, email, mobile |
| `hostel_allocation` | studentName, admissionNo, roomNo, bedNo (NUMBER), hostelBlock, startDate, pendingFee (NUMBER), email, mobile |
| `transport_route` | studentName, admissionNo, routeName, stopName, vehicleNo, pickupTime, distanceKm (NUMBER), email, mobile |
| `payroll_run` | employeeName, employeeId, month, basicPay, allowances, deductions, netPay (all NUMBER except month), email, mobile |

## Workflows (Workflow Builder seeds)

Each module: 3-step Staff → Approver → Completed (SYSTEM), mirroring the exam/fee pattern.

| Workflow key | Steps |
|---|---|
| `library` | `LIBRARY_STAFF` → `LIBRARIAN` → Completed |
| `hostel` | `HOSTEL_STAFF` → `WARDEN` → Completed |
| `transport` | `TRANSPORT_STAFF` → `TRANSPORT_MANAGER` → Completed |
| `payroll` | `PAYROLL_STAFF` → `ACCOUNTANT` → Completed |

## Rules (platform seeds)

| Rule | Condition | Action |
|---|---|---|
| `library_bookid_blank` | `library.bookId` == `""` | `BLOCK_LIBRARY` |
| `library_duedays_negative` | `library.dueDays` < 0 | `BLOCK_LIBRARY` |
| `library_duedays_overflow` | `library.dueDays` > 30 | `NOTIFY_LIBRARY` |
| `hostel_bedno_min` | `hostel.bedNo` < 1 | `BLOCK_HOSTEL` |
| `hostel_pending_fee_notify` | `hostel.pendingFee` > 0 | `NOTIFY_HOSTEL` |
| `transport_distance_negative` | `transport.distanceKm` < 0 | `BLOCK_TRANSPORT` |
| `transport_distance_overflow` | `transport.distanceKm` > 50 | `NOTIFY_TRANSPORT` |
| `payroll_netpay_negative` | `payroll.netPay` < 0 | `BLOCK_PAYROLL` |
| `payroll_netpay_low_notify` | `payroll.netPay` < 5000 | `NOTIFY_PAYROLL` |

`dueDays` is not a declared `library_issue` form field; it is an optional extra answer key that
the rule engine can evaluate if a caller supplies it (the submit endpoint does not restrict
answers to declared form fields). The blank `bookId` and negative `distanceKm`/`netPay`/`bedNo`
checks cover the mandatory-field cases exercised by the verify scripts.

## Module settings (School Settings seeds)

| Module key | formKey | workflowKey | requiredFeatureFlag | approveNotificationTemplateId |
|---|---|---|---|---|
| `library` | `library_issue` | `library` | `FEATURE_LIBRARY` | `library_approved` |
| `hostel` | `hostel_allocation` | `hostel` | `FEATURE_HOSTEL` | `hostel_approved` |
| `transport` | `transport_route` | `transport` | `FEATURE_TRANSPORT` | `transport_approved` |
| `payroll` | `payroll_run` | `payroll` | `FEATURE_PAYROLL` | `payroll_approved` |

## Subscription

`FEATURE_LIBRARY`, `FEATURE_HOSTEL`, `FEATURE_TRANSPORT`, `FEATURE_PAYROLL` are enabled on the
`starter` plan and backfilled onto every existing plan by `SubscriptionPlanSeeder`.

## Notification templates

`library_approved`, `hostel_approved`, `transport_approved`, `payroll_approved` are seeded
platform templates. `NotificationController.EVENTS` was extended with `LIBRARY`, `HOSTEL`,
`PAYROLL` (`TRANSPORT` already existed).

## UI (school-ui)

Angular features under `apps/school-ui/src/app/features/{library|hostel|transport|payroll}`,
each mirroring the exam feature (bootstrap → dynamic form → submit → inbox/detail → approve
action → notification delivery). Routes: `admin/library`, `admin/hostel`, `admin/transport`,
`admin/payroll`. Nav labels: Library, Hostel, Transport, Payroll.

## Gateway

`D:\sugamflow\gateway-service` routes 44-47 forward `/api/library`, `/api/hostel`,
`/api/transport`, `/api/payroll` to the corresponding Eureka service names.
`RequestIdGatewayFilter.isSchoolErpPath` was extended to recognise the four new path prefixes.

## Verify

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
.\scripts\start-platform.ps1
.\scripts\start-services.ps1 -Restart
.\scripts\verify-library.ps1
.\scripts\verify-hostel.ps1
.\scripts\verify-transport.ps1
.\scripts\verify-payroll.ps1
```

Demo login: `demo-school` / `admin` / `password`.
