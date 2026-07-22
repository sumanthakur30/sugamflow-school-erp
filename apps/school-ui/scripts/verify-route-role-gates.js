const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  });
  const page = await browser.newPage();

  await page.goto('http://localhost:4300/login', { waitUntil: 'domcontentloaded' });
  await page.locator('input[name="organizationId"]').fill('demo-school');
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('password');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('**/admin/dashboard', { timeout: 60000 });

  async function setUiRole(role) {
    await page.evaluate((value) => localStorage.setItem('sf.role', value), role);
  }

  await setUiRole('PARENT');
  await page.goto('http://localhost:4300/admin/modules');
  await page.waitForURL('**/parent', { timeout: 15000 });
  console.log('PARENT_CONFIG_REDIRECT', page.url().endsWith('/parent'));

  await setUiRole('TEACHER');
  await page.goto('http://localhost:4300/parent');
  await page.waitForURL('**/teacher', { timeout: 15000 });
  console.log('TEACHER_PARENT_REDIRECT', page.url().endsWith('/teacher'));

  await setUiRole('PRINCIPAL');
  await page.goto('http://localhost:4300/admin/modules');
  await page.waitForURL('**/admin/dashboard', { timeout: 15000 });
  console.log('PRINCIPAL_CONFIG_REDIRECT', page.url().endsWith('/admin/dashboard'));

  await page.goto('http://localhost:4300/admin/branches');
  await page.waitForURL('**/admin/branches', { timeout: 15000 });
  console.log('PRINCIPAL_BRANCH_ALLOWED', page.url().endsWith('/admin/branches'));

  await setUiRole('SHOP_OWNER');
  await page.goto('http://localhost:4300/admin/modules');
  await page.waitForURL('**/admin/modules', { timeout: 15000 });
  console.log('OWNER_CONFIG_ALLOWED', page.url().endsWith('/admin/modules'));

  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
