import { chromium } from 'playwright';

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const errors = [];
  const failed = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });
  page.on('response', (r) => {
    if (r.status() >= 400) failed.push(`${r.status()} ${r.url()}`);
  });

  await page.goto('http://localhost:4300/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1000);
  const inputs = page.locator('form input');
  if ((await inputs.count()) >= 3) {
    await inputs.nth(0).fill('demo-school');
    await inputs.nth(1).fill('admin');
    await inputs.nth(2).fill('password');
    await page.getByRole('button', { name: /sign in|login|continue/i }).click();
    await page.waitForTimeout(2500);
  }

  await page.goto('http://localhost:4300/admin/fee', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(4000);

  const body = (await page.locator('body').innerText()).trim();
  console.log('URL', page.url());
  console.log('BODY_LEN', body.length);
  console.log('BODY_PREVIEW', JSON.stringify(body.slice(0, 400)));
  console.log('ERRORS\n' + errors.slice(0, 20).join('\n'));
  console.log('FAILED\n' + failed.slice(0, 20).join('\n'));
  await page.screenshot({ path: 'D:/school/apps/school-ui/scripts/fee-blank-check.png', fullPage: true });
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
