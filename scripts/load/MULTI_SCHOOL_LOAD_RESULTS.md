# Multi-school concurrent load test results

Date: 2026-07-19  
Gateway: `http://localhost:9090`  
Tenants: `demo-school` + `load-school-01` … `load-school-05` (6 schools)

## How to re-run

```powershell
cd D:\school
.\scripts\seed-multi-school-load.ps1

cd D:\school\scripts\load
# Baseline: 1 concurrent user per school
k6 run multi-school-load.js -e QUICK=1 -e VUS=6 -e DURATION=60s -e THINK_TIME_MS=250 -e RESULT_DIR=results

# Realistic: ~2 concurrent users per school
k6 run multi-school-load.js -e QUICK=1 -e VUS=12 -e DURATION=90s -e THINK_TIME_MS=300 -e RESULT_DIR=results

# Stress (expect gateway/service 5xx on local single-host stack)
k6 run multi-school-load.js -e QUICK=1 -e VUS=18 -e DURATION=90s -e THINK_TIME_MS=50 -e RESULT_DIR=results
```

## Results summary

| Scenario | VUs | Duration | Requests | Fail % | p95 latency | Throughput | Tenant leaks |
|---|---:|---|---:|---:|---:|---:|---:|
| Baseline (1 user/school) | 6 | 60s | 1,318 | **0.00%** | **96 ms** | 21 req/s | **0** |
| Realistic (2 users/school) | 12 | 90s | 3,175 | **0.00%** | **158 ms** | 35 req/s | **0** |
| Stress (aggressive) | 18 | 90s | 11,941 | **43%** | 397 ms | 131 req/s | **0** |
| Early run (login storm) | 12 | 60s | 1,907 | 16% | 1.1 s | 29 req/s | **0** |

## Findings

1. **Multi-tenant isolation held** under all runs (`school_tenant_leak = 0`). No school saw another school's rows.
2. **6 schools concurrent is healthy** on the current local stack at ~1–2 active users per school.
3. **Latency is good** when the stack is not overloaded (p95 under ~160 ms for realistic mix).
4. **Local capacity cliff** appears around aggressive ~18 VU / ~130 req/s with short think time: domain APIs return many 5xx while settings/theme stay mostly healthy. This is expected for many JVMs + small DB pools on one machine.
5. **Auth bcrypt login storms** are expensive: avoid hammering `/api/v1/auth/login` from every VU; reuse tokens (script default).

## Production guidance from this test

- For a pilot of **5–10 schools**, size for at least **2–5 concurrent admins per school** plus parent/teacher portal traffic.
- Prefer **separate DB pools / higher RDS connections**, gateway timeouts reviewed, and horizontal scale before pushing beyond ~50–100 concurrent interactive users on one host.
- Keep School UI same-origin `/api` through gateway; do not expose service ports.
- Re-run this k6 suite after each release as a multi-tenant smoke gate.

Raw JSON: `D:\school\scripts\load\results\`
