import { buildSimpleNav } from './simple-nav';
import { NavGroup, NavItem } from './nav-catalog';
import { roleKey, roleLabel } from '../core/role-label';

const groups: NavGroup[] = [
  {
    id: 'students',
    label: 'Students',
    icon: 'users',
    items: [
      { path: '/admin/students', label: 'Student Master', feature: 'FEATURE_STUDENT_MASTER' },
      { path: '/admin/import', label: 'Import Workbench', feature: 'FEATURE_STUDENT_MASTER' },
      { path: '/admin/udise-export', label: 'UDISE+ export', feature: 'FEATURE_STUDENT_MASTER' },
    ],
  },
  {
    id: 'finance',
    label: 'Finance',
    icon: 'wallet',
    items: [
      { path: '/admin/fee', label: 'Fee Collection', feature: 'FEATURE_FEE' },
      { path: '/admin/fee-desk', label: 'Fee desk', feature: 'FEATURE_FEE' },
    ],
  },
  {
    id: 'admin',
    label: 'Admin',
    icon: 'settings',
    items: [
      { path: '/admin/settings', label: 'Settings' },
      { path: '/admin/forms', label: 'Form Builder' },
      { path: '/admin/workflows', label: 'Workflows' },
      { path: '/admin/ops', label: 'Ops Depth' },
      { path: '/admin/compliance/campaigns', label: 'Campaign Workspace' },
    ],
  },
];

function keepAll(item: NavItem): boolean {
  return item.feature !== 'FEATURE_TRANSPORT';
}

describe('simple navigation', () => {
  it('shows the daily jobs and hides builder tools', () => {
    const nav = buildSimpleNav('small', groups, keepAll);
    expect(nav.primary.map((item) => item.label)).toEqual(['Students', 'Fees', 'Settings']);
    const moreLabels = nav.more.map((item) => item.label);
    expect(moreLabels).toContain('Board export');
    expect(moreLabels).toContain('Fee dues');
    expect(moreLabels).not.toContain('Form Builder');
    expect(moreLabels).not.toContain('Workflows');
    expect(moreLabels).not.toContain('Ops Depth');
    expect(moreLabels).not.toContain('Campaign Workspace');
    expect(moreLabels).not.toContain('Import Workbench');
  });

  it('adds campus modules only for a large school', () => {
    const catalog: NavGroup[] = [
      ...groups,
      {
        id: 'ops',
        label: 'Operations',
        icon: 'building',
        items: [{ path: '/admin/transport', label: 'Transport', feature: 'FEATURE_TRANSPORT' }],
      },
    ];
    const small = buildSimpleNav('small', catalog, () => true);
    const large = buildSimpleNav('large', catalog, () => true);
    expect(small.primary.some((item) => item.path === '/admin/transport')).toBeFalse();
    expect(large.primary.some((item) => item.label === 'Transport')).toBeTrue();
  });
});

describe('role labels', () => {
  it('shows Owner without changing the role code', () => {
    expect(roleKey('SHOP OWNER')).toBe('SHOP_OWNER');
    expect(roleLabel('SHOP OWNER')).toBe('Owner');
    expect(roleLabel('SHOP_OWNER')).toBe('Owner');
  });
});
