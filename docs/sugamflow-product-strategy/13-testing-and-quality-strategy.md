# 13 — Testing and quality strategy

Research date: 21 November 2026.

## Rule

A module is done when its acceptance test in the backlog passes, not when a menu item appears. Tests use fictional schools and fictional children. Production student rows are not fixtures and not copies in documents.

## Layers

| Layer | What it catches | Where it lives |
|---|---|---|
| Unit | Fee plan math, discount limits, class-name matching | Service tests, as `StandardFeePlanTest` already does |
| API | Tenant filter, idempotent payment, history freeze | Service tests with two organizations |
| UI | Parent phone width, one attendance save, language switch | Browser pass on the school UI |
| Regression | The last fee, admission, and attendance bugs | A short list run before every school release |
| Security | Cross-tenant, cross-role, webhook signature, document token | API tests plus a review before AI |
| Performance | Fee list and attendance save with a full class | One scripted class of realistic size, not a production dump |
| Restore | A backup can become a running database | Operations drill on a spare database |

## Release smoke (every deploy)

1. Login as the school owner.
2. Open Students, Fees, Attendance, Exams.
3. Open a receipt and confirm the total.
4. Login as a parent test user and confirm only that child is listed.
5. Hit one disabled feature URL and confirm it is rejected.

## Phase tests

**Phase 0:** B-02 and B-03 automated.

**Phase 1:** B-05 through B-14. Payment test uses the gateway’s test mode. The webhook is posted twice.

**Phase 3:** Ask numbers equal screen numbers. A parent token asking for “all students” is rejected or limited to their child.

**Phase 4:** A generated draft has no send button that skips the editor. Logs for the test do not contain a full document.

## Data integrity

- Unique student for one admission number inside a school and year.
- Fee definition version increases. Previous version is not active.
- Demand lines do not change after a structure edit.
- Payment idempotency key prevents two receipts.

## What not to automate yet

Visual pixel diffs of the whole admin shell. They fail when copy is translated and they do not catch a wrong fee. Test the money and the permission.

## Quality gate

No school release with a red history test or a red tenant test. No AI release without the consent-purpose test. No sales claim for a module whose smoke step is missing.
