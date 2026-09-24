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

  try {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 });
    if (!page.url().includes('/admin')) {
      const inputs = page.locator('form input');
      if ((await inputs.count()) >= 3) {
        await inputs.nth(0).fill(ORG);
        await inputs.nth(1).fill(USER);
        await inputs.nth(2).fill(PASS);
      }
      await page.getByRole('button', { name: /sign in|login|continue/i }).click();
      await page.waitForURL(/\/(admin|parent|teacher)/, { timeout: 45000 });
    }

    await page.goto(`${BASE}/admin/fee`, { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(1500);

    const more = page.locator('button.order-action-btn', { hasText: 'More' }).first();
    const moreBox = await more.boundingBox();
    await more.click();
    await page.waitForTimeout(400);

    const menu = page.locator('.more-menu-anchored');
    const visible = await menu.isVisible();
    const menuBox = await menu.boundingBox();
    console.log(JSON.stringify({ visible, moreBox, menuBox }, null, 2));

    if (!visible || !moreBox || !menuBox) {
      fail('Menu not visible near More');
      return;
    }

    const nearX =
      Math.abs(menuBox.x + menuBox.width - moreBox.x - moreBox.width) < 100 ||
      Math.abs(menuBox.x - moreBox.x) < 100;
    const nearY = menuBox.y >= moreBox.y - 8 && menuBox.y < moreBox.y + moreBox.height + 100;
    console.log(JSON.stringify({ nearX, nearY }));
    if (!nearX || !nearY) {
      fail(`Menu not anchored near More (nearX=${nearX}, nearY=${nearY})`);
      return;
    }

    const items = await menu.locator('button[role="menuitem"]').allTextContents();
    console.log('items', items.map((t) => t.trim()));
    for (const label of ['Edit', 'Thermal print', 'Student profile', 'Fee history', 'Delete']) {
      if (!items.some((t) => t.trim() === label)) fail(`Missing ${label}`);
    }

    if (!process.exitCode) console.log('PASS: More menu opens beside the More button');
  } catch (e) {
    fail(e?.message || String(e));
  } finally {
    await browser.close();
  }
}

main();
