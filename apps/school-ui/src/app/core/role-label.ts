/** JWT role codes stay unchanged. This is only the words on screen. */
export function roleKey(role: string | null | undefined): string {
  return String(role || '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_');
}

const LABELS: Record<string, string> = {
  SHOP_OWNER: 'Owner',
  OWNER: 'Owner',
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  PRINCIPAL: 'Principal',
  TEACHER: 'Teacher',
  CLASS_TEACHER: 'Class Teacher',
  ACCOUNTANT: 'Accountant',
  ACCOUNTS: 'Accountant',
  FINANCE: 'Accountant',
  RECEPTION: 'Reception',
  RECEPTIONIST: 'Reception',
  LIBRARIAN: 'Librarian',
  PARENT: 'Parent',
  GUARDIAN: 'Parent',
  STUDENT: 'Student',
  STAFF: 'Staff',
};

export function roleLabel(role: string | null | undefined): string {
  const key = roleKey(role);
  if (!key) {
    return 'Staff';
  }
  return LABELS[key] || key.replace(/_/g, ' ');
}
