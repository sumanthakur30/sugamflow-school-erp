import { ParamMap } from '@angular/router';

export type ListViewMode = 'list' | 'new' | 'detail';

export function parseListViewParams(params: ParamMap): {
  mode: ListViewMode;
  id: string | null;
} {
  const isNew = params.get('new') === '1' || params.get('new') === 'true';
  if (isNew) {
    return { mode: 'new', id: null };
  }
  const id = params.get('id')?.trim() || null;
  if (id) {
    return { mode: 'detail', id };
  }
  return { mode: 'list', id: null };
}
