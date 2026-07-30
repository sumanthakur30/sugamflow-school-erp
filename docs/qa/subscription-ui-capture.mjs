/**
 * Subscription Admin UI capture — desktop / tablet / mobile screenshots.
 * Login: organizationId=demo-school, username=admin, password=password
 */
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(__dirname, 'subscription-screenshots');
const BASE = 'http://localhost:4300';

const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 390, height: 844 },
];

const tabs = [
  { key: 'plans', label: 'Plans' },
  { key: 'catalog', label: 'Catalog' },
  { key: 'license', label: 'License' },
  { key: 'usage', label: 'Usage' },
];

fs.mkdirSync(OUT, { recursive: true });

const meta = {
  startedAt: new Date().toISOString(),
  loginOk: false,
  loginError: null,
  screenshots: [],
  notes: [],
};

async function fillLogin(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });
  // Prefer labels / name attributes from login.component.html
  const org = page.locator('input[name="organizationId"]');
  const user = page.locator('input[name="username"]');
  const pass = page.locator('input[name="password"]');
  await org.fill('demo-school');
  await user.fill('admin');
  await pass.fill('password');
  // Prefer Admin destination
  const dest = page.locator('select[name="destination"]');
  if (await dest.count()) {
    await dest.selectOption('admin');
  }
  await page.locator('form button[type="submit"]').click();
  try {
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 45000 });
    meta.loginOk = true;
  } catch (e) {
    const errEl = page.locator('.error, [role="alert"]');
    meta.loginError = (await errEl.first().textContent().catch(() => null)) || e.message;
    meta.loginOk = false;
    await page.screenshot({
      path: path.join(OUT, 'mobile-login-error.png'),
      fullPage: true,
    });
    meta.screenshots.push('mobile-login-error.png');
  }
}

async function clickTab(page, label) {
  const btn = page.locator('.subscription-admin .tabs button', { hasText: label });
  if (await btn.count()) {
    await btn.first().click();
    await page.waitForTimeout(800);
    return true;
  }
  // fallback: any button with text
  const any = page.getByRole('button', { name: label });
  if (await any.count()) {
    await any.first().click();
    await page.waitForTimeout(800);
    return true;
  }
  return false;
}

async function captureViewport(browser, vp) {
  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  page.setDefaultTimeout(45000);

  if (vp.name === 'mobile') {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.screenshot({
      path: path.join(OUT, 'mobile-login.png'),
      fullPage: true,
    });
    meta.screenshots.push('mobile-login.png');
  }

  await fillLogin(page);

  if (!meta.loginOk) {
    meta.notes.push(`${vp.name}: login failed — trying direct /admin/subscription`);
    await page.goto(`${BASE}/admin/subscription`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.screenshot({
      path: path.join(OUT, `${vp.name}-direct-subscription.png`),
      fullPage: true,
    });
    meta.screenshots.push(`${vp.name}-direct-subscription.png`);
  } else {
    await page.goto(`${BASE}/admin/subscription`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(1200);
  }

  for (const tab of tabs) {
    const clicked = await clickTab(page, tab.label);
    if (!clicked) {
      meta.notes.push(`${vp.name}: tab button "${tab.label}" not found`);
    }
    await page.waitForTimeout(600);
    const file = `${vp.name}-${tab.key}.png`;
    await page.screenshot({ path: path.join(OUT, file), fullPage: true });
    meta.screenshots.push(file);
  }

  await context.close();
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    for (const vp of viewports) {
      // reset loginOk per viewport attempt tracking — keep last success
      const prevLogin = meta.loginOk;
      await captureViewport(browser, vp);
      if (prevLogin && !meta.loginOk) {
        // keep true if any prior succeeded
        meta.loginOk = true;
      }
    }
  } finally {
    await browser.close();
  }
  meta.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, 'capture-meta.json'), JSON.stringify(meta, null, 2));
  console.log(JSON.stringify(meta, null, 2));
})().catch((err) => {
  console.error(err);
  process.exit(1);
});

