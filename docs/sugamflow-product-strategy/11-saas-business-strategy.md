# 11 — SaaS business strategy

Research date: 21 November 2026. Competitor rupee prices are not stated. Public pages reviewed did not give a rate card that this document is willing to copy. Entab’s own page describes a demo and a price for the school. Teachmint and Fedena pages read here did not publish a list price.

## Who pays first

| Segment | Need | What to sell | What not to sell yet |
|---|---|---|---|
| Small school, one campus, limited staff | Fees, attendance, report cards, parent messages | Core plan | AI, GPS, hostel, a ledger |
| Medium school | The core, plus library or transport, more staff roles | Core plus one operations module | A custom build |
| Large or multi-campus | Campuses, roles, payroll, compliance export | Core plus campus and the modules they use | Unlimited custom fields without an export plan |
| Group or chain | Several schools, one owner | Separate tenants, one owner login | A shared student database across schools |

Coaching and college presets can remain. They are not the first sales story.

## Packaging (recommendation)

Use the feature flags that already exist. Do not invent a second plan editor.

**Standard, every paying school**

- Students, classes, attendance, exams and report cards, fee structure and collection, receipts, dues, parent and teacher web, notices, Hindi and English on the daily screens.

**Premium, switched by flag**

- Extra campuses, payroll depth, transport, library, hostel, website and CMS, compliance packs, payment gateway if it has a cost, and AI drafts with a usage cap.

**Not a plan item until it exists**

- Face recognition, inventory, general ledger, GPS attendance, “AI-powered ERP” as a blanket phrase.

A basic school must not click through premium setup to collect a fee. The simple nav already points at the daily work. Keep that.

## Price shape, without fake numbers

Recommendation, to be priced from SugamFlow’s own cost and from a sales conversation:

- A base annual or monthly fee for the standard set, sized by student bands, because the per-student billing controller already exists.
- A module fee for each premium flag the school turns on.
- A metered AI allowance later, shown in the product, so a noisy month is not a surprise.
- Implementation as a paid service: import students, one fee structure, one training morning. Do not hide that cost inside an “unlimited” plan.

Do not copy Teachpay’s credit or EMI product into the standard plan. That is a lending workflow with KYC, from Teachmint’s own FAQ, and it is a different business.

## Trial and onboarding

- A trial school with sample data clearly marked, or a real school limited to one campus and a student cap.
- The first session ends when the school has saved classes, one fee structure, and one receipt. That is the activation event.
- Import from a spreadsheet is the migration service. Do not promise Tally sync until a mapper exists. Tally was not found in this codebase.

## Retention

Schools leave when fees do not match the register, when parents cannot see a receipt, or when a year rollover frightens the accountant. Retention work is Phase 1, not a newsletter.

Upgrade when the school adds a campus, a bus, or a hostel, or when they ask for drafts in Hindi. Do not upgrade them by turning on every flag.

## Position

“The school system that keeps last year’s receipts true, and that a parent can open on a phone.”

Not: “100 modules and an AI chatbot.”

## Support

A WhatsApp or phone path for fee and login issues during admission season and fee week. An in-app ticket already exists and can be the record. The person who answers must be able to see the tenant without a shared password. If impersonation is added, it writes an audit row.

## Marketing

Show one school day: admit, mark attendance, collect, print. Offer the standard set. Mention AI only after Phase 3 is in production for a named pilot. Website CMS is a separate conversation for schools that want a public site.
