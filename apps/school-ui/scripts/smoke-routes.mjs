import { chromium, request } from 'playwright';

const uiBase = process.env.UI_BASE_URL || 'http://localhost:4300';
const apiBase = process.env.API_BASE_URL || 'http://localhost:9090';
const shopId = process.env.SHOP_ID || 'demo-school';
const username = process.env.USERNAME || `admin_${shopId}`;
const password = process.env.PASSWORD || 'password';

const adminRoutes = [
  '/admin/dashboard',
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

const portalRoutes = [
  '/parent',
  '/parent/attendance',
  '/parent/fees',
  '/parent/exams',
  '/parent/report-cards',
  '/parent/homework',
  '/teacher',
  '/teacher/students',
  '/teacher/attendance',
  '/teacher/gradebook',
  '/teacher/report-cards',
  '/teacher/homework',
];

const api = await request.newContext();
const loginResponse = await api.post(`${apiBase}/api/v1/auth/login`, {
  data: { shopId, username, password },
});
if (!loginResponse.ok()) {
  throw new Error(`Login failed: ${loginResponse.status()} ${await loginResponse.text()}`);
}
const session = await loginResponse.json();
if (!session.accessToken || session.mfaRequired) {
  throw new Error('Login did not return a usable access token');
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(
  ({ auth, tenant }) => {
    localStorage.setItem('sf.accessToken', auth.accessToken);
    localStorage.setItem('sf.session', JSON.stringify(auth));
    localStorage.setItem('sf.tenantId', tenant);
    localStorage.setItem('sf.branchId', 'main');
    localStorage.setItem('sf.sessionId', '2025-26');
    localStorage.setItem('sf.userId', auth.username);
    localStorage.setItem('sf.role', auth.role);
  },
  { auth: session, tenant: shopId },
);

const page = await context.newPage();
const failures = [];
let currentRoute = '';

page.on('pageerror', (error) => {
  failures.push({ route: currentRoute, type: 'page error', detail: error.message });
});
page.on('response', (response) => {
  if (response.status() >= 500) {
    failures.push({
      route: currentRoute,
      type: `HTTP ${response.status()}`,
      detail: response.url(),
    });
  }
});

for (const route of [...adminRoutes, ...portalRoutes]) {
  currentRoute = route;
  const before = failures.length;
  try {
    await page.goto(`${uiBase}${route}`, { waitUntil: 'domcontentloaded', timeout: 20_000 });
    await page.waitForTimeout(900);
    const finalPath = new URL(page.url()).pathname;
    const heading = (await page.locator('h1, h2').first().textContent().catch(() => ''))?.trim();
    const body = await page.locator('body').innerText();
    if (finalPath === '/login') {
      failures.push({ route, type: 'redirect', detail: 'Unexpected redirect to login' });
    }
    if (/0 Unknown Error|Unable to connect to the remote server/i.test(body)) {
      failures.push({ route, type: 'UI error', detail: 'Gateway connection error rendered' });
    }
    if (!heading) {
      failures.push({ route, type: 'render', detail: 'No page heading found' });
    }
    const result = failures.length === before ? 'PASS' : 'FAIL';
    console.log(`${result.padEnd(5)} ${route.padEnd(32)} ${heading || '(no heading)'}`);
  } catch (error) {
    failures.push({ route, type: 'navigation', detail: error.message });
    console.log(`FAIL  ${route.padEnd(32)} ${error.message}`);
  }
}

await browser.close();
await api.dispose();

if (failures.length) {
  console.error('\nRoute smoke failures:');
  for (const failure of failures) {
    console.error(`- ${failure.route}: ${failure.type} — ${failure.detail}`);
  }
  process.exit(1);
}

console.log(`\nPASS: ${adminRoutes.length + portalRoutes.length} routed pages rendered without fatal errors.`);
