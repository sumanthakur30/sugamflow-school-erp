const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  await page.goto('http://localhost:4300/login', { waitUntil: 'domcontentloaded' });
  await page.locator('input[name="organizationId"]').fill('demo-school');
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('password');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('**/admin/dashboard', { timeout: 60000 });

  for (const portal of ['parent', 'teacher']) {
    const role = portal === 'parent' ? 'PARENT' : 'TEACHER';
    await page.evaluate((value) => localStorage.setItem('sf.role', value), role);
    await page.goto(`http://localhost:4300/${portal}`);
    await page.locator('.live-badge').filter({ hasText: /Live|Refreshing/i }).waitFor({
      timeout: 30000,
    });
    await page.locator('.live-badge').filter({ hasText: 'Live' }).waitFor({ timeout: 30000 });
    const text = await page.locator('.portal-home').innerText();
    const staticFixtureVisible = /Priya Nair|Ms\. Anita Sharma|Unit Test 2 — A/.test(text);
    const widgets = await page.locator('.widget-stat').count();
    console.log(portal.toUpperCase(), 'WIDGETS', widgets, 'STATIC_FIXTURE', staticFixtureVisible);
    if (staticFixtureVisible || widgets < 3) {
      process.exitCode = 1;
    }
  }
  await page.evaluate(() => localStorage.setItem('sf.role', 'SHOP_OWNER'));

  console.log('PAGE_ERRORS', pageErrors.length);
  if (pageErrors.length) {
    process.exitCode = 1;
  }
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
