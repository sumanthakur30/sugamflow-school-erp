import { HttpInterceptorFn } from '@angular/common/http';
import {
  SESSION_ACADEMIC_KEY,
  SESSION_BRANCH_KEY,
  SESSION_DATA_KEY,
  SESSION_ROLE_KEY,
  SESSION_TENANT_KEY,
  SESSION_USER_KEY,
} from './session-keys';

interface SessionSnapshot {
  username?: string;
  role?: string;
  shopId?: string;
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

/** Injects multi-tenant headers — scoped to signed-in organization/branch/session. */
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  const session = readSession();
  const cloned = req.clone({
    setHeaders: {
      'X-Tenant-Id': localStorage.getItem(SESSION_TENANT_KEY) ?? session?.shopId ?? 'demo-school',
      'X-Branch-Id': localStorage.getItem(SESSION_BRANCH_KEY) ?? 'main',
      'X-Academic-Session-Id': localStorage.getItem(SESSION_ACADEMIC_KEY) ?? '2025-26',
      'X-User-Id': localStorage.getItem(SESSION_USER_KEY) ?? session?.username ?? '',
      'X-Role-Code': localStorage.getItem(SESSION_ROLE_KEY) ?? session?.role ?? '',
    },
  });
  return next(cloned);
};
