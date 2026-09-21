import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';

/**
 * Catch-all for unmatched URLs. Prefer role home when logged in —
 * never bounce an authenticated session to /login just because a link
 * was malformed (e.g. query string stuffed into routerLink path).
 */
export const unknownRouteGuard: CanActivateFn = () => {
  const auth = inject(AuthSessionService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  const role = (auth.getRole() || '').toUpperCase();
  if (role === 'PARENT' || role === 'GUARDIAN' || role === 'STUDENT') {
    return router.createUrlTree(['/parent']);
  }
  if (role === 'TEACHER') {
    return router.createUrlTree(['/teacher']);
  }
  return router.createUrlTree(['/admin/dashboard']);
};
