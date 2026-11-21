# Ledger posting from a fee receipt

This note shows how one fee receipt already saved on the fee desk would become one debit and one credit. It does not post those lines, and it does not rename the finance screen.

## The receipt that already exists

A parent pays ₹5,000 in cash for tuition. The fee desk keeps that as one collection row:

| Field | Value |
|---|---|
| Receipt | RCP-1042 |
| Student | Asha Kumar |
| Admission no | ADM-2026-MAIN-23D482 |
| Fee head | Tuition |
| Amount | ₹5,000 |
| Mode | CASH |

That row stays as it was saved. A later edit of the fee structure does not rewrite it.

## The two lines a ledger would add

The same receipt would add two lines that add up to the same amount:

| Account | Debit | Credit |
|---|---:|---:|
| Cash | 5,000 | |
| Tuition income | | 5,000 |

Cash increases because the school received the money. Tuition income increases because the fee was earned. The debit and the credit are equal, so the receipt is balanced.

An online payment uses the same pair, with Bank in place of Cash. A refund is a new row, so the original receipt stays as it was saved: debit Tuition income and credit Cash for the refunded amount.

## What this item does not do

The finance page keeps its current name and its current list of receipts. These two lines are the design for a later ledger. They are not stored, and there is no balance-sheet screen in this item.
