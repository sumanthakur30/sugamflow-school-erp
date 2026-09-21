# Feature Gap Analysis — SugamFlow vs Industry Platforms

Compared platforms: PowerSchool, Fedena, Entab CampusCare, MyClassCampus, Teachmint, EduSys, OpenEduCat, Classter, Skyward, Gradelink.

Legend: **P** = parity target, **D** = differentiator (SugamFlow ahead via config engines), **L** = later phase.

| Capability | Industry baseline | SugamFlow target | Priority |
|---|---|---|---|
| SIS / student master | All | Config-driven masters + Form Builder | P |
| Attendance | All | Rule Engine + AI Attendance flag | P |
| Fee & billing | Most | Template receipts + pending-fee rules | P |
| Exam / gradebook | Most | Localization grades + Report Card designer | P |
| Parent / teacher apps | Teachmint, PowerSchool, Classter | Feature-flagged apps per plan | P |
| Multi-branch / campus | Classter, PowerSchool, Entab | First-class branch config | P |
| White-label branding | Limited | Full Design Studio (colors, login, splash, reports) | D |
| Subscription / feature marketplace | SaaS vendors vary | Full Subscription Configuration Engine | D |
| Dynamic Form Builder | OpenEduCat (limited), Classter | Full field types + conditional visibility | D |
| Workflow / approvals | Enterprise ERPs | Visual Workflow Builder + SLA/escalation | D |
| Business Rule Engine | Rare in K-12 SaaS | IF/THEN Rule Engine for academics & ops | D |
| Drag-drop Report/Certificate designer | Entab (partial) | Template-driven for all official docs | D |
| Role-based dashboards & menus | Most (static) | Dynamic Menu + Role UI Config | D |
| Notification multi-channel | SMS/Email common; WA varies | SMS, WhatsApp, Email, Push, In-App, Voice, Telegram | P |
| AI features | Teachmint, newer suites | AI Config toggles + usage limits | D |
| Localization | PowerSchool, Classter | Locale, currency, paper size, grade systems | P |
| Config audit / rollback | Rare | Full version control + approval | D |
| Library / Hostel / Transport / Payroll | Fedena, OpenEduCat, Entab | Module flags + module settings pages | P |
| **Student / Staff Directory** | PowerSchool, Fedena, Entab, Teachmint | Phase 22 Global Directory (search, filters, summary, CSV, staff master) | P |
| Offline mode | Done (Phase 16) | Outbox + sync API + admin UI | — |
| Biometric / Face / GPS | Niche | Plan feature flags wired to adapters | L |

## Build order (practical)

1. **Foundation** — tenancy, gateway, config-service, subscription feature flags, audit  
2. **Design Studio + localization + menus** — white-label parity  
3. **Form Builder + UI component config** — eliminate school-specific forms  
4. **Workflow + Rule engines** — configurable ops  
5. **Report / Certificate designer + Notification engine**  
6. **Module settings pages + domain services** (admission, fee, exam, …)  
7. **AI config + advanced device integrations**

SugamFlow equals or exceeds the compared platforms where configuration engines replace custom code.
