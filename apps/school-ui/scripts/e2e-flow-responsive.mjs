import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const outDir = 'D:/school/apps/school-ui/scripts';
fs.mkdirSync(outDir, { recursive: true });

const results = [];
function ok(name, pass, detail = '') {
  results.push({ name, pass: !!pass, detail: String(detail || '') });
  console.log(`${pass ? 'PASS' : 'FAIL'} | ${name}${detail ? ` — ${detail}` : ''}`);
}

async function login(page) {
  await page.goto('http://127.0.0.1:4301/login', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  const i = page.locator('form input');
  await i.nth(0).fill('demo-school');
  await i.nth(1).fill('admin');
  await i.nth(2).fill('password');
  await page.locator('form button[type=submit]').click({ force: true });
  await page.waitForTimeout(2800);
}

async function runDesktop(page) {
  await login(page);
  ok('Login reaches admin', /\/admin/.test(page.url()), page.url());

  // Students list
  await page.goto('http://127.0.0.1:4301/admin/students', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(3000);
  const studentRows = await page.locator('table tbody tr').count();
  ok('Students list loads', studentRows > 0, `rows=${studentRows}`);

  // Open via link should set ?id=
  const openLink = page.getByRole('link', { name: 'Open' }).first();
  await openLink.click();
  await page.waitForTimeout(2000);
  const detailUrl = page.url();
  ok('Open navigates to student detail', /[?&]id=/.test(detailUrl), detailUrl);

  // Direct detail for Kunal
  await page.goto(
    'http://127.0.0.1:4301/admin/students?id=bd1f9a51-a4e0-484e-98c5-5c2ab648504a',
    { waitUntil: 'domcontentloaded' },
  );
  await page.waitForTimeout(2500);
  const grid = await page.locator('.read-grid').innerText();
  ok('Student detail Class shows grade level', /CLASS\s*\nGrade 8\b/.test(grid), grid.slice(0, 220));
  ok(
    'Student detail Class section shows grade-section',
    /CLASS SECTION\s*\nGrade 8-A/.test(grid),
    grid.slice(0, 220),
  );

  await page.getByRole('button', { name: 'Edit', exact: true }).click();
  await page.waitForTimeout(800);
  const houseSelect = page
    .locator('select')
    .filter({ has: page.locator('option', { hasText: 'Red House' }) });
  ok('Student edit has House dropdown', (await houseSelect.count()) > 0);
  await page.getByRole('button', { name: 'Cancel' }).click().catch(() => {});
  await page.screenshot({ path: path.join(outDir, 'e2e-student-detail.png') });

  // Directory filter
  await page.goto('http://127.0.0.1:4301/admin/student-directory', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  const toggle = page.getByRole('button', { name: /filters/i });
  if (await toggle.count()) await toggle.click();
  await page.waitForTimeout(400);
  const classSelect = page.locator('select[name=classSection]');
  const opts = await classSelect.locator('option').allTextContents();
  ok('Directory Class/Section is dropdown', opts.length > 1, opts.join(', '));
  await classSelect.selectOption({ label: 'Grade 8-A' });
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await page.waitForTimeout(2000);
  const classes = await page.locator('.dir-table tbody tr td:nth-child(3)').allTextContents();
  ok(
    'Directory filter Grade 8-A works',
    classes.length > 0 && classes.every((c) => c.includes('Grade 8-A')),
    classes.join('|'),
  );
  await page.screenshot({ path: path.join(outDir, 'e2e-directory.png') });

  // 360
  await page.goto(
    'http://127.0.0.1:4301/admin/students/790bbd3c-ff5b-4a37-baa5-845a4f287477/360',
    { waitUntil: 'domcontentloaded' },
  );
  await page.waitForTimeout(3000);
  const tabs = (await page.locator('.tabs button').allTextContents()).map((t) => t.trim());
  ok(
    '360 tabs titlecased',
    tabs.includes('Profile') && tabs.includes('Guardians'),
    tabs.join(','),
  );
  ok(
    '360 shows house chip',
    (await page.locator('.house-chip').count()) > 0,
    await page
      .locator('.house-chip')
      .first()
      .innerText()
      .catch(() => ''),
  );
  await page.screenshot({ path: path.join(outDir, 'e2e-360.png') });

  // Admission
  await page.goto('http://127.0.0.1:4301/admin/admission?new=1', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(2500);
  const admHouse = page
    .locator('select')
    .filter({ has: page.locator('option', { hasText: 'Red House' }) });
  const admOpts = await admHouse.locator('option').allTextContents().catch(() => []);
  ok('Admission has House dropdown', admOpts.some((o) => /Red House/.test(o)), admOpts.join(','));
  const namedHouse = await page.locator('select[name=house]').count();
  ok('Admission house select has name attribute', namedHouse === 1, `count=${namedHouse}`);
  await page.screenshot({ path: path.join(outDir, 'e2e-admission.png') });

  // Design Studio
  await page.goto('http://127.0.0.1:4301/admin/design-studio', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  const colors = await page.locator('input[type=color]').count();
  ok(
    'Design Studio color settings open',
    (await page.getByRole('heading', { name: 'Design Studio' }).count()) === 1 && colors >= 10,
    `colors=${colors}`,
  );

  // Lifecycle
  await page.goto('http://127.0.0.1:4301/admin/lifecycle', {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  ok(
    'Promotions & Transfers page opens',
    (await page.getByRole('heading', { name: /Promotions|Lifecycle|Transfers/i }).count()) > 0,
  );
  await page.screenshot({ path: path.join(outDir, 'e2e-lifecycle.png') });
}

async function runResponsive(browser) {
  const viewports = [
    { name: 'mobile', width: 375, height: 812 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'desktop', width: 1440, height: 900 },
  ];

  for (const vp of viewports) {
    const page = await browser.newPage({
      viewport: { width: vp.width, height: vp.height },
    });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));

    await login(page);
    await page.goto('http://127.0.0.1:4301/admin/student-directory', {
      waitUntil: 'domcontentloaded',
      timeout: 60000,
    });
    await page.waitForTimeout(3000);

    const overflowX = await page.evaluate(() => {
      const doc = document.documentElement;
      return Math.max(
        doc.scrollWidth - window.innerWidth,
        document.body.scrollWidth - window.innerWidth,
      );
    });
    ok(`${vp.name} no major horizontal overflow`, overflowX <= 40, `overflowX=${overflowX}`);
    ok(
      `${vp.name} page title visible`,
      await page
        .getByRole('heading', { name: /Student Directory|Students/i })
        .first()
        .isVisible()
        .catch(() => false),
    );

    if (vp.name === 'mobile' || vp.name === 'tablet') {
      const hamburger = page.locator('.context-menu-toggle');
      ok(`${vp.name} has nav toggle control`, (await hamburger.count()) > 0);
      if (await hamburger.count()) {
        await hamburger.click();
        await page.waitForTimeout(500);
        ok(`${vp.name} drawer opens`, (await page.locator('.shell.nav-open').count()) > 0);
        const closeBtn = page.getByRole('button', { name: 'Close navigation' });
        if (await closeBtn.isVisible().catch(() => false)) {
          await closeBtn.click();
        } else {
          const scrim = page.locator('.nav-scrim');
          const box = await scrim.boundingBox();
          if (box) {
            await page.mouse.click(box.x + box.width - 12, box.y + box.height / 2);
          }
        }
        await page.waitForTimeout(500);
        ok(
          `${vp.name} drawer dismisses`,
          (await page.locator('.shell.nav-open').count()) === 0,
        );
      }
    }

    ok(`${vp.name} directory table present`, (await page.locator('table').count()) > 0);

    const filtersBtn = page.getByRole('button', { name: /filters/i });
    if (await filtersBtn.count()) {
      await filtersBtn.click();
      await page.waitForTimeout(400);
      ok(
        `${vp.name} class dropdown usable`,
        await page.locator('select[name=classSection]').isVisible().catch(() => false),
      );
    } else {
      ok(`${vp.name} class dropdown usable`, false, 'no filters button');
    }

    await page.goto('http://127.0.0.1:4301/admin/students', {
      waitUntil: 'domcontentloaded',
    });
    await page.waitForTimeout(2500);
    const studentsOverflow = await page.evaluate(() => {
      const doc = document.documentElement;
      return Math.max(
        doc.scrollWidth - window.innerWidth,
        document.body.scrollWidth - window.innerWidth,
      );
    });
    // Wide tables may scroll horizontally inside the panel — page-level overflow under 120px is ok.
    ok(
      `${vp.name} students page overflow ok`,
      studentsOverflow <= 120,
      `overflowX=${studentsOverflow}`,
    );

    await page.goto('http://127.0.0.1:4301/admin/design-studio', {
      waitUntil: 'domcontentloaded',
    });
    await page.waitForTimeout(2500);
    ok(
      `${vp.name} design studio usable`,
      (await page.locator('input[type=color]').count()) >= 10,
    );

    await page.screenshot({
      path: path.join(outDir, `e2e-responsive-${vp.name}.png`),
      fullPage: true,
    });
    ok(`${vp.name} no page errors`, errors.length === 0, errors.slice(0, 3).join(' | '));
    await page.close();
  }
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
try {
  await runDesktop(page);
} catch (e) {
  ok('Desktop suite threw', false, e.message);
}
await page.close();
try {
  await runResponsive(browser);
} catch (e) {
  ok('Responsive suite threw', false, e.message);
}
await browser.close();

const failed = results.filter((r) => !r.pass);
console.log(
  `SUMMARY total=${results.length} pass=${results.length - failed.length} fail=${failed.length}`,
);
fs.writeFileSync(
  path.join(outDir, 'e2e-results.json'),
  JSON.stringify({ results, failed }, null, 2),
);
process.exit(failed.length ? 2 : 0);
