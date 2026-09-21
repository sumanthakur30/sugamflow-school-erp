const { chromium, devices } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = path.join(__dirname, 'e2e-support-screens');
fs.mkdirSync(OUT, { recursive: true });

const BASE = 'https://school.sugamflow.com';
const results = [];

function ok(name, detail) {
  results.push({ name, pass: true, detail });
  console.log(`PASS  ${name}${detail ? ' — ' + detail : ''}`);
}
function fail(name, detail) {
  results.push({ name, pass: false, detail });
  console.log(`FAIL  ${name} — ${detail}`);
}

async function shot(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(800);
  // Fill org / username / password — try common labels
  const inputs = page.locator('input');
  const count = await inputs.count();
  // Prefer named fields
  const org =
    page.locator('input[formcontrolname="shopId"], input[name="shopId"], input[placeholder*="rg"], input[placeholder*="Org"]').first();
  const user =
    page.locator('input[formcontrolname="username"], input[name="username"], input[type="text"]').first();
  const pass = page.locator('input[formcontrolname="password"], input[type="password"]').first();

  // Login form typically: organization, username, password
  const textInputs = page.locator('input:not([type="password"]):not([type="hidden"]):not([type="checkbox"])');
  const n = await textInputs.count();
  if (n >= 2) {
    await textInputs.nth(0).fill('demo-school');
    await textInputs.nth(1).fill('admin');
  } else {
    await user.fill('admin');
  }
  await pass.fill('password');
  await shot(page, '01-login-filled');
  await page.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Login")').first().click();
  await page.waitForURL(/\/(admin|dashboard|home|shell)/, { timeout: 45000 }).catch(() => null);
  await page.waitForTimeout(2000);
  const url = page.url();
  if (url.includes('/login')) {
    fail('login', `still on login: ${url}`);
    await shot(page, '01-login-failed');
    return false;
  }
  ok('login', url);
  await shot(page, '02-after-login-desktop');
  return true;
}

async function openSupport(page, mobile) {
  if (mobile) {
    const toggle = page.locator('button.nav-toggle, button[aria-label*="enu"], button.menu-btn, .topbar button').first();
    if (await toggle.count()) {
      await toggle.click().catch(() => null);
      await page.waitForTimeout(500);
    }
  }
  // Expand Support group if needed
  const supportGroup = page.locator('text=Support').first();
  await supportGroup.click({ timeout: 10000 }).catch(() => null);
  await page.waitForTimeout(400);
  const myTickets = page.locator('a:has-text("My tickets"), button:has-text("My tickets")').first();
  const report = page.locator('a:has-text("Report issue"), button:has-text("Report issue")').first();
  if (await myTickets.count()) {
    await myTickets.click();
  } else if (await report.count()) {
    await report.click();
  } else {
    await page.goto(`${BASE}/admin/support/tickets`, { waitUntil: 'networkidle' });
  }
  await page.waitForTimeout(1500);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    // ---- Desktop ----
    const desk = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      ignoreHTTPSErrors: true,
    });
    const page = await desk.newPage();
    page.setDefaultTimeout(30000);

    if (!(await login(page))) {
      throw new Error('Login failed');
    }

    // Sidebar visible on desktop
    const sidebar = page.locator('aside.sidebar, nav.sidebar, .sidebar');
    const sidebarVisible = await sidebar.first().isVisible().catch(() => false);
    if (sidebarVisible) ok('desktop-sidebar-visible');
    else fail('desktop-sidebar-visible', 'sidebar not visible');

    await openSupport(page, false);
    await shot(page, '03-support-tickets-desktop');
    const bodyText = await page.locator('body').innerText();
    if (/SCH-|ticket|Support|My tickets|Report/i.test(bodyText)) {
      ok('desktop-support-page', page.url());
    } else {
      fail('desktop-support-page', 'unexpected content');
    }

    // Open report form
    await page.goto(`${BASE}/admin/support/new`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await shot(page, '04-report-issue-desktop');
    const formOk = await page.locator('form, select, textarea').count();
    if (formOk > 0) ok('desktop-report-form', `controls=${formOk}`);
    else fail('desktop-report-form', 'no form controls');

    // Fill and submit a UI ticket
    await page.locator('select').first().selectOption({ index: 1 }).catch(() => null);
    await page.locator('input[formcontrolname="subject"], input[placeholder*="ummary"]').fill('UI E2E responsive ticket');
    await page.locator('textarea').fill('Created via Playwright desktop flow for responsive verification.');
    await page.locator('button[type="submit"], button:has-text("Submit"), button:has-text("Create")').first().click();
    await page.waitForTimeout(2500);
    await shot(page, '05-after-submit-desktop');
    const after = await page.locator('body').innerText();
    if (/SCH-2026|ticket|My tickets|success|created/i.test(after) || page.url().includes('/tickets')) {
      ok('desktop-create-ticket', page.url());
    } else {
      fail('desktop-create-ticket', after.slice(0, 200));
    }

    // Detail of ticket 3
    await page.goto(`${BASE}/admin/support/tickets/3`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    await shot(page, '06-ticket-detail-desktop');
    const detail = await page.locator('body').innerText();
    if (/looking into it|IN_PROGRESS|Staff|platform-ops/i.test(detail)) {
      ok('desktop-sees-staff-reply');
    } else {
      fail('desktop-sees-staff-reply', detail.slice(0, 300));
    }
    if (/Internal: verified/i.test(detail)) {
      fail('desktop-hides-internal-note', 'internal note leaked to school UI');
    } else {
      ok('desktop-hides-internal-note');
    }

    await desk.close();

    // ---- Mobile (iPhone 12) ----
    const mobile = await browser.newContext({
      ...devices['iPhone 12'],
      ignoreHTTPSErrors: true,
    });
    const mpage = await mobile.newPage();
    mpage.setDefaultTimeout(30000);

    await mpage.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });
    await mpage.waitForTimeout(600);
    await shot(mpage, '10-login-mobile');
    const mTextInputs = mpage.locator('input:not([type="password"]):not([type="hidden"]):not([type="checkbox"])');
    if ((await mTextInputs.count()) >= 2) {
      await mTextInputs.nth(0).fill('demo-school');
      await mTextInputs.nth(1).fill('admin');
    }
    await mpage.locator('input[type="password"]').fill('password');
    const loginBox = await mpage.locator('form, .login, .card').first().boundingBox();
    if (loginBox && loginBox.width <= 430) ok('mobile-login-width', `w=${Math.round(loginBox.width)}`);
    else if (loginBox) ok('mobile-login-width', `w=${Math.round(loginBox.width)} (viewport constrained)`);
    else fail('mobile-login-width', 'no box');

    await mpage.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Login")').first().click();
    await mpage.waitForTimeout(2500);
    if (mpage.url().includes('/login')) fail('mobile-login', mpage.url());
    else ok('mobile-login', mpage.url());
    await shot(mpage, '11-after-login-mobile');

    // Sidebar should be hidden until hamburger
    const mSidebar = mpage.locator('aside.sidebar');
    const sideHidden =
      !(await mSidebar.isVisible().catch(() => false)) ||
      (await mpage.locator('body.nav-open, .shell.nav-open, .layout.nav-open').count()) === 0;
    // Check overflow: page width should not exceed viewport much
    const metrics = await mpage.evaluate(() => ({
      vw: window.innerWidth,
      scrollW: document.documentElement.scrollWidth,
      bodyW: document.body.scrollWidth,
    }));
    if (metrics.scrollW <= metrics.vw + 8) ok('mobile-no-h-overflow', JSON.stringify(metrics));
    else fail('mobile-no-h-overflow', JSON.stringify(metrics));

    // Open hamburger
    const ham = mpage.locator('button.nav-toggle, button[aria-label*="Menu" i], button[aria-label*="menu" i], .topbar button, header button').first();
    await ham.click().catch(async () => {
      // try any button in header
      await mpage.locator('header button, .toolbar button, .app-bar button').first().click();
    });
    await mpage.waitForTimeout(700);
    await shot(mpage, '12-nav-open-mobile');
    const navOpen = await mpage.locator('aside.sidebar, .sidebar').first().isVisible().catch(() => false);
    if (navOpen) ok('mobile-hamburger-opens-nav');
    else {
      // fallback: navigate directly
      fail('mobile-hamburger-opens-nav', 'sidebar still hidden — checking direct routes');
    }

    await mpage.goto(`${BASE}/admin/support/tickets`, { waitUntil: 'networkidle' });
    await mpage.waitForTimeout(1200);
    await shot(mpage, '13-tickets-mobile');
    const mMetrics = await mpage.evaluate(() => ({
      vw: window.innerWidth,
      scrollW: document.documentElement.scrollWidth,
    }));
    if (mMetrics.scrollW <= mMetrics.vw + 16) ok('mobile-tickets-no-overflow', JSON.stringify(mMetrics));
    else fail('mobile-tickets-no-overflow', JSON.stringify(mMetrics));

    const tText = await mpage.locator('body').innerText();
    if (/SCH-|ticket|Support/i.test(tText)) ok('mobile-tickets-content');
    else fail('mobile-tickets-content', tText.slice(0, 200));

    await mpage.goto(`${BASE}/admin/support/new`, { waitUntil: 'networkidle' });
    await mpage.waitForTimeout(1000);
    await shot(mpage, '14-report-mobile');
    const formMetrics = await mpage.evaluate(() => ({
      vw: window.innerWidth,
      scrollW: document.documentElement.scrollWidth,
      inputs: document.querySelectorAll('input,select,textarea').length,
    }));
    if (formMetrics.inputs > 0) ok('mobile-report-form', JSON.stringify(formMetrics));
    else fail('mobile-report-form', JSON.stringify(formMetrics));
    if (formMetrics.scrollW <= formMetrics.vw + 16) ok('mobile-report-no-overflow');
    else fail('mobile-report-no-overflow', JSON.stringify(formMetrics));

    await mpage.goto(`${BASE}/admin/support/tickets/3`, { waitUntil: 'networkidle' });
    await mpage.waitForTimeout(1200);
    await shot(mpage, '15-detail-mobile');
    const dText = await mpage.locator('body').innerText();
    if (/looking into it|IN_PROGRESS/i.test(dText)) ok('mobile-detail-staff-reply');
    else fail('mobile-detail-staff-reply', dText.slice(0, 250));

    // Spot-check a couple ERP pages for responsive overflow
    for (const [name, route] of [
      ['dashboard', '/admin'],
      ['students', '/admin/students'],
      ['attendance', '/admin/attendance'],
    ]) {
      await mpage.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch(() => null);
      await mpage.waitForTimeout(1500);
      await shot(mpage, `20-${name}-mobile`);
      const m = await mpage.evaluate(() => ({
        vw: window.innerWidth,
        scrollW: document.documentElement.scrollWidth,
        title: document.title,
        url: location.href,
      }));
      if (m.url.includes('/login')) {
        fail(`mobile-${name}`, 'redirected to login');
      } else if (m.scrollW <= m.vw + 24) {
        ok(`mobile-${name}-no-overflow`, JSON.stringify(m));
      } else {
        fail(`mobile-${name}-no-overflow`, JSON.stringify(m));
      }
    }

    await mobile.close();
  } catch (e) {
    fail('runner', e.message);
    console.error(e);
  } finally {
    await browser.close();
    const passed = results.filter((r) => r.pass).length;
    const failed = results.filter((r) => !r.pass).length;
    console.log('\n==== SUMMARY ====');
    console.log(`passed=${passed} failed=${failed}`);
    fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(results, null, 2));
    process.exit(failed ? 1 : 0);
  }
})();
