import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthSessionService } from './auth-session.service';

/** Injects multi-tenant headers — scoped to signed-in organization/branch/session. */
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthSessionService);
  const cloned = req.clone({
    setHeaders: {
      'X-Tenant-Id': localStorage.getItem('sf.tenantId') ?? auth.getOrganizationId(),
      'X-Branch-Id': localStorage.getItem('sf.branchId') ?? 'main',
      'X-Academic-Session-Id': localStorage.getItem('sf.sessionId') ?? '2025-26',
      'X-User-Id': localStorage.getItem('sf.userId') ?? auth.getSession()?.username ?? '',
      'X-Role-Code': localStorage.getItem('sf.role') ?? auth.getRole() ?? '',
    },
  });
  return next(cloned);
};
