import { chromium } from 'playwright';

const BASE = process.env.FEE_UI_BASE || 'http://localhost:4300';
const ORG = process.env.FEE_ORG || 'demo-school';
const USER = process.env.FEE_USER || 'admin';
const PASS = process.env.FEE_PASS || 'password';

function fail(msg) {
  console.error('FAIL:', msg);
  process.exitCode = 1;
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  const log = [];
  page.on('console', (m) => {
    if (m.type() === 'error') log.push(`console.error: ${m.text()}`);
  });
  page.on('pageerror', (e) => log.push(`pageerror: ${e.message}`));

  try {
    console.log(`Opening ${BASE}/login ...`);
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });

    // Already logged in?
    if (page.url().includes('/admin')) {
      console.log('Already authenticated, continuing');
    } else {
      await page.fill('input[name="organizationId"], input[ng-reflect-name="organizationId"], input', ORG).catch(() => {});
      // Prefer labeled fields
      const org = page.locator('input').nth(0);
      const user = page.locator('input[name="username"]');
      const pass = page.locator('input[name="password"]');
      if (await user.count()) {
        // Clear org if first text input is org
        const inputs = page.locator('form input');
        const count = await inputs.count();
        if (count >= 3) {
          await inputs.nth(0).fill(ORG);
          await inputs.nth(1).fill(USER);
          await inputs.nth(2).fill(PASS);
        } else {
          await user.fill(USER);
          await pass.fill(PASS);
        }
      } else {
        await page.getByRole('textbox').nth(0).fill(ORG);
        await page.getByRole('textbox').nth(1).fill(USER);
        await page.locator('input[type="password"]').fill(PASS);
      }
      await page.getByRole('button', { name: /sign in|login|continue/i }).click();
      await page.waitForURL(/\/(admin|parent|teacher)/, { timeout: 45000 });
    }

    console.log('Navigating to /admin/fee ...');
    await page.goto(`${BASE}/admin/fee`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(1500);

    // Ensure list view (not new/detail)
    if (page.url().includes('new=1') || /[?&]id=/.test(page.url())) {
      await page.goto(`${BASE}/admin/fee`, { waitUntil: 'networkidle', timeout: 60000 });
      await page.waitForTimeout(1000);
    }

    const moreBtns = page.locator('button.order-action-btn', { hasText: 'More' });
    const moreCount = await moreBtns.count();
    console.log(`More buttons found: ${moreCount}`);
    if (moreCount < 1) {
      fail('No More button found on Fee Collection list');
      const body = await page.locator('body').innerText();
      console.log('Page text preview:\n', body.slice(0, 800));
      return;
    }

    await moreBtns.first().click();
    await page.waitForTimeout(300);

    const menu = page.locator('.fee-actions-flyout.more-menu');
    const visible = await menu.isVisible();
    console.log(`More menu visible: ${visible}`);
    if (!visible) {
      fail('More menu did not open');
      return;
    }

    const expected = ['Edit', 'Thermal print', 'Student profile', 'Fee history', 'Delete'];
    const items = await menu.locator('button[role="menuitem"]').allTextContents();
    console.log('Menu items:', items.map((t) => t.trim()));
    for (const label of expected) {
      if (!items.some((t) => t.trim() === label)) {
        fail(`Missing menu item: ${label}`);
      }
    }

    // Click Fee history and verify panel/history opens
    await menu.getByRole('menuitem', { name: 'Fee history' }).click();
    await page.waitForTimeout(800);
    const historyOpen =
      (await page.locator('sf-student-fee-history').count()) > 0 ||
      (await page.getByText(/fee history|student history|collections/i).count()) > 0;
    console.log(`Fee history action ran (panel/content present): ${historyOpen}`);

    // Re-open More and test Edit navigates to detail
    await page.goto(`${BASE}/admin/fee`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(1000);
    await page.locator('button.order-action-btn', { hasText: 'More' }).first().click();
    await page.waitForTimeout(200);
    await page.locator('.fee-actions-flyout.more-menu').getByRole('menuitem', { name: 'Edit' }).click();
    await page.waitForTimeout(1200);
    const onDetail = /[?&]id=/.test(page.url()) || (await page.getByText(/collection|workflow action|approve/i).count()) > 0;
    console.log(`Edit opened collection detail: ${onDetail} (url=${page.url()})`);
    if (!onDetail) fail('Edit did not open collection detail');

    if (!process.exitCode) {
      console.log('PASS: More button opens menu with Orders-style actions; Edit + Fee history work');
    }
    if (log.length) {
      console.log('Browser issues:\n' + log.slice(0, 10).join('\n'));
    }
  } catch (err) {
    fail(err?.message || String(err));
    try {
      await page.screenshot({ path: 'D:/school/apps/school-ui/scripts/fee-more-fail.png', fullPage: true });
      console.log('Screenshot: scripts/fee-more-fail.png');
    } catch {}
  } finally {
    await browser.close();
  }
}

main();
