const { chromium } = require('playwright');

(async () => {
  const b = await chromium.launch({ headless: true });
  const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
  await p.goto('http://localhost:4300/login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  const inputs = p.locator('form input');
  await inputs.nth(0).fill('demo-school');
  await inputs.nth(1).fill('admin');
  await inputs.nth(2).fill('password');
  await p.getByRole('button', { name: /sign in|login|continue/i }).click();
  await p.waitForTimeout(3000);

  await p.goto('http://localhost:4300/admin/library', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(2500);
  const tabs = p.locator('nav.lib-tabs');
  await tabs.getByRole('button', { name: /Workflow inbox/ }).click();
  await p.waitForTimeout(2500);

  const table = p.locator('.inbox-table');
  const headers = await table.locator('thead th').allInnerTexts();
  const rowCount = await table.locator('tbody tr.inbox-row').count();
  const chips = await table.locator('.status-chip').allInnerTexts();

  // click first row -> detail should populate
  if (rowCount > 0) {
    await table.locator('tbody tr.inbox-row').first().click();
    await p.waitForTimeout(1500);
  }
  const selected = await table.locator('tbody tr.inbox-row.selected').count();
  const detailText = (await p.locator('.library-layout section.panel').nth(1).innerText()).slice(0, 200);

  console.log('HEADERS', JSON.stringify(headers.map((h) => h.trim())));
  console.log('ROWS', rowCount, 'SELECTED', selected);
  console.log('CHIPS', JSON.stringify([...new Set(chips)]));
  console.log('DETAIL', JSON.stringify(detailText));

  await p.screenshot({ path: 'D:/school/apps/school-ui/scripts/inbox-redesign.png', fullPage: false });

  // mobile check
  await p.setViewportSize({ width: 390, height: 844 });
  await p.waitForTimeout(1000);
  const overflow = await p.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2);
  console.log('MOBILE_OVERFLOW', overflow);
  await p.screenshot({ path: 'D:/school/apps/school-ui/scripts/inbox-redesign-mobile.png', fullPage: false });

  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
