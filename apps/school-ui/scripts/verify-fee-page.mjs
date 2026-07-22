import { chromium } from 'playwright';

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await page.goto('http://127.0.0.1:4301/login', { waitUntil: 'domcontentloaded' });
  await page.fill('input[name="organizationId"]', 'demo-school');
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'password');
  await page.click('button[type="submit"]');
  try {
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 45000 });
  } catch {}
  await page.waitForTimeout(2000);
  console.log('after login', page.url());

  await page.goto('http://127.0.0.1:4301/admin/fee', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const body = (await page.locator('body').innerText()).trim();
  console.log('fee url', page.url());
  console.log('has Fee Collection', body.includes('Fee Collection'));
  console.log('has More', body.includes('More'));
  console.log('preview', JSON.stringify(body.slice(0, 400)));
  console.log('errors', errors.slice(0, 5).join(' | '));
  await page.screenshot({ path: 'D:/school/apps/school-ui/scripts/fee-fixed.png', fullPage: true });
  await browser.close();
  if (!body.includes('Fee Collection')) process.exitCode = 1;
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
