/**
 * Write-heavy multi-school load: enroll students + create fee collections concurrently.
 *
 * Prerequisites:
 *   .\scripts\seed-multi-school-load.ps1
 *
 *   k6 run multi-school-write-load.js -e QUICK=1
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:9090';
const QUICK = ['1', 'true', 'yes'].includes((__ENV.QUICK || 'false').toLowerCase());
const THINK_MS = Number(__ENV.THINK_TIME_MS || (QUICK ? '200' : '400'));
const BRANCH = __ENV.BRANCH_ID || 'main';
const SESSION = __ENV.ACADEMIC_SESSION_ID || 'load-write';

const writeMs = new Trend('write_ms', true);
const enrollMs = new Trend('enroll_ms', true);
const feeMs = new Trend('fee_create_ms', true);
const failRate = new Rate('write_fail_rate');
const okWrites = new Counter('write_success');
const failWrites = new Counter('write_failures');
const bySchool = new Counter('write_by_school');

const SCHOOLS = new SharedArray('schools', () => [
  { shopId: 'demo-school', username: 'admin_demo-school', password: 'password' },
  { shopId: 'load-school-01', username: 'admin_load-school-01', password: 'password' },
  { shopId: 'load-school-02', username: 'admin_load-school-02', password: 'password' },
  { shopId: 'load-school-03', username: 'admin_load-school-03', password: 'password' },
  { shopId: 'load-school-04', username: 'admin_load-school-04', password: 'password' },
  { shopId: 'load-school-05', username: 'admin_load-school-05', password: 'password' }
]);

const vuState = {};

export const options = QUICK
  ? {
      setupTimeout: '4m',
      vus: Number(__ENV.VUS || '12'),
      duration: __ENV.DURATION || '90s',
      thresholds: {
        http_req_failed: ['rate<0.12'],
        write_fail_rate: ['rate<0.15'],
        enroll_ms: ['p(95)<8000'],
        fee_create_ms: ['p(95)<4000']
      }
    }
  : {
      setupTimeout: '4m',
      scenarios: {
        writers: {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: [
            { duration: '1m', target: Number(__ENV.STAGE1_VUS || '6') },
            { duration: '2m', target: Number(__ENV.STAGE2_VUS || '12') },
            { duration: '1m', target: Number(__ENV.STAGE3_VUS || '18') },
            { duration: '1m', target: 0 }
          ]
        }
      },
      thresholds: {
        http_req_failed: ['rate<0.10'],
        write_fail_rate: ['rate<0.12'],
        enroll_ms: ['p(95)<10000'],
        fee_create_ms: ['p(95)<5000']
      }
    };

function schoolForVu() {
  return SCHOOLS[(__VU - 1) % SCHOOLS.length];
}

function mark(ok) {
  failRate.add(ok ? 0 : 1);
  if (ok) okWrites.add(1);
  else failWrites.add(1);
}

function login(school) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ shopId: school.shopId, username: school.username, password: school.password }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s', tags: { name: 'login' } }
  );
  let token = '';
  try {
    token = res.json('accessToken') || '';
  } catch (_) {}
  check(res, { 'login ok': (r) => r.status === 200 && !!token });
  return token;
}

function headers(token, school) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'X-Tenant-Id': school.shopId,
    'X-Shop-Id': school.shopId,
    'X-Branch-Id': BRANCH,
    'X-Academic-Session-Id': SESSION
  };
}

function ensureContext(data) {
  const school = schoolForVu();
  const key = `${__VU}:${school.shopId}`;
  if (vuState[key]?.token && vuState[key]?.sectionLabel) return vuState[key];

  const token = data.tokens[school.shopId];
  if (!token) return null;
  const ctx = data.contexts[school.shopId];
  vuState[key] = {
    school,
    token,
    sectionLabel: ctx.sectionLabel,
    sectionId: ctx.sectionId
  };
  return vuState[key];
}

function postJson(path, body, h, tag) {
  const started = Date.now();
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: h,
    timeout: '30s',
    tags: { name: tag }
  });
  writeMs.add(Date.now() - started);
  return res;
}

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) throw new Error(`gateway down: ${health.status}`);

  const tokens = {};
  const contexts = {};
  const stamp = String(Date.now()).slice(-8);

  for (const school of SCHOOLS) {
    const token = login(school);
    if (!token) throw new Error(`login failed ${school.shopId}`);
    tokens[school.shopId] = token;
    const h = headers(token, school);

    http.post(`${BASE_URL}/api/config/provision`, JSON.stringify({ schoolName: school.shopId }), {
      headers: h,
      timeout: '30s',
      tags: { name: 'provision' }
    });

    const classRes = postJson(
      '/api/academic/classes',
      {
        name: `WriteLoad ${school.shopId} ${stamp}`,
        code: `WL${stamp}`.slice(0, 12),
        sequenceNo: 70
      },
      h,
      'create-class'
    );
    const classId = classRes.json('data.id');
    const label = `WL-${school.shopId}-${stamp}`;
    const secRes = postJson(
      '/api/academic/sections',
      {
        classId,
        name: 'A',
        code: `WLA${stamp}`.slice(0, 12),
        studentLabel: label
      },
      h,
      'create-section'
    );
    contexts[school.shopId] = {
      sectionLabel: label,
      sectionId: secRes.json('data.id'),
      classId
    };
  }

  return { tokens, contexts };
}

export default function (data) {
  const ctx = ensureContext(data);
  if (!ctx) {
    sleep(0.5);
    return;
  }
  const { school, token, sectionLabel } = ctx;
  const h = headers(token, school);
  const uniq = `${__VU}-${__ITER}-${Date.now().toString().slice(-7)}`;
  const mobile = `9${String(100000000 + (__VU * 100000) + (__ITER % 90000) + (Date.now() % 1000))}`.slice(0, 10);

  const t0 = Date.now();
  const adm = postJson(
    '/api/admission/applications',
    {
      answers: {
        fullName: `Load Student ${uniq}`,
        age: 12,
        mobile,
        email: `load.${uniq}@${school.shopId}.local`,
        classApplied: sectionLabel,
        classSection: sectionLabel,
        documentsComplete: true,
        guardianFullName: 'Load Guardian',
        guardianRelation: 'Father',
        guardianMobile: '9811112233'
      }
    },
    h,
    'admission-create'
  );

  let app = null;
  try {
    app = adm.json('data');
  } catch (_) {}
  if (!app?.id) {
    mark(false);
    bySchool.add(1, { school: school.shopId, op: 'admission-fail' });
    sleep(THINK_MS / 1000);
    return;
  }

  for (let i = 0; i < 8; i++) {
    if (app.status === 'APPROVED' && app.enrolledAdmissionNo) break;
    const upd = http.post(
      `${BASE_URL}/api/admission/applications/${app.id}/actions`,
      JSON.stringify({ action: 'APPROVE', comment: `load ${i}` }),
      {
        headers: h,
        timeout: '30s',
        tags: { name: 'admission-approve' },
        // Workflow may return transitional non-2xx; business check is enrolled flag.
        responseCallback: http.expectedStatuses(200, 201, 202, 400, 409)
      }
    );
    writeMs.add(upd.timings.duration);
    try {
      app = upd.json('data') || app;
    } catch (_) {}
  }
  enrollMs.add(Date.now() - t0);

  const enrolled = !!(app?.enrolledAdmissionNo && app?.enrolledStudentId);
  check(null, { enrolled: () => enrolled });
  if (!enrolled) {
    mark(false);
    bySchool.add(1, { school: school.shopId, op: 'enroll-fail' });
    sleep(THINK_MS / 1000);
    return;
  }

  const t1 = Date.now();
  const fee = postJson(
    '/api/fee/collections',
    {
      answers: {
        admissionNo: app.enrolledAdmissionNo,
        studentName: `Load Student ${uniq}`,
        amount: 500 + (__ITER % 50) * 10,
        feeHead: `TUITION-${uniq}`,
        pendingDays: 2,
        dueDate: new Date().toISOString().slice(0, 10),
        paymentMode: 'UPI',
        email: `load.${uniq}@${school.shopId}.local`,
        mobile
      }
    },
    h,
    'fee-create'
  );
  feeMs.add(Date.now() - t1);

  let feeId = '';
  try {
    feeId = fee.json('data.id') || '';
  } catch (_) {}
  const ok =
    enrolled &&
    check(fee, {
      'fee 2xx': (r) => r.status >= 200 && r.status < 300,
      'fee id': () => !!feeId
    });
  mark(ok);
  bySchool.add(1, { school: school.shopId, op: ok ? 'write-ok' : 'write-fail' });

  // Light confirmation reads
  http.get(`${BASE_URL}/api/student/students?page=0&size=5`, {
    headers: h,
    timeout: '20s',
    tags: { name: 'students-read' }
  });
  http.get(`${BASE_URL}/api/fee/collections?page=0&size=5`, {
    headers: h,
    timeout: '20s',
    tags: { name: 'fees-read' }
  });

  sleep(THINK_MS / 1000);
}

export function handleSummary(data) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outDir = __ENV.RESULT_DIR || 'results';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/multi-school-write-${stamp}.json`]: JSON.stringify(data, null, 2)
  };
}
