import { HttpInterceptorFn } from '@angular/common/http';
import {
  SESSION_ROLE_KEY,
  SESSION_TENANT_KEY,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
  SESSION_DATA_KEY,
} from './session-keys';

interface SessionSnapshot {
  username?: string;
  role?: string;
  shopId?: string;
  tenantId?: number | string;
}

function readSession(): SessionSnapshot | null {
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

function isPlatformAccountsUrl(url: string): boolean {
  return url.includes('/api/v1/accounts');
}

/** Attaches SugamFlow JWT; login/invite endpoints stay unauthenticated. */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(SESSION_TOKEN_KEY);
  if (!token || req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  const session = readSession();
  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
  };

  const role = (localStorage.getItem(SESSION_ROLE_KEY) ?? session?.role ?? '').trim();
  if (role) {
    headers['X-Auth-Role'] = role.toUpperCase();
  }
  const username = (localStorage.getItem(SESSION_USER_KEY) ?? session?.username ?? '').trim();
  if (username) {
    headers['X-Auth-User'] = username;
  }

  const orgId = (localStorage.getItem(SESSION_TENANT_KEY) ?? session?.shopId ?? '').trim();
  if (isPlatformAccountsUrl(req.url)) {
    // account-service expects numeric platform tenant + org slug shopId
    const tenantId = session?.tenantId;
    if (tenantId != null && String(tenantId).trim() !== '') {
      headers['X-Tenant-Id'] = String(tenantId).trim();
    }
    if (orgId) {
      headers['X-Shop-Id'] = orgId;
    }
  } else if (orgId) {
    headers['X-Tenant-Id'] = orgId;
    headers['X-Shop-Id'] = orgId;
  }

  return next(req.clone({ setHeaders: headers }));
};
