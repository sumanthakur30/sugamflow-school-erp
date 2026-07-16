# School ERP — PostgreSQL (shared with SugamFlow)

School uses the **same host PostgreSQL** as SugamFlow — not a separate `school-postgres` container.

Reference: `D:/sugamflow/docs/LOCAL-POSTGRES.md`

## Connection

| Property | Value |
|---|---|
| Host (Windows tools / school JVM) | `localhost` |
| Host (SugamFlow Docker services) | `host.docker.internal` |
| Port | `5432` |
| Admin user | `postgres` |
| Admin password | `postgres` (change if your local install differs) |
| Admin database | `postgres` |
| Server | PostgreSQL 17 (`postgresql-x64-17`) |
| `psql` | `C:\Program Files\PostgreSQL\17\bin\psql.exe` |

## Create school databases

```powershell
cd D:\school
.\infra\postgres\init-local.ps1
```

If your postgres password is not `postgres`:

```powershell
.\infra\postgres\init-local.ps1 -AdminPassword 'YOUR_PASSWORD'
```

## Per-service databases

| Service | Database | User / Password |
|---|---|---|
| school-settings-service | `school_settings_db` | `school_settings` |
| subscription-service | `school_subscription_db` | `school_subscription` |
| form-builder-service | `school_forms_db` | `school_forms` |
| workflow-service | `school_workflow_db` | `school_workflow` |
| rule-engine-service | `school_rules_db` | `school_rules` |
| report-builder-service | `school_reports_db` | `school_reports` |
| school-notification-config-service | `school_notif_cfg_db` | `school_notif_cfg` |
| audit-service | `school_audit_db` | `school_audit` |

JDBC example: `jdbc:postgresql://localhost:5432/school_settings_db`

Full env template: `infra/postgres/connection.env.example`

## Why school Docker Postgres was removed

`school-postgres` on `:5432` conflicts with SugamFlow’s host PostgreSQL. Keep one server; create school DBs beside `shopdb`, `productdb`, etc.

## Cleanup failed school container (optional)

```powershell
docker rm -f school-postgres
docker volume rm school_pgdata
```

## Phase 1 — Datasource wiring (done)

Each school service `application.properties` now includes:

- `spring.datasource.url/username/password` (defaults to local school_* DBs)
- JPA (`ddl-auto=validate`)
- Flyway (`classpath:db/migration`, per-service history table)
- Actuator DB health

Example (`school-settings-service`):

```properties
spring.datasource.url=${SCHOOL_SETTINGS_DB_URL:jdbc:postgresql://localhost:5432/school_settings_db}
spring.datasource.username=${SCHOOL_SETTINGS_DB_USERNAME:school_settings}
spring.datasource.password=${SCHOOL_SETTINGS_DB_PASSWORD:school_settings}
spring.flyway.enabled=true
spring.flyway.table=flyway_schema_history_school_settings
```

Override via env vars if passwords differ.

## Phase 2 — Config persistence (settings + subscription)

| Service | Tables |
|---|---|
| school-settings-service | `design_theme`, `module_settings`, `menu_config`, `localization_settings`, `ui_screen_config`, `ai_settings`, `role_dashboard_config` |
| subscription-service | `subscription_plan`, `tenant_subscription` |

Verified: theme colors persist in DB; plan assign stores `tenant_subscription`.

### Phase 2 complete — all school config services

| Service | Tables |
|---|---|
| form-builder-service | `form_definition` |
| workflow-service | `workflow_definition` |
| rule-engine-service | `business_rule` |
| report-builder-service | `report_template` |
| school-notification-config-service | `notification_template` |
| audit-service | `config_change_audit` |
| school-settings-service | `org_branch` (campuses) |

Platform defaults use `organization_id IS NULL`; tenant saves override per school.