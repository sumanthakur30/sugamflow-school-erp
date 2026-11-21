# 05 — AI transformation strategy

Research date: 21 November 2026.

## The honest starting point

SugamFlow is not an AI product today. The pieces that exist are:

- **Ask the school.** English keywords. “Fee” calls the defaulter API and this month’s income-expense API. “Attendance” calls attendance. Anything else returns a fixed sentence. The screen says it cannot post a receipt or change marks. Verified in `ask-school.component.ts`.
- **AI configuration.** A page of boolean flags stored through `/api/config/ui/ai`.
- **Compliance scan.** A heuristic job. The code path can attach an external tip and label the provider `heuristic+llm`.
- **Website draft.** A local template, with an optional OpenAI-compatible call. The copy is for an editor to change before publish.

That is the right shape for a first step: read-only, school-scoped, and a person still publishes. It is not a strategy.

## What to build, in order

### 1. A query layer, not a chatbot

The questions schools ask are repetitive: today’s collection, dues, low attendance, missing marks, incomplete admission files. Answer them with ordinary queries that already enforce tenant, campus, session, and role. Put those queries behind one internal API the Ask screen and, later, a model can call.

Do this before any general model. A wrong SQL string from a model is a data leak. A named query is testable.

PowerSchool’s PowerBuddy, documented in March 2026, does a similar thing for reports a staff member may already open. Copy that boundary, not a vendor’s brand.

### 2. Fee intelligence without automatic penalties

Use the fee tables SugamFlow already has.

- Deterministic: dues by class, unpaid count, collection today versus the same day last week, a reminder list.
- Conventional analytics: a simple forecast from last year’s monthly pattern. Label it as a forecast.
- Not allowed: an automatic late fee, waiver, or parent message.

A human sends the reminder. The product may draft it.

### 3. Academic help a teacher can throw away

- Class summary after marks are saved: average, who is far below the class, which subject. Numbers from the gradebook, not a model’s memory.
- A draft question paper or worksheet that is clearly a draft.
- No automatic promotion, no hidden “risk score” stored on the student, no parent-facing label such as “weak child”.

Alma’s public BeaconAI page is the tone to copy: show a pattern, let people act.

### 4. Administration drafts

Notices, parent messages, and admission replies in Hindi and English. The user edits and sends. Store the prompt name, the user, the time, and the fact that a person sent it. Do not store the whole student file in the prompt.

### 5. Documents

OCR and extraction can pre-fill an admission form from a scanned file. Nothing is official until a clerk saves it. This matches the rule already used for “Other” values on the student form: store what the person confirmed, not the placeholder word.

### 6. A model, last

Only after the query tools exist:

- The model may call those tools. It may not receive a raw export of the school.
- The tool checks the same permission as the screen.
- Each school is a separate tenant in retrieval. There is no shared vector index of student essays.
- Prompts are versioned. Answers that change money or marks are refused.
- Usage is counted per school so a plan can cap it.
- Logs store the tool name and the user, not a copy of Aadhaar, phone, or health notes.
- Student rows are not training data for a public model.

## Which engine for which job

| Job | Engine | Why |
|---|---|---|
| Dues, headcount, missing marks | Deterministic query | Must match the screen |
| “Compared with last month” | SQL or a small aggregate | Cheap and explainable |
| Draft a notice or translate it | LLM | Language is the hard part |
| Read a scanned admission form | Document model, then human confirm | Errors are expected |
| Predict who will not pay | Optional later, and only as a list for a clerk | Easy to misuse |
| Decide promotion, discipline, or a fee waiver | Never a model | High impact on a child or on money |

## Providers (recommendation)

Do not bind the product to one vendor in the first design. An OpenAI-compatible HTTP API is already the pattern in the CMS assistant. Keep that adapter, add a local or India-region option later if a school contract requires it, and keep a no-model mode that only runs the deterministic queries.

Send the smallest text that answers the task. A fee question needs totals and admission numbers the user already sees, not the student’s documents.

## Cost

Assumption: a small school’s deterministic Ask screen costs almost nothing. LLM cost becomes real when every parent message is generated or every document is OCR’d. Cap by plan. Show the school its usage. Do not hide a metered model inside an unlimited “AI” checkbox.

## What not to claim in a sales call

Do not say SugamFlow is AI-powered because the flag page exists. Say: the product can answer fee and attendance questions from your records, and drafts stay in the editor until you send them. Add model language only after the tool-calling path is in production for one pilot school.
