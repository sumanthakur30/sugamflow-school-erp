# Multi-school write + portal mix load results

Date: 2026-07-19  
Gateway: `http://localhost:9090`  
Schools: `demo-school` + `load-school-01` … `05`

## Artifacts

| Item | Path |
|---|---|
| Write-heavy k6 | `D:\school\scripts\load\multi-school-write-load.js` |
| Portal mix k6 | `D:\school\scripts\load\portal-mix-load.js` |
| Seed schools | `D:\school\scripts\seed-multi-school-load.ps1` |
| Seed teacher/parent fixtures | `D:\school\scripts\seed-portal-load-fixtures.ps1` |
| Fixture JSON | `D:\school\scripts\load\portal-fixture.json` |
| Raw results | `D:\school\scripts\load\results\` |

## 1) Write-heavy (admission enroll + fee create)

### Realistic (6 VUs, 75s, think 400ms)

| Metric | Result |
|---|---|
| Iterations | 485 |
| Successful enroll+fee writes | **382** (~4.99/s) |
| Business write success | **~79%** (382/485) |
| Enroll success (checks) | **~96%+** |
| Fee success when enrolled | **~99%** |
| Enroll p95 | **697 ms** |
| Fee create p95 | **182 ms** |
| HTTP fail rate | **3.5%** |
| Cross-tenant leak | **0** (not applicable for writes; schools isolated by auth) |

### Stress (12 VUs, 90s)

| Metric | Result |
|---|---|
| Successful writes | **386** |
| Write fail rate | higher (~56% business mark) |
| Enroll p95 | ~2.8 s |
| HTTP fail rate | ~17% |

**Finding:** Concurrent multi-school enrollments work well at ~1 writer/school. Pushing harder stresses admission workflow + DB pools.

## 2) Parent + teacher portal mix

### Fixture setup

- Auth roles seeded: `TEACHER` / `PARENT` per school
- Each school linked with student, guardian username, published homework, fee row
- Logins through gateway JWT (not internal-header bypass)

### Healthy probe (single requests after recovery)

Teacher/parent bootstrap, students, homework/mine, fees mostly **200**.

### Concurrent portal run (after write storm / saturated student-service)

`student-service` became the bottleneck. Portal bootstrap stayed mostly OK; `/api/student/**` and Student 360 timed out under concurrency.

| Metric | Observed |
|---|---|
| Checks that completed (not 401 / not spoof) | Strong — no auth leakage |
| HTTP fail (timeouts/5xx under saturation) | **15–69%** |
| Portal tenant leak | **0** |
| Bottleneck | **student-service** (restarted after test) |

### Cleaner single-request probe (recovered)

Teacher/parent bootstrap, students, homework, fees returned **200** when hit one at a time.

**Finding:** Parent/teacher multi-school identity isolation is correct. Concurrent portal traffic on one laptop is limited by `student-service` (especially Student 360 fan-out). Production needs stronger sizing for student-service / DB pools, or avoid chaining write storms into portal peaks.

## Combined capacity guidance

| Workload | Local single-host verdict |
|---|---|
| 6 schools × read APIs | Healthy |
| 6 schools × 1 writer each (enroll+fee) | Healthy (~5 successful writes/s) |
| 6 schools × teacher+parent concurrent | Healthy only if student-service is not already saturated |
| Aggressive combined write then portal | **Not healthy** on one machine — restart student-service / raise DB pools / split services |

## Re-run commands

```powershell
cd D:\school
.\scripts\seed-multi-school-load.ps1
.\scripts\seed-portal-load-fixtures.ps1

cd D:\school\scripts\load
k6 run multi-school-write-load.js -e QUICK=1 -e VUS=6 -e DURATION=75s -e THINK_TIME_MS=400 -e RESULT_DIR=results
k6 run portal-mix-load.js -e QUICK=1 -e VUS=12 -e DURATION=75s -e THINK_TIME_MS=350 -e RESULT_DIR=results
```

Run portal mix on a fresh/recovered stack; do not chain it immediately after a write storm on the same laptop JVM set.
