export interface ListSortOption {
  key: string;
  label: string;
}

export interface ListToolbarState {
  q: string;
  sortBy: string;
  sortDir: 'ASC' | 'DESC';
  pageSize: number;
}

export const DEFAULT_PAGE_SIZES = [10, 25, 50, 100, 200] as const;

export interface ListStatusOption {
  value: string;
  label: string;
}

export function toggleSortDir(dir: 'ASC' | 'DESC'): 'ASC' | 'DESC' {
  return dir === 'ASC' ? 'DESC' : 'ASC';
}

/** Next sort state when a column header is clicked (same column toggles direction). */
export function nextHeaderSort(
  sortBy: string,
  sortDir: 'ASC' | 'DESC',
  key: string,
): { sortBy: string; sortDir: 'ASC' | 'DESC' } {
  if (sortBy === key) {
    return { sortBy, sortDir: toggleSortDir(sortDir) };
  }
  return { sortBy: key, sortDir: 'ASC' };
}

/** Arrow shown next to a sortable column header. */
export function headerSortIndicator(
  sortBy: string,
  sortDir: 'ASC' | 'DESC',
  key: string,
): string {
  if (sortBy !== key) {
    return '↕';
  }
  return sortDir === 'ASC' ? '↑' : '↓';
}

/** Client-side sort for the current page (or full array). */
export function sortRows<T extends Record<string, any>>(
  rows: T[],
  sortBy: string,
  sortDir: 'ASC' | 'DESC',
  resolve?: (row: T, key: string) => unknown,
): T[] {
  if (!sortBy || !rows?.length) {
    return rows ?? [];
  }
  const dir = sortDir === 'ASC' ? 1 : -1;
  const get = resolve ?? ((row: T, key: string) => row?.[key]);
  return [...rows].sort((a, b) => {
    const av = normalizeSortValue(get(a, sortBy));
    const bv = normalizeSortValue(get(b, sortBy));
    if (av < bv) return -1 * dir;
    if (av > bv) return 1 * dir;
    return 0;
  });
}

function normalizeSortValue(v: unknown): string | number {
  if (v == null || v === '') return '';
  if (typeof v === 'number') return v;
  if (typeof v === 'boolean') return v ? 1 : 0;
  return String(v).trim().toLowerCase();
}

export function pageMeta(page: number, size: number, total: number): {
  from: number;
  to: number;
  totalPages: number;
} {
  if (!total) {
    return { from: 0, to: 0, totalPages: 1 };
  }
  const totalPages = Math.max(1, Math.ceil(total / size));
  return {
    from: page * size + 1,
    to: Math.min((page + 1) * size, total),
    totalPages,
  };
}
