import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListSortOption, sortRows } from '../../shared/list-toolbar/list-controls';

@Component({
  selector: 'sf-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, ListToolbarComponent],
  templateUrl: './audit.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './audit.component.scss',
  ],
})
export class AuditComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  featureEnabled = false;
  error = '';
  statusMsg = '';
  busy = false;

  entityTypes: string[] = [];
  statuses: string[] = [];

  filterEntityType = '';
  filterStatus = '';
  filterEntityKey = '';

  entries: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  rollbackReason = '';

  // Client-side text search/sort/page — API already applies entityType/status/entityKey filters.
  listQ = '';
  sortBy = 'timestamp';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageSize = 50;
  pageIndex = 0;
  readonly sortOptions: ListSortOption[] = [
    { key: 'timestamp', label: 'Changed At' },
    { key: 'entityType', label: 'Entity Type' },
    { key: 'entityKey', label: 'Entity Key' },
    { key: 'status', label: 'Status' },
    { key: 'changedBy', label: 'Changed By' },
  ];

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.bootstrap());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  bootstrap(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/audit/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.entityTypes = boot.entityTypes ?? [];
      this.statuses = boot.statuses ?? [];
      this.loading = false;
      if (this.featureEnabled) {
        this.reload();
      }
    };
    const peeked = this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(err, 'Audit bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  reload(): void {
    this.error = '';
    const params = new URLSearchParams();
    if (this.filterEntityType) {
      params.set('entityType', this.filterEntityType);
    }
    if (this.filterStatus) {
      params.set('status', this.filterStatus);
    }
    if (this.filterEntityKey.trim()) {
      params.set('entityKey', this.filterEntityKey.trim());
    }
    const qs = params.toString();
    const path = qs ? `/api/audit/config-changes?${qs}` : '/api/audit/config-changes';
    this.api.get<any[]>(path).subscribe({
      next: (list) => {
        this.entries = list ?? [];
        if (this.selectedId) {
          const still = this.entries.find((e) => e.id === this.selectedId);
          if (still) {
            this.select(still);
          } else {
            this.selectedId = null;
            this.selected = null;
          }
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load audit log'),
    });
  }

  clearFilters(): void {
    this.filterEntityType = '';
    this.filterStatus = '';
    this.filterEntityKey = '';
    this.reload();
  }

  get filteredEntries(): any[] {
    let rows = this.entries;
    if (this.listQ.trim()) {
      const q = this.listQ.trim().toLowerCase();
      rows = rows.filter(
        (e) =>
          String(e.entityType ?? '').toLowerCase().includes(q) ||
          String(e.entityKey ?? '').toLowerCase().includes(q) ||
          String(e.changedBy ?? '').toLowerCase().includes(q) ||
          String(e.reason ?? '').toLowerCase().includes(q),
      );
    }
    rows = sortRows(rows, this.sortBy, this.sortDir);
    const start = this.pageIndex * this.pageSize;
    return rows.slice(start, start + this.pageSize);
  }

  get filteredEntriesTotal(): number {
    if (!this.listQ.trim()) return this.entries.length;
    const q = this.listQ.trim().toLowerCase();
    return this.entries.filter(
      (e) =>
        String(e.entityType ?? '').toLowerCase().includes(q) ||
        String(e.entityKey ?? '').toLowerCase().includes(q) ||
        String(e.changedBy ?? '').toLowerCase().includes(q) ||
        String(e.reason ?? '').toLowerCase().includes(q),
    ).length;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      this.sortBy !== 'timestamp' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.sortBy = 'timestamp';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
  }

  select(row: any): void {
    this.selectedId = row.id;
    this.statusMsg = '';
    this.rollbackReason = '';
    this.api.get<any>(`/api/audit/config-changes/${row.id}`).subscribe({
      next: (detail) => (this.selected = detail),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load change detail'),
    });
  }

  approve(): void {
    if (!this.selectedId) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/audit/config-changes/${this.selectedId}/approve`, {}).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg = 'Change approved';
        this.reload();
        if (this.selectedId) {
          this.api
            .get<any>(`/api/audit/config-changes/${this.selectedId}`)
            .subscribe((d) => (this.selected = d));
        }
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Approve failed';
      },
    });
  }

  rollback(): void {
    if (!this.selectedId) {
      return;
    }
    if (
      !confirm(
        'Rollback will restore the previous configuration value and mark this change as rolled back. Continue?',
      )
    ) {
      return;
    }
    this.busy = true;
    this.api
      .post(`/api/audit/config-changes/${this.selectedId}/rollback`, {
        reason: this.rollbackReason || undefined,
      })
      .subscribe({
        next: (rb: any) => {
          this.busy = false;
          this.statusMsg = `Rolled back — new audit entry ${rb?.id ?? ''}`.trim();
          this.selectedId = rb?.id ?? null;
          this.reload();
          if (this.selectedId) {
            this.api
              .get<any>(`/api/audit/config-changes/${this.selectedId}`)
              .subscribe((d) => (this.selected = d));
          }
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Rollback failed';
        },
      });
  }

  seedSample(): void {
    this.busy = true;
    this.api
      .post('/api/audit/config-changes', {
        entityType: 'DESIGN_THEME',
        entityKey: 'theme',
        oldValue: { primary: '#0B6E4F', status: 'PUBLISHED' },
        newValue: { primary: '#084C61', status: 'DRAFT' },
        reason: 'Sample brand refresh (manual)',
      })
      .subscribe({
        next: (row: any) => {
          this.busy = false;
          this.statusMsg = 'Sample change recorded';
          this.selectedId = row?.id ?? null;
          this.reload();
          if (this.selectedId) {
            this.select({ id: this.selectedId });
          }
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Failed to record sample';
        },
      });
  }

  json(value: unknown): string {
    try {
      return JSON.stringify(value ?? null, null, 2);
    } catch {
      return String(value);
    }
  }

  statusClass(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'chip ok';
      case 'PENDING_APPROVAL':
        return 'chip warn';
      case 'ROLLED_BACK':
        return 'chip muted';
      default:
        return 'chip';
    }
  }
}
