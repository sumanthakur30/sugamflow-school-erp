import { NavGroup, NavItem } from './nav-catalog';
import { InstitutionPreset } from '../core/institution-preset.service';

export interface SimpleNav {
  primary: NavItem[];
  more: NavItem[];
}

/** Kept in the product, reached from Settings, not listed in the daily menu. */
const HIDDEN_FROM_MENU = new Set([
  '/admin/forms',
  '/admin/workflows',
  '/admin/rules',
  '/admin/menus',
  '/admin/ai',
  '/admin/ops',
  '/admin/import',
  '/admin/compliance/campaigns',
  '/admin/compliance/platform-templates',
]);

const FRIENDLY: Record<string, string> = {
  '/admin/udise-export': 'Board export',
  '/admin/student-directory': 'Student search',
  '/admin/fee-desk': 'Fee dues',
  '/admin/devices': 'Attendance devices',
  '/admin/biometric': 'Biometric attendance',
  '/admin/leads': 'Enquiries',
  '/admin/admission': 'Applications',
  '/admin/lifecycle': 'Promotions',
  '/admin/notifications': 'Notices',
  '/admin/reports': 'Report designer',
  '/admin/offline': 'Offline attendance',
  '/admin/privacy': 'Student privacy',
  '/parent': 'Parent app',
  '/teacher': 'Teacher app',
};

export function buildSimpleNav(
  preset: InstitutionPreset,
  groups: NavGroup[],
  keep: (item: NavItem) => boolean,
): SimpleNav {
  const catalog = flatten(groups);
  const primary: NavItem[] = [];
  const push = (path: string, label: string) => {
    const item = allow(catalog, keep, path, label);
    if (item) {
      primary.push(item);
    }
  };

  push('/admin/dashboard', 'Home');
  push('/admin/students', 'Students');
  push('/admin/fee', 'Fees');
  push('/admin/attendance', 'Attendance');
  push('/admin/exam', preset === 'coaching' ? 'Tests' : preset === 'college' ? 'Results' : 'Exams');
  push('/admin/staff-directory', preset === 'college' ? 'Faculty' : 'Staff');
  if (preset === 'large') {
    push('/admin/transport', 'Transport');
    push('/admin/library', 'Library');
    push('/admin/payroll', 'Payroll');
    push('/admin/branches', 'Campuses');
  }
  if (preset === 'college') {
    const college: NavItem = { path: '/admin/college', label: 'College' };
    if (keep(college)) {
      primary.push(college);
    }
  }
  const messages =
    allow(catalog, keep, '/admin/comms', 'Messages') ||
    allow(catalog, keep, '/admin/notifications', 'Messages');
  if (messages) {
    primary.push(messages);
  }
  push('/admin/reports-hub', 'Reports');
  push('/admin/settings', 'Settings');

  const primaryPaths = new Set(primary.map((item) => item.path.split('?')[0]));
  const more: NavItem[] = [];
  const seen = new Set<string>();
  for (const item of catalog) {
    const path = item.path.split('?')[0];
    if (primaryPaths.has(path) || HIDDEN_FROM_MENU.has(path) || seen.has(item.path)) {
      continue;
    }
    seen.add(item.path);
    if (!keep(item)) {
      continue;
    }
    more.push({ ...item, label: FRIENDLY[path] || item.label });
  }
  return { primary, more };
}

function flatten(groups: NavGroup[]): NavItem[] {
  const out: NavItem[] = [];
  for (const group of groups) {
    out.push(...group.items);
  }
  return out;
}

function allow(
  catalog: NavItem[],
  keep: (item: NavItem) => boolean,
  path: string,
  label: string,
): NavItem | null {
  const found = catalog.find((item) => item.path.split('?')[0] === path);
  if (!found && path !== '/admin/college') {
    return null;
  }
  const item: NavItem = found ? { ...found, label } : { path, label };
  return keep(item) ? item : null;
}
