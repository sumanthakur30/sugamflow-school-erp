import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { map } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import { EntitlementsService } from './entitlements.service';
import { featureDeniedUrl, roleHome } from './role-home';

/** Route data: { feature?: string, roles?: string[], permissions?: string[] } */
export const featureGuard: CanActivateFn = (route) => {
  const auth = inject(AuthSessionService);
  const entitlements = inject(EntitlementsService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }

  const feature = route.data?.['feature'] as string | undefined;
  const roles = route.data?.['roles'] as string[] | undefined;
  const permissions = route.data?.['permissions'] as string[] | undefined;
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

  if (permissions?.length && !auth.hasAnyPermission(permissions)) {
    return router.createUrlTree([roleHome(role)]);
  }

  if (!feature) {
    return true;
  }

  const decide = (flags: Record<string, boolean> | undefined): true | UrlTree => {
    if (!flags) {
      // Do not open the module. /modules-unavailable is not feature-gated, so this cannot loop.
      return router.createUrlTree(['/modules-unavailable']);
    }
    return flags[feature] === true ? true : router.parseUrl(featureDeniedUrl(role, feature));
  };

  const current = entitlements.current();
  if (current?.loadState === 'ready' && current.featureFlags) {
    return decide(current.featureFlags);
  }
  if (current?.loadState === 'unavailable') {
    return router.createUrlTree(['/modules-unavailable']);
  }

  return entitlements.load().pipe(map((e) => decide(e.featureFlags)));
};
