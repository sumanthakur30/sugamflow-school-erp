const { chromium } = require('playwright');

const report = {
  sectionId: '11111111-1111-1111-1111-111111111111',
  sectionLabel: 'Grade 8-A',
  termKey: 'TERM2',
  publishedOnly: true,
  subjects: [
    { subjectId: 'math', name: 'Mathematics' },
    { subjectId: 'science', name: 'Science' },
  ],
  students: [
    {
      studentId: '22222222-2222-2222-2222-222222222222',
      admissionNo: 'ADM-001',
      studentName: 'Asha Sharma',
      classSection: 'Grade 8-A',
      subjects: [
        { subjectId: 'math', name: 'Mathematics', marksObtained: 90, maxMarks: 100, grade: 'A+' },
        { subjectId: 'science', name: 'Science', marksObtained: 45, maxMarks: 100, grade: 'D' },
      ],
      totalObtained: 135,
      totalMax: 200,
      percentage: 67.5,
      overallGrade: 'B',
      result: 'PASS',
    },
  ],
};

const insight = {
  studentId: report.students[0].studentId,
  admissionNo: 'ADM-001',
  studentName: 'Asha Sharma',
  sectionId: report.sectionId,
  termKey: 'TERM2',
  summary: {
    currentPercentage: 67.5,
    previousPercentage: 50,
    changePercentagePoints: 17.5,
    trend: 'IMPROVING',
    overallMessage: 'Performance improved by 17.5 percentage points from the previous assessed term.',
  },
  strengths: [
    {
      subjectId: 'math',
      subject: 'Mathematics',
      percentage: 90,
      label: 'Strong understanding',
      recommendation: 'Continue extension work and apply this strength in projects or peer learning.',
    },
  ],
  focusAreas: [
    {
      subjectId: 'science',
      subject: 'Science',
      percentage: 45,
      label: 'Foundation needs reinforcement',
      recommendation: 'Schedule two focused practice sessions weekly and correct errors with feedback.',
    },
  ],
  recommendedActions: [
    'Prioritize Science in the next study plan.',
    'Use one short diagnostic exercise each week and review incorrect answers.',
  ],
  termTrend: [
    { termKey: 'TERM1', percentage: 50, assessments: 2, current: false },
    { termKey: 'TERM2', percentage: 67.5, assessments: 2, current: true },
  ],
  evidence: { subjectsAnalyzed: 2, termsAnalyzed: 2 },
  disclaimer:
    'Insights are generated from published marks only. Teachers should apply professional judgment and consider attendance, learning needs, and context.',
};

function envelope(data) {
  return { success: true, data, message: null };
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('http://localhost:4300/login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  const inputs = page.locator('form input');
  await inputs.nth(0).fill('demo-school');
  await inputs.nth(1).fill('admin');
  await inputs.nth(2).fill('password');
  await page.getByRole('button', { name: /sign in|login|continue/i }).click();
  await page.waitForTimeout(2200);

  await page.route('**/api/academic/sections', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([{ id: report.sectionId, name: 'A', studentLabel: 'Grade 8-A' }]),
      ),
    }),
  );
  await page.route('**/api/exam/report-cards?**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(report)),
    }),
  );
  await page.route('**/api/exam/report-cards/student/insights?**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(insight)),
    }),
  );
  await page.route('**/api/exam/report-cards/mine', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            sectionId: report.sectionId,
            sectionLabel: report.sectionLabel,
            termKey: report.termKey,
            student: report.students[0],
          },
        ]),
      ),
    }),
  );
  await page.route('**/api/exam/report-cards/mine/insights?**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(insight)),
    }),
  );

  await page.goto('http://localhost:4300/teacher/report-cards', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(1800);
  await page.locator('input[name="termKey"]').fill('TERM2');
  await page.locator('input[name="termKey"]').blur();
  await page.waitForTimeout(300);
  await page.getByRole('button', { name: 'Insights', exact: true }).click();
  await page.waitForTimeout(500);

  const panel = page.locator('sf-report-card-insights').first();
  const text = await panel.innerText();
  console.log('TEACHER_INSIGHTS', /Improving/.test(text), /Mathematics/.test(text), /Science/.test(text));
  console.log('TERM_TREND', /TERM1/.test(text), /TERM2/.test(text));
  console.log('PAGE_ERRORS', errors.length);
  await panel.scrollIntoViewIfNeeded();
  await page.screenshot({
    path: 'D:/school/apps/school-ui/scripts/report-card-insights.png',
    fullPage: false,
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
  );
  console.log('MOBILE_PAGE_OVERFLOW', overflow);
  if (overflow) {
    const offenders = await page.evaluate(() => {
      const width = document.documentElement.clientWidth;
      return [...document.querySelectorAll('*')]
        .map((element) => {
          const rect = element.getBoundingClientRect();
          return {
            name: `${element.tagName}.${String(element.className).split(' ').slice(0, 3).join('.')}`,
            width: Math.round(rect.width),
            right: Math.round(rect.right),
          };
        })
        .filter((item) => item.right > width + 2)
        .slice(0, 15);
    });
    console.log('OFFENDERS', JSON.stringify(offenders));
  }

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://localhost:4300/parent/report-cards', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(1200);
  await page.getByRole('button', { name: 'View performance insights' }).click();
  await page.waitForTimeout(400);
  const parentText = await page.locator('sf-report-card-insights').first().innerText();
  console.log('PARENT_INSIGHTS', /Improving/.test(parentText), /Science/.test(parentText));

  if (
    !/Improving/.test(text) ||
    !/Mathematics/.test(text) ||
    !/Science/.test(text) ||
    !/Improving/.test(parentText) ||
    !/Science/.test(parentText) ||
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
