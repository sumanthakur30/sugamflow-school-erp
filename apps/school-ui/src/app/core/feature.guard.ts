import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import { EntitlementsService } from './entitlements.service';

/** Route data: { feature?: string, roles?: string[] } */
export const featureGuard: CanActivateFn = (route) => {
  const auth = inject(AuthSessionService);
  const entitlements = inject(EntitlementsService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }

  const feature = route.data?.['feature'] as string | undefined;
  const roles = route.data?.['roles'] as string[] | undefined;
  const role = (auth.getRole() || '').toUpperCase();

  if (roles?.length) {
    const ok = roles.map((r) => r.toUpperCase()).includes(role);
    if (!ok) {
      const home = roleHome(role);
      // Prevent infinite redirects when home is this same guarded route.
      const here = '/' + route.pathFromRoot
        .map((r) => r.url.map((s) => s.path).join('/'))
        .filter(Boolean)
        .join('/');
      if (home === here || home === router.url.split('?')[0]) {
        return router.createUrlTree(['/login']);
      }
      return router.createUrlTree([home]);
    }
  }

  if (!feature) {
    return true;
  }

  const current = entitlements.current();
  if (current?.featureFlags) {
    return current.featureFlags[feature] === true
      ? true
      : router.createUrlTree([roleHome(role)]);
  }

  return entitlements.load().pipe(
    map((e) => {
      const flags = e?.featureFlags;
      // Fail open when subscription/entitlements is down — otherwise every
      // guarded route redirects to /admin/admission and loops into a blank page.
      if (!flags) {
        return true;
      }
      return flags[feature] === true ? true : router.createUrlTree([roleHome(role)]);
    }),
  );
};

function roleHome(role: string): string {
  if (role === 'PARENT' || role === 'GUARDIAN' || role === 'STUDENT') {
    return '/parent';
  }
  if (role === 'TEACHER') {
    return '/teacher';
  }
  return '/admin/dashboard';
}
