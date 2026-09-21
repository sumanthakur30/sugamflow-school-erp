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
  await p.waitForTimeout(2500);
  await p.goto('http://localhost:4300/admin/library', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(3500);

  await p.locator('nav.lib-tabs').getByRole('button', { name: /Issued Books/ }).click();
  await p.waitForTimeout(2500);
  const issued = await p.locator('.lib-table').innerText();
  console.log('ISSUED_HAS_AYUSH', /ayush/i.test(issued));
  console.log('ISSUED_SNIPPET', issued.slice(0, 900).replace(/\n+/g, ' | '));

  await p.locator('nav.lib-tabs').getByRole('button', { name: /Workflow inbox/ }).click();
  await p.waitForTimeout(2000);
  const callout = await p.locator('.inbox-callout').innerText().catch(() => 'NO_CALLOUT');
  console.log('CALLOUT', callout.replace(/\n+/g, ' | '));
  const inbox = await p.locator('.inbox-table').innerText();
  console.log('INBOX_HAS_AYUSH', /ayush/i.test(inbox));

  await p.screenshot({ path: 'D:/school/apps/school-ui/scripts/ayush-issued-check.png' });
  await b.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
