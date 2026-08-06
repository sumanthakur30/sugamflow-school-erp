import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import {
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';

type CommsView = 'history' | 'compose';
type StatusFilter = '' | 'SENT' | 'ATTENTION' | 'PARTIAL_FAILED' | 'FAILED' | 'QUEUED';

@Component({
  selector: 'sf-comms-hub',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './comms-hub.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    './comms-hub.component.scss',
  ],
})
export class CommsHubComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  busy = false;
  error = '';
  status = '';
  featureEnabled = false;
  announcements: any[] = [];
  channels = ['IN_APP', 'EMAIL', 'SMS', 'WHATSAPP'];
  audiences = ['PRIMARY_PARENTS', 'PARENTS', 'ALL_ACTIVE'];
  view: CommsView = 'history';

  listQ = '';
  statusFilter: StatusFilter = '';
  channelFilter = '';
  sortBy = 'createdAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageSize = 25;
  pageIndex = 0;

  draft = {
    title: '',
    body: '',
    channel: 'IN_APP',
    audience: 'PRIMARY_PARENTS',
  };

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    const path = '/api/school/notification-config/comms/bootstrap';
    const apply = (b: any) => {
      this.featureEnabled = !!b.featureEnabled;
      this.announcements = b.announcements || [];
      this.channels = Array.isArray(b.channels) && b.channels.length ? b.channels : this.channels;
      this.audiences = Array.isArray(b.audiences) && b.audiences.length ? b.audiences : this.audiences;
      this.loading = false;
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
        this.error = ModuleBootstrapService.errorMessage(
          err,
          'Comms hub unavailable — enable FEATURE_COMMS_HUB and restart settings/comms.',
        );
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  publish(): void {
    const title = this.draft.title.trim();
    const body = this.draft.body.trim();
    if (!title || !body) {
      this.error = 'Enter both a title and message before publishing.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .post<any>('/api/school/notification-config/comms/announcements', {
        ...this.draft,
        title,
        body,
      })
      .subscribe({
      next: (row) => {
        this.busy = false;
        this.status = `“${title}” was published. ${this.deliveryMessage(row)}`;
        this.announcements = [row, ...this.announcements.filter((item) => item.id !== row.id)];
        this.resetDraft();
        this.view = 'history';
        this.statusFilter = '';
        this.channelFilter = '';
        this.pageIndex = 0;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Publish failed';
      },
    });
  }

  selectView(view: CommsView): void {
    this.view = view;
    this.error = '';
  }

  openStatus(status: StatusFilter): void {
    this.view = 'history';
    this.statusFilter = status;
    this.channelFilter = '';
    this.pageIndex = 0;
  }

  get sentCount(): number {
    return this.announcements.filter((a) => String(a.status).toUpperCase() === 'SENT').length;
  }

  get attentionCount(): number {
    return this.announcements.filter((a) =>
      ['FAILED', 'PARTIAL_FAILED'].includes(String(a.status).toUpperCase()),
    ).length;
  }

  get totalRecipients(): number {
    return this.announcements.reduce((total, announcement) => {
      const summary = this.deliverySummary(announcement);
      const sent = Number(summary['sent'] ?? 0);
      const failed = Number(summary['failed'] ?? 0);
      const created = Number(summary['outboxCreated'] ?? summary['targets'] ?? summary['guardians'] ?? 0);
      return total + (sent + failed > 0 ? sent + failed : created);
    }, 0);
  }

  private matchingAnnouncements(): any[] {
    let rows = this.announcements;
    if (this.listQ.trim()) {
      const q = this.listQ.trim().toLowerCase();
      rows = rows.filter(
        (a) =>
          String(a.title ?? '').toLowerCase().includes(q) ||
          String(a.channel ?? '').toLowerCase().includes(q) ||
          String(a.audience ?? '').toLowerCase().includes(q) ||
          String(a.status ?? '').toLowerCase().includes(q),
      );
    }
    if (this.statusFilter) {
      rows =
        this.statusFilter === 'ATTENTION'
          ? rows.filter((a) =>
              ['FAILED', 'PARTIAL_FAILED'].includes(String(a.status || '').toUpperCase()),
            )
          : rows.filter((a) => String(a.status || '').toUpperCase() === this.statusFilter);
    }
    if (this.channelFilter) {
      rows = rows.filter((a) => String(a.channel || '').toUpperCase() === this.channelFilter);
    }
    return sortRows(rows, this.sortBy, this.sortDir, (row, key) => row?.[key]);
  }

  get filteredAnnouncements(): any[] {
    const rows = this.matchingAnnouncements();
    const start = this.pageIndex * this.pageSize;
    return rows.slice(start, start + this.pageSize);
  }

  get filteredAnnouncementsTotal(): number {
    return this.matchingAnnouncements().length;
  }

  get pageLabel(): string {
    const total = this.filteredAnnouncementsTotal;
    if (!total) return '0 announcements';
    const meta = pageMeta(this.pageIndex, this.pageSize, total);
    return `${meta.from}–${meta.to} of ${total}`;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      !!this.statusFilter ||
      !!this.channelFilter ||
      this.sortBy !== 'createdAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 25
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.statusFilter = '';
    this.channelFilter = '';
    this.sortBy = 'createdAt';
    this.sortDir = 'DESC';
    this.pageSize = 25;
    this.pageIndex = 0;
  }

  sortByColumn(key: string): void {
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.pageIndex = 0;
  }

  sortIcon(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  deliverySummary(announcement: any): Record<string, any> {
    const delivery = announcement?.delivery;
    if (!Array.isArray(delivery)) return {};
    return delivery.find((item) => item?.sent != null || item?.targets != null) || delivery[0] || {};
  }

  deliveryRate(announcement: any): number {
    const summary = this.deliverySummary(announcement);
    const sent = Number(summary['sent'] || 0);
    const failed = Number(summary['failed'] || 0);
    const total = sent + failed;
    return total ? Math.round((sent / total) * 100) : 0;
  }

  statusLabel(value: unknown): string {
    const status = String(value || '').toUpperCase();
    if (status === 'PARTIAL_FAILED') return 'Partially sent';
    if (status === 'DISPATCHING') return 'Sending';
    if (status === 'SENT') return 'Sent';
    if (status === 'FAILED') return 'Failed';
    if (status === 'QUEUED') return 'Queued';
    return status ? status.charAt(0) + status.slice(1).toLowerCase() : 'Unknown';
  }

  statusClass(value: unknown): string {
    const status = String(value || '').toUpperCase();
    if (status === 'SENT') return 'sent';
    if (status === 'FAILED') return 'failed';
    if (status === 'PARTIAL_FAILED') return 'warning';
    return 'queued';
  }

  channelLabel(value: unknown): string {
    const channel = String(value || '').toUpperCase();
    const labels: Record<string, string> = {
      IN_APP: 'In-app',
      EMAIL: 'Email + in-app',
      SMS: 'SMS + in-app',
      WHATSAPP: 'WhatsApp + in-app',
    };
    return labels[channel] || channel;
  }

  audienceLabel(value: unknown): string {
    const raw = String(value || '').toUpperCase();
    if (raw === 'PRIMARY_PARENTS' || raw === 'FATHER_MOTHER' || raw === 'FATHER_AND_MOTHER') {
      return 'Father & Mother only';
    }
    if (raw === 'ALL_ACTIVE' || raw === 'ALL') {
      return 'All active guardians';
    }
    return 'All linked parents / guardians';
  }

  fmtDate(value: unknown): string {
    if (!value) return '—';
    const date = new Date(String(value));
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  resetDraft(): void {
    this.draft = {
      title: '',
      body: '',
      channel: 'IN_APP',
      audience: 'PRIMARY_PARENTS',
    };
  }

  private deliveryMessage(row: any): string {
    const summary = row?.fanOut || this.deliverySummary(row);
    const sent = Number(summary?.sent || 0);
    const failed = Number(summary?.failed || 0);
    if (sent && !failed) return `Delivered to ${sent} recipient${sent === 1 ? '' : 's'}.`;
    if (sent) return `Delivered to ${sent}; ${failed} failed.`;
    return 'Delivery has been queued.';
  }
}
