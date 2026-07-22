const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });

  await page.goto('http://localhost:4300/login', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  const inputs = page.locator('form input');
  await inputs.nth(0).fill('demo-school');
  await inputs.nth(1).fill('admin');
  await inputs.nth(2).fill('password');
  await page.getByRole('button', { name: /sign in|login|continue/i }).click();
  await page.waitForTimeout(2500);

  await page.goto('http://localhost:4300/admin/timetable', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(3000);

  const section = page.locator('select[name="ttSection"]');
  await section.selectOption({ label: 'Grade 8-A' });
  await page.waitForTimeout(1800);

  const planner = page.locator('.ai-planner');
  await planner.getByRole('button', { name: 'Generate timetable' }).click();
  await page.waitForTimeout(2500);

  const summary = await planner.locator('.generation-summary').innerText();
  const conflicts = await planner.locator('.conflict-panel li').count();
  const filled = await page.locator('.tt-grid td.slot-filled').count();
  await planner.locator('details.constraint-details').evaluate((node) => {
    node.open = true;
  });
  const ruleCards = await planner.locator('.constraint-card').count();
  const availabilityButtons = await planner.locator('.availability-row button').count();

  console.log('SUMMARY', summary.replace(/\n+/g, ' | '));
  console.log('CONFLICTS', conflicts);
  console.log('FILLED_SLOTS', filled);
  console.log('RULE_CARDS', ruleCards);
  console.log('AVAILABILITY_BUTTONS', availabilityButtons);
  console.log('PAGE_ERRORS', errors.length);

  await planner.scrollIntoViewIfNeeded();
  await page.screenshot({
    path: 'D:/school/apps/school-ui/scripts/ai-timetable.png',
    fullPage: false,
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(700);
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
  );
  console.log('MOBILE_PAGE_OVERFLOW', overflow);

  if (
    !summary.includes('4 / 4') ||
    conflicts !== 0 ||
    filled !== 4 ||
    ruleCards < 1 ||
    availabilityButtons < 1 ||
    errors.length ||
    overflow
  ) {
    process.exitCode = 1;
  }
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
