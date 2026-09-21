import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import {
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';

type HostelView = 'beds' | 'allocate' | 'occupancies' | 'workflow';

@Component({
  selector: 'sf-hostel',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './hostel.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './hostel.component.scss',
  ],
})
export class HostelComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  view: HostelView = 'beds';
  formKey = 'hostel_allocation';
  workflowKey = 'hostel';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  records: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  actionComment = '';
  submitting = false;

  beds: any[] = [];
  occupancies: any[] = [];
  showBedForm = false;
  bedDraft = { blockKey: 'main_block', roomNo: '', bedNo: 1 };
  allocDraft = { bedId: '' };
  selectedStudent: StudentLookupRow | null = null;
  lookupNonce = 0;

  // Workflow form assisted selection.
  workflowStudent: StudentLookupRow | null = null;
  workflowLookupNonce = 0;
  workflowBedId = '';

  // Beds table (client-side).
  bedQ = '';
  bedStatusFilter = '';
  bedSortBy = 'roomNo';
  bedSortDir: 'ASC' | 'DESC' = 'ASC';
  bedPageSize = 25;
  bedPageIndex = 0;

  // Occupancies table (client-side).
  occQ = '';
  occSortBy = 'studentName';
  occSortDir: 'ASC' | 'DESC' = 'ASC';
  occPageSize = 25;
  occPageIndex = 0;

  // Workflow inbox toolbar.
  listQ = '';
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
  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/hostel/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.formKey = boot.formKey;
      this.workflowKey = boot.workflowKey;
      this.fields = this.extractFields(boot.form);
      this.resetWorkflowAnswers();
      this.loading = false;
      this.loadRecords();
      this.loadBeds();
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
        this.error = ModuleBootstrapService.errorMessage(err, 'Hostel bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  selectView(v: HostelView): void {
    this.view = v;
    this.statusMsg = '';
    this.error = '';
  }

  loadBeds(): void {
    this.api.get<any[]>('/api/hostel/beds').subscribe({
      next: (list) => (this.beds = list ?? []),
      error: () => (this.beds = []),
    });
    this.api.get<any[]>('/api/hostel/beds/occupancies').subscribe({
      next: (list) => (this.occupancies = list ?? []),
      error: () => (this.occupancies = []),
    });
  }

  // ---------- Stats ----------

  get vacantCount(): number {
    return this.beds.filter((b) => b.status === 'VACANT').length;
  }

  get occupiedCount(): number {
    return this.beds.filter((b) => b.status === 'OCCUPIED').length;
  }

  get occupancyPercent(): number {
    if (!this.beds.length) return 0;
    return Math.round((this.occupiedCount / this.beds.length) * 100);
  }

  openBedSummary(status: '' | 'VACANT' | 'OCCUPIED'): void {
    this.view = 'beds';
    this.bedStatusFilter = status;
    this.bedPageIndex = 0;
    this.statusMsg = '';
    this.error = '';
  }

  openResidents(): void {
    this.view = 'occupancies';
    this.occPageIndex = 0;
    this.statusMsg = '';
    this.error = '';
  }

  // ---------- Beds table ----------

  bedLabel(b: any): string {
    return `${b.blockKey} · Room ${b.roomNo} · Bed ${b.bedNo}`;
  }

  occupantOf(bedId: string): any | null {
    return this.occupancies.find((o) => o.bedId === bedId && o.status === 'ACTIVE') ?? null;
  }

  private bedMatches(b: any, q: string): boolean {
    return (
      String(b.roomNo ?? '').toLowerCase().includes(q) ||
      String(b.blockKey ?? '').toLowerCase().includes(q) ||
      String(b.status ?? '').toLowerCase().includes(q) ||
      String(this.occupantOf(b.id)?.studentName ?? '').toLowerCase().includes(q) ||
      String(this.occupantOf(b.id)?.admissionNo ?? '').toLowerCase().includes(q)
    );
  }

  private bedRows(): any[] {
    let rows = this.beds;
    if (this.bedStatusFilter) {
      rows = rows.filter((b) => b.status === this.bedStatusFilter);
    }
    const q = this.bedQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter((b) => this.bedMatches(b, q));
    }
    return sortRows(rows, this.bedSortBy, this.bedSortDir);
  }

  get filteredBeds(): any[] {
    const start = this.bedPageIndex * this.bedPageSize;
    return this.bedRows().slice(start, start + this.bedPageSize);
  }

  get filteredBedsTotal(): number {
    return this.bedRows().length;
  }

  get bedPageLabel(): string {
    const total = this.filteredBedsTotal;
    if (!total) return '0 beds';
    const meta = pageMeta(this.bedPageIndex, this.bedPageSize, total);
    return `${meta.from}–${meta.to} of ${total} beds`;
  }

  sortBedsBy(key: string): void {
    if (this.bedSortBy === key) {
      this.bedSortDir = this.bedSortDir === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.bedSortBy = key;
      this.bedSortDir = 'ASC';
    }
    this.bedPageIndex = 0;
  }

  bedSortIcon(key: string): string {
    if (this.bedSortBy !== key) return '↕';
    return this.bedSortDir === 'ASC' ? '↑' : '↓';
  }

  clearBedFilters(): void {
    this.bedQ = '';
    this.bedStatusFilter = '';
    this.bedSortBy = 'roomNo';
    this.bedSortDir = 'ASC';
    this.bedPageIndex = 0;
  }

  saveBed(): void {
    this.submitting = true;
    this.statusMsg = '';
    this.api.post<any>('/api/hostel/beds', this.bedDraft).subscribe({
      next: () => {
        this.submitting = false;
        this.statusMsg = `Bed added — ${this.bedDraft.blockKey} / Room ${this.bedDraft.roomNo} / Bed ${this.bedDraft.bedNo}`;
        this.bedDraft = { blockKey: this.bedDraft.blockKey, roomNo: '', bedNo: 1 };
        this.loadBeds();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Save bed failed';
      },
    });
  }

  // ---------- Allocate ----------

  get vacantBeds(): any[] {
    return sortRows(
      this.beds.filter((b) => b.status === 'VACANT'),
      'roomNo',
      'ASC',
    );
  }

  get selectedStudentOccupancy(): any | null {
    const adm = this.selectedStudent?.admissionNo;
    if (!adm) return null;
    return (
      this.occupancies.find(
        (o) =>
          o.status === 'ACTIVE' &&
          String(o.admissionNo ?? '').toLowerCase() === String(adm).toLowerCase(),
      ) ?? null
    );
  }

  onStudentSelected(row: StudentLookupRow): void {
    this.selectedStudent = row;
    this.statusMsg = '';
    this.error = '';
  }

  onStudentCleared(): void {
    this.selectedStudent = null;
  }

  allocate(): void {
    if (!this.selectedStudent?.admissionNo || !this.allocDraft.bedId) return;
    this.submitting = true;
    this.statusMsg = '';
    this.error = '';
    const bed = this.beds.find((b) => b.id === this.allocDraft.bedId);
    this.api
      .post<any>('/api/hostel/beds/allocate', {
        bedId: this.allocDraft.bedId,
        admissionNo: this.selectedStudent.admissionNo,
        studentId: this.selectedStudent.id,
        studentName: this.selectedStudent.fullName,
      })
      .subscribe({
        next: () => {
          this.submitting = false;
          this.statusMsg = `Bed allocated — ${this.selectedStudent?.fullName || this.selectedStudent?.admissionNo} → ${bed ? this.bedLabel(bed) : 'bed'}`;
          this.allocDraft = { bedId: '' };
          this.selectedStudent = null;
          this.lookupNonce++;
          this.loadBeds();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Allocate failed';
        },
      });
  }

  release(o: any): void {
    this.submitting = true;
    this.statusMsg = '';
    this.api.post<any>(`/api/hostel/beds/occupancies/${o.id}/release`, {}).subscribe({
      next: () => {
        this.submitting = false;
        this.statusMsg = `Bed released — ${o.studentName || o.admissionNo}`;
        this.loadBeds();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Release failed';
      },
    });
  }

  // ---------- Occupancies table ----------

  occBedLabel(o: any): string {
    if (o.label) return o.label;
    if (o.blockKey) return `${o.blockKey} / Room ${o.roomNo} / Bed ${o.bedNo}`;
    const bed = this.beds.find((b) => b.id === o.bedId);
    return bed ? this.bedLabel(bed) : o.bedId;
  }

  private occRows(): any[] {
    let rows = this.occupancies;
    const q = this.occQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (o) =>
          String(o.admissionNo ?? '').toLowerCase().includes(q) ||
          String(o.studentName ?? '').toLowerCase().includes(q) ||
          this.occBedLabel(o).toLowerCase().includes(q),
      );
    }
    return sortRows(rows, this.occSortBy, this.occSortDir);
  }

  get filteredOccupancies(): any[] {
    const start = this.occPageIndex * this.occPageSize;
    return this.occRows().slice(start, start + this.occPageSize);
  }

  get filteredOccupanciesTotal(): number {
    return this.occRows().length;
  }

  get occPageLabel(): string {
    const total = this.filteredOccupanciesTotal;
    if (!total) return '0 residents';
    const meta = pageMeta(this.occPageIndex, this.occPageSize, total);
    return `${meta.from}–${meta.to} of ${total} residents`;
  }

  sortOccBy(key: string): void {
    if (this.occSortBy === key) {
      this.occSortDir = this.occSortDir === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.occSortBy = key;
      this.occSortDir = 'ASC';
    }
    this.occPageIndex = 0;
  }

  occSortIcon(key: string): string {
    if (this.occSortBy !== key) return '↕';
    return this.occSortDir === 'ASC' ? '↑' : '↓';
  }

  clearOccFilters(): void {
    this.occQ = '';
    this.occSortBy = 'studentName';
    this.occSortDir = 'ASC';
    this.occPageIndex = 0;
  }

  fmtDate(v: string | null | undefined): string {
    if (!v) return '—';
    try {
      return new Date(v).toLocaleDateString(undefined, {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return String(v);
    }
  }

  // ---------- Workflow form assisted selection ----------

  onWorkflowStudentSelected(student: StudentLookupRow): void {
    this.workflowStudent = student;
    this.setAnswer(['studentName', 'fullName'], student.fullName || '');
    this.setAnswer(['admissionNo', 'admissionNumber'], student.admissionNo || '');
    this.setAnswer(['classSection', 'class'], student.classSection || '');
    this.setAnswer(['email', 'studentEmail'], student.email || '');
    this.setAnswer(['mobile', 'phone', 'studentMobile'], student.mobile || '');
    this.ensureDefaultStartDate();
    this.error = '';
  }

  onWorkflowStudentCleared(): void {
    this.workflowStudent = null;
    this.setAnswer(['studentName', 'fullName'], '');
    this.setAnswer(['admissionNo', 'admissionNumber'], '');
    this.setAnswer(['classSection', 'class'], '');
    this.setAnswer(['email', 'studentEmail'], '');
    this.setAnswer(['mobile', 'phone', 'studentMobile'], '');
  }

  onWorkflowBedSelected(bedId: string): void {
    this.workflowBedId = bedId || '';
    this.syncBedAnswersFromSelection();
    this.error = '';
  }

  get canSubmitWorkflow(): boolean {
    return !!this.workflowStudent && !!this.workflowBedId;
  }

  isDateField(field: { key: string; type: string }): boolean {
    const type = String(field.type || '').toUpperCase();
    return type === 'DATE' || type === 'DATE_PICKER' || /date$/i.test(field.key);
  }

  isWorkflowAutoField(key: string): boolean {
    const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
    return [
      'studentname',
      'fullname',
      'admissionno',
      'admissionnumber',
      'class',
      'classsection',
      'email',
      'studentemail',
      'mobile',
      'phone',
      'studentmobile',
      'hostelblock',
      'blockkey',
      'block',
      'roomno',
      'room',
      'bedno',
      'bed',
    ].includes(normalized);
  }

  private setAnswer(keys: string[], value: unknown): void {
    const targets = new Set(keys.map((k) => k.replace(/[^a-z0-9]/gi, '').toLowerCase()));
    for (const field of this.fields) {
      const normalized = field.key.replace(/[^a-z0-9]/gi, '').toLowerCase();
      if (targets.has(normalized)) {
        this.answers[field.key] = value;
      }
    }
  }

  // ---------- Workflow inbox ----------

  sortInboxBy(key: string): void {
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.loadRecords();
  }

  inboxSortIcon(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  recordStatusLabel(status: unknown): string {
    const s = String(status || '').toUpperCase();
    if (s === 'IN_PROGRESS') return 'In progress';
    if (s === 'INFO_REQUESTED') return 'Info requested';
    if (s === 'APPROVED') return 'Approved';
    if (s === 'REJECTED') return 'Rejected';
    if (s === 'COMPLETED') return 'Completed';
    return s ? s.charAt(0) + s.slice(1).toLowerCase() : '—';
  }

  recordStatusClass(status: unknown): string {
    const s = String(status || '').toUpperCase();
    if (s === 'APPROVED' || s === 'COMPLETED') return 'ok';
    if (s === 'REJECTED') return 'bad';
    if (s === 'INFO_REQUESTED') return 'warn';
    return 'progress';
  }

  loadRecords(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/hostel/records', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const name = String(row.answers?.studentName ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const id = String(row.id ?? '').toLowerCase();
              return name.includes(q) || status.includes(q) || id.includes(q);
            });
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
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.loadRecords();
  }

  submit(): void {
    this.error = '';
    this.statusMsg = '';
    if (!this.workflowStudent) {
      this.error = 'Select a student before submitting.';
      return;
    }
    if (!this.workflowBedId) {
      this.error = 'Select a vacant bed before submitting.';
      return;
    }
    this.syncBedAnswersFromSelection();
    this.ensureDefaultStartDate();
    const missing = this.missingMandatoryLabels();
    if (missing.length) {
      this.error = `Please fill required fields: ${missing.join(', ')}.`;
      return;
    }
    this.submitting = true;
    this.api
      .post<any>('/api/hostel/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selectedId = row.id;
          this.selected = row;
          this.statusMsg = `Request submitted for ${row.answers?.studentName || 'student'} — it is now in the approval inbox.`;
          this.workflowStudent = null;
          this.workflowBedId = '';
          this.workflowLookupNonce++;
          this.resetWorkflowAnswers();
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    this.selectedId = row.id;
    this.api.get<any>(`/api/hostel/records/${row.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId) {
      return;
    }
    this.submitting = true;
    this.api
      .post<any>(`/api/hostel/records/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selected = row;
          this.actionComment = '';
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  answerEntries(answers: Record<string, unknown> | null | undefined): Array<{
    label: string;
    value: string;
  }> {
    if (!answers) return [];
    const labelFor = (key: string): string => {
      const field = this.fields.find((f) => f.key === key);
      if (field?.label) return field.label;
      return key
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/[_-]+/g, ' ')
        .replace(/^./, (c) => c.toUpperCase());
    };
    return Object.entries(answers)
      .filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([key, v]) => ({
        label: labelFor(key),
        value: typeof v === 'boolean' ? (v ? 'Yes' : 'No') : String(v),
      }));
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'HOSTEL_APPROVED') {
        return intents[i].delivery ?? [];
      }
    }
    return [];
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

  private resetWorkflowAnswers(): void {
    for (const f of this.fields) {
      if (f.type === 'CHECKBOX') {
        this.answers[f.key] = false;
      } else if (f.type === 'NUMBER' && this.isWorkflowAutoField(f.key)) {
        this.answers[f.key] = '';
      } else if (f.type === 'NUMBER') {
        this.answers[f.key] = 0;
      } else {
        this.answers[f.key] = '';
      }
    }
    this.ensureDefaultStartDate();
  }

  private ensureDefaultStartDate(): void {
    const today = this.todayIsoDate();
    for (const f of this.fields) {
      if (!this.isDateField(f)) continue;
      const current = this.answers[f.key];
      if (current == null || String(current).trim() === '') {
        this.answers[f.key] = today;
      }
    }
  }

  private syncBedAnswersFromSelection(): void {
    if (!this.workflowBedId) {
      this.setAnswer(['hostelBlock', 'blockKey', 'block'], '');
      this.setAnswer(['roomNo', 'room'], '');
      this.setAnswer(['bedNo', 'bed'], '');
      return;
    }
    const bed = this.beds.find((b) => String(b.id) === String(this.workflowBedId));
    this.setAnswer(['hostelBlock', 'blockKey', 'block'], bed?.blockKey || '');
    this.setAnswer(['roomNo', 'room'], bed?.roomNo || '');
    this.setAnswer(['bedNo', 'bed'], bed ? bed.bedNo : '');
  }

  private todayIsoDate(): string {
    const d = new Date();
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  private missingMandatoryLabels(): string[] {
    const missing: string[] = [];
    for (const f of this.fields) {
      if (!f.mandatory) continue;
      const v = this.answers[f.key];
      if (v == null || String(v).trim() === '') {
        missing.push(f.label || f.key);
      }
    }
    return missing;
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
