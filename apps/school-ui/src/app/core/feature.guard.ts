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
    const elevated = role === 'SHOP_OWNER' || role === 'SUPER_ADMIN' || role === 'ADMIN';
    const ok = elevated || roles.map((r) => r.toUpperCase()).includes(role);
    if (!ok) {
      return router.createUrlTree(['/admin/admission']);
    }
  }

  if (!feature) {
    return true;
  }

  const current = entitlements.current();
  if (current?.featureFlags) {
    return current.featureFlags[feature] === true
      ? true
      : router.createUrlTree(['/admin/admission']);
  }

  return entitlements.load().pipe(
    map((e) =>
      e.featureFlags?.[feature] === true ? true : router.createUrlTree(['/admin/admission']),
    ),
  );
};
