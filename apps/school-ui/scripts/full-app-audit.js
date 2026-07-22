const { chromium } = require('playwright');
const fs = require('fs');

const base = 'http://127.0.0.1:4300';
const routes = [
  '/admin/admission',
  '/admin/fee',
  '/admin/finance',
  '/admin/income-expense',
  '/admin/attendance',
  '/admin/devices',
  '/admin/offline',
  '/admin/exam',
  '/admin/lms',
  '/admin/library',
  '/admin/hostel',
  '/admin/transport',
  '/admin/payroll',
  '/admin/student-directory',
  '/admin/students',
  '/admin/import',
  '/admin/comms',
  '/admin/staff-directory',
  '/admin/staff-directory/add',
  '/admin/lifecycle',
  '/admin/academic',
  '/admin/timetable',
  '/admin/ops',
  '/admin/branches',
  '/admin/design-studio',
  '/admin/subscription',
  '/admin/modules',
  '/admin/forms',
  '/admin/workflows',
  '/admin/rules',
  '/admin/reports-hub',
  '/admin/reports',
  '/admin/notifications',
  '/admin/menus',
  '/admin/localization',
  '/admin/ai',
  '/admin/audit',
];

async function login(page) {
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  const inputs = page.locator('form input');
  await inputs.nth(0).fill('demo-school');
  await inputs.nth(1).fill('admin');
  await inputs.nth(2).fill('password');
  await page.locator('form button[type=submit]').click({ force: true });
  await page.waitForURL(/\/admin\//, { timeout: 30000 });
}

async function auditViewport(browser, name, viewport) {
  const page = await browser.newPage({ viewport });
  await login(page);
  const results = [];

  for (const route of routes) {
    const apiErrors = [];
    const consoleErrors = [];
    const onResponse = (response) => {
      if (response.status() >= 400 && response.url().includes('/api/')) {
        apiErrors.push(`${response.status()} ${response.url().replace(/^https?:\/\/[^/]+/, '')}`);
      }
    };
    const onConsole = (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text());
    };
    page.on('response', onResponse);
    page.on('console', onConsole);

    const started = Date.now();
    let navigationError = '';
    try {
      await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.waitForTimeout(1800);
    } catch (error) {
      navigationError = error.message;
    }
    const elapsedMs = Date.now() - started;
    const body = await page.locator('body').innerText().catch(() => '');
    const overflow = await page
      .evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2)
      .catch(() => false);
    const loadingStuck = /searching…|loading…|loading\.\.\.|saving…/i.test(body);
    const visibleError = /(failed to load|service is not responding|internal server error|something went wrong)/i.test(
      body,
    );
    const redirected = !page.url().includes(route);

    results.push({
      viewport: name,
      route,
      finalUrl: page.url(),
      elapsedMs,
      redirected,
      navigationError,
      overflow,
      loadingStuck,
      visibleError,
      apiErrors: [...new Set(apiErrors)].slice(0, 10),
      consoleErrors: [...new Set(consoleErrors)].slice(0, 10),
      title: (await page.locator('h1, h2').first().innerText().catch(() => '')).trim(),
    });
    console.log(
      `${name.padEnd(7)} ${route.padEnd(31)} ${String(elapsedMs).padStart(5)}ms ` +
        `api=${apiErrors.length} console=${consoleErrors.length} overflow=${overflow} ` +
        `stuck=${loadingStuck} visibleError=${visibleError} redirected=${redirected}`,
    );
    page.off('response', onResponse);
    page.off('console', onConsole);
  }

  await page.close();
  return results;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const desktop = await auditViewport(browser, 'desktop', { width: 1440, height: 900 });
  const mobile = await auditViewport(browser, 'mobile', { width: 390, height: 844 });
  await browser.close();

  const results = [...desktop, ...mobile];
  const summary = {
    generatedAt: new Date().toISOString(),
    routes: routes.length,
    checks: results.length,
    apiErrorRoutes: results.filter((r) => r.apiErrors.length).length,
    consoleErrorRoutes: results.filter((r) => r.consoleErrors.length).length,
    overflowRoutes: results.filter((r) => r.overflow).length,
    loadingStuckRoutes: results.filter((r) => r.loadingStuck).length,
    visibleErrorRoutes: results.filter((r) => r.visibleError).length,
    redirectedRoutes: results.filter((r) => r.redirected).length,
    p50Ms: percentile(results.map((r) => r.elapsedMs), 50),
    p95Ms: percentile(results.map((r) => r.elapsedMs), 95),
    maxMs: Math.max(...results.map((r) => r.elapsedMs)),
  };
  fs.writeFileSync(
    'scripts/full-app-audit-results.json',
    JSON.stringify({ summary, results }, null, 2),
  );
  console.log('SUMMARY', JSON.stringify(summary));
})().catch((error) => {
  console.error(error);
  process.exit(1);
});

function percentile(values, p) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}
