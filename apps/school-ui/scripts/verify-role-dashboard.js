const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('http://localhost:4300/login', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.locator('input[name="organizationId"]').fill('demo-school');
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('password');
  await page.locator('select[name="destination"]').selectOption('admin');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('**/admin/dashboard', { timeout: 60000 });
  await page.getByRole('heading', { name: /dashboard/i }).waitFor({ timeout: 30000 });
  await page.locator('.metric-card:not(.skeleton)').first().waitFor({ timeout: 30000 });

  const metricCount = await page.locator('.metric-card:not(.skeleton)').count();
  const actionCount = await page.locator('.action-card').count();
  const text = await page.locator('.dashboard-page').innerText();
  console.log('DASHBOARD_URL', page.url());
  console.log('METRICS', metricCount);
  console.log('ACTIONS', actionCount);
  console.log('HAS_STUDENTS', /Active students/i.test(text));
  console.log('HAS_ADMISSIONS', /Admissions in review/i.test(text));
  console.log('HAS_FEE_MONTH', /Fee collection \(month\)/i.test(text));
  console.log('PAGE_ERRORS', errors.length);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(300);
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  console.log('MOBILE_OVERFLOW', overflow);
  await page.screenshot({ path: 'role-dashboard.png', fullPage: true });

  if (metricCount < 4 || actionCount < 2 || errors.length || overflow) {
    process.exitCode = 1;
  }
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
