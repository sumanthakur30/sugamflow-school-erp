/** Landing route after login. Parent and teacher homes are themselves feature-gated. */
export function roleHome(role: string | null | undefined): string {
  const normalized = (role || '').toUpperCase();
  if (normalized === 'PARENT' || normalized === 'GUARDIAN' || normalized === 'STUDENT') {
    return '/parent';
  }
  if (normalized === 'TEACHER') {
    return '/teacher';
  }
  return '/admin/dashboard';
}

/**
 * Where to send a user when a feature flag is off.
 * Parent and teacher homes require their own flag, so sending them there loops.
 */
export function featureDeniedUrl(role: string | null | undefined, feature: string): string {
  const normalized = (role || '').toUpperCase();
  const homeIsThisFeature =
    ((normalized === 'PARENT' || normalized === 'GUARDIAN' || normalized === 'STUDENT') &&
      feature === 'FEATURE_PARENT_APP') ||
    (normalized === 'TEACHER' && feature === 'FEATURE_TEACHER_APP');
  if (homeIsThisFeature) {
    return '/modules-unavailable?reason=plan';
  }
  return roleHome(normalized);
}
