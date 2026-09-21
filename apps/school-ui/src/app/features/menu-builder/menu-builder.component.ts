import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

export interface MenuNodeDraft {
  id: string;
  label: string;
  icon?: string;
  route: string;
  order: number;
  visible: boolean;
  roles: string[];
  requiredFeatureFlags: string[];
  branchIds: string[];
  children: MenuNodeDraft[];
}

@Component({
  selector: 'sf-menu-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './menu-builder.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class MenuBuilderComponent implements OnInit {
  private readonly api = inject(ApiService);

  menus: MenuNodeDraft[] = [];
  selectedPath: number[] = [];
  draft: MenuNodeDraft | null = null;
  rolesText = '';
  flagsText = '';
  branchesText = '';
  busy = false;
  status = '';
  error = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<MenuNodeDraft[]>('/api/config/menus').subscribe({
      next: (m) => {
        this.menus = (m ?? []).map((n) => this.normalize(n));
        if (this.menus.length && !this.draft) {
          this.select([0]);
        } else if (this.selectedPath.length) {
          this.select(this.selectedPath);
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load menus'),
    });
  }

  select(path: number[]): void {
    this.selectedPath = [...path];
    const node = this.nodeAt(path);
    this.draft = node ? this.clone(node) : null;
    if (this.draft) {
      this.rolesText = (this.draft.roles || []).join(', ');
      this.flagsText = (this.draft.requiredFeatureFlags || []).join(', ');
      this.branchesText = (this.draft.branchIds || []).join(', ');
    }
    this.status = '';
    this.error = '';
  }

  addRoot(): void {
    const n = this.menus.length + 1;
    this.menus.push(this.blankNode(`item_${n}`, `Menu ${n}`, `/admin`, n));
    this.select([this.menus.length - 1]);
  }

  addChild(): void {
    if (!this.selectedPath.length) return;
    const parent = this.nodeAt(this.selectedPath);
    if (!parent) return;
    const n = (parent.children?.length || 0) + 1;
    parent.children = parent.children || [];
    parent.children.push(
      this.blankNode(`${parent.id}_child_${n}`, `Child ${n}`, parent.route || '/admin', n),
    );
    this.select([...this.selectedPath, parent.children.length - 1]);
  }

  removeSelected(): void {
    if (!this.selectedPath.length) return;
    if (this.selectedPath.length === 1) {
      this.menus.splice(this.selectedPath[0], 1);
      this.draft = null;
      this.selectedPath = [];
      return;
    }
    const parentPath = this.selectedPath.slice(0, -1);
    const parent = this.nodeAt(parentPath);
    const idx = this.selectedPath[this.selectedPath.length - 1];
    parent?.children?.splice(idx, 1);
    this.select(parentPath);
  }

  applyDraft(): void {
    if (!this.draft || !this.selectedPath.length) return;
    this.draft.roles = this.splitCsv(this.rolesText);
    this.draft.requiredFeatureFlags = this.splitCsv(this.flagsText);
    this.draft.branchIds = this.splitCsv(this.branchesText);
    const target = this.nodeAt(this.selectedPath);
    if (!target) return;
    // Preserve children from tree; update scalar fields from draft
    target.id = this.draft.id;
    target.label = this.draft.label;
    target.icon = this.draft.icon;
    target.route = this.draft.route;
    target.order = Number(this.draft.order) || 0;
    target.visible = !!this.draft.visible;
    target.roles = [...this.draft.roles];
    target.requiredFeatureFlags = [...this.draft.requiredFeatureFlags];
    target.branchIds = [...this.draft.branchIds];
  }

  save(): void {
    this.applyDraft();
    this.busy = true;
    this.error = '';
    this.api.put<MenuNodeDraft[]>('/api/config/menus', this.menus).subscribe({
      next: (saved) => {
        this.busy = false;
        this.menus = (saved ?? []).map((n) => this.normalize(n));
        this.status = 'Menu tree saved';
        if (this.selectedPath.length) {
          this.select(this.selectedPath);
        }
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  private nodeAt(path: number[]): MenuNodeDraft | null {
    let cur: MenuNodeDraft[] | undefined = this.menus;
    let node: MenuNodeDraft | null = null;
    for (const idx of path) {
      if (!cur || !cur[idx]) return null;
      node = cur[idx];
      cur = node.children;
    }
    return node;
  }

  private blankNode(id: string, label: string, route: string, order: number): MenuNodeDraft {
    return {
      id,
      label,
      icon: '',
      route,
      order,
      visible: true,
      roles: [],
      requiredFeatureFlags: [],
      branchIds: [],
      children: [],
    };
  }

  private normalize(n: any): MenuNodeDraft {
    return {
      id: n?.id || '',
      label: n?.label || '',
      icon: n?.icon || '',
      route: n?.route || '',
      order: n?.order ?? 0,
      visible: n?.visible !== false,
      roles: Array.isArray(n?.roles) ? [...n.roles] : [],
      requiredFeatureFlags: Array.isArray(n?.requiredFeatureFlags)
        ? [...n.requiredFeatureFlags]
        : [],
      branchIds: Array.isArray(n?.branchIds) ? [...n.branchIds] : [],
      children: Array.isArray(n?.children) ? n.children.map((c: any) => this.normalize(c)) : [],
    };
  }

  private splitCsv(text: string): string[] {
    return (text || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }

  private clone<T>(v: T): T {
    return JSON.parse(JSON.stringify(v ?? null));
  }
}
