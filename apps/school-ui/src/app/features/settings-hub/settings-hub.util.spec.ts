import { planStatus } from './settings-hub.util';

describe('planStatus', () => {
  it('treats a missing feature as always available', () => {
    expect(planStatus({ FEATURE_FEE: false }, undefined)).toBe('active');
  });

  it('reads an enabled plan flag', () => {
    expect(planStatus({ FEATURE_FEE: true }, 'FEATURE_FEE')).toBe('active');
  });

  it('keeps a disabled plan flag off without inventing access', () => {
    expect(planStatus({ FEATURE_PAYROLL: false }, 'FEATURE_PAYROLL')).toBe('not-on-plan');
  });

  it('stays unknown until the plan has loaded', () => {
    expect(planStatus(undefined, 'FEATURE_FEE')).toBe('unknown');
  });
});
