const { chromium } = require('playwright');

(async () => {
  const b = await chromium.launch({ headless: true });
  const p = await b.newPage();
  const api = [];
  p.on('response', async (r) => {
    const u = r.url();
    if (!u.includes('/api/')) return;
    if (!/library|student|workflow|record|circulation/i.test(u)) return;
    try {
      const t = await r.text();
      api.push({
        status: r.status(),
        url: u.replace(/^https?:\/\/[^/]+/, ''),
        body: t.slice(0, 8000),
      });
    } catch {}
  });

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
  const issuedText = await p.locator('body').innerText();
  console.log('=== ISSUED TAB SNIPPET ===');
  const issuedMatch = issuedText.match(/Issued Books[\s\S]{0,2000}/i);
  console.log((issuedMatch ? issuedMatch[0] : issuedText).slice(0, 1800));

  await p.locator('nav.lib-tabs').getByRole('button', { name: /Workflow inbox/ }).click();
  await p.waitForTimeout(2500);
  const inboxText = await p.locator('.inbox-table').innerText().catch(() => '');
  console.log('=== INBOX TABLE ===');
  console.log(inboxText.slice(0, 2500));

  console.log('AYUSH_IN_ISSUED', /ayush/i.test(issuedText));
  console.log('AYUSH_IN_INBOX', /ayush/i.test(inboxText));

  console.log('=== API HITS ===');
  for (const a of api) {
    const mentionsAyush = /ayush/i.test(a.body);
    if (/circulation|records|students/i.test(a.url) || mentionsAyush) {
      console.log(a.status, a.url, 'ayush=', mentionsAyush, a.body.slice(0, 1200).replace(/\s+/g, ' '));
    }
  }

  await b.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
