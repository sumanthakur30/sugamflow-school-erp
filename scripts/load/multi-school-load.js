/**
 * Multi-school concurrent load test for School ERP.
 *
 * Rotates VUs across demo-school + load-school-01..05 and hits core school APIs
 * through the shared SugamFlow gateway.
 *
 * Prerequisites:
 *   .\scripts\seed-multi-school-load.ps1
 *   Gateway :9090 + school services up
 *
 * Quick smoke:
 *   k6 run multi-school-load.js -e QUICK=1
 *
 * Fuller peak (5 schools x concurrent admins):
 *   k6 run multi-school-load.js -e STAGE3_VUS=30
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:9090';
const QUICK = ['1', 'true', 'yes'].includes((__ENV.QUICK || 'false').toLowerCase());
const THINK_MS = Number(__ENV.THINK_TIME_MS || (QUICK ? '50' : '150'));
const LOGIN_TIMEOUT = __ENV.LOGIN_TIMEOUT || '30s';
const REAUTH_EVERY = Number(__ENV.REAUTH_EVERY || '200');
const BRANCH = __ENV.BRANCH_ID || 'main';
const SESSION = __ENV.ACADEMIC_SESSION_ID || '2025-26';
const ALLOW_RUNTIME_LOGIN = ['1', 'true', 'yes'].includes((__ENV.ALLOW_RUNTIME_LOGIN || 'false').toLowerCase());

const loginMs = new Trend('school_login_ms', true);
const readMs = new Trend('school_read_ms', true);
const writeMs = new Trend('school_write_ms', true);
const failRate = new Rate('school_fail_rate');
const failCount = new Counter('school_failures');
const tenantLeak = new Counter('school_tenant_leak');
const bySchool = new Counter('school_requests');
const status5xx = new Counter('school_http_5xx');
const status503 = new Counter('school_http_503');
const status4xx = new Counter('school_http_4xx');
const status2xx = new Counter('school_http_2xx');

/** @type {Array<{shopId:string,username:string,password:string}>} */
const SCHOOLS = [
  { shopId: 'demo-school', username: 'admin_demo-school', password: 'password' },
  { shopId: 'load-school-01', username: 'admin_load-school-01', password: 'password' },
  { shopId: 'load-school-02', username: 'admin_load-school-02', password: 'password' },
  { shopId: 'load-school-03', username: 'admin_load-school-03', password: 'password' },
  { shopId: 'load-school-04', username: 'admin_load-school-04', password: 'password' },
  { shopId: 'load-school-05', username: 'admin_load-school-05', password: 'password' }
];

const READS = [
  { path: '/api/config/design-studio/theme', label: 'theme' },
  { path: '/api/config/branches', label: 'branches' },
  { path: '/api/subscription/plans', label: 'subscription-plans' },
  { path: '/api/student/bootstrap', label: 'student-bootstrap' },
  { path: '/api/student/students?page=0&size=20', label: 'students' },
  { path: '/api/admission/bootstrap', label: 'admission-bootstrap' },
  { path: '/api/admission/applications?page=0&size=20', label: 'admissions' },
  { path: '/api/fee/bootstrap', label: 'fee-bootstrap' },
  { path: '/api/fee/collections?page=0&size=20', label: 'fee-collections' },
  { path: '/api/attendance/bootstrap', label: 'attendance-bootstrap' },
  { path: '/api/attendance/records?page=0&size=20', label: 'attendance' },
  { path: '/api/exam/bootstrap', label: 'exam-bootstrap' },
  { path: '/api/exam/records?page=0&size=20', label: 'exams' },
  { path: '/api/exam/homework', label: 'homework' },
  { path: '/api/library/bootstrap', label: 'library-bootstrap' },
  { path: '/api/library/records?page=0&size=20', label: 'library' },
  { path: '/api/hostel/bootstrap', label: 'hostel-bootstrap' },
  { path: '/api/hostel/beds', label: 'hostel-beds' },
  { path: '/api/transport/bootstrap', label: 'transport-bootstrap' },
  { path: '/api/transport/routes', label: 'transport-routes' },
  { path: '/api/academic/bootstrap', label: 'academic-bootstrap' },
  { path: '/api/academic/classes', label: 'academic-classes' },
  { path: '/api/staff/bootstrap', label: 'staff-bootstrap' },
  { path: '/api/staff/staff?page=0&size=20', label: 'staff' },
  { path: '/api/school/notification-config/comms/announcements?page=0&size=20', label: 'comms' }
];

const vuSession = {};

export const options = QUICK
  ? {
      setupTimeout: '3m',
      vus: Number(__ENV.VUS || String(SCHOOLS.length * 2)),
      duration: __ENV.DURATION || '60s',
      thresholds: {
        http_req_failed: ['rate<0.15'],
        http_req_duration: ['p(95)<5000'],
        school_fail_rate: ['rate<0.20'],
        school_login_ms: ['p(95)<4000'],
        school_tenant_leak: ['count==0']
      }
    }
  : {
      setupTimeout: '3m',
      scenarios: {
        multi_school_mix: {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: [
            { duration: __ENV.STAGE1 || '1m', target: Number(__ENV.STAGE1_VUS || '12') },
            { duration: __ENV.STAGE2 || '2m', target: Number(__ENV.STAGE2_VUS || '24') },
            { duration: __ENV.STAGE3 || '2m', target: Number(__ENV.STAGE3_VUS || '36') },
            { duration: __ENV.STAGE4 || '1m', target: 0 }
          ],
          exec: 'schoolFlow'
        }
      },
      thresholds: {
        http_req_failed: ['rate<0.10'],
        http_req_duration: ['p(95)<4000', 'p(99)<8000'],
        school_login_ms: ['p(95)<3000'],
        school_read_ms: ['p(95)<2500'],
        school_fail_rate: ['rate<0.12'],
        school_tenant_leak: ['count==0']
      }
    };

function schoolForVu() {
  return SCHOOLS[(__VU - 1) % SCHOOLS.length];
}

function markFail(ok) {
  failRate.add(ok ? 0 : 1);
  if (!ok) failCount.add(1);
}

function login(school) {
  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      shopId: school.shopId,
      username: school.username,
      password: school.password
    }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      timeout: LOGIN_TIMEOUT,
      tags: { name: 'school-login', school: school.shopId }
    }
  );
  loginMs.add(Date.now() - started);
  bySchool.add(1, { school: school.shopId, op: 'login' });

  let token = '';
  try {
    token = res.json('accessToken') || '';
  } catch (_) {
    token = '';
  }

  const ok = check(res, {
    'login 200': (r) => r.status === 200,
    'login has token': () => !!token
  });
  markFail(ok);
  return ok ? token : '';
}

function authHeaders(token, school) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Tenant-Id': school.shopId,
    'X-Shop-Id': school.shopId,
    'X-Branch-Id': BRANCH,
    'X-Academic-Session-Id': SESSION
  };
}

function ensureSession(school, sharedTokens) {
  const key = `${__VU}:${school.shopId}`;
  const cur = vuSession[key];
  if (cur && cur.token && cur.uses < REAUTH_EVERY) {
    cur.uses += 1;
    return cur.token;
  }

  const shared = sharedTokens?.[school.shopId];
  if (shared) {
    vuSession[key] = { token: shared, uses: 1 };
    return shared;
  }

  if (!ALLOW_RUNTIME_LOGIN) {
    markFail(true);
    return '';
  }

  const token = login(school);
  if (token) vuSession[key] = { token, uses: 1 };
  return token;
}

function getJson(path, headers, label, school) {
  const started = Date.now();
  const res = http.get(`${BASE_URL}${path}`, {
    headers,
    timeout: '20s',
    tags: { name: label, school: school.shopId }
  });
  readMs.add(Date.now() - started);
  bySchool.add(1, { school: school.shopId, op: label });

  if (res.status >= 200 && res.status < 300) status2xx.add(1, { school: school.shopId, op: label });
  else if (res.status === 503) status503.add(1, { school: school.shopId, op: label });
  else if (res.status >= 500) status5xx.add(1, { school: school.shopId, op: label });
  else if (res.status >= 400) status4xx.add(1, { school: school.shopId, op: label });

  const ok = check(res, {
    [`${label} not 5xx`]: (r) => r.status < 500,
    [`${label} not 401/403`]: (r) => r.status !== 401 && r.status !== 403
  });
  markFail(ok);

  // Soft success: empty modules may return 404/400 while stack is still healthy.
  if (res.status >= 200 && res.status < 300) {
    try {
      const body = res.json();
      const items = body?.data?.items || body?.data?.content || body?.data || [];
      if (Array.isArray(items)) {
        for (const row of items) {
          const org = row?.organizationId || row?.orgId || row?.tenantId;
          if (org && org !== school.shopId) {
            tenantLeak.add(1);
            console.error(`TENANT LEAK school=${school.shopId} saw org=${org} on ${label}`);
          }
        }
      } else if (body?.data?.organizationId && body.data.organizationId !== school.shopId) {
        tenantLeak.add(1);
        console.error(`TENANT LEAK school=${school.shopId} theme/org=${body.data.organizationId}`);
      }
    } catch (_) {
      // non-JSON ok for some endpoints
    }
  }
  return res;
}

function provisionIfNeeded(headers, school) {
  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/config/provision`,
    JSON.stringify({ schoolName: `Load ${school.shopId}` }),
    {
      headers,
      timeout: '30s',
      tags: { name: 'provision', school: school.shopId }
    }
  );
  writeMs.add(Date.now() - started);
  bySchool.add(1, { school: school.shopId, op: 'provision' });
  const ok = check(res, {
    'provision accepted': (r) => r.status === 200 || r.status === 201 || r.status === 204
  });
  markFail(ok);
  return res;
}

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) {
    throw new Error(`Gateway unhealthy: status=${health.status} body=${health.body}`);
  }

  const tokens = {};
  const ready = [];
  for (const school of SCHOOLS) {
    const token = login(school);
    if (!token) {
      console.warn(`SKIP school=${school.shopId} login failed`);
      continue;
    }
    const headers = authHeaders(token, school);
    provisionIfNeeded(headers, school);
    tokens[school.shopId] = token;
    ready.push(school.shopId);
  }

  if (ready.length < 2) {
    throw new Error(`Need >=2 schools ready for multi-school load; ready=${ready.join(',')}`);
  }

  return { readySchools: ready, schoolCount: ready.length, tokens };
}

export function schoolFlow(data) {
  const school = schoolForVu();
  if (data?.readySchools && !data.readySchools.includes(school.shopId)) {
    sleep(0.2);
    return;
  }

  const token = ensureSession(school, data?.tokens);
  if (!token) {
    sleep(0.5);
    return;
  }

  const headers = authHeaders(token, school);
  const picks = [
    READS[Math.floor(Math.random() * READS.length)],
    READS[Math.floor(Math.random() * READS.length)],
    READS[Math.floor(Math.random() * READS.length)]
  ];

  for (const read of picks) {
    getJson(read.path, headers, read.label, school);
    sleep(THINK_MS / 1000);
  }
}

export default function (data) {
  schoolFlow(data);
}

export function handleSummary(data) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outDir = `${__ENV.RESULT_DIR || 'results'}`;
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/multi-school-load-${stamp}.json`]: JSON.stringify(data, null, 2)
  };
}
