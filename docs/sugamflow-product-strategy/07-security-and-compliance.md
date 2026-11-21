# 07 — Security and compliance

Research date: 21 November 2026. This is an engineering map. It is not a legal opinion and it does not say SugamFlow is compliant.

## What the product already does

Verified in code structure, not in a penetration test:

- Login and a bearer token on school APIs.
- Feature guards on routes.
- Tenant, branch, and session context.
- A privacy desk for student and parent requests, with a stated intent not to destroy academic history casually.
- Document verify-by-token as a public route. That route needs a review so a token cannot be guessed or reused forever.
- Audit service present.
- Fee history preserved when the structure changes.

Not established in this pass: MFA, encryption-at-rest evidence, a retention schedule, a breach runbook, a data-processing agreement, or a completed access review of every query.

## DPDP Act, 2023

Source: [Digital Personal Data Protection Act, 2023 (MeitY PDF)](https://www.meity.gov.in/static/uploads/2024/02/Digital-Personal-Data-Protection-Act-2023.pdf), read 21 November 2026.

Facts from the Act that matter to a school product:

- A child is a person who has not completed 18 years.
- Consent must be free, specific, informed, and unambiguous, and limited to the purpose.
- Consent can be withdrawn as easily as it was given.
- Before processing a child’s personal data, the fiduciary must obtain verifiable consent of the parent or lawful guardian, in the manner prescribed.
- Processing that is likely to harm a child’s well-being is not allowed.
- Tracking, behavioural monitoring of children, and targeted advertising directed at children are restricted, with room for classes and purposes the government may prescribe.
- The Act also provides other lawful bases and duties (security safeguards, purpose limitation, erasure, breach intimation). Those duties need a counsel to map onto a school and onto SugamFlow as a processor or fiduciary. This document does not make that call.

## DPDP Rules, 2025

Source: notification text dated 13 November 2025, G.S.R. 846(E), read from a public PDF on 21 November 2026. MeitY lists the rules as published 14 November 2025. Commencement is staggered: some rules on publication, Rule 4 after one year, and Rules 3, 5 to 16, 22 and 23 after eighteen months. **Confirm the dates with counsel before telling a customer a deadline.**

Rule 10, as read, requires technical and organisational measures so that the person giving consent for a child is an identifiable adult, using identity and age details the fiduciary already holds, or details or a virtual token from an authorised entity, including a Digital Locker provider. The illustration is an online account for a child. A school ERP is not that illustration. Counsel must say how far Rule 10 applies to a school that already holds admission forms signed by a parent.

## What SugamFlow should do anyway

These are product recommendations, stronger than “wait for a memo”, because they are also good school practice:

- Record who consented, for which child, for which purpose, and when. Store the purpose in words a parent can read.
- Separate purposes: running the school, collecting fees, sending a fee reminder, and sending a model-generated draft are not one purpose.
- Do not use student data to train a public model.
- Do not send documents, Aadhaar, health notes, or a full student dump to a model. Send the minimum text for a draft, after a person asks.
- Health and emergency fields, where they exist, should be visible to roles that need them on a trip or in a clinic, not to every teacher by default.
- A parent should be able to see what is held about their child. The privacy desk is the start, not the end.
- Retention: keep fee and exam records for the period the school and the law require, and do not keep a model prompt log of the same data for convenience.
- Breach: name an owner, a clock, and a customer notice path before the first paid AI feature.

## School and board rules

| Topic | What was found | Limit |
|---|---|---|
| UDISE+ | An export route exists | The government format changes. An export screen is not a filing |
| APAAR | Consent is mentioned in earlier school ID-card and lifecycle work | Not re-verified against the current APAAR spec in this pass |
| CBSE | A compliance module: profile, campaigns, disclosure, readiness | A dashboard is not an affiliation certificate |
| State boards | Not implemented as separate packs in the inventory | Do not claim state-board coverage |
| Accounting and tax | Fee receipts and an income-expense desk | GST and society accounting need an accountant. The software does not yet post a ledger |

## Security tests to require before AI

- A parent token cannot read another child’s fees.
- A teacher token cannot read payroll.
- Ask uses the same filter as the screen it summarizes.
- A forged or expired document token fails.
- Fee write endpoints reject a body that would change an old receipt.
- Feature flag off means the API rejects the call, not only the menu.

Specialist validation still required: DPDP role (fiduciary versus processor), Rule 10’s application to schools, board export formats, and any payment-aggregator obligations around Teachpay-like credit. SugamFlow should not copy that credit product casually.
