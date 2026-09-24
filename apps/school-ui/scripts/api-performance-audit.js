const fs = require('fs');

const gateway = 'http://127.0.0.1:9090';
const endpoints = [
  '/api/config/branches/bootstrap',
  '/api/subscription/tenants/current/entitlements',
  '/api/student/directory/summary',
  '/api/student/students?page=0&size=20',
  '/api/staff/directory/staff?page=0&size=20',
  '/api/admission/applications?page=0&size=20',
  '/api/fee/collections?page=0&size=20',
  '/api/fee/finance/income-expense?preset=THIS_MONTH&branchIds=ALL',
  '/api/payroll/records?page=0&size=20',
  '/api/payroll/reports/salary-summary?preset=THIS_MONTH&branchIds=ALL',
  '/api/attendance/records?page=0&size=20',
  '/api/exam/records?page=0&size=20',
  '/api/library/records?page=0&size=20',
  '/api/hostel/records?page=0&size=20',
  '/api/transport/records?page=0&size=20',
  '/api/audit/config-changes?page=0&size=20',
];

async function timedFetch(url, options = {}, timeoutMs = 15000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const start = performance.now();
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    await response.arrayBuffer();
    return {
      ms: Math.round(performance.now() - start),
      status: response.status,
      ok: response.ok,
    };
  } catch (error) {
    return {
      ms: Math.round(performance.now() - start),
      status: 0,
      ok: false,
      error: error.name === 'AbortError' ? 'timeout' : error.message,
    };
  } finally {
    clearTimeout(timer);
  }
}

(async () => {
  const loginResponse = await fetch(`${gateway}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      shopId: 'demo-school',
      username: 'admin_demo-school',
      password: 'password',
    }),
  });
  const login = await loginResponse.json();
  const token = login.accessToken || login.token || login.data?.accessToken;
  if (!token) throw new Error('Login did not return a token');

  const headers = {
    authorization: `Bearer ${token}`,
    'x-tenant-id': 'demo-school',
    'x-branch-id': 'main',
    'x-academic-session-id': '2025-26',
    'x-user-id': 'admin_demo-school',
    'x-role-code': 'SHOP_OWNER',
  };

  const results = [];
  for (const endpoint of endpoints) {
    // One warm-up prevents JVM/class initialization from dominating the sample.
    await timedFetch(`${gateway}${endpoint}`, { headers });
    const samples = [];
    for (let i = 0; i < 8; i += 1) {
      samples.push(await timedFetch(`${gateway}${endpoint}`, { headers }));
    }
    const latencies = samples.map((s) => s.ms).sort((a, b) => a - b);
    const item = {
      endpoint,
      requests: samples.length,
      successful: samples.filter((s) => s.ok).length,
      statuses: [...new Set(samples.map((s) => s.status))],
      p50Ms: percentile(latencies, 50),
      p95Ms: percentile(latencies, 95),
      maxMs: Math.max(...latencies),
      errors: samples.filter((s) => !s.ok).map((s) => s.error || `HTTP ${s.status}`),
    };
    results.push(item);
    console.log(
      `${endpoint.padEnd(75)} ok=${item.successful}/${item.requests} ` +
        `p50=${item.p50Ms}ms p95=${item.p95Ms}ms max=${item.maxMs}ms`,
    );
  }

  // Lightweight concurrency check against the report and student list.
  const loadTargets = [
    '/api/student/students?page=0&size=20',
    '/api/fee/finance/income-expense?preset=THIS_MONTH&branchIds=ALL',
  ];
  const concurrent = [];
  for (const endpoint of loadTargets) {
    const started = performance.now();
    const samples = await Promise.all(
      Array.from({ length: 20 }, () => timedFetch(`${gateway}${endpoint}`, { headers }, 20000)),
    );
    concurrent.push({
      endpoint,
      concurrency: 20,
      wallMs: Math.round(performance.now() - started),
      successful: samples.filter((s) => s.ok).length,
      p50Ms: percentile(samples.map((s) => s.ms), 50),
      p95Ms: percentile(samples.map((s) => s.ms), 95),
      maxMs: Math.max(...samples.map((s) => s.ms)),
    });
  }

  const summary = {
    generatedAt: new Date().toISOString(),
    endpoints: endpoints.length,
    healthyEndpoints: results.filter((r) => r.successful === r.requests).length,
    failedEndpoints: results.filter((r) => r.successful < r.requests).length,
    p95Under500Ms: results.filter((r) => r.p95Ms <= 500).length,
    p95Over1000Ms: results.filter((r) => r.p95Ms > 1000).length,
  };
  fs.writeFileSync(
    'scripts/api-performance-audit-results.json',
    JSON.stringify({ summary, results, concurrent }, null, 2),
  );
  console.log('SUMMARY', JSON.stringify(summary));
  console.log('CONCURRENT', JSON.stringify(concurrent));
})().catch((error) => {
  console.error(error);
  process.exit(1);
});

function percentile(values, p) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}
