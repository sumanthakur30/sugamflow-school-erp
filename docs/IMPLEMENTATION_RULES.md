# SugamFlow School ERP — Implementation Rules

**Philosophy:** Configuration > Customization > Development  
**Target:** ≥95% of school requirements solved through configuration; &lt;5% requires coding.

## Mandatory rules

1. **Configuration over Customization over Coding** — prefer Admin Settings / Design Studio over code changes.
2. **No school-specific Java or Angular code** — differences between schools are configuration only.
3. **Every feature must support Feature Flags** — gated by subscription plan and org/branch settings.
4. **Every screen must support dynamic field configuration** — hide/show, mandatory, readonly, order, role/branch visibility.
5. **Every report must be template-driven** — Design Studio / Report Builder; no hardcoded layouts.
6. **Every workflow must be configurable** — Workflow Builder for steps, SLA, escalation, notifications.
7. **Every business rule must use the Rule Engine** — no hardcoded IF/THEN in domain services.
8. **Every subscription must control features through configuration** — limits and modules via plan metadata.
9. **Org / branch / academic session independent settings** — hierarchical config with override + inheritance.
10. **White-label without code changes** — branding, domain, login, and theming via Design Studio.
11. **UI components metadata-driven** where feasible — forms, menus, dashboards, widgets from API.
12. **Horizontal scalability and multi-tenancy from day one** — tenant isolation on every config and business entity.
13. **Maximize reuse of shared services** — school UI and school microservices are separate; share contracts and gateway patterns.
14. **Audit every configuration change** — who, old/new value, reason, timestamp, rollback, approval.

## Architectural boundaries

| Surface | Technology | Responsibility |
|---|---|---|
| School Management UI | Angular (`apps/school-ui`) | Separate app for school admins/staff/parents/students portals as routed experiences |
| School microservices | Spring Boot (`services/*`) | Config, subscription, forms, workflows, rules, reports, notifications, audit |
| API Gateway | Spring Cloud Gateway | Auth, tenant context, routing to school services |

## Definition of done for any feature

- [ ] Configurable from Admin Settings or Design Studio
- [ ] Feature-flagged and subscription-gated
- [ ] Org / branch / session scoped
- [ ] Audited with rollback support
- [ ] No school-specific branches in source
- [ ] Contract documented in `shared/contracts`
