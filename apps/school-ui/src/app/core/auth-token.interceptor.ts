import { HttpInterceptorFn } from '@angular/common/http';
import {
  SESSION_ROLE_KEY,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
} from './session-keys';
import {
  readSessionSnapshot,
  resolveOrganizationId,
} from './tenant-context.util';

function isPlatformAccountsUrl(url: string): boolean {
  return url.includes('/api/v1/accounts');
}

/** Attaches SugamFlow JWT; login/invite endpoints stay unauthenticated. */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(SESSION_TOKEN_KEY);
  if (!token || req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  const session = readSessionSnapshot();
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

  const orgId = resolveOrganizationId();
  if (isPlatformAccountsUrl(req.url)) {
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
