import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
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

@Component({
  selector: 'sf-exam',
  standalone: true,
  imports: [CommonModule, FormsModule, ListToolbarComponent, ListPagerComponent],
  templateUrl: './exam.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/inbox-list.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './exam.component.scss',
  ],
})
export class ExamComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private routeSub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'exam_marks';
  workflowKey = 'exam';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
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
    { key: 'studentName', label: 'Student Name' },
    { key: 'status', label: 'Status' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'IN_PROGRESS', label: 'IN_PROGRESS' },
    { value: 'APPROVED', label: 'APPROVED' },
    { value: 'REJECTED', label: 'REJECTED' },
  ];

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
      this.resetAnswers();
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
    this.statusMsg = '';
    this.api.get<any>(`/api/exam/records/${id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/exam/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        this.resetAnswers();
        this.loading = false;
        this.loadRecords();
        this.syncFromRoute(this.route.snapshot.queryParamMap);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Exam bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadRecords(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/exam/records', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const name = String(row.answers?.studentName ?? '').toLowerCase();
              const subject = String(row.answers?.subject ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const id = String(row.id ?? '').toLowerCase();
              return (
                name.includes(q) ||
                subject.includes(q) ||
                status.includes(q) ||
                id.includes(q)
              );
            });
          }
          if (this.listStatus) {
            const st = this.listStatus.toUpperCase();
            items = items.filter((row) => String(row.status || '').toUpperCase() === st);
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'studentName') return row.answers?.studentName;
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
    this.submitLocked = true;
    this.submitting = true;
    this.error = '';
    this.statusMsg = 'Saving marks…';
    this.api
      .post<any>('/api/exam/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = 'Marks entry submitted.';
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
      .post<any>(`/api/exam/records/${this.selectedId}/actions`, {
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

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'EXAM_APPROVED') {
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
    const name = this.answer(row, 'studentName');
    return name === '—' ? String(row?.id || '—') : name;
  }

  marksLabel(row: any): string {
    const a = row?.answers ?? {};
    const marks = a.marks ?? a.score ?? a.marksObtained;
    if (marks == null || String(marks).trim() === '') return '—';
    const max = a.maxMarks;
    if (max != null && String(max).trim() !== '') {
      return `${marks}/${max}`;
    }
    return String(marks);
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

  detailFields(): Array<{ key: string; label: string; value: string }> {
    const answers = this.selected?.answers ?? {};
    return Object.keys(answers)
      .filter((k) => answers[k] == null || typeof answers[k] !== 'object')
      .map((key) => ({
        key,
        label: this.prettyLabel(key),
        value: this.answer(this.selected, key),
      }));
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
      subject: 'Subject',
      marks: 'Marks',
      score: 'Score',
      marksObtained: 'Marks obtained',
      maxMarks: 'Max marks',
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
