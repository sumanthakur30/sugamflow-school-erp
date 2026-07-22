/**
 * Parent + teacher portal concurrent mix across multiple schools.
 *
 * Prerequisites:
 *   .\scripts\seed-portal-load-fixtures.ps1
 *   fixture file: scripts/load/portal-fixture.json
 *
 *   k6 run portal-mix-load.js -e QUICK=1
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:9090';
const QUICK = ['1', 'true', 'yes'].includes((__ENV.QUICK || 'false').toLowerCase());
const THINK_MS = Number(__ENV.THINK_TIME_MS || (QUICK ? '200' : '350'));
const BRANCH = __ENV.BRANCH_ID || 'main';
const SESSION = __ENV.ACADEMIC_SESSION_ID || 'load-portal';
const FIXTURE_PATH = __ENV.FIXTURE || 'portal-fixture.json';

const loginMs = new Trend('portal_login_ms', true);
const readMs = new Trend('portal_read_ms', true);
const failRate = new Rate('portal_fail_rate');
const failCount = new Counter('portal_failures');
const tenantLeak = new Counter('portal_tenant_leak');
const byRole = new Counter('portal_by_role');

const SCHOOLS = new SharedArray('portal-schools', () => {
  const raw = open(FIXTURE_PATH);
  const parsed = JSON.parse(raw);
  return parsed.schools || [];
});

const vuSession = {};

export const options = QUICK
  ? {
      setupTimeout: '3m',
      vus: Number(__ENV.VUS || String(Math.max(12, SCHOOLS.length * 2))),
      duration: __ENV.DURATION || '90s',
      thresholds: {
        http_req_failed: ['rate<0.12'],
        portal_fail_rate: ['rate<0.15'],
        portal_login_ms: ['p(95)<3000'],
        portal_read_ms: ['p(95)<2500'],
        portal_tenant_leak: ['count==0']
      }
    }
  : {
      setupTimeout: '3m',
      scenarios: {
        portals: {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: [
            { duration: '1m', target: Number(__ENV.STAGE1_VUS || '12') },
            { duration: '2m', target: Number(__ENV.STAGE2_VUS || '24') },
            { duration: '1m', target: 0 }
          ]
        }
      },
      thresholds: {
        http_req_failed: ['rate<0.10'],
        portal_fail_rate: ['rate<0.12'],
        portal_tenant_leak: ['count==0']
      }
    };

function schoolForVu() {
  return SCHOOLS[(__VU - 1) % SCHOOLS.length];
}

function personaForVu() {
  // Odd VUs = teacher, even VUs = parent
  return __VU % 2 === 1 ? 'teacher' : 'parent';
}

function markFail(ok) {
  failRate.add(ok ? 0 : 1);
  if (!ok) failCount.add(1);
}

function login(shopId, username, password) {
  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ shopId, username, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '30s',
      tags: { name: 'portal-login', school: shopId }
    }
  );
  loginMs.add(Date.now() - started);
  let token = '';
  try {
    token = res.json('accessToken') || '';
  } catch (_) {}
  const ok = check(res, {
    'login 200': (r) => r.status === 200,
    'login token': () => !!token
  });
  markFail(ok);
  return ok ? token : '';
}

function authHeaders(token, shopId) {
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

function getJson(path, h, label, school, role) {
  const started = Date.now();
  const res = http.get(`${BASE_URL}${path}`, {
    headers: h,
    timeout: '20s',
    tags: { name: label, school: school.shopId, role }
  });
  readMs.add(Date.now() - started);
  byRole.add(1, { role, op: label, school: school.shopId });

  const ok = check(res, {
    [`${label} not 5xx`]: (r) => r.status < 500,
    [`${label} not 401`]: (r) => r.status !== 401
  });
  markFail(ok);

  if (res.status >= 200 && res.status < 300) {
    try {
      const body = res.json();
      const items = body?.data?.items || body?.data?.content || [];
      if (Array.isArray(items)) {
        for (const row of items) {
          const org = row?.organizationId || row?.orgId || row?.tenantId;
          if (org && org !== school.shopId) {
            tenantLeak.add(1);
            console.error(`TENANT LEAK ${role} ${school.shopId} saw ${org} on ${label}`);
          }
        }
      }
    } catch (_) {}
  }
  return res;
}

function ensureSession(school, role, shared) {
  const key = `${__VU}:${school.shopId}:${role}`;
  const cur = vuSession[key];
  if (cur?.token) return cur.token;

  const sharedToken = shared?.[`${school.shopId}:${role}`];
  if (sharedToken) {
    vuSession[key] = { token: sharedToken };
    return sharedToken;
  }

  const username = role === 'teacher' ? school.teacher : school.parent;
  const token = login(school.shopId, username, school.password || 'password');
  if (token) vuSession[key] = { token };
  return token;
}

export function setup() {
  if (!SCHOOLS.length) throw new Error(`No schools in ${FIXTURE_PATH}. Run seed-portal-load-fixtures.ps1`);

  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) throw new Error(`gateway unhealthy: ${health.status}`);

  const tokens = {};
  for (const school of SCHOOLS) {
    for (const role of ['teacher', 'parent']) {
      const username = role === 'teacher' ? school.teacher : school.parent;
      const token = login(school.shopId, username, school.password || 'password');
      if (!token) throw new Error(`portal login failed ${school.shopId}/${username}`);
      tokens[`${school.shopId}:${role}`] = token;
    }
  }
  return { tokens, schoolCount: SCHOOLS.length };
}

export default function (data) {
  const school = schoolForVu();
  const role = personaForVu();
  const token = ensureSession(school, role, data.tokens);
  if (!token) {
    sleep(0.5);
    return;
  }
  const h = authHeaders(token, school.shopId);

  if (role === 'teacher') {
    getJson('/api/config/portals/teacher/bootstrap', h, 'teacher-bootstrap', school, role);
    getJson('/api/academic/teacher-scope?username=' + encodeURIComponent(school.teacher), h, 'teacher-scope', school, role);
    getJson('/api/exam/homework', h, 'teacher-homework', school, role);
    getJson('/api/student/students?page=0&size=10', h, 'teacher-students', school, role);
  } else {
    getJson('/api/config/portals/parent/bootstrap', h, 'parent-bootstrap', school, role);
    getJson('/api/exam/homework/mine', h, 'parent-homework-mine', school, role);
    getJson('/api/fee/collections?page=0&size=10', h, 'parent-fees', school, role);
    getJson('/api/student/students?page=0&size=10', h, 'parent-students', school, role);
  }

  sleep(THINK_MS / 1000);
}

export function handleSummary(data) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outDir = __ENV.RESULT_DIR || 'results';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/portal-mix-${stamp}.json`]: JSON.stringify(data, null, 2)
  };
}
