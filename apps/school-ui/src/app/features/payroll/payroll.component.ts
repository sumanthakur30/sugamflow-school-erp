import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, TimeoutError, catchError, throwError, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
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
  private routeSub?: Subscription;

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
  /** List-first: form/detail driven by ?new=1 / ?id= */
  formOpen = false;
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
    { value: 'APPROVED', label: 'APPROVED' },
    { value: 'REJECTED', label: 'REJECTED' },
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
    this.reload();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
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
      this.fieldErrors = {};
      this.resetAnswers();
      this.lookupNonce++;
      return;
    }
    this.formOpen = false;
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

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/payroll/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        this.resetAnswers();
        this.loading = false;
        this.loadRecords();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Payroll bootstrap failed';
        this.featureEnabled = false;
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
    this.statusMsg = 'Saving payroll run… please wait.';
    this.api
      .post<any>('/api/payroll/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
        clientRequestId: crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
      })
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
          this.statusMsg = `Payroll run saved${row?.id ? ` (${row.id})` : ''}.`;
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

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId || this.submitting || this.submitLocked) return;
    this.submitting = true;
    this.submitLocked = true;
    this.statusMsg = `${action === 'APPROVE' ? 'Approving' : action === 'REJECT' ? 'Rejecting' : 'Requesting info'}…`;
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
    if (s === 'REJECTED') return 'badge badge-bad';
    if (s === 'IN_PROGRESS') return 'badge badge-progress';
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
    if (!String(this.answers['staffRecordId'] || '').trim()) {
      errors['employeeName'] = 'Select an employee from the staff list';
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
