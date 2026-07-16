# Phase 12 — Drag-drop Report / Certificate Designer (richer)

Turns the report template list into a real canvas designer: palette → drop → position →
bind → save → PDF preview. Templates remain JSON configuration; PDF render is
layout-driven (no school-specific hardcoding).

## Feature flag

`FEATURE_REPORT_BUILDER` (seeded onto starter-tier plans via `SubscriptionPlanSeeder`).

## APIs

| Method | Path | Notes |
|---|---|---|
| GET | `/api/reports/bootstrap` | Flag, palette `elementTypes`, sample data, templates |
| GET | `/api/reports/element-types` | Designer palette metadata |
| GET/PUT | `/api/reports/templates/{key}` | Load / save (elements normalized with ids) |
| POST | `/api/reports/templates/{key}/render` | PDF from saved template |
| POST | `/api/reports/preview` | PDF from unsaved canvas JSON |

## Element types

`heading`, `text`, `field`, `line`, `box`, `image` — each with `id`, `x`, `y`, `width`,
`height`, optional `text` / `bind` / `fontSize` / `align` / `bold`.

Canvas coordinates use layout `794×1123` px (A4 @ ~96dpi). PDF render scales onto A4
points and places elements absolutely (top-left origin in designer).

## UI

`apps/school-ui` → `/admin/reports`

- Template list + palette (drag)
- Zoomable canvas (drop + pointer drag to move)
- Property inspector
- Save / Preview PDF / Render saved

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-report-designer.ps1
```

Requires gateway `:9090`, subscription `:8182`, report-builder `:8186`.
