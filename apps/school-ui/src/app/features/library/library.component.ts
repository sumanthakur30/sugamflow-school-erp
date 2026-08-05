import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageResult } from '../../core/api.service';
import {
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';

type LibraryView = 'catalog' | 'issue' | 'issued' | 'workflow';

@Component({
  selector: 'sf-library',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './library.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './library.component.scss',
  ],
})
export class LibraryComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  view: LibraryView = 'catalog';
  formKey = 'library_issue';
  workflowKey = 'library';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  records: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  actionComment = '';
  submitting = false;

  books: any[] = [];
  issues: any[] = [];

  // Catalog
  showBookForm = false;
  bookDraft = { title: '', author: '', isbn: '', copiesTotal: 1 };
  bookQ = '';
  bookSortBy = 'title';
  bookSortDir: 'ASC' | 'DESC' = 'ASC';
  bookAvailability: '' | 'AVAILABLE' | 'OUT' = '';
  bookPageSize = 25;
  bookPageIndex = 0;

  // Issue / return
  issueDraft = {
    bookId: '',
    studentId: '',
    admissionNo: '',
    studentName: '',
    classSection: '',
    loanDays: 14,
  };
  selectedStudent: StudentLookupRow | null = null;
  lookupNonce = 0;

  // Workflow form assisted selection
  workflowStudent: StudentLookupRow | null = null;
  workflowLookupNonce = 0;
  workflowBookId = '';

  // Issued books list
  issueQ = '';
  issueClass = '';
  overdueOnly = false;
  issueSortBy = 'dueAt';
  issueSortDir: 'ASC' | 'DESC' = 'ASC';
  issuePageSize = 25;
  issuePageIndex = 0;

  // Workflow inbox
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
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/library/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        for (const f of this.fields) {
          if (this.answers[f.key] === undefined) {
            this.answers[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? 0 : '';
          }
        }
        this.loading = false;
        this.loadRecords();
        this.loadCirculation();
      },
      error: (err) => {
        this.loading = false;
        // Keep featureEnabled unchanged on transport errors so we don't imply FEATURE_LIBRARY is off.
        const status = err?.status;
        const detail = err?.error?.message ?? err?.message ?? 'Library bootstrap failed';
        this.error =
          status === 503 || status === 0
            ? `Library service unavailable (${status || 'network'}). ${detail}`
            : detail;
      },
    });
  }

  selectView(view: LibraryView): void {
    this.view = view;
    this.error = '';
    this.statusMsg = '';
  }

  loadCirculation(): void {
    this.api.get<any[]>('/api/library/circulation/books').subscribe({
      next: (list) => (this.books = list ?? []),
      error: () => (this.books = []),
    });
    this.api.get<any[]>('/api/library/circulation/issues').subscribe({
      next: (list) => {
        this.issues = list ?? [];
        this.enrichMissingClassSections();
      },
      error: () => (this.issues = []),
    });
  }

  /** Older issues may lack classSection; fill from Student Master by admission no. */
  private enrichMissingClassSections(): void {
    const missing = this.issues.filter(
      (i) => !String(i.classSection || '').trim() && String(i.admissionNo || '').trim(),
    );
    if (!missing.length) return;

    const admissions = [
      ...new Set(
        missing.map((i) => String(i.admissionNo || '').trim()).filter(Boolean),
      ),
    ];

    // Resolve each missing admission (directory default page may not include every student).
    for (const admissionNo of admissions) {
      this.api
        .getPage<any>('/api/student/directory/students', 0, 20, {
          q: admissionNo,
          status: 'ACTIVE',
          includeDeleted: 'false',
        })
        .subscribe({
          next: (page) => {
            const match = (page.items ?? []).find(
              (s) =>
                String(s.admissionNo || '')
                  .trim()
                  .toLowerCase() === admissionNo.toLowerCase(),
            );
            const cls = String(
              match?.classSection || match?.className || match?.currentClass || '',
            ).trim();
            if (!cls) return;
            this.issues = this.issues.map((i) => {
              if (String(i.classSection || '').trim()) return i;
              if (
                String(i.admissionNo || '')
                  .trim()
                  .toLowerCase() !== admissionNo.toLowerCase()
              ) {
                return i;
              }
              return { ...i, classSection: cls };
            });
          },
          error: () => {
            /* keep blank class if directory is unavailable */
          },
        });
    }
  }

  // ---------- Catalog ----------

  saveBook(): void {
    this.submitting = true;
    this.error = '';
    this.api.post<any>('/api/library/circulation/books', this.bookDraft).subscribe({
      next: () => {
        this.submitting = false;
        this.statusMsg = `Book “${this.bookDraft.title}” added to the catalog.`;
        this.bookDraft = { title: '', author: '', isbn: '', copiesTotal: 1 };
        this.showBookForm = false;
        this.loadCirculation();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Save book failed';
      },
    });
  }

  sortBooksBy(key: string): void {
    const next = nextHeaderSort(this.bookSortBy, this.bookSortDir, key);
    this.bookSortBy = next.sortBy;
    this.bookSortDir = next.sortDir;
  }

  bookSortIcon(key: string): string {
    return headerSortIndicator(this.bookSortBy, this.bookSortDir, key);
  }

  private matchingBooks(): any[] {
    let rows = this.books;
    const q = this.bookQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (b) =>
          String(b.title ?? '').toLowerCase().includes(q) ||
          String(b.author ?? '').toLowerCase().includes(q) ||
          String(b.isbn ?? '').toLowerCase().includes(q),
      );
    }
    if (this.bookAvailability === 'AVAILABLE') {
      rows = rows.filter((b) => Number(b.copiesAvailable || 0) > 0);
    } else if (this.bookAvailability === 'OUT') {
      rows = rows.filter((b) => Number(b.copiesAvailable || 0) <= 0);
    }
    return rows;
  }

  get filteredBooks(): any[] {
    const rows = sortRows(this.matchingBooks(), this.bookSortBy, this.bookSortDir);
    const start = this.bookPageIndex * this.bookPageSize;
    return rows.slice(start, start + this.bookPageSize);
  }

  get filteredBooksTotal(): number {
    return this.matchingBooks().length;
  }

  get bookPageLabel(): string {
    const meta = pageMeta(this.bookPageIndex, this.bookPageSize, this.filteredBooksTotal);
    return meta.from ? `${meta.from}–${meta.to} of ${this.filteredBooksTotal}` : '0 books';
  }

  clearBookFilters(): void {
    this.bookQ = '';
    this.bookAvailability = '';
    this.bookSortBy = 'title';
    this.bookSortDir = 'ASC';
    this.bookPageIndex = 0;
  }

  // ---------- Issue / return ----------

  onStudentSelected(student: StudentLookupRow): void {
    this.selectedStudent = student;
    this.issueDraft.studentId = student.id || '';
    this.issueDraft.admissionNo = student.admissionNo || '';
    this.issueDraft.studentName = student.fullName || '';
    this.issueDraft.classSection = student.classSection || '';
    this.error = '';
  }

  onStudentCleared(): void {
    this.selectedStudent = null;
    this.issueDraft.studentId = '';
    this.issueDraft.admissionNo = '';
    this.issueDraft.studentName = '';
    this.issueDraft.classSection = '';
  }

  get availableBooks(): any[] {
    return sortRows(
      this.books.filter((b) => Number(b.copiesAvailable || 0) > 0),
      'title',
      'ASC',
    );
  }

  get studentOpenIssues(): any[] {
    const adm = String(this.selectedStudent?.admissionNo || '').trim().toLowerCase();
    if (!adm) return [];
    return this.issues.filter(
      (i) => String(i.admissionNo || '').trim().toLowerCase() === adm,
    );
  }

  issueBook(): void {
    if (!this.selectedStudent) {
      this.error = 'Search and select a student before issuing the book';
      return;
    }
    if (!this.issueDraft.bookId) {
      this.error = 'Choose a book to issue';
      return;
    }
    this.submitting = true;
    this.error = '';
    this.api.post<any>('/api/library/circulation/issue', this.issueDraft).subscribe({
      next: () => {
        this.submitting = false;
        const name = this.issueDraft.studentName || 'student';
        this.statusMsg = `Book issued to ${name}. Open loans are listed under Issued Books.`;
        this.issueDraft.bookId = '';
        this.issueDraft.loanDays = 14;
        this.view = 'issued';
        this.issueQ = name;
        this.issuePageIndex = 0;
        this.loadCirculation();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Issue failed';
      },
    });
  }

  returnBook(id: string): void {
    this.submitting = true;
    this.error = '';
    this.api.post<any>(`/api/library/circulation/issues/${id}/return`, {}).subscribe({
      next: (res) => {
        this.submitting = false;
        const fine = Number(res?.fineAmount || 0);
        this.statusMsg = fine > 0 ? `Book returned. Late fine ₹${fine}.` : 'Book returned.';
        this.loadCirculation();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Return failed';
      },
    });
  }

  // ---------- Issued books list ----------

  bookTitle(bookId: string | null | undefined, fallbackTitle?: string): string {
    if (fallbackTitle) return String(fallbackTitle);
    const book = this.books.find((b) => String(b.id) === String(bookId));
    return book?.title || (bookId ? String(bookId).slice(0, 8) : '—');
  }

  isOverdue(issue: any): boolean {
    if (!issue?.dueAt) return false;
    return new Date(issue.dueAt).getTime() < Date.now();
  }

  overdueDays(issue: any): number {
    if (!this.isOverdue(issue)) return 0;
    return Math.floor((Date.now() - new Date(issue.dueAt).getTime()) / 86400000);
  }

  get issueClassOptions(): string[] {
    const set = new Set<string>();
    for (const i of this.issues) {
      const c = String(i.classSection || '').trim();
      if (c) set.add(c);
    }
    return [...set].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  }

  sortIssuesBy(key: string): void {
    const next = nextHeaderSort(this.issueSortBy, this.issueSortDir, key);
    this.issueSortBy = next.sortBy;
    this.issueSortDir = next.sortDir;
  }

  issueSortIcon(key: string): string {
    return headerSortIndicator(this.issueSortBy, this.issueSortDir, key);
  }

  private matchingIssues(): any[] {
    let rows = this.issues;
    const q = this.issueQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (i) =>
          String(i.studentName ?? '').toLowerCase().includes(q) ||
          String(i.admissionNo ?? '').toLowerCase().includes(q) ||
          String(i.classSection ?? '').toLowerCase().includes(q) ||
          this.bookTitle(i.bookId).toLowerCase().includes(q) ||
          String(i.bookTitle ?? '').toLowerCase().includes(q),
      );
    }
    if (this.issueClass) {
      rows = rows.filter((i) => String(i.classSection || '') === this.issueClass);
    }
    if (this.overdueOnly) {
      rows = rows.filter((i) => this.isOverdue(i));
    }
    return rows;
  }

  get filteredIssues(): any[] {
    const rows = sortRows(this.matchingIssues(), this.issueSortBy, this.issueSortDir, (row, key) =>
      key === 'bookTitle' ? this.bookTitle(row.bookId, row.bookTitle) : row?.[key],
    );
    const start = this.issuePageIndex * this.issuePageSize;
    return rows.slice(start, start + this.issuePageSize);
  }

  get filteredIssuesTotal(): number {
    return this.matchingIssues().length;
  }

  get overdueCount(): number {
    return this.issues.filter((i) => this.isOverdue(i)).length;
  }

  get openLoanNames(): string {
    const names = this.issues
      .map((i) => String(i.studentName || '').trim())
      .filter(Boolean)
      .slice(0, 3);
    if (!names.length) return 'students with open loans';
    if (this.issues.length > names.length) {
      return `${names.join(', ')} +${this.issues.length - names.length} more`;
    }
    return names.join(', ');
  }

  get issuePageLabel(): string {
    const meta = pageMeta(this.issuePageIndex, this.issuePageSize, this.filteredIssuesTotal);
    return meta.from ? `${meta.from}–${meta.to} of ${this.filteredIssuesTotal}` : '0 issues';
  }

  clearIssueFilters(): void {
    this.issueQ = '';
    this.issueClass = '';
    this.overdueOnly = false;
    this.issueSortBy = 'dueAt';
    this.issueSortDir = 'ASC';
    this.issuePageIndex = 0;
  }

  onWorkflowStudentSelected(student: StudentLookupRow): void {
    this.workflowStudent = student;
    this.setAnswer(['studentName', 'fullName'], student.fullName || '');
    this.setAnswer(['admissionNo', 'admissionNumber'], student.admissionNo || '');
    this.setAnswer(['classSection', 'class'], student.classSection || '');
    this.setAnswer(['email', 'studentEmail'], student.email || '');
    this.setAnswer(['mobile', 'phone', 'studentMobile'], student.mobile || '');
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

  onWorkflowBookSelected(bookId: string): void {
    this.workflowBookId = bookId;
    const book = this.books.find((b) => String(b.id) === String(bookId));
    this.setAnswer(['bookId'], book?.id || '');
    this.setAnswer(['bookTitle'], book?.title || '');
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
      'booktitle',
      'bookid',
    ].includes(normalized);
  }

  fmtDate(v?: string): string {
    if (!v) return '—';
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return String(v).slice(0, 10);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  // ---------- Workflow inbox ----------

  loadRecords(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/library/records', this.pageIndex, this.pageSize, {
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
    return s ? s.charAt(0) + s.slice(1).toLowerCase() : '—';
  }

  recordStatusClass(status: unknown): string {
    const s = String(status || '').toUpperCase();
    if (s === 'APPROVED') return 'ok';
    if (s === 'REJECTED') return 'bad';
    if (s === 'INFO_REQUESTED') return 'warn';
    return 'progress';
  }

  submit(): void {
    this.submitting = true;
    this.error = '';
    this.api
      .post<any>('/api/library/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selectedId = row.id;
          this.selected = row;
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
    this.api.get<any>(`/api/library/records/${row.id}`).subscribe({
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
      .post<any>(`/api/library/records/${this.selectedId}/actions`, {
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
      .filter(([, v]) => v !== null && v !== undefined && v !== '' && typeof v !== 'object')
      .map(([key, v]) => ({
        label: labelFor(key),
        value: typeof v === 'boolean' ? (v ? 'Yes' : 'No') : String(v),
      }));
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'LIBRARY_APPROVED') {
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

  private setAnswer(keys: string[], value: unknown): void {
    const targets = new Set(keys.map((k) => k.replace(/[^a-z0-9]/gi, '').toLowerCase()));
    for (const field of this.fields) {
      const normalized = field.key.replace(/[^a-z0-9]/gi, '').toLowerCase();
      if (targets.has(normalized)) {
        this.answers[field.key] = value;
      }
    }
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
