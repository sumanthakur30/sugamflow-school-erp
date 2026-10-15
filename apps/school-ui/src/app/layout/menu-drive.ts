import { NavGroup, NavItem } from './nav-catalog';

export interface SavedMenuNode {
  id: string;
  label: string;
  icon?: string;
  route: string;
  order: number;
  visible: boolean;
  roles: string[];
  requiredFeatureFlags: string[];
  children: SavedMenuNode[];
}

/** The three placeholder nodes saved before a school edits the live menu. */
export function isStarterMenu(nodes: SavedMenuNode[] | null | undefined): boolean {
  if (!nodes?.length) {
    return true;
  }
  return nodes.every((node) => node.id !== 'students' && !(node.children || []).length);
}

export function catalogAsMenu(groups: NavGroup[]): SavedMenuNode[] {
  return groups.map((group, index) => ({
    id: group.id,
    label: group.label,
    icon: group.icon,
    route: group.items[0]?.path || '/admin/dashboard',
    order: index + 1,
    visible: true,
    roles: [],
    requiredFeatureFlags: [],
    children: group.items.map((item, itemIndex) => ({
      id: `${group.id}_${itemIndex}`,
      label: item.label,
      route: item.path,
      order: itemIndex + 1,
      visible: true,
      roles: item.roles ? [...item.roles] : [],
      requiredFeatureFlags: item.feature ? [item.feature] : [],
      children: [],
    })),
  }));
}

/**
 * A saved tree controls label, order, and visibility for groups it contains.
 * Routes that are not in the Angular catalog are ignored.
 * Groups the school has not saved stay on the built-in catalog.
 */
export function applySavedMenu(
  groups: NavGroup[],
  saved: SavedMenuNode[] | null | undefined,
  keep: (item: NavItem) => boolean,
): NavGroup[] {
  if (isStarterMenu(saved)) {
    return groups
      .map((group) => ({ ...group, items: group.items.filter(keep) }))
      .filter((group) => group.items.length > 0);
  }
  const ordered = [...(saved || [])].sort((a, b) => (a.order || 0) - (b.order || 0));
  const seen = new Set<string>();
  const out: NavGroup[] = [];
  for (const node of ordered) {
    const catalog = groups.find((group) => group.id === node.id);
    if (!catalog || node.visible === false) {
      if (catalog) {
        seen.add(catalog.id);
      }
      continue;
    }
    seen.add(catalog.id);
    const known = new Map(catalog.items.map((item) => [item.path, item]));
    const children = [...(node.children || [])].sort((a, b) => (a.order || 0) - (b.order || 0));
    const items: NavItem[] = [];
    for (const child of children) {
      if (child.visible === false || !child.route) {
        continue;
      }
      const base = known.get(child.route);
      if (!base) {
        continue;
      }
      const item: NavItem = {
        ...base,
        label: child.label || base.label,
        roles: child.roles?.length ? child.roles : base.roles,
        feature: child.requiredFeatureFlags?.[0] || base.feature,
      };
      if (keep(item)) {
        items.push(item);
      }
    }
    if (items.length) {
      out.push({ ...catalog, label: node.label || catalog.label, icon: node.icon || catalog.icon, items });
    }
  }
  for (const group of groups) {
    if (seen.has(group.id)) {
      continue;
    }
    const items = group.items.filter(keep);
    if (items.length) {
      out.push({ ...group, items });
    }
  }
  return out;
}
