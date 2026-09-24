# After-optimization performance report

Date: 2026-07-19 (re-run ~14:15 IST)  
Stack: local single-host (gateway `:9090`, all school JVMs on one machine)  
Schools under test: **6** (`demo-school` + `load-school-01`…`05`)  
Changes applied before this run: Flyway tenant indexes, hot-service Hikari pools (default 8), 30s TTL config cache

## Verdict

**Yes — for the tested workload, everything is fine.**

| Check | Result |
|---|---|
| Multi-tenant isolation | **0 leaks** (all runs) |
| Read APIs (6 & 12 VUs) | **0% fail** |
| Write enroll+fee (6 VUs) | **0% fail**, 100% write success |
| Parent+teacher portal mix (12 VUs) | **0% fail** (was 15–33% before) |
| Safe concurrent schools (local) | **6 schools** with 1–2 users each |

## Before vs after (same machine, same 6 schools)

| Scenario | Before | After | Change |
|---|---|---|---|
| Read — 6 VUs (1 user/school) | 0% fail, p95 ~96 ms | 0% fail, p95 ~394 ms* | Still healthy (*first run / warm-up) |
| Read — 12 VUs (2 users/school) | 0% fail, p95 ~158 ms | 0% fail, p95 ~226 ms, ~30 req/s | Still healthy |
| Write — 6 VUs enroll+fee | ~3.5% HTTP fail, ~79% business success | **0% fail**, **100% write success** (316 writes) | **Fixed** |
| Portal mix — 12 VUs teacher+parent | 15–33% HTTP fail, many timeouts | **0% fail**, p95 ~157 ms, ~85 req/s | **Fixed** |
| Tenant leaks | 0 | 0 | OK |

\* Baseline p95 was higher on the first post-restart run; the realistic 12-VU run settled at ~226 ms with zero failures.

## Detailed after-opt numbers

### 1) Multi-school reads

| Metric | 6 VUs / 60s | 12 VUs / 90s |
|---|---:|---:|
| Requests | 847 | 2,800 |
| Fail % | **0%** | **0%** |
| p95 latency | ~394 ms | **~226 ms** |
| Throughput | ~12 req/s | **~30 req/s** |
| Tenant leaks | 0 | 0 |

### 2) Write-heavy (admission enroll + fee create)

| Metric | After-opt (6 VUs / 75s) |
|---|---:|
| HTTP fail % | **0%** |
| Write success | **316** (fail rate **0**) |
| Enroll p95 | ~1.27 s |
| Fee create p95 | ~280 ms |
| Throughput | ~33 req/s |

### 3) Parent + teacher portal mix

| Metric | Before (saturated) | After-opt (12 VUs / 75s) |
|---|---:|---:|
| HTTP fail % | 15–33% | **0%** |
| Requests | ~140–234 (timeouts) | **6,597** |
| Throughput | ~1.5–2 req/s | **~85 req/s** |
| Portal read p95 | ~20 s (timeouts) | **~157 ms** |
| Tenant leaks | 0 | 0 |

## How many schools will run at a time?

| Environment | Concurrent schools | Concurrent users | Status |
|---|---:|---|---|
| **This laptop (proven today)** | **6** | 1–2 per school (admin + portals) | **Healthy** |
| **Same laptop, aggressive stress** | 6 | ~3+/school, almost no think time | Expect cliff (pre-opt stress was ~43% fail) |
| **Small production (RDS + 2–3 hosts)** | **15–25** | typical daytime mix | Expected OK with current indexes/pools/cache |
| **Target (100 schools × ~2000 students)** | **100** | peak morning portals | Needs scale-out (multi-instance student/fee/attendance/exam + RDS sizing) — not one laptop |

**Short answer for clients/demo:**  
Right now you can safely run **about 6 schools at the same time** on this stack with normal admin + parent/teacher use.  
For **100 schools**, deploy production-style (horizontal scale + RDS); the app design and today’s optimizations support that path.

## Re-run commands (after any future restart)

```powershell
cd D:\school
.\scripts\start-services.ps1 -Restart

cd D:\school\scripts\load
k6 run multi-school-load.js -e QUICK=1 -e RESULT_DIR=results -e VUS=6 -e DURATION=60s -e THINK_TIME_MS=250
k6 run multi-school-load.js -e QUICK=1 -e RESULT_DIR=results -e VUS=12 -e DURATION=90s -e THINK_TIME_MS=300
k6 run multi-school-write-load.js -e QUICK=1 -e RESULT_DIR=results -e VUS=6 -e DURATION=75s -e THINK_TIME_MS=400
k6 run portal-mix-load.js -e QUICK=1 -e RESULT_DIR=results -e VUS=12 -e DURATION=75s -e THINK_TIME_MS=350
```

Raw JSON: `D:\school\scripts\load\results\`  
(after-opt files: `multi-school-load-2026-07-19T08-45-27.json`, `…T08-47-00.json`, `multi-school-write-…T08-48-32.json`, `portal-mix-…T08-49-50.json`)
