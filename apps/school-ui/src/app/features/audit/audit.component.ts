import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit.component.html',
  styleUrls: ['../../shared/admin-page.scss', './audit.component.scss'],
})
export class AuditComponent implements OnInit {
  private readonly api = inject(ApiService);

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

  ngOnInit(): void {
    this.bootstrap();
  }

  bootstrap(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/audit/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.entityTypes = boot.entityTypes ?? [];
        this.statuses = boot.statuses ?? [];
        this.loading = false;
        if (this.featureEnabled) {
          this.reload();
        }
      },
      error: (err) => {
        this.loading = false;
        this.featureEnabled = false;
        this.error = err?.error?.message ?? err?.message ?? 'Audit bootstrap failed';
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
