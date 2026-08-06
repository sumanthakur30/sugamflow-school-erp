import { HttpInterceptorFn } from '@angular/common/http';
import {
  SESSION_ACADEMIC_KEY,
  SESSION_ROLE_KEY,
  SESSION_USER_KEY,
} from './session-keys';
import {
  readSessionSnapshot,
  resolveBranchId,
  resolveOrganizationId,
} from './tenant-context.util';

/** Injects multi-tenant headers — scoped to signed-in organization/branch/session. */
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  const session = readSessionSnapshot();
  const isPlatformAccounts = req.url.includes('/api/v1/accounts');
  const orgId = resolveOrganizationId();
  const headers: Record<string, string> = {
    'X-Branch-Id': resolveBranchId(),
    'X-Academic-Session-Id': localStorage.getItem(SESSION_ACADEMIC_KEY) ?? '2025-26',
    'X-User-Id': localStorage.getItem(SESSION_USER_KEY) ?? session?.username ?? '',
    'X-Role-Code': localStorage.getItem(SESSION_ROLE_KEY) ?? session?.role ?? '',
    // Prevent browser from replaying demo-school KPI GETs after HCP login.
    'Cache-Control': 'no-store',
    Pragma: 'no-cache',
  };
  // Do not overwrite numeric X-Tenant-Id set by authTokenInterceptor for account-service.
  // Skip tenant headers when logged out / org unknown — avoids demo-school bleed on login page.
  if (!isPlatformAccounts && orgId) {
    headers['X-Tenant-Id'] = orgId;
    headers['X-Shop-Id'] = orgId;
  }
  return next(req.clone({ setHeaders: headers }));
};
