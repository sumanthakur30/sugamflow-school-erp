import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  selector: 'sf-students',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ListToolbarComponent, ListPagerComponent],
  templateUrl: './students.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './students.component.scss',
  ],
})
export class StudentsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private routeSub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'student_master';
  parentFormKey = 'parent_master';
  guardiansAnswerKey = 'guardians';
  parentFields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  masterFields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  students: any[] = [];
  selected: any = null;
  selectedId: string | null = null;
  guardianDraft: Record<string, unknown> = {};
  savingGuardians = false;

  editOpen = false;
  editAnswers: Record<string, unknown> = {};
  editReason = '';
  savingProfile = false;
  readonly houseOptions = ['Red', 'Blue', 'Green', 'Yellow'];

  deleteOpen = false;
  deleteReason = '';
  restoreReason = '';
  statusDraft = '';
  busyAction = false;

  auditItems: any[] = [];
  auditLoading = false;
  showAudit = false;
  documents: any[] = [];
  documentsLoading = false;

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
    { key: 'fullName', label: 'Student Name' },
    { key: 'admissionNo', label: 'Admission No' },
    { key: 'classApplied', label: 'Class' },
    { key: 'status', label: 'Status' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'ACTIVE', label: 'ACTIVE' },
    { value: 'TRANSFERRED', label: 'TRANSFERRED' },
    { value: 'INACTIVE', label: 'INACTIVE' },
  ];

  /** Preferred student answer keys for structured detail (order matters). */
  private readonly preferredAnswerKeys = [
    'fullName',
    'admissionNo',
    'classApplied',
    'house',
    'age',
    'gender',
    'dateOfBirth',
    'mobile',
    'email',
    'fatherName',
    'parentName',
    'address',
    'bloodGroup',
  ];

  /**
   * Extra profile fields shown in Edit even when the configured student form
   * only has a subset (e.g. form may omit fatherName while the profile displays it).
   */
  private readonly editableExtraKeys = [
    'gender',
    'fatherName',
    'parentName',
    'rollNo',
    'house',
    'category',
    'classSection',
    'dateOfBirth',
    'address',
    'bloodGroup',
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
    if (this.busyAction || this.savingProfile || this.savingGuardians) return;
    if (this.deleteOpen) {
      this.deleteOpen = false;
      return;
    }
    if (this.editOpen) {
      this.cancelEdit();
      return;
    }
    if (this.selected) {
      this.closeDetail();
    }
  }

  private syncFromRoute(params: import('@angular/router').ParamMap): void {
    const { mode, id } = parseListViewParams(params);
    if (mode === 'detail' && id) {
      if (this.selectedId !== id || !this.selected) {
        this.loadDetail(id);
      }
      return;
    }
    this.selected = null;
    this.selectedId = null;
    this.editOpen = false;
    this.deleteOpen = false;
    this.showAudit = false;
    this.resetGuardianDraft();
    this.statusMsg = '';
  }

  private loadDetail(id: string): void {
    this.selectedId = id;
    this.error = '';
    this.statusMsg = '';
    this.editOpen = false;
    this.deleteOpen = false;
    this.api.get<any>(`/api/student/students/${id}`).subscribe({
      next: (full) => {
        this.selected = full;
        this.selectedId = full?.id ?? id;
        this.statusDraft = String(full?.status || 'ACTIVE');
        this.resetGuardianDraft();
        this.loadDocuments();
        if (this.showAudit) {
          this.loadAudit();
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load student'),
    });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/student/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.parentFormKey = boot.parentFormKey ?? 'parent_master';
        this.guardiansAnswerKey = boot.guardiansAnswerKey ?? 'guardians';
        this.parentFields = this.extractFields(boot.parentForm);
        this.masterFields = this.extractFields(boot.form).filter(
          (f) => f.key !== this.guardiansAnswerKey && f.key !== 'guardians',
        );
        this.resetGuardianDraft();
        this.loading = false;
        this.loadStudents();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Student bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadStudents(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/student/students', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const name = String(row.answers?.fullName ?? '').toLowerCase();
              const admissionNo = String(row.admissionNo ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const klass = String(row.answers?.classApplied ?? '').toLowerCase();
              return (
                name.includes(q) ||
                admissionNo.includes(q) ||
                status.includes(q) ||
                klass.includes(q)
              );
            });
          }
          if (this.listStatus) {
            const st = this.listStatus.toUpperCase();
            items = items.filter((row) => String(row.status || '').toUpperCase() === st);
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'fullName') return row.answers?.fullName;
            if (key === 'classApplied') return row.answers?.classApplied;
            if (key === 'updatedAt') return row.updatedAt || row.createdAt;
            return row?.[key];
          });
          this.page = { ...p, items };
          this.students = items;
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to load students';
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
    this.loadStudents();
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
    this.loadStudents();
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
    this.loadStudents();
  }

  sortIndicator(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  select(row: any): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: row.id },
    });
  }

  closeDetail(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  guardians(): any[] {
    const fromDto = this.selected?.guardians;
    if (Array.isArray(fromDto)) {
      return fromDto;
    }
    const fromAnswers = this.selected?.answers?.[this.guardiansAnswerKey];
    return Array.isArray(fromAnswers) ? fromAnswers : [];
  }

  /** Structured student fields for the detail page (skips guardian blob). */
  studentFields(): Array<{ key: string; label: string; value: string }> {
    const answers = this.selected?.answers ?? {};
    const skip = new Set([this.guardiansAnswerKey, 'guardians']);
    const out: Array<{ key: string; label: string; value: string }> = [];
    const seen = new Set<string>();

    const push = (key: string) => {
      if (seen.has(key) || skip.has(key)) return;
      const raw = answers[key];
      if (raw == null || typeof raw === 'object') return;
      const value =
        key === 'classApplied'
          ? this.formatGrade(raw)
          : key === 'classSection'
            ? this.formatClass(raw)
            : this.formatAnswerValue(raw);
      if (value === '—') return;
      seen.add(key);
      out.push({ key, label: this.prettyLabel(key), value });
    };

    for (const key of this.preferredAnswerKeys) {
      push(key);
    }
    for (const key of Object.keys(answers)) {
      push(key);
    }

    // Surface top-level admissionNo if not already in answers
    if (!seen.has('admissionNo') && this.selected?.admissionNo) {
      out.unshift({
        key: 'admissionNo',
        label: 'Admission No',
        value: String(this.selected.admissionNo),
      });
    }
    return out;
  }

  addGuardian(): void {
    if (!this.selected?.id || this.savingGuardians) {
      return;
    }
    const draft = this.normalizeGuardian(this.guardianDraft);
    const name = String(draft['fullName'] ?? '').trim();
    if (!name) {
      this.error = 'Guardian full name is required.';
      return;
    }
    this.error = '';
    const next = [...this.guardians(), draft];
    this.saveGuardians(next, true);
  }

  removeGuardian(index: number): void {
    if (!this.selected?.id || this.savingGuardians || this.selected?.deleted) {
      return;
    }
    const next = this.guardians().filter((_: any, i: number) => i !== index);
    this.saveGuardians(next, false);
  }

  /** Fields available in the Edit form (configured form + common profile extras). */
  editFields(): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    const seen = new Set(this.masterFields.map((f) => f.key));
    const extras: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
    for (const key of this.editableExtraKeys) {
      if (seen.has(key)) continue;
      seen.add(key);
      extras.push({
        key,
        label: this.prettyLabel(key),
        type: key === 'hostel' || key === 'transport' || key === 'scholarship' ? 'CHECKBOX' : 'TEXTBOX',
        mandatory: false,
      });
    }
    return [...this.masterFields, ...extras];
  }

  startEdit(): void {
    if (!this.selected || this.selected.deleted) return;
    const draft: Record<string, unknown> = { ...(this.selected.answers || {}) };
    if (this.selected.admissionNo && draft['admissionNo'] == null) {
      draft['admissionNo'] = this.selected.admissionNo;
    }
    for (const f of this.editFields()) {
      if (draft[f.key] === undefined) {
        draft[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? 0 : '';
      }
    }
    this.editAnswers = draft;
    this.editReason = '';
    this.editOpen = true;
    this.error = '';
  }

  cancelEdit(): void {
    this.editOpen = false;
    this.editReason = '';
  }

  saveEdit(): void {
    if (!this.selected?.id || this.savingProfile) return;
    const house = String(this.editAnswers['house'] ?? '').trim();
    if (!house) {
      this.error = 'Select a house before saving the student profile.';
      return;
    }
    if (!this.availableHouseOptions().includes(house)) {
      this.error = 'Select a valid school house.';
      return;
    }
    this.savingProfile = true;
    this.error = '';
    this.statusMsg = 'Saving student profile…';
    const payload: Record<string, unknown> = {
      answers: this.editAnswers,
      reason: this.editReason?.trim() || 'Student profile correction',
    };
    this.api.put<any>(`/api/student/students/${this.selected.id}`, payload).subscribe({
      next: (full) => {
        this.savingProfile = false;
        this.editOpen = false;
        this.selected = full;
        this.statusDraft = String(full?.status || 'ACTIVE');
        this.statusMsg = 'Student profile updated.';
        this.loadStudents();
        if (this.showAudit) this.loadAudit();
      },
      error: (err) => {
        this.savingProfile = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? 'Failed to update student';
      },
    });
  }

  availableHouseOptions(): string[] {
    const current = String(this.editAnswers['house'] ?? this.selected?.answers?.house ?? '').trim();
    return current && !this.houseOptions.includes(current)
      ? [current, ...this.houseOptions]
      : this.houseOptions;
  }

  openDelete(): void {
    if (!this.selected || this.selected.deleted) return;
    this.deleteReason = '';
    this.deleteOpen = true;
  }

  confirmSoftDelete(): void {
    if (!this.selected?.id || this.busyAction) return;
    const reason = this.deleteReason.trim();
    if (!reason) {
      this.error = 'Deletion reason is required.';
      return;
    }
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/soft-delete`, { reason })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.deleteOpen = false;
          this.selected = full;
          this.statusMsg = 'Student soft-deleted.';
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Soft delete failed';
        },
      });
  }

  restoreStudent(): void {
    if (!this.selected?.id || !this.selected.deleted || this.busyAction) return;
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/restore`, {
        reason: this.restoreReason?.trim() || 'Student restored',
        status: 'ACTIVE',
      })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.selected = full;
          this.statusDraft = String(full?.status || 'ACTIVE');
          this.statusMsg = 'Student restored.';
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Restore failed';
        },
      });
  }

  applyStatus(): void {
    if (!this.selected?.id || this.selected.deleted || this.busyAction) return;
    const status = String(this.statusDraft || '').trim().toUpperCase();
    if (!status || status === String(this.selected.status || '').toUpperCase()) return;
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/status`, {
        status,
        reason: `Status set to ${status}`,
      })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.selected = full;
          this.statusDraft = String(full?.status || status);
          this.statusMsg = `Status updated to ${status}.`;
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Status change failed';
        },
      });
  }

  toggleAudit(): void {
    this.showAudit = !this.showAudit;
    if (this.showAudit) {
      this.loadAudit();
    }
  }

  loadAudit(): void {
    if (!this.selected?.id) return;
    this.auditLoading = true;
    this.api.getPage<any>(`/api/student/students/${this.selected.id}/audit`, 0, 50).subscribe({
      next: (p) => {
        this.auditItems = p.items || [];
        this.auditLoading = false;
      },
      error: () => {
        this.auditLoading = false;
        this.auditItems = [];
      },
    });
  }

  historyChanges(h: any): Array<{ field: string; from: unknown; to: unknown }> {
    return Array.isArray(h?.changes) ? h.changes : [];
  }

  studentName(row: any): string {
    const name = this.answer(row, 'fullName');
    return name === '—' ? String(row?.admissionNo || row?.id || '—') : name;
  }

  answer(row: any, key: string): string {
    const v = row?.answers?.[key] ?? (key === 'admissionNo' ? row?.admissionNo : undefined);
    if (key === 'classApplied') {
      return this.formatClass(v);
    }
    return this.formatAnswerValue(v);
  }

  displayClass(row: any): string {
    return this.formatClass(row?.answers?.classApplied ?? row?.classSection);
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'ACTIVE' || s === 'ENROLLED' || s === 'APPROVED' || s === 'PROMOTED')
      return 'badge badge-ok';
    if (
      s === 'INACTIVE' ||
      s === 'WITHDRAWN' ||
      s === 'REJECTED' ||
      s === 'DELETED' ||
      s === 'EXPELLED'
    )
      return 'badge badge-bad';
    if (s === 'PENDING' || s === 'IN_PROGRESS' || s === 'SUSPENDED' || s === 'TRANSFERRED')
      return 'badge badge-progress';
    return 'badge';
  }

  documentLabel(type: unknown): string {
    const t = String(type || '').toUpperCase();
    if (t === 'ID_CARD') return 'ID card';
    if (t === 'BONAFIDE') return 'Bonafide certificate';
    if (t === 'CHARACTER_CERTIFICATE') return 'Character certificate';
    return String(type || 'Document');
  }

  loadDocuments(): void {
    if (!this.selected?.id) {
      this.documents = [];
      return;
    }
    this.documentsLoading = true;
    this.api.get<any[]>(`/api/student/students/${this.selected.id}/documents`).subscribe({
      next: (rows) => {
        this.documents = rows ?? [];
        this.documentsLoading = false;
      },
      error: () => {
        this.documents = [];
        this.documentsLoading = false;
      },
    });
  }

  issueDocument(type: 'ID_CARD' | 'BONAFIDE' | 'CHARACTER_CERTIFICATE'): void {
    if (!this.selected?.id || this.busyAction || this.selected.deleted) {
      return;
    }
    this.busyAction = true;
    this.error = '';
    this.statusMsg = `Issuing ${this.documentLabel(type)}…`;
    this.api.post<any>(`/api/student/students/${this.selected.id}/documents`, { type }).subscribe({
      next: (doc) => {
        this.busyAction = false;
        this.statusMsg = `${this.documentLabel(type)} issued (${doc.referenceNo}).`;
        this.loadDocuments();
        if (doc?.id) {
          this.downloadDocument(doc);
        }
      },
      error: (err) => {
        this.busyAction = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? 'Document issue failed';
      },
    });
  }

  downloadDocument(doc: any): void {
    if (!doc?.id) return;
    this.api.getBlob(`/api/student/documents/${doc.id}/pdf`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.fileName || `student-document-${doc.id}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'PDF download failed'),
    });
  }

  revokeDocument(doc: any): void {
    if (!doc?.id || this.busyAction) return;
    this.busyAction = true;
    this.api
      .post<any>(`/api/student/documents/${doc.id}/revoke`, { reason: 'Revoked from student profile' })
      .subscribe({
        next: () => {
          this.busyAction = false;
          this.statusMsg = `Document ${doc.referenceNo} revoked.`;
          this.loadDocuments();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Revoke failed';
        },
      });
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

  private saveGuardians(guardians: any[], clearDraft: boolean): void {
    this.savingGuardians = true;
    this.error = '';
    this.statusMsg = 'Saving guardians…';
    this.api
      .put<any>(`/api/student/students/${this.selected.id}/guardians`, { guardians })
      .subscribe({
        next: (full) => {
          this.savingGuardians = false;
          this.selected = full;
          this.statusMsg = clearDraft ? 'Guardian added.' : 'Guardian removed.';
          if (clearDraft) {
            this.resetGuardianDraft();
          }
          this.loadStudents();
        },
        error: (err) => {
          this.savingGuardians = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Failed to save guardians';
        },
      });
  }

  private resetGuardianDraft(): void {
    const draft: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      draft[f.key] = f.type === 'CHECKBOX' ? false : '';
    }
    if (draft['isPrimary'] === false && this.guardians().length === 0) {
      draft['isPrimary'] = true;
    }
    this.guardianDraft = draft;
  }

  private normalizeGuardian(raw: Record<string, unknown>): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      let v = raw[f.key];
      if (f.type === 'CHECKBOX') {
        v = !!v;
      }
      out[f.key] = v;
    }
    return out;
  }

  private formatAnswerValue(v: unknown): string {
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    if (v == null || String(v).trim() === '') return '—';
    return String(v);
  }

  /** Hide smoke-test class labels (ReportCard-…, Gradebook-…, etc.). */
  private formatClass(raw: unknown): string {
    const s = raw == null ? '' : String(raw).trim();
    if (!s) return '—';
    if (/^(ReportCard|Gradebook|Alert|Roster)-\d{10,}$/i.test(s)) {
      return '—';
    }
    return s;
  }

  /**
   * Grade level without the section suffix, e.g. "Grade 8-A" -> "Grade 8".
   * Used for the "Class" field so it does not duplicate "Class section".
   */
  private formatGrade(raw: unknown): string {
    const s = this.formatClass(raw);
    if (s === '—') return s;
    const match = s.match(/^(.*?)[\s\-/]+([A-Za-z]|\d{1,2})$/);
    return match ? match[1].trim() : s;
  }

  private prettyLabel(key: string): string {
    const map: Record<string, string> = {
      fullName: 'Full name',
      admissionNo: 'Admission No',
      classApplied: 'Class',
      classSection: 'Class section',
      dateOfBirth: 'Date of birth',
      bloodGroup: 'Blood group',
      fatherName: 'Father name',
      parentName: 'Parent name',
      rollNo: 'Roll no',
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
