import {
  SESSION_BRANCH_KEY,
  SESSION_DATA_KEY,
  SESSION_TENANT_KEY,
  SESSION_TOKEN_KEY,
} from './session-keys';

interface SessionSnapshot {
  username?: string;
  role?: string;
  shopId?: string;
  tenantId?: number | string;
}

export function readSessionSnapshot(): SessionSnapshot | null {
  const raw = localStorage.getItem(SESSION_DATA_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as SessionSnapshot;
  } catch {
    return null;
  }
}

function shopIdFromJwt(): string {
  const token = localStorage.getItem(SESSION_TOKEN_KEY);
  if (!token) {
    return '';
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return '';
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64)) as { shopId?: string | number };
    return payload.shopId != null ? String(payload.shopId).trim() : '';
  } catch {
    return '';
  }
}

/**
 * Canonical school org slug for API headers.
 * Prefer JWT / session shopId over sf.tenantId so a stale localStorage tenant
 * cannot keep serving demo-school KPIs after HCP login.
 *
 * Important: when logged out, do NOT write the "demo-school" fallback into
 * localStorage — that poisoned the next HCP login (Saraswati branding + 50 students).
 */
export function resolveOrganizationId(): string {
  const session = readSessionSnapshot();
  const fromJwt = shopIdFromJwt();
  const fromSession = (session?.shopId || '').trim();
  const fromStore = (localStorage.getItem(SESSION_TENANT_KEY) || '').trim();
  const signedInOrg = (fromJwt || fromSession || '').trim();
  if (signedInOrg) {
    if (fromStore !== signedInOrg) {
      localStorage.setItem(SESSION_TENANT_KEY, signedInOrg);
    }
    return signedInOrg;
  }
  // Logged out: return whatever is typed/stored for the login screen, else empty.
  // Callers that need a placeholder should pass one explicitly — never persist demo here.
  return fromStore;
}

/** Campus key — never keep another school's branch id after org switch. */
export function resolveBranchId(): string {
  const raw = (localStorage.getItem(SESSION_BRANCH_KEY) || 'main').trim() || 'main';
  return raw;
}
