# Phase 16 — Offline mode

Config-driven **client outbox + server sync** for allow-listed mutations when the
network is unavailable. Gated by `FEATURE_OFFLINE_MODE`. No school-specific sync
logic — entity types and cache keys come from module settings (`offline`).

## Feature flag

| Flag | Role |
|---|---|
| `FEATURE_OFFLINE_MODE` | Enables bootstrap, sync, batches, and admin UI |

Per-entity flags still apply (`FEATURE_ATTENDANCE`, `FEATURE_FEE`, etc.) when
filtering allow-listed types.

## Module settings (`offline`)

| Key | Default | Role |
|---|---|---|
| `enabled` | `true` | Module master switch |
| `maxQueueSize` | `200` | Client localStorage cap |
| `syncBatchSize` | `25` | Max items per sync POST |
| `autoSyncOnReconnect` | `true` | UI auto-flush on `online` |
| `allowedEntityTypes` | attendance / fee / exam / library POSTs | Allow-list |
| `cacheKeys` | forms + modules + theme | Manifest for shell cache |

## APIs (`school-settings-service` `:8181`)

| Method | Path |
|---|---|
| GET | `/api/config/offline/bootstrap` |
| GET | `/api/config/offline/manifest` |
| POST | `/api/config/offline/sync` |
| GET | `/api/config/offline/batches` |

Sync forwards each allow-listed item through the public gateway
(`settings.integrations.public-api-base-url`, default `http://localhost:9090`)
with the caller's `Authorization` and tenant headers.

Item statuses: `SYNCED`, `FAILED`, `REJECTED`. Batch: `COMPLETED`, `PARTIAL`, `FAILED`.

## UI

Admin → **Offline Mode** (`/admin/offline`):

- Connectivity chip + local outbox (localStorage)
- Enqueue sample / Sync now / Clear
- Recent sync batches
- Optional shell service worker (`/offline-sw.js`) when feature is on

Shell rail shows Online/Offline and queued count.

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-offline.ps1
```

Requires gateway `:9090`, subscription `:8182`, settings `:8181`, and attendance
`:8192` (sample sync uses `ATTENDANCE_MARK`).
