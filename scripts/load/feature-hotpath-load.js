/**
 * Hot-path load for recent UI features:
 *  - attendance roster GET
 *  - staff directory search (payroll lookup)
 *  - student directory search (fee/scan lookup)
 *
 *   k6 run feature-hotpath-load.js -e QUICK=1
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:9090';
const QUICK = ['1', 'true', 'yes'].includes((__ENV.QUICK || 'false').toLowerCase());
const THINK_MS = Number(__ENV.THINK_TIME_MS || (QUICK ? '200' : '350'));
const BRANCH = __ENV.BRANCH_ID || 'main';
const SESSION = __ENV.ACADEMIC_SESSION_ID || '2025-26';
const DATE = __ENV.ROSTER_DATE || new Date().toISOString().slice(0, 10);

const rosterMs = new Trend('hot_roster_ms', true);
const staffMs = new Trend('hot_staff_ms', true);
const studentMs = new Trend('hot_student_ms', true);
const failRate = new Rate('hot_fail_rate');
const failCount = new Counter('hot_failures');

const SCHOOLS = [
  { shopId: 'demo-school', username: 'admin_demo-school', password: 'password' },
  { shopId: 'load-school-01', username: 'admin_load-school-01', password: 'password' },
  { shopId: 'load-school-02', username: 'admin_load-school-02', password: 'password' },
  { shopId: 'load-school-03', username: 'admin_load-school-03', password: 'password' },
  { shopId: 'load-school-04', username: 'admin_load-school-04', password: 'password' },
  { shopId: 'load-school-05', username: 'admin_load-school-05', password: 'password' }
];

export const options = QUICK
  ? {
      setupTimeout: '3m',
      vus: Number(__ENV.VUS || '12'),
      duration: __ENV.DURATION || '60s',
      thresholds: {
        http_req_failed: ['rate<0.12'],
        hot_fail_rate: ['rate<0.15'],
        hot_roster_ms: ['p(95)<3000'],
        hot_staff_ms: ['p(95)<2500'],
        hot_student_ms: ['p(95)<2500']
      }
    }
  : {
      setupTimeout: '3m',
      vus: Number(__ENV.VUS || '18'),
      duration: __ENV.DURATION || '2m',
      thresholds: {
        http_req_failed: ['rate<0.10'],
        hot_fail_rate: ['rate<0.12']
      }
    };

function login(school) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      shopId: school.shopId,
      username: school.username,
      password: school.password
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '30s',
      tags: { name: 'hot-login', school: school.shopId }
    }
  );
  let token = '';
  try {
    token = res.json('accessToken') || '';
  } catch (_) {}
  return res.status === 200 && token ? token : '';
}

function headers(token, shopId) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Tenant-Id': shopId,
    'X-Shop-Id': shopId,
    'X-Branch-Id': BRANCH,
    'X-Academic-Session-Id': SESSION
  };
}

function pickSectionId(body) {
  const data = body?.data ?? body;
  const list = Array.isArray(data)
    ? data
    : data?.items || data?.content || data?.sections || [];
  if (!Array.isArray(list) || !list.length) return '';
  const first = list[0];
  return first?.id || first?.sectionId || '';
}

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) throw new Error(`gateway unhealthy: ${health.status}`);

  const ctx = {};
  for (const school of SCHOOLS) {
    const token = login(school);
    if (!token) throw new Error(`login failed ${school.shopId}`);
    const h = headers(token, school.shopId);
    let sectionId = '';
    for (const path of ['/api/academic/sections', '/api/academic/bootstrap']) {
      const res = http.get(`${BASE_URL}${path}`, { headers: h, timeout: '20s' });
      if (res.status >= 200 && res.status < 300) {
        try {
          sectionId = pickSectionId(res.json());
        } catch (_) {}
        if (sectionId) break;
        if (path.endsWith('bootstrap')) {
          try {
            const boot = res.json();
            const secs = boot?.data?.sections || boot?.sections || [];
            if (Array.isArray(secs) && secs[0]) sectionId = secs[0].id || secs[0].sectionId || '';
          } catch (_) {}
        }
      }
    }
    ctx[school.shopId] = { token, sectionId };
  }
  return { ctx, date: DATE };
}

function mark(ok) {
  failRate.add(ok ? 0 : 1);
  if (!ok) failCount.add(1);
}

export default function (data) {
  const school = SCHOOLS[(__VU - 1) % SCHOOLS.length];
  const session = data.ctx[school.shopId];
  if (!session?.token) {
    sleep(0.5);
    return;
  }
  const h = headers(session.token, school.shopId);
  const q = encodeURIComponent(['a', 'pri', 'k', 'an', 'ra', 'om'][(__ITER + __VU) % 6]);

  if (session.sectionId) {
    const started = Date.now();
    const roster = http.get(
      `${BASE_URL}/api/attendance/roster?sectionId=${encodeURIComponent(session.sectionId)}&date=${data.date}`,
      { headers: h, timeout: '20s', tags: { name: 'attendance-roster', school: school.shopId } }
    );
    rosterMs.add(Date.now() - started);
    mark(
      check(roster, {
        'roster <500': (r) => r.status < 500,
        'roster not 401': (r) => r.status !== 401
      })
    );
  }

  {
    const started = Date.now();
    const staff = http.get(`${BASE_URL}/api/staff/directory/staff?page=0&size=20&q=${q}`, {
      headers: h,
      timeout: '20s',
      tags: { name: 'staff-directory', school: school.shopId }
    });
    staffMs.add(Date.now() - started);
    mark(
      check(staff, {
        'staff <500': (r) => r.status < 500,
        'staff not 401': (r) => r.status !== 401
      })
    );
  }

  {
    const started = Date.now();
    const students = http.get(`${BASE_URL}/api/student/students?page=0&size=20&q=${q}`, {
      headers: h,
      timeout: '20s',
      tags: { name: 'student-search', school: school.shopId }
    });
    studentMs.add(Date.now() - started);
    mark(
      check(students, {
        'student <500': (r) => r.status < 500,
        'student not 401': (r) => r.status !== 401
      })
    );
  }

  sleep(THINK_MS / 1000);
}

export function handleSummary(data) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outDir = __ENV.RESULT_DIR || 'results';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/feature-hotpath-${stamp}.json`]: JSON.stringify(data, null, 2)
  };
}
