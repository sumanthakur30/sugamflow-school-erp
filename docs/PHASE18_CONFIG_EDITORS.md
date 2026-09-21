# Phase 18 — Real config editors

List-detail editors for Form, Workflow, Rule, Menu, and Module Settings —
wired to existing CRUD APIs. No school-specific code; tenant overrides via
configuration.

## Feature flags

| Flag | Screen |
|---|---|
| `FEATURE_FORM_BUILDER` | `/admin/forms` |
| `FEATURE_WORKFLOW_BUILDER` | `/admin/workflows` |
| `FEATURE_RULE_ENGINE` | `/admin/rules` |
| `FEATURE_ADMIN_CONFIG` | `/admin/modules`, `/admin/menus` |

Seeder enables these on Starter/Basic/Standard (and higher) plans.

## Editors

| Screen | Capabilities |
|---|---|
| Form Builder | New/edit form; sections; field label/key/type/mandatory; reorder; Save (POST/PUT `/api/forms`) |
| Workflow Builder | New/edit workflow; steps (role, SLA, auto-approve); reorder; Save |
| Rule Engine | New/edit rule (when/then); evaluate JSON context |
| Menu Builder | Tree edit (root/child); roles/flags/branches CSV; Save whole tree |
| Module Settings | All setting keys by type (bool/number/string/string[]/JSON); add/remove keys |

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-config-editors.ps1
```

Requires gateway `:9090`, form/workflow/rule/settings/subscription services.
