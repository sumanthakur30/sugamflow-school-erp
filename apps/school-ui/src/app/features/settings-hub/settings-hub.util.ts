export type PlanStatus = 'active' | 'not-on-plan' | 'unknown';

export function planStatus(
  flags: Record<string, boolean> | null | undefined,
  feature: string | undefined,
): PlanStatus {
  if (!feature) {
    return 'active';
  }
  if (!flags) {
    return 'unknown';
  }
  return flags[feature] === true ? 'active' : 'not-on-plan';
}
