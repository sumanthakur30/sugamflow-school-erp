import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService, PageResult } from '../../core/api.service';
import { ListPagerComponent } from '../../shared/list-toolbar/list-pager.component';
import {
  ListSortOption,
  ListStatusOption,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { parseListViewParams } from '../../shared/list-toolbar/list-view-route';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';
import { StudentFeeHistoryComponent } from '../../shared/student-fee-history/student-fee-history.component';

/** Fee form keys filled from Student Master — never typed manually. */
const IDENTITY_FIELD_KEYS = new Set(['studentName', 'admissionNo', 'email', 'mobile']);

type DatePreset = '' | 'today' | 'yesterday' | 'last7' | 'last30' | 'month';
type DueFilter = '' | 'has_due' | 'no_due' | 'due_gt_500';

@Component({
  selector: 'sf-fee',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    ListPagerComponent,
    StudentLookupComponent,
    StudentFeeHistoryComponent,
  ],
  templateUrl: './fee.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/inbox-list.scss',
    './fee.component.scss',
  ],
})
export class FeeComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private routeSub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'fee_collection';
  workflowKey = 'fee';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  collections: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  /** List-first: form/detail driven by ?new=1 / ?id= */
  formOpen = false;
  actionComment = '';
  submitting = false;
  private submitLocked = false;

  /** Student lookup state (New Collection). */
  selectedStudent: StudentLookupRow | null = null;
  studentDetail: any = null;
  feeSummary: any = null;
  feeHeads: Array<{ key: string; label: string }> = [];
  readonly paymentModes: Array<{ key: string; label: string }> = [
    { key: 'CASH', label: 'Cash' },
    { key: 'UPI', label: 'UPI' },
    { key: 'CARD', label: 'Card' },
    { key: 'NET_BANKING', label: 'Net Banking' },
    { key: 'CHEQUE', label: 'Cheque' },
    { key: 'WALLET', label: 'Wallet' },
    { key: 'QR', label: 'QR' },
    { key: 'ONLINE', label: 'Online / Gateway' },
    { key: 'OTHER', label: 'Other' },
  ];
  studentLoading = false;
  lookupSeed = '';
  /** Remount lookup when opening a fresh New Collection form. */
  lookupNonce = 0;

  listQ = '';
  listStatus = '';
  datePreset: DatePreset = '';
  dueFilter: DueFilter = '';
  dueAmountMin: number | null = null;
  dueAmountMax: number | null = null;
  moreMenuId: string | null = null;
  moreMenuRow: any = null;
  moreMenuPos = { top: 0, left: 0 };
  /** Ignore the document click that opens the menu (same gesture). */
  private suppressMoreDocClose = false;
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 50;
  searching = false;
  /** Full fetched set — filtered/paginated client-side (Orders-style). */
  allCollections: any[] = [];
  filteredTotal = 0;
  historyOpen = false;
  historyAdmissionNo = '';
  page: PageResult<any> = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  };
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'studentName', label: 'Student Name' },
    { key: 'status', label: 'Status' },
    { key: 'amount', label: 'Amount' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'APPROVED', label: 'Approved' },
    { value: 'REJECTED', label: 'Rejected' },
  ];
  readonly datePresets: Array<{ id: DatePreset; label: string }> = [
    { id: 'today', label: 'Today' },
    { id: 'yesterday', label: 'Yesterday' },
    { id: 'last7', label: 'Last 7 days' },
    { id: 'last30', label: 'Last 30 days' },
    { id: 'month', label: 'This month' },
  ];
  readonly dueFilters: Array<{ id: DueFilter; label: string }> = [
    { id: 'has_due', label: 'Has Due' },
    { id: 'no_due', label: 'No Due' },
    { id: 'due_gt_500', label: 'Due > 500' },
  ];

  ngOnInit(): void {
    this.routeSub = this.route.queryParamMap.subscribe((params) => this.syncFromRoute(params));
    this.reload();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  onDocClick(event: MouseEvent): void {
    if (this.suppressMoreDocClose || !this.moreMenuRow) return;
    const target = event.target;
    // Defer so View / Print / More handlers finish before we close the flyout.
    setTimeout(() => {
      if (this.suppressMoreDocClose || !this.moreMenuRow) return;
      if (!(target instanceof Element)) {
        this.closeMoreMenu();
        return;
      }
      if (target.closest('.order-actions-inline, .fee-actions-flyout, [data-fee-more-btn]')) {
        return;
      }
      this.closeMoreMenu();
    }, 0);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.moreMenuId) {
      this.closeMoreMenu();
      return;
    }
    if (this.submitting) return;
    if (this.historyOpen) {
      this.closeHistory();
      return;
    }
    if (this.formOpen) {
      this.closeForm();
      return;
    }
    if (this.selected) {
      this.closeDetail();
    }
  }

  openForm(): void {
    const admissionNo = this.route.snapshot.queryParamMap.get('admissionNo');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: admissionNo ? { new: '1', admissionNo } : { new: '1' },
    });
  }

  closeForm(): void {
    if (this.submitting) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  closeDetail(): void {
    this.actionComment = '';
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  private syncFromRoute(params: import('@angular/router').ParamMap): void {
    const { mode, id } = parseListViewParams(params);
    if (mode === 'new') {
      this.formOpen = true;
      this.selected = null;
      this.selectedId = null;
      this.error = '';
      this.statusMsg = '';
      this.resetAnswers();
      this.clearStudentSelection(false);
      this.lookupSeed = params.get('admissionNo') || '';
      this.lookupNonce += 1;
      if (this.lookupSeed && this.answers['admissionNo'] !== undefined) {
        this.answers['admissionNo'] = this.lookupSeed;
      }
      return;
    }
    this.formOpen = false;
    this.clearStudentSelection(false);
    if (mode === 'detail' && id) {
      if (this.selectedId !== id || !this.selected) {
        this.loadDetail(id);
      }
      return;
    }
    this.selected = null;
    this.selectedId = null;
    this.actionComment = '';
    const q = (params.get('q') || '').trim();
    if (q && this.listQ !== q) {
      this.listQ = q;
      this.pageIndex = 0;
      if (this.allCollections.length) this.applyListFilters();
    }
  }

  private loadDetail(id: string): void {
    this.selectedId = id;
    this.error = '';
    this.statusMsg = '';
    this.api.get<any>(`/api/fee/collections/${id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load collection'),
    });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/fee/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        this.resetAnswers();
        this.loading = false;
        this.loadCollections();
        this.loadFeeHeads();
        // Re-apply route view now that fields exist (e.g. ?new=1 + admissionNo).
        this.syncFromRoute(this.route.snapshot.queryParamMap);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Fee bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  private loadFeeHeads(): void {
    this.api
      .get<any[]>('/api/fee/finance/heads')
      .pipe(catchError(() => of([] as any[])))
      .subscribe((rows) => {
        const list = Array.isArray(rows) ? rows : [];
        this.feeHeads = list
          .map((r) => {
            const key = String(r.definitionKey || r.key || r.name || '').trim();
            const label = String(r.label || r.name || r.title || key).trim();
            const active = r.active !== false && r.enabled !== false && r.status !== 'INACTIVE';
            return key && active ? { key, label: label || key } : null;
          })
          .filter((x): x is { key: string; label: string } => !!x);
      });
  }

  loadCollections(): void {
    this.searching = true;
    this.api.getPage<any>('/api/fee/collections', 0, 200).subscribe({
      next: (p) => {
        this.allCollections = p.items || [];
        this.applyListFilters();
        this.searching = false;
      },
      error: (err) => {
        this.searching = false;
        this.error = err?.error?.message ?? 'Failed to load collections';
      },
    });
  }

  applyListFilters(): void {
    let items = [...this.allCollections];
    if (this.listQ?.trim()) {
      const q = this.listQ.trim().toLowerCase();
      items = items.filter((row) => {
        const name = String(row.answers?.studentName ?? '').toLowerCase();
        const admissionNo = String(row.answers?.admissionNo ?? '').toLowerCase();
        const mobile = String(row.answers?.mobile ?? '').toLowerCase();
        const feeHead = String(row.answers?.feeHead ?? '').toLowerCase();
        const paymentMode = String(row.answers?.paymentMode ?? '').toLowerCase();
        const status = String(row.status ?? '').toLowerCase();
        const id = String(row.id ?? '').toLowerCase();
        return (
          name.includes(q) ||
          admissionNo.includes(q) ||
          mobile.includes(q) ||
          feeHead.includes(q) ||
          paymentMode.includes(q) ||
          status.includes(q) ||
          id.includes(q)
        );
      });
    }
    if (this.listStatus) {
      const st = this.listStatus.toUpperCase();
      items = items.filter((row) => String(row.status || '').toUpperCase() === st);
    }
    if (this.datePreset) {
      items = items.filter((row) => this.matchesDatePreset(row.updatedAt || row.createdAt, this.datePreset));
    }
    if (this.dueFilter === 'has_due') {
      items = items.filter((row) => String(row.status || '').toUpperCase() !== 'APPROVED');
    } else if (this.dueFilter === 'no_due') {
      items = items.filter((row) => String(row.status || '').toUpperCase() === 'APPROVED');
    } else if (this.dueFilter === 'due_gt_500') {
      items = items.filter((row) => {
        if (String(row.status || '').toUpperCase() === 'APPROVED') return false;
        return Number(row.answers?.amount) > 500;
      });
    }
    if (this.dueAmountMin != null && this.dueAmountMin !== ('' as any)) {
      const min = Number(this.dueAmountMin);
      if (!Number.isNaN(min)) {
        items = items.filter((row) => this.dueAmountValue(row) >= min);
      }
    }
    if (this.dueAmountMax != null && this.dueAmountMax !== ('' as any)) {
      const max = Number(this.dueAmountMax);
      if (!Number.isNaN(max)) {
        items = items.filter((row) => this.dueAmountValue(row) <= max);
      }
    }
    items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
      if (key === 'studentName') return row.answers?.studentName;
      if (key === 'amount') return row.answers?.amount;
      if (key === 'updatedAt') return row.updatedAt || row.createdAt;
      return row?.[key];
    });
    this.filteredTotal = items.length;
    const totalPages = Math.max(1, Math.ceil(items.length / this.pageSize) || 1);
    if (this.pageIndex >= totalPages) this.pageIndex = Math.max(0, totalPages - 1);
    const start = this.pageIndex * this.pageSize;
    const pageItems = items.slice(start, start + this.pageSize);
    this.collections = pageItems;
    this.page = {
      items: pageItems,
      page: this.pageIndex,
      size: this.pageSize,
      totalElements: items.length,
      totalPages,
      hasNext: start + this.pageSize < items.length,
    };
  }

  private matchesDatePreset(iso: unknown, preset: DatePreset): boolean {
    if (!iso || !preset) return true;
    const d = new Date(String(iso));
    if (Number.isNaN(d.getTime())) return false;
    const now = new Date();
    const startOfDay = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate());
    const today = startOfDay(now);
    if (preset === 'today') return startOfDay(d).getTime() === today.getTime();
    if (preset === 'yesterday') {
      const y = new Date(today);
      y.setDate(y.getDate() - 1);
      return startOfDay(d).getTime() === y.getTime();
    }
    if (preset === 'last7') {
      const from = new Date(today);
      from.setDate(from.getDate() - 6);
      return d >= from;
    }
    if (preset === 'last30') {
      const from = new Date(today);
      from.setDate(from.getDate() - 29);
      return d >= from;
    }
    if (preset === 'month') {
      return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
    }
    return true;
  }

  setDatePreset(preset: DatePreset): void {
    this.datePreset = this.datePreset === preset ? '' : preset;
    this.pageIndex = 0;
    this.applyListFilters();
  }

  setDueFilter(filter: DueFilter): void {
    this.dueFilter = this.dueFilter === filter ? '' : filter;
    this.dueAmountMin = null;
    this.dueAmountMax = null;
    this.pageIndex = 0;
    this.applyListFilters();
  }

  openHistory(admissionNo = ''): void {
    this.historyAdmissionNo = admissionNo || '';
    this.historyOpen = true;
  }

  closeHistory(): void {
    this.historyOpen = false;
    this.historyAdmissionNo = '';
  }

  onHistoryCollect(admissionNo: string): void {
    this.closeHistory();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: admissionNo ? { new: '1', admissionNo } : { new: '1' },
    });
  }

  onHistoryOpenCollection(id: string): void {
    this.closeHistory();
    void this.router.navigate([], { relativeTo: this.route, queryParams: { id } });
  }

  openHistoryForRow(row: any, event?: Event): void {
    event?.stopPropagation();
    const adm = String(row?.answers?.admissionNo || '').trim();
    this.openHistory(adm);
  }

  /** Print HTML without pop-ups (hidden iframe). */
  printHtmlViaIframe(html: string): boolean {
    try {
      const iframe = document.createElement('iframe');
      iframe.setAttribute('title', 'fee-print');
      iframe.style.position = 'fixed';
      iframe.style.right = '0';
      iframe.style.bottom = '0';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.border = '0';
      iframe.style.opacity = '0';
      iframe.style.pointerEvents = 'none';
      document.body.appendChild(iframe);
      const doc = iframe.contentDocument || iframe.contentWindow?.document;
      if (!doc) {
        iframe.remove();
        return false;
      }
      doc.open();
      doc.write(html);
      doc.close();
      const win = iframe.contentWindow;
      if (!win) {
        iframe.remove();
        return false;
      }
      const cleanup = () => {
        setTimeout(() => iframe.remove(), 1000);
      };
      const trigger = () => {
        try {
          win.focus();
          win.print();
        } finally {
          cleanup();
        }
      };
      setTimeout(trigger, 250);
      return true;
    } catch {
      return false;
    }
  }

  /** Print a PDF blob via hidden iframe; returns false if frame print is unavailable. */
  printPdfBlob(blob: Blob, filename: string): boolean {
    try {
      const pdfBlob =
        blob.type && blob.type.includes('pdf')
          ? blob
          : new Blob([blob], { type: 'application/pdf' });
      const url = URL.createObjectURL(pdfBlob);
      const iframe = document.createElement('iframe');
      iframe.setAttribute('title', filename);
      iframe.style.position = 'fixed';
      iframe.style.right = '0';
      iframe.style.bottom = '0';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.border = '0';
      iframe.style.opacity = '0';
      document.body.appendChild(iframe);
      let printed = false;
      const cleanup = () => {
        setTimeout(() => {
          iframe.remove();
          URL.revokeObjectURL(url);
        }, 1500);
      };
      iframe.onload = () => {
        if (printed) return;
        printed = true;
        try {
          iframe.contentWindow?.focus();
          iframe.contentWindow?.print();
        } finally {
          cleanup();
        }
      };
      iframe.src = url;
      setTimeout(() => {
        if (printed) return;
        printed = true;
        try {
          iframe.contentWindow?.focus();
          iframe.contentWindow?.print();
        } catch {
          // ignore
        } finally {
          cleanup();
        }
      }, 800);
      return true;
    } catch {
      return false;
    }
  }

  downloadReceiptFor(row: any, event?: Event): void {
    event?.stopPropagation();
    event?.preventDefault();
    this.closeMoreMenu();
    if (!row?.id) return;
    this.error = '';
    this.statusMsg = '';

    // Always allow Print: prefer PDF receipt when available, otherwise thermal HTML slip.
    if (this.canPrint(row)) {
      this.statusMsg = 'Preparing receipt…';
      this.api.getBlob(`/api/fee/collections/${row.id}/receipt`).subscribe({
        next: (blob) => {
          this.statusMsg = '';
          const ok = this.printPdfBlob(blob, `fee-receipt-${row.id}.pdf`);
          if (!ok) {
            // Fallback download if print frame failed
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `fee-receipt-${row.id}.pdf`;
            a.click();
            URL.revokeObjectURL(url);
            this.statusMsg = 'Receipt downloaded.';
          } else {
            this.statusMsg = 'Print dialog opened.';
          }
        },
        error: () => {
          this.statusMsg = '';
          this.printThermalReceipt(row, false);
        },
      });
      return;
    }
    this.printThermalReceipt(row, false);
  }

  canPrint(row: any): boolean {
    if (!row?.id) return false;
    if (row.hasFeeReceipt === true || row.hasFeeReceipt === 'true') return true;
    return String(row.status || '').toUpperCase() === 'APPROVED';
  }

  collectionIdShort(row: any): string {
    const id = String(row?.id || '');
    return id ? `COL-${id.slice(0, 8).toUpperCase()}` : '—';
  }

  classHint(row: any): string {
    return String(row?.answers?.classSection || row?.answers?.classApplied || '').trim();
  }

  dueAmountLabel(row: any): string {
    if (String(row?.status || '').toUpperCase() === 'APPROVED') return '';
    const n = Number(row?.answers?.amount);
    if (!n) return '';
    return `Due ${this.formatMoney(n)}`;
  }

  get showingFrom(): number {
    return pageMeta(this.pageIndex, this.pageSize, this.filteredTotal).from;
  }

  get showingTo(): number {
    return pageMeta(this.pageIndex, this.pageSize, this.filteredTotal).to;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      !!this.listStatus ||
      !!this.datePreset ||
      !!this.dueFilter ||
      this.dueAmountMin != null ||
      this.dueAmountMax != null ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  get collectionFields(): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    return this.fields.filter((f) => !IDENTITY_FIELD_KEYS.has(f.key));
  }

  clearListFilters(): void {
    this.listQ = '';
    this.listStatus = '';
    this.datePreset = '';
    this.dueFilter = '';
    this.dueAmountMin = null;
    this.dueAmountMax = null;
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.closeMoreMenu();
    this.applyListFilters();
  }

  toggleMoreMenu(row: any, event?: Event): void {
    event?.stopPropagation();
    event?.preventDefault();
    const id = String(row?.id || '');
    if (!id) return;
    if (this.moreMenuId === id) {
      this.closeMoreMenu();
      return;
    }
    this.moreMenuId = id;
    this.moreMenuRow = row;
    // Anchored under the More button (absolute). Keep pos for any future fixed fallback.
    const btn =
      ((event?.currentTarget as HTMLElement | null)?.closest?.('button') as HTMLElement | null) ??
      (event?.target instanceof Element
        ? (event.target.closest('button') as HTMLElement | null)
        : null);
    if (btn) {
      const rect = btn.getBoundingClientRect();
      this.moreMenuPos = {
        top: Math.round(rect.bottom + 4),
        left: Math.round(Math.max(8, rect.right - 200)),
      };
    }
    this.suppressMoreDocClose = true;
    setTimeout(() => {
      this.suppressMoreDocClose = false;
    }, 50);
  }

  closeMoreMenu(): void {
    this.moreMenuId = null;
    this.moreMenuRow = null;
    this.suppressMoreDocClose = false;
  }

  editCollection(row: any): void {
    this.closeMoreMenu();
    this.select(row);
  }

  openStudentProfile(row: any): void {
    this.closeMoreMenu();
    const studentId = String(row?.student?.id || '').trim();
    if (studentId) {
      void this.router.navigate(['/admin/students'], { queryParams: { id: studentId } });
      return;
    }
    const admissionNo = String(row?.answers?.admissionNo || '').trim();
    if (!admissionNo) {
      this.error = 'No admission number on this collection to open a student profile.';
      return;
    }
    this.api.get<any>(`/api/student/students/by-admission/${encodeURIComponent(admissionNo)}`).subscribe({
      next: (student) => {
        const id = student?.id;
        if (id) {
          void this.router.navigate(['/admin/students'], { queryParams: { id } });
          return;
        }
        void this.router.navigate(['/admin/student-directory'], {
          queryParams: { q: admissionNo },
        });
      },
      error: () => {
        void this.router.navigate(['/admin/student-directory'], {
          queryParams: { q: admissionNo },
        });
      },
    });
  }

  printThermalReceipt(row: any, closeMenu = true): void {
    if (closeMenu) this.closeMoreMenu();
    if (!row?.id) return;
    this.error = '';
    const html = this.buildThermalReceiptHtml(row);
    const ok = this.printHtmlViaIframe(html);
    if (!ok) {
      this.error = 'Could not open print dialog. Check browser print settings and try again.';
      return;
    }
    this.statusMsg = 'Thermal print dialog opened.';
  }

  private buildThermalReceiptHtml(row: any): string {
    const name = this.escapeHtml(this.studentName(row));
    const admissionNo = this.escapeHtml(this.answer(row, 'admissionNo'));
    const mobile = this.escapeHtml(this.answer(row, 'mobile'));
    const feeHead = this.escapeHtml(this.answer(row, 'feeHead'));
    const mode = this.escapeHtml(this.answer(row, 'paymentMode'));
    const amount = this.escapeHtml(this.formatMoney(row?.answers?.amount));
    const status = this.escapeHtml(this.paymentLabel(row));
    const when = this.escapeHtml(this.formatWhen(row?.updatedAt || row?.createdAt));
    const colId = this.escapeHtml(this.collectionIdShort(row));
    return `<!doctype html>
<html><head><meta charset="utf-8" /><title>${colId}</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; width: 280px; margin: 12px auto; color: #111; }
  h1 { font-size: 14px; margin: 0 0 8px; text-align: center; }
  .line { border-top: 1px dashed #999; margin: 8px 0; }
  .row { display: flex; justify-content: space-between; gap: 8px; margin: 3px 0; }
  .muted { color: #555; }
  .total { font-size: 14px; font-weight: 700; }
  @media print { body { margin: 0; } }
</style></head><body>
  <h1>Fee Receipt</h1>
  <div class="muted" style="text-align:center">${colId}</div>
  <div class="line"></div>
  <div class="row"><span>Student</span><span>${name}</span></div>
  <div class="row"><span>Admission</span><span>${admissionNo}</span></div>
  <div class="row"><span>Mobile</span><span>${mobile}</span></div>
  <div class="line"></div>
  <div class="row"><span>Fee head</span><span>${feeHead}</span></div>
  <div class="row"><span>Mode</span><span>${mode}</span></div>
  <div class="row"><span>Status</span><span>${status}</span></div>
  <div class="row"><span>Date</span><span>${when}</span></div>
  <div class="line"></div>
  <div class="row total"><span>Total</span><span>${amount}</span></div>
  <div class="line"></div>
  <div class="muted" style="text-align:center">Thank you</div>
</body></html>`;
  }

  private escapeHtml(v: unknown): string {
    return String(v ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  deleteCollection(row: any): void {
    this.closeMoreMenu();
    if (!row?.id) return;
    const status = String(row.status || '').toUpperCase();
    if (status === 'APPROVED') {
      this.error = 'Approved collections cannot be deleted. Use Reject only on open collections.';
      return;
    }
    if (status === 'REJECTED') {
      this.error = 'This collection is already rejected.';
      return;
    }
    const label = `${this.studentName(row)} · ${this.collectionIdShort(row)}`;
    if (!window.confirm(`Delete collection for ${label}? This rejects the collection and cannot be undone.`)) {
      return;
    }
    this.error = '';
    this.statusMsg = 'Deleting collection…';
    this.api
      .post<any>(`/api/fee/collections/${row.id}/actions`, {
        action: 'REJECT',
        comment: 'Deleted from fee list',
      })
      .subscribe({
        next: () => {
          this.statusMsg = 'Collection deleted (rejected).';
          this.loadCollections();
        },
        error: (err) => {
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Delete failed';
        },
      });
  }

  setSort(key: string): void {
    if (this.sortBy === key) {
      this.sortDir = this.sortDir === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortBy = key;
      this.sortDir = key === 'studentName' ? 'ASC' : 'DESC';
    }
    this.pageIndex = 0;
    this.applyListFilters();
  }

  sortIndicator(key: string): string {
    if (this.sortBy !== key) return '';
    return this.sortDir === 'ASC' ? ' ↑' : ' ↓';
  }

  paymentLabel(row: any): string {
    const s = String(row?.status || '').toUpperCase();
    if (s === 'APPROVED') return 'PAID';
    if (s === 'REJECTED') return 'REJECTED';
    if (s === 'INFO_REQUESTED') return 'INFO';
    return 'PENDING';
  }

  paymentBadgeClass(row: any): string {
    const s = this.paymentLabel(row);
    if (s === 'PAID') return 'badge badge-ok';
    if (s === 'REJECTED') return 'badge badge-bad';
    return 'badge badge-progress';
  }

  onDueRangeChange(): void {
    this.pageIndex = 0;
    this.dueFilter = '';
    this.applyListFilters();
  }

  dueAmountValue(row: any): number {
    if (String(row?.status || '').toUpperCase() === 'APPROVED') return 0;
    const n = Number(row?.answers?.amount);
    return Number.isFinite(n) ? n : 0;
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
    this.applyListFilters();
  }

  onStudentSelected(row: StudentLookupRow): void {
    this.selectedStudent = row;
    this.error = '';
    this.applyLookupRow(row);
    this.studentLoading = true;
    const admissionNo = String(row.admissionNo || '').trim();
    const isApplicant = row.source === 'ADMISSION';

    if (isApplicant) {
      // Applicant is not in Student Master yet — load application + fee snapshot only.
      forkJoin({
        detail: this.api.get<any>(`/api/admission/applications/${row.applicationId || row.id}`).pipe(
          catchError((err) => {
            this.error = err?.error?.message ?? 'Failed to load application';
            return of(null);
          }),
        ),
        summary: admissionNo
          ? this.api
              .get<any>(`/api/fee/clearance/${encodeURIComponent(admissionNo)}`)
              .pipe(catchError(() => of(null)))
          : of(null),
      }).subscribe(({ detail, summary }) => {
        this.studentLoading = false;
        this.studentDetail = detail
          ? {
              ...detail,
              answers: {
                ...(detail.answers ?? {}),
                fullName: row.fullName,
                classSection: row.classSection,
              },
              status: 'APPLICANT',
            }
          : { answers: { fullName: row.fullName, ...row }, status: 'APPLICANT' };
        this.feeSummary = summary;
        if (detail?.answers) {
          this.setAnswer('studentName', row.fullName || detail.answers.fullName || '');
          this.setAnswer('email', detail.answers.email || row.email || '');
          this.setAnswer('mobile', detail.answers.mobile || row.mobile || '');
          this.setAnswer('admissionNo', admissionNo);
        }
        if (summary) {
          this.applyFeeSummary(summary);
        }
      });
      return;
    }

    forkJoin({
      detail: this.api.get<any>(`/api/student/students/${row.id}`).pipe(
        catchError((err) => {
          this.error = err?.error?.message ?? 'Failed to load student profile';
          return of(null);
        }),
      ),
      summary: admissionNo
        ? this.api.get<any>(`/api/fee/students/${encodeURIComponent(admissionNo)}/fee-summary`).pipe(
            catchError(() =>
              this.api
                .get<any>(`/api/fee/clearance/${encodeURIComponent(admissionNo)}`)
                .pipe(catchError(() => of(null))),
            ),
          )
        : of(null),
    }).subscribe(({ detail, summary }) => {
      this.studentLoading = false;
      this.studentDetail = detail;
      this.feeSummary = summary;
      if (detail) {
        this.applyStudentDetail(detail);
      }
      if (summary) {
        this.applyFeeSummary(summary);
      }
    });
  }

  onStudentCleared(): void {
    this.clearStudentSelection(true);
  }

  isIdentityField(key: string): boolean {
    return IDENTITY_FIELD_KEYS.has(key);
  }

  isFieldLocked(key: string): boolean {
    return this.submitting || (this.isIdentityField(key) && !!this.selectedStudent);
  }

  cardValue(key: string): string {
    const answers = this.studentDetail?.answers ?? {};
    const fromDetail = answers[key];
    if (fromDetail != null && String(fromDetail).trim() !== '') {
      return String(fromDetail);
    }
    const fromRow = (this.selectedStudent as any)?.[key];
    if (fromRow != null && String(fromRow).trim() !== '') {
      return String(fromRow);
    }
    if (key === 'fullName' || key === 'studentName') {
      return this.selectedStudent?.fullName || '—';
    }
    if (key === 'admissionNo') return this.selectedStudent?.admissionNo || '—';
    if (key === 'classSection') return this.selectedStudent?.classSection || '—';
    if (key === 'parentName' || key === 'fatherName') {
      return this.selectedStudent?.parentName || '—';
    }
    return '—';
  }

  submit(): void {
    if (this.submitLocked || this.submitting) return;
    if (!this.selectedStudent?.admissionNo && !String(this.answers['admissionNo'] || '').trim()) {
      this.error = 'Select a student from the master list before submitting.';
      return;
    }
    this.submitLocked = true;
    this.submitting = true;
    this.error = '';
    this.statusMsg = 'Saving collection…';
    this.api
      .post<any>('/api/fee/collections', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = 'Collection submitted.';
          this.pageIndex = 0;
          this.loadCollections();
          if (row?.id) {
            void this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { id: row.id },
            });
          } else {
            void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
          }
        },
        error: (err) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: row.id },
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId || this.submitLocked || this.submitting) return;
    this.submitLocked = true;
    this.submitting = true;
    this.statusMsg = 'Processing workflow action…';
    this.api
      .post<any>(`/api/fee/collections/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.submitLocked = false;
          this.selected = row;
          this.actionComment = '';
          this.statusMsg = `Action ${action} completed.`;
          this.loadCollections();
        },
        error: (err) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  printReceipt(): void {
    if (!this.selected) return;
    this.downloadReceiptFor(this.selected);
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'FEE_APPROVED') {
        return intents[i].delivery ?? [];
      }
    }
    return [];
  }

  answer(row: any, key: string): string {
    const v = row?.answers?.[key];
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    if (v == null || String(v).trim() === '') return '—';
    return String(v);
  }

  studentName(row: any): string {
    const fromStudent = row?.student?.fullName;
    if (fromStudent && String(fromStudent).trim()) return String(fromStudent);
    const name = this.answer(row, 'studentName');
    return name === '—' ? String(row?.id || '—') : name;
  }

  detailStudent(row: any = this.selected): any {
    return row?.student || {};
  }

  crumbLabel(row: any = this.selected): string {
    if (row?.displayTitle) return String(row.displayTitle);
    const s = this.detailStudent(row);
    const adm = s.admissionNo || this.answer(row, 'admissionNo');
    const name = s.fullName || this.studentName(row);
    const cls = s.classSection || '—';
    if (adm && adm !== '—') return `${adm} — ${name} (${cls})`;
    return name;
  }

  studentField(key: string, fallbackAnswerKey?: string): string {
    const s = this.detailStudent();
    const v = s?.[key];
    if (v != null && String(v).trim() !== '') return String(v);
    if (fallbackAnswerKey) return this.answer(this.selected, fallbackAnswerKey);
    return '—';
  }

  classDisplay(): string {
    const s = this.detailStudent();
    if (s.classSection) return String(s.classSection);
    const cls = s.className || '';
    const sec = s.section || '';
    if (cls && sec) return `${cls}-${sec}`;
    if (cls) return String(cls);
    return this.answer(this.selected, 'classSection');
  }

  collectionMeta(): Array<{ label: string; value: string; money?: boolean }> {
    const a = this.selected?.answers ?? {};
    const receipt =
      a.receiptRef ||
      a.receiptNo ||
      (this.selected?.hasFeeReceipt ? `RCP-${String(this.selected.id).slice(0, 8).toUpperCase()}` : '—');
    return [
      { label: 'Receipt No', value: String(receipt) },
      { label: 'Fee Head', value: this.answer(this.selected, 'feeHead') },
      { label: 'Amount', value: this.formatMoney(a.amount), money: true },
      { label: 'Payment Mode', value: this.answer(this.selected, 'paymentMode') },
      { label: 'Payment Date', value: this.formatWhen(this.selected?.updatedAt || this.selected?.createdAt) },
      { label: 'Pending Days', value: this.answer(this.selected, 'pendingDays') },
      { label: 'Collected By', value: String(this.selected?.createdBy || '—') },
      {
        label: 'Transaction ID',
        value: String(a.gatewayTxnId || a.transactionId || this.selected?.id || '—'),
      },
      { label: 'Approval Status', value: String(this.selected?.status || '—') },
    ];
  }

  /** Identity fields are shown on the student card — hide from the flat answers grid. */
  detailFields(): Array<{ key: string; label: string; value: string }> {
    const hide = new Set([
      'studentName',
      'admissionNo',
      'email',
      'mobile',
      'feeHead',
      'amount',
      'paymentMode',
      'pendingDays',
    ]);
    const answers = this.selected?.answers ?? {};
    return Object.keys(answers)
      .filter((k) => !hide.has(k))
      .filter((k) => answers[k] == null || typeof answers[k] !== 'object')
      .map((key) => ({
        key,
        label: this.prettyLabel(key),
        value: this.answer(this.selected, key),
      }));
  }

  printStudentCard(): void {
    window.print();
  }

  formatMoney(raw: unknown): string {
    if (raw == null || raw === '') return '—';
    const n = Number(raw);
    if (Number.isNaN(n)) return String(raw);
    return n.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'APPROVED' || s === 'PAID' || s === 'ACTIVE') return 'badge badge-ok';
    if (s === 'REJECTED' || s === 'CANCELLED') return 'badge badge-bad';
    if (s === 'IN_PROGRESS' || s === 'PENDING') return 'badge badge-progress';
    return 'badge';
  }

  formatWhen(raw: unknown): string {
    if (!raw) return '—';
    const d = new Date(String(raw));
    if (Number.isNaN(d.getTime())) return String(raw);
    return d.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  private applyLookupRow(row: StudentLookupRow): void {
    this.setAnswer('studentName', row.fullName || '');
    this.setAnswer('admissionNo', row.admissionNo || '');
    this.setAnswer('email', row.email || '');
    this.setAnswer('mobile', row.mobile || '');
  }

  private applyStudentDetail(detail: any): void {
    const answers = detail?.answers ?? {};
    const name =
      answers.fullName || answers.studentName || this.selectedStudent?.fullName || '';
    this.setAnswer('studentName', name);
    this.setAnswer('admissionNo', detail?.admissionNo || answers.admissionNo || '');
    this.setAnswer('email', answers.email || '');
    this.setAnswer('mobile', answers.mobile || '');
  }

  private applyFeeSummary(summary: any): void {
    if (summary?.pendingDays != null && this.answers['pendingDays'] !== undefined) {
      this.answers['pendingDays'] = Number(summary.pendingDays) || 0;
    }
    const pending = Number(summary?.pendingAmount);
    if (
      !Number.isNaN(pending) &&
      pending > 0 &&
      this.answers['amount'] !== undefined &&
      (this.answers['amount'] === 0 || this.answers['amount'] === '0' || this.answers['amount'] === '')
    ) {
      this.answers['amount'] = pending;
    }
  }

  private setAnswer(key: string, value: unknown): void {
    if (this.answers[key] === undefined) return;
    this.answers[key] = value;
  }

  private clearStudentSelection(resetIdentity: boolean): void {
    this.selectedStudent = null;
    this.studentDetail = null;
    this.feeSummary = null;
    this.studentLoading = false;
    if (resetIdentity) {
      for (const key of IDENTITY_FIELD_KEYS) {
        if (this.answers[key] !== undefined) {
          this.answers[key] = '';
        }
      }
      if (this.answers['pendingDays'] !== undefined) {
        this.answers['pendingDays'] = 0;
      }
    }
  }

  private resetAnswers(): void {
    const next: Record<string, unknown> = {};
    for (const f of this.fields) {
      next[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? 0 : '';
    }
    this.answers = next;
  }

  private normalizeAnswers(): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.fields) {
      let v = this.answers[f.key];
      if (f.type === 'NUMBER' && v !== '' && v != null) {
        v = Number(v);
      }
      if (f.type === 'CHECKBOX') {
        v = !!v;
      }
      out[f.key] = v;
    }
    return out;
  }

  private prettyLabel(key: string): string {
    const map: Record<string, string> = {
      studentName: 'Student name',
      admissionNo: 'Admission No',
      amount: 'Amount',
    };
    if (map[key]) return map[key];
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (c) => c.toUpperCase())
      .trim();
  }

  private extractFields(
    form: any,
  ): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    const fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
    for (const section of form?.sections ?? []) {
      for (const field of section.fields ?? []) {
        fields.push({
          key: field.key,
          label: field.label ?? field.key,
          type: field.type ?? 'TEXTBOX',
          mandatory: !!field.mandatory,
        });
      }
    }
    return fields;
  }
}
