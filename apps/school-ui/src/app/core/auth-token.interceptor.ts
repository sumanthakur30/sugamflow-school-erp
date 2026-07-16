import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthSessionService } from './auth-session.service';

/** Attaches SugamFlow JWT; login/invite endpoints stay unauthenticated. */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthSessionService);
  const token = auth.getAccessToken();
  if (!token || req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
  };

  const role = auth.getRole()?.trim();
  if (role) {
    headers['X-Auth-Role'] = role.toUpperCase();
  }
  const username = auth.getSession()?.username?.trim();
  if (username) {
    headers['X-Auth-User'] = username;
  }

  const orgId = auth.getOrganizationId();
  if (orgId) {
    headers['X-Tenant-Id'] = orgId;
    headers['X-Shop-Id'] = orgId;
  }

  return next(req.clone({ setHeaders: headers }));
};
