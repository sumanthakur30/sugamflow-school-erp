import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, TimeoutError, catchError, throwError, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListPagerComponent } from '../../shared/list-toolbar/list-pager.component';
import {
  ListSortOption,
  ListStatusOption,
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { parseListViewParams } from '../../shared/list-toolbar/list-view-route';
import { StaffLookupComponent, StaffLookupRow } from '../../shared/staff-lookup';

@Component({
  selector: 'sf-payroll',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ListToolbarComponent,
    ListPagerComponent,
    StaffLookupComponent,
  ],
  templateUrl: './payroll.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './payroll.component.scss',
  ],
})
export class PayrollComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private routeSub?: Subscription;
  private campusReadySub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'payroll_run';
  workflowKey = 'payroll';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  fieldErrors: Record<string, string> = {};
  selectedStaff: StaffLookupRow | null = null;
  lookupNonce = 0;
  records: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  /** List-first: form/detail driven by ?new=1 / ?id= / ?id=&edit=1 */
  formOpen = false;
  editingId: string | null = null;
  /** CORRECTION / ADJUSTMENT parent when creating a linked run */
  parentRecordId: string | null = null;
  runType: 'REGULAR' | 'CORRECTION' | 'ADJUSTMENT' = 'REGULAR';
  actionComment = '';
  submitting = false;
  private submitLocked = false;

  listQ = '';
  listStatus = '';
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 50;
  searching = false;
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
    { key: 'employeeName', label: 'Employee Name' },
    { key: 'status', label: 'Status' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'IN_PROGRESS', label: 'IN_PROGRESS' },
    { value: 'INFO_REQUESTED', label: 'INFO_REQUESTED' },
    { value: 'APPROVED', label: 'APPROVED' },
    { value: 'REJECTED', label: 'REJECTED' },
    { value: 'VOIDED', label: 'VOIDED' },
    { value: 'REVERSED', label: 'REVERSED' },
  ];
  readonly monthOptions = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];
  readonly yearOptions = Array.from({ length: 7 }, (_, i) => new Date().getFullYear() - 2 + i);

  ngOnInit(): void {
    this.routeSub = this.route.queryParamMap.subscribe((params) => this.syncFromRoute(params));
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    this.campusReadySub?.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.submitting) return;
    if (this.formOpen) {
      this.closeForm();
      return;
    }
    if (this.selected) {
      this.closeDetail();
    }
  }

  openForm(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { new: '1' },
    });
  }

  openEdit(row: any, event?: Event): void {
    event?.stopPropagation();
    if (!this.canEdit(row)) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: row.id, edit: '1' },
    });
  }

  openCorrection(row: any, event?: Event): void {
    event?.stopPropagation();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { new: '1', parentId: row.id, runType: 'CORRECTION' },
    });
  }

  openAdjustment(row: any, event?: Event): void {
    event?.stopPropagation();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { new: '1', parentId: row.id, runType: 'ADJUSTMENT' },
    });
  }

  closeForm(): void {
    if (this.submitting) return;
    this.editingId = null;
    this.parentRecordId = null;
    this.runType = 'REGULAR';
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  closeDetail(): void {
    this.actionComment = '';
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  private syncFromRoute(params: import('@angular/router').ParamMap): void {
    const { mode, id } = parseListViewParams(params);
    const editMode = params.get('edit') === '1' || params.get('edit') === 'true';
    const parentId = params.get('parentId')?.trim() || null;
    const runTypeRaw = (params.get('runType') || 'REGULAR').trim().toUpperCase();
    const linkedRunType =
      runTypeRaw === 'CORRECTION' || runTypeRaw === 'ADJUSTMENT' ? runTypeRaw : 'REGULAR';

    if (mode === 'new') {
      this.formOpen = true;
      this.editingId = null;
      this.selected = null;
      this.selectedId = null;
      this.parentRecordId = parentId;
      this.runType = linkedRunType as 'REGULAR' | 'CORRECTION' | 'ADJUSTMENT';
      this.error = '';
      this.statusMsg = '';
      this.fieldErrors = {};
      this.resetAnswers();
      this.lookupNonce++;
      if (parentId) {
        this.prefillFromParent(parentId);
      }
      return;
    }
    if (mode === 'detail' && id && editMode) {
      this.formOpen = false;
      this.selected = null;
      this.parentRecordId = null;
      this.runType = 'REGULAR';
      this.loadDetailForEdit(id);
      return;
    }
    this.formOpen = false;
    this.editingId = null;
    this.parentRecordId = null;
    this.runType = 'REGULAR';
    if (mode === 'detail' && id) {
      if (this.selectedId !== id || !this.selected) {
        this.loadDetail(id);
      }
      return;
    }
    this.selected = null;
    this.selectedId = null;
    this.actionComment = '';
  }

  private loadDetail(id: string): void {
    this.selectedId = id;
    this.error = '';
    this.api.get<any>(`/api/payroll/records/${id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  private loadDetailForEdit(id: string): void {
    this.selectedId = id;
    this.error = '';
    this.statusMsg = '';
    this.api.get<any>(`/api/payroll/records/${id}`).subscribe({
      next: (full) => {
        if (!this.canEdit(full)) {
          this.error = 'Only draft payroll runs can be edited.';
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { id },
          });
          return;
        }
        this.selected = full;
        this.editingId = id;
        this.formOpen = true;
        this.runType = (full.runType || 'REGULAR') as 'REGULAR' | 'CORRECTION' | 'ADJUSTMENT';
        this.parentRecordId = full.parentRecordId || null;
        this.populateAnswersFromRecord(full);
        this.lookupNonce++;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  private prefillFromParent(parentId: string): void {
    this.api.get<any>(`/api/payroll/records/${parentId}`).subscribe({
      next: (parent) => {
        this.populateAnswersFromRecord(parent);
        this.lookupNonce++;
        this.statusMsg =
          this.runType === 'ADJUSTMENT'
            ? `Adjustment linked to paid run ${parentId}.`
            : `Correction linked to reversed run ${parentId}.`;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load parent payroll run';
      },
    });
  }

  private populateAnswersFromRecord(row: any): void {
    const answers = row?.answers ?? {};
    const next: Record<string, unknown> = { ...this.answers };
    for (const f of this.fields) {
      if (answers[f.key] !== undefined && answers[f.key] !== null) {
        next[f.key] = answers[f.key];
      }
    }
    next['staffRecordId'] = answers['staffRecordId'] ?? next['staffRecordId'] ?? '';
    next['employeeName'] = answers['employeeName'] ?? '';
    next['employeeId'] = answers['employeeId'] ?? '';
    next['email'] = answers['email'] ?? '';
    next['mobile'] = answers['mobile'] ?? '';
    next['month'] = answers['month'] ?? next['month'];
    next['year'] = answers['year'] ?? next['year'];
    next['basicPay'] = answers['basicPay'] ?? '';
    next['allowances'] = answers['allowances'] ?? '';
    next['deductions'] = answers['deductions'] ?? '';
    next['grossPay'] = answers['grossPay'] ?? 0;
    next['netPay'] = answers['netPay'] ?? 0;
    this.answers = next;
    this.selectedStaff = next['employeeName']
      ? ({
          id: String(next['staffRecordId'] || ''),
          fullName: String(next['employeeName'] || ''),
          employeeNo: String(next['employeeId'] || ''),
          email: String(next['email'] || ''),
          mobile: String(next['mobile'] || ''),
        } as StaffLookupRow)
      : null;
    this.recalculatePay();
    this.fieldErrors = {};
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/payroll/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.formKey = boot.formKey;
      this.workflowKey = boot.workflowKey;
      this.fields = this.extractFields(boot.form);
      this.resetAnswers();
      this.loading = false;
      this.loadRecords();
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
        this.error = ModuleBootstrapService.errorMessage(err, 'Payroll bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  onStaffSelected(row: StaffLookupRow): void {
    this.selectedStaff = row;
    this.answers['staffRecordId'] = row.id || '';
    this.answers['employeeName'] = row.fullName || row.name || '';
    this.answers['employeeId'] = row.employeeNo || row.id || '';
    this.answers['email'] = row.email || '';
    this.answers['mobile'] = row.mobile || '';
    this.clearFieldError('employeeName');
    this.clearFieldError('employeeId');
    this.error = '';
  }

  onStaffCleared(): void {
    this.selectedStaff = null;
    this.answers['staffRecordId'] = '';
    this.answers['employeeName'] = '';
    this.answers['employeeId'] = '';
    this.answers['email'] = '';
    this.answers['mobile'] = '';
  }

  recalculatePay(): void {
    const basic = this.nonNegativeNumber(this.answers['basicPay']);
    const allowances = this.nonNegativeNumber(this.answers['allowances']);
    const deductions = this.nonNegativeNumber(this.answers['deductions']);
    const gross = basic + allowances;
    this.answers['grossPay'] = gross;
    this.answers['netPay'] = gross - deductions;
    this.clearFieldError('basicPay');
    this.clearFieldError('allowances');
    this.clearFieldError('deductions');
    this.clearFieldError('netPay');
  }

  loadRecords(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/payroll/records', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const name = String(row.answers?.employeeName ?? '').toLowerCase();
              const mobile = String(row.answers?.mobile ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const id = String(row.id ?? '').toLowerCase();
              return (
                name.includes(q) || mobile.includes(q) || status.includes(q) || id.includes(q)
              );
            });
          }
          if (this.listStatus) {
            const st = this.listStatus.toUpperCase();
            items = items.filter((row) => String(row.status || '').toUpperCase() === st);
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'employeeName') return row.answers?.employeeName;
            if (key === 'updatedAt') return row.updatedAt || row.createdAt;
            return row?.[key];
          });
          this.page = { ...p, items };
          this.records = items;
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to load records';
        },
      });
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .to;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      !!this.listStatus ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.listStatus = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.loadRecords();
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
    this.loadRecords();
  }

  isSortableColumn(key: string): boolean {
    return this.sortOptions.some((option) => option.key === key);
  }

  sortByColumn(key: string): void {
    if (!this.isSortableColumn(key)) {
      return;
    }
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.pageIndex = 0;
    this.loadRecords();
  }

  sortIndicator(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  submit(): void {
    if (this.submitLocked || this.submitting) return;
    this.error = '';
    this.statusMsg = '';
    this.fieldErrors = {};
    if (!this.validateForm()) {
      this.error = 'Please fix the highlighted fields before submitting.';
      return;
    }

    this.submitLocked = true;
    this.submitting = true;
    const editing = !!this.editingId;
    this.statusMsg = editing ? 'Updating payroll run… please wait.' : 'Saving payroll run… please wait.';

    const payload: Record<string, unknown> = {
      formKey: this.formKey,
      workflowKey: this.workflowKey,
      answers: this.normalizeAnswers(),
      clientRequestId: crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
    };
    if (!editing) {
      payload['runType'] = this.runType;
      if (this.parentRecordId) {
        payload['parentRecordId'] = this.parentRecordId;
      }
    }

    const req$ = editing
      ? this.api.put<any>(`/api/payroll/records/${this.editingId}`, payload)
      : this.api.post<any>('/api/payroll/records', payload);

    req$
      .pipe(
        timeout(15000),
        catchError((err) => {
          if (err instanceof TimeoutError || err?.name === 'TimeoutError') {
            return throwError(
              () =>
                ({
                  error: {
                    message:
                      'Payroll service is not responding. Please restart payroll-service and try again.',
                  },
                }) as any,
            );
          }
          return throwError(() => err);
        }),
      )
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = editing
            ? `Payroll run updated${row?.id ? ` (${row.id})` : ''}.`
            : `Payroll run saved${row?.id ? ` (${row.id})` : ''}.`;
          this.editingId = null;
          this.parentRecordId = null;
          this.runType = 'REGULAR';
          this.resetAnswers();
          this.pageIndex = 0;
          this.loadRecords();
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
          this.error = err?.error?.message ?? err?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: row.id },
    });
  }

  act(
    action:
      | 'APPROVE'
      | 'REJECT'
      | 'REQUEST_INFO'
      | 'CANCEL'
      | 'REVERSE'
      | 'MARK_PAID',
  ): void {
    if (!this.selectedId || this.submitting || this.submitLocked) return;
    this.submitting = true;
    this.submitLocked = true;
    const labels: Record<string, string> = {
      APPROVE: 'Approving',
      REJECT: 'Rejecting',
      REQUEST_INFO: 'Requesting info',
      CANCEL: 'Cancelling draft',
      REVERSE: 'Reversing',
      MARK_PAID: 'Marking paid',
    };
    this.statusMsg = `${labels[action] || action}…`;
    this.error = '';
    this.api
      .post<any>(`/api/payroll/records/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = `Action ${action} completed.`;
          this.selected = row;
          this.actionComment = '';
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  cancelDraft(row: any, event?: Event): void {
    event?.stopPropagation();
    if (!this.canCancel(row)) return;
    if (!confirm('Cancel this draft payroll run? It will be voided and kept for audit.')) return;
    this.runLifecycleAction(row.id, 'CANCEL', 'Draft cancelled.');
  }

  reverseRun(row: any, event?: Event): void {
    event?.stopPropagation();
    if (!this.canReverse(row)) return;
    if (
      !confirm(
        'Reverse this approved unpaid run? History is kept. You can then create a correction.',
      )
    ) {
      return;
    }
    this.runLifecycleAction(row.id, 'REVERSE', 'Run reversed.', () => {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { id: row.id },
      });
    });
  }

  /** Reverse approved unpaid run, then open a prefilled correction form. */
  correctRun(row: any, event?: Event): void {
    event?.stopPropagation();
    if (this.canCorrect(row)) {
      this.openCorrection(row, event);
      return;
    }
    if (!this.canReverse(row)) return;
    if (
      !confirm(
        'Reverse this approved unpaid run and open a correction? Original stay in history.',
      )
    ) {
      return;
    }
    this.runLifecycleAction(row.id, 'REVERSE', 'Run reversed — open correction.', () => {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { new: '1', parentId: row.id, runType: 'CORRECTION' },
      });
    });
  }

  markPaid(row: any, event?: Event): void {
    event?.stopPropagation();
    if (!this.canMarkPaid(row)) return;
    if (!confirm('Mark this approved payroll as paid out? Further mistakes need an adjustment.')) {
      return;
    }
    this.runLifecycleAction(row.id, 'MARK_PAID', 'Marked as paid.');
  }

  private runLifecycleAction(
    id: string,
    action: 'CANCEL' | 'REVERSE' | 'MARK_PAID',
    okMsg: string,
    onOk?: () => void,
  ): void {
    if (this.submitting || this.submitLocked) return;
    this.submitting = true;
    this.submitLocked = true;
    this.error = '';
    this.statusMsg = `${action}…`;
    this.api.post<any>(`/api/payroll/records/${id}/actions`, { action }).subscribe({
      next: (row) => {
        this.submitting = false;
        this.submitLocked = false;
        this.statusMsg = okMsg;
        if (this.selectedId === id) {
          this.selected = row;
        }
        this.loadRecords();
        onOk?.();
      },
      error: (err) => {
        this.submitting = false;
        this.submitLocked = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? `${action} failed`;
      },
    });
  }

  canEdit(row: any): boolean {
    if (row?.canEdit === true) return true;
    const s = String(row?.status || '').toUpperCase();
    return s === 'IN_PROGRESS' || s === 'INFO_REQUESTED';
  }

  canCancel(row: any): boolean {
    if (row?.canCancel === true) return true;
    return this.canEdit(row);
  }

  canReverse(row: any): boolean {
    if (row?.canReverse === true) return true;
    return (
      String(row?.status || '').toUpperCase() === 'APPROVED' &&
      String(row?.payoutStatus || 'UNPAID').toUpperCase() === 'UNPAID'
    );
  }

  canCorrect(row: any): boolean {
    if (row?.canCorrect === true) return true;
    return String(row?.status || '').toUpperCase() === 'REVERSED';
  }

  canMarkPaid(row: any): boolean {
    if (row?.canMarkPaid === true) return true;
    return this.canReverse(row);
  }

  canAdjust(row: any): boolean {
    if (row?.canAdjust === true) return true;
    return (
      String(row?.status || '').toUpperCase() === 'APPROVED' &&
      String(row?.payoutStatus || '').toUpperCase() === 'PAID'
    );
  }

  canWorkflowAct(row: any): boolean {
    if (row?.canWorkflowAct === true) return true;
    const s = String(row?.status || '').toUpperCase();
    return s === 'IN_PROGRESS' || s === 'INFO_REQUESTED';
  }

  formTitle(): string {
    if (this.editingId) return 'Edit Payroll Run';
    if (this.runType === 'CORRECTION') return 'Correction Payroll Run';
    if (this.runType === 'ADJUSTMENT') return 'Adjustment Payroll Run';
    return 'New Payroll Run';
  }

  formSubtitle(): string {
    if (this.editingId) return 'Update draft pay details before approval.';
    if (this.runType === 'CORRECTION') {
      return 'Correct amounts after reversing the original approved unpaid run. Linked for audit.';
    }
    if (this.runType === 'ADJUSTMENT') {
      return 'Post-payout adjustment for a paid run. Original paid slip stays unchanged.';
    }
    return 'Enter employee pay details and submit for approval.';
  }

  answer(row: any, key: string): string {
    const v = row?.answers?.[key];
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    if (v == null || String(v).trim() === '') return '—';
    return String(v);
  }

  employeeName(row: any): string {
    const name = this.answer(row, 'employeeName');
    return name === '—' ? String(row?.id || '—') : name;
  }

  formatMoney(raw: unknown): string {
    if (raw == null || String(raw).trim() === '') return '—';
    const n = Number(raw);
    if (Number.isNaN(n)) return String(raw);
    return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'APPROVED') return 'badge badge-ok';
    if (s === 'REJECTED' || s === 'VOIDED' || s === 'REVERSED') return 'badge badge-bad';
    if (s === 'IN_PROGRESS' || s === 'INFO_REQUESTED') return 'badge badge-progress';
    return 'badge';
  }

  payoutLabel(row: any): string {
    const s = String(row?.status || '').toUpperCase();
    if (s !== 'APPROVED') return '';
    return String(row?.payoutStatus || 'UNPAID').toUpperCase() === 'PAID' ? 'PAID' : 'UNPAID';
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

  fieldInvalid(key: string): boolean {
    return !!this.fieldErrors[key];
  }

  clearFieldError(key: string): void {
    if (this.fieldErrors[key]) {
      const next = { ...this.fieldErrors };
      delete next[key];
      this.fieldErrors = next;
    }
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'PAYROLL_APPROVED') {
        return intents[i].delivery ?? [];
      }
    }
    return [];
  }

  private validateForm(): boolean {
    const errors: Record<string, string> = {};
    if (
      !String(this.answers['staffRecordId'] || '').trim() &&
      !String(this.answers['employeeId'] || '').trim()
    ) {
      errors['employeeName'] = 'Select an employee from the staff list';
    }
    // Linked correction/adjustment: keep parent staff id when lookup id was never stored.
    if (
      !String(this.answers['staffRecordId'] || '').trim() &&
      String(this.answers['employeeId'] || '').trim()
    ) {
      this.answers['staffRecordId'] = String(this.answers['employeeId']);
    }
    if (!String(this.answers['year'] || '').trim()) {
      errors['year'] = 'Payroll year is required';
    }
    if (
      this.nonNegativeNumber(this.answers['deductions']) >
      this.nonNegativeNumber(this.answers['grossPay'])
    ) {
      errors['deductions'] = 'Deductions cannot be greater than gross pay';
    }
    for (const f of this.fields) {
      const raw = this.answers[f.key];
      if (f.type === 'CHECKBOX') {
        if (f.mandatory && !raw) errors[f.key] = `${f.label} is required`;
        continue;
      }
      const value = raw == null ? '' : String(raw).trim();
      if (f.mandatory && !value && value !== '0') {
        errors[f.key] = `${f.label} is required`;
        continue;
      }
      if (!value && value !== '0') continue;
      if (f.type === 'NUMBER' && Number.isNaN(Number(value))) {
        errors[f.key] = `${f.label} must be a number`;
      }
      if (
        ['basicPay', 'allowances', 'deductions'].includes(f.key) &&
        Number(value) < 0
      ) {
        errors[f.key] = `${f.label} cannot be negative`;
      }
      if (f.key === 'mobile') {
        const digits = value.replace(/\D/g, '');
        if (digits.length < 10) errors[f.key] = 'Enter a valid 10-digit mobile number';
      }
    }
    this.fieldErrors = errors;
    return Object.keys(errors).length === 0;
  }

  private resetAnswers(): void {
    const now = new Date();
    const next: Record<string, unknown> = {};
    for (const f of this.fields) {
      next[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? '' : '';
    }
    next['staffRecordId'] = '';
    next['month'] = this.monthOptions[now.getMonth()];
    next['year'] = now.getFullYear();
    next['grossPay'] = 0;
    next['netPay'] = 0;
    this.answers = next;
    this.selectedStaff = null;
    this.fieldErrors = {};
  }

  private normalizeAnswers(): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.fields) {
      let v = this.answers[f.key];
      if (f.type === 'NUMBER' && v !== '' && v != null) v = Number(v);
      if (f.type === 'CHECKBOX') v = !!v;
      if (typeof v === 'string') v = v.trim();
      out[f.key] = v;
    }
    out['staffRecordId'] = String(this.answers['staffRecordId'] || '').trim();
    out['year'] = Number(this.answers['year']);
    out['grossPay'] = this.nonNegativeNumber(this.answers['grossPay']);
    out['netPay'] = Number(this.answers['netPay']) || 0;
    return out;
  }

  private nonNegativeNumber(value: unknown): number {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : 0;
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
