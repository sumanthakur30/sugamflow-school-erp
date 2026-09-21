# Phase 23 — Operational readiness

Production-oriented defaults shared by every school microservice via
`school-common`: bounded inter-service HTTP, correlation IDs, access logs,
actuator probes, and Prometheus metrics.

## What landed

| Area | Behaviour |
|---|---|
| HTTP timeouts | Every `RestClient.Builder` gets connect **3s** / read **10s** (override with `SCHOOL_HTTP_CONNECT_TIMEOUT_MS` / `SCHOOL_HTTP_READ_TIMEOUT_MS`) |
| Correlation | Inbound `X-Request-Id` accepted or generated; echoed on response; propagated on inter-service hops via `TenantHeaders`; MDC keys `requestId`, `tenantId`, `userId` |
| Access log | One structured line per non-actuator request: method, path, status, durationMs + MDC fields |
| Graceful shutdown | `server.shutdown=graceful` with 30s drain |
| Probes | `/actuator/health/liveness`, `/actuator/health/readiness` (readiness includes `db`) |
| Metrics | `/actuator/prometheus` + `/actuator/metrics` on every school service |
| Log pattern | `%5p [app,requestId,tenantId,userId]` |

Shared defaults live in:

```text
services/school-common/src/main/resources/school-ops.defaults.properties
```

Each service imports them with:

```properties
spring.config.import=optional:configserver:...,optional:classpath:school-ops.defaults.properties
```

`start-services.ps1` also passes the same actuator/probe flags on the JVM command line so
restarts pick them up even before a full rebuild. Application-local properties / env vars still win.

## Key classes (`school-common`)

| Class | Role |
|---|---|
| `HttpClientConfig` / `HttpClientProperties` | `RestClientCustomizer` with connect/read timeouts |
| `CorrelationIdFilter` | `X-Request-Id` + MDC |
| `AccessLogFilter` | Structured access logging |
| `TenantHeaders` | Propagates `X-Request-Id` on outbound calls |

## Verify

```powershell
powershell -NoProfile -File D:\school\scripts\verify-ops-readiness.ps1
# Fail if any school service is down:
powershell -NoProfile -File D:\school\scripts\verify-ops-readiness.ps1 -Strict
```

Checks:

1. Platform core (Eureka, gateway, auth, notification)
2. Each school port: health + liveness + readiness + prometheus
3. Correlation id echo through gateway → settings

## Ops notes

- **Rolling restart:** readiness fails while Flyway/DB is unavailable; liveness stays UP so the process is not killed mid-drain.
- **Scraping:** point Prometheus at `http://<host>:<port>/actuator/prometheus` for each school service (or via Eureka service discovery).
- **Stuck downstream:** with timeouts enabled, a hung peer fails the call in ≤10s instead of exhausting Tomcat threads.
- **Elevated/locked jars:** if a service jar cannot be replaced (Access Denied), restart that process from an admin shell after rebuilding (`.\scripts\start-services.ps1 -Restart`). Until then `verify-ops-readiness.ps1` reports WARN for health-UP-but-probes-missing.
- **Notification 503:** treated as WARN (delivery stack / mail often degraded locally); use `-Strict` only when every school port must be green.

## Pilot backups and go/no-go

```powershell
# Custom-format dumps for every school_* database
powershell -NoProfile -File D:\school\scripts\backup-school-dbs.ps1

# Restore one dump (destructive when -Clean)
powershell -NoProfile -File D:\school\scripts\restore-school-db.ps1 `
  -DumpFile D:\school\backups\<stamp>\school_student_db.dump `
  -Database school_student_db `
  -Clean

# Combined pilot readiness
powershell -NoProfile -File D:\school\scripts\verify-pilot-ready.ps1
```

See `docs/PILOT_RUNBOOK.md` for persona smoke paths and alert triage.

## Not in this slice (follow-ups)

- Distributed tracing exporter (OTLP / Zipkin)
- Central log shipping (ELK / Loki)
- Circuit breakers / bulkheads (Resilience4j)
- Rate limiting at gateway
- Synthetic uptime monitors outside the box
